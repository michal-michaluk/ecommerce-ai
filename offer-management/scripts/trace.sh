#!/usr/bin/env bash
set -euo pipefail

usage() {
    cat >&2 <<EOF
Usage: trace.sh <spanId>            Show trace tree + correlated logs for a span
       trace.sh --requests          List HTTP requests (url + spanId) in time window
       trace.sh --errors            List errors/exceptions (spanId) in time window
       trace.sh --window 5m ...     Restrict to last N (e.g. 20s, 5m, 1h) [default: all]

Find a trace by spanId and show:
  - Trace tree (spans with parent/child relationships)
  - Correlated logs (by traceId)

Modes:
  - Local: reads traces.jsonl / logs.jsonl from current directory or OTEL_DATA_DIR
  - K3s:   reads the collector's emptyDir via the k3s node container (podman exec).
           The collector image is distroless (no shell), so kubectl exec/cp
           into the pod does NOT work — reading via the node is required.

Options:
  -t, --tree        Show only span tree (default: tree + logs)
  -l, --logs        Show only correlated logs
  -r, --requests    List HTTP requests (method, url, status, spanId, duration)
  -e, --errors      List error spans (status.code==2) and ERROR/WARN log records
  -w, --window DUR  Time window to search (e.g. 20s, 5m, 1h); default: all data
  -n, --namespace   K8s namespace (default: auto-detect collector across namespaces)
  -c, --container    k3s node container name (default: auto-detect, e.g. offer-management-k3s)
  -h, --help        Show this help
EOF
}

TRACE_FILE="traces.jsonl"
LOG_FILE="logs.jsonl"
NAMESPACE=""
K3S_CONTAINER=""
WINDOW_SECS=""
SHOW_TREE=true
SHOW_LOGS=true
LIST_REQUESTS=false
LIST_ERRORS=false
SPAN_ID=""

# parse duration like 20s / 5m / 1h / 30m10s -> seconds
parse_dur() {
    local d="$1" total=0 num unit
    while [[ "$d" =~ ^([0-9]+)([smh])(.*)$ ]]; do
        num=${BASH_REMATCH[1]}; unit=${BASH_REMATCH[2]}; d=${BASH_REMATCH[3]}
        case "$unit" in
            s) total=$((total + num)) ;;
            m) total=$((total + num * 60)) ;;
            h) total=$((total + num * 3600)) ;;
        esac
    done
    [[ "$total" -eq 0 ]] && { echo "ERROR: invalid duration '$1' (use e.g. 20s, 5m, 1h)" >&2; exit 1; }
    echo "$total"
}

[[ $# -eq 0 ]] && { usage; exit 1; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        -t|--tree) SHOW_LOGS=false; shift ;;
        -l|--logs) SHOW_TREE=false; shift ;;
        -r|--requests) LIST_REQUESTS=true; SHOW_TREE=false; SHOW_LOGS=false; shift ;;
        -e|--errors) LIST_ERRORS=true; SHOW_TREE=false; SHOW_LOGS=false; shift ;;
        -w|--window) WINDOW_SECS=$(parse_dur "$2"); shift 2 ;;
        -n|--namespace) NAMESPACE="$2"; shift 2 ;;
        -c|--container) K3S_CONTAINER="$2"; shift 2 ;;
        *) SPAN_ID="$1"; shift ;;
    esac
done

[[ -z "$SPAN_ID" && "$LIST_REQUESTS" == false && "$LIST_ERRORS" == false ]] && { echo "ERROR: spanId required (or use --requests/--errors)" >&2; usage >&2; exit 1; }

# Determine data source
if [[ -n "${OTEL_DATA_DIR:-}" ]]; then
    TRACE_FILE="${OTEL_DATA_DIR}/traces.jsonl"
    LOG_FILE="${OTEL_DATA_DIR}/logs.jsonl"
fi

# cutoff ns for time window (epoch ns). Empty = no window.
compute_cutoff_ns() {
    local now_ns cutoff_ns
    if [[ -n "$WINDOW_SECS" ]]; then
        now_ns=$(date +%s%N)
        cutoff_ns=$((now_ns - WINDOW_SECS * 1000000000))
        echo "$cutoff_ns"
    fi
}

# Auto-detect the k3s node container name (the control-plane container), e.g.
# offer-management-k3s, from running containers if not given.
detect_k3s_container() {
    podman ps --format '{{.Names}}' 2>/dev/null | grep -E '^offer-management-k3s$|k3s' | head -1
}

# Locate the collector's emptyDir data dir on the k3s node.
find_collector_pod() {
    if [[ -z "$NAMESPACE" ]]; then
        kubectl get pods -A -l app=otel-collector -o jsonpath='{range .items[*]}{.metadata.namespace}{" "}{.metadata.name}{"\n"}{end}' 2>/dev/null | head -1
    else
        kubectl get pods -n "$NAMESPACE" -l app=otel-collector -o jsonpath='{.items[0].metadata.namespace} {.items[0].metadata.name}' 2>/dev/null
    fi
}

collector_data_dir_on_node() {
    local node pod uid dir podinfo ns
    [[ -z "$K3S_CONTAINER" ]] && K3S_CONTAINER=$(detect_k3s_container)
    [[ -z "$K3S_CONTAINER" ]] && { echo "ERROR: cannot detect the k3s node container" >&2; return 1; }

    node=$(podman ps --format '{{.Names}}' | grep "^${K3S_CONTAINER}$" | head -1)
    [[ -z "$node" ]] && { echo "ERROR: no k3s node container $K3S_CONTAINER" >&2; return 1; }

    podinfo=$(find_collector_pod)
    [[ -z "$podinfo" ]] && { echo "ERROR: no otel-collector pod found" >&2; return 1; }
    ns=${podinfo%% *}
    pod=${podinfo##* }

    uid=$(kubectl get pod -n "$ns" "$pod" -o jsonpath='{.metadata.uid}')
    dir=$(podman exec "$node" sh -c "find /var/lib/kubelet/pods/${uid}/volumes -name traces.jsonl -path '*otel*' 2>/dev/null | head -1" 2>/dev/null)
    echo "${dir%/traces.jsonl}"
}

# Stream the given jsonl file (local or via k3s node)
emit_file() {
    local file="$1" dir node
    if [[ -f "$file" ]]; then
        cat "$file"
    else
        dir=$(collector_data_dir_on_node)
        [[ -z "$dir" ]] && { echo "ERROR: no local files and could not read collector data" >&2; exit 1; }
        [[ -z "$K3S_CONTAINER" ]] && K3S_CONTAINER=$(detect_k3s_container)
        [[ -z "$K3S_CONTAINER" ]] && { echo "ERROR: cannot detect the k3s node container" >&2; exit 1; }
        podman exec "$K3S_CONTAINER" sh -c "cat $dir/$(basename $file)"
    fi
}

# Run jq over the given file
run_jq() {
    local file="$1"; shift
    emit_file "$file" | jq "$@"
}

# window filter: returns jq condition on timestamp var $t; 'true' if no window
window_cond() {
    local cutoff
    cutoff=$(compute_cutoff_ns)
    if [[ -n "$cutoff" ]]; then
        echo "\$t >= ${cutoff}"
    else
        echo "true"
    fi
}

# ============================================================================
# MODE: list HTTP requests
# ============================================================================
if $LIST_REQUESTS; then
    WC=$(window_cond)
    echo "=== HTTP Requests ${WINDOW_SECS:+last ${WINDOW_SECS}s} ==="
    run_jq "$TRACE_FILE" -r --arg wc "$WC" '
      ..|objects|.resourceSpans[]?.scopeSpans[]?.spans[]?
      | select(.attributes? | map(.key == "http.request.method") | any)
      | . as $s | (.startTimeUnixNano | tonumber) as $t
      | select('"$WC"')
      | $s
      | [.spanId,
         ( (.attributes[]|select(.key=="http.request.method")|.value.stringValue // .value.intValue) // "?" ),
         ( (.attributes[]|select(.key=="url.full")|.value.stringValue // .value.intValue) // 
           (.attributes[]|select(.key=="url.path")|.value.stringValue // .value.intValue) // "?" ),
         ( (.attributes[]|select(.key=="http.response.status_code")|.value.stringValue // .value.intValue) // "?" ),
         ((((.endTimeUnixNano//0|tonumber)-(.startTimeUnixNano//0|tonumber))/1000000) // 0)]
      | "\(.[0])  \(.[1]) \(.[2])  status=\(.[3])  \(.[4])ms"'
    echo ""
    exit 0
fi

# ============================================================================
# MODE: list errors / exceptions
# ============================================================================
if $LIST_ERRORS; then
    WC=$(window_cond)
    echo "=== Errors / Exceptions ${WINDOW_SECS:+last ${WINDOW_SECS}s} ==="
    echo "--- error spans (status.code == 2) ---"
    run_jq "$TRACE_FILE" -r --arg wc "$WC" '
      ..|objects|.resourceSpans[]?.scopeSpans[]?.spans[]?
      | select(.status.code? == 2)
      | . as $s | (.startTimeUnixNano | tonumber) as $t
      | select('"$WC"')
      | $s
      | "\(.spanId)  \(.name)  status=\(.status.message // "ERROR")"'
    echo "--- error/warn log records ---"
    run_jq "$LOG_FILE" -r --arg wc "$WC" '
      ..|objects|.logRecords[]?
      | select(.severityText? == "ERROR" or .severityText? == "WARN")
      | . as $s | (.timeUnixNano | tonumber) as $t
      | select('"$WC"')
      | $s
      | "\(.spanId // "-")  [\(.severityText)]  \(.body.stringValue // .body // "?")"'
    echo ""
    exit 0
fi

# ============================================================================
# MODE: trace by spanId (default)
# ============================================================================
echo "=== Looking up span: $SPAN_ID ==="
TRACE_ID=$(run_jq "$TRACE_FILE" -r --arg s "$SPAN_ID" '..|select(.spanId?==$s)|.traceId' | head -1)

if [[ -z "$TRACE_ID" ]]; then
    echo "ERROR: Span $SPAN_ID not found in traces" >&2
    exit 1
fi
echo "traceId: $TRACE_ID"
echo ""

if $SHOW_TREE; then
    echo "=== Span Tree ==="
    run_jq "$TRACE_FILE" -r --arg t "$TRACE_ID" '..|objects|.resourceSpans[]?.scopeSpans[]?.spans[]?|select(.traceId?==$t)|"\(.spanId) parent=\(.parentSpanId // "root") name=\(.name // "?") kind=\(.kind // "?")"'
    echo ""
fi

if $SHOW_LOGS; then
    echo "=== Correlated Logs ==="
    run_jq "$LOG_FILE" -r --arg t "$TRACE_ID" '..|objects|.logRecords[]?|select(.traceId?==$t)|"\(.severityText // "?") \(.body.stringValue // .body // "?")"' || echo "(no log records found)"
fi