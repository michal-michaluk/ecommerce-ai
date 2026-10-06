#!/usr/bin/env bash
#
# I6 e2e — proves the offer-management -> browsing offer event boundary end to end
# against the live k3s stack. It drives the real lifecycle through the HTTP API
# with role tokens, then reads the in-cluster consumer's materialised read model
# (offer-management/k8s/infra/browsing-offer-consumer.yaml) after each boundary
# event, and asserts that only the three element-03 events cross the topic.
#
# Exits non-zero on the first failed assertion.
#
# Env:
#   BASE_URL        offer-management ingress          (default http://localhost:8080)
#   KEYCLOAK_URL    Keycloak realm base               (default http://localhost:18081)
#   KUBECONFIG      kubeconfig                        (default <root>/.k3s-kubeconfig)
#   CONSUMER_PORT   local port for the consumer svc   (default 18090)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:18081}"
KUBECONFIG="${K3S_KUBECONFIG:-${ROOT_DIR}/.k3s-kubeconfig}"
CONSUMER_PORT="${CONSUMER_PORT:-18090}"
NAMESPACE="${NAMESPACE:-offer-management}"
REALM="${REALM:-iot}"
CLIENT_ID="${CLIENT_ID:-iot-service}"
CLIENT_SECRET="${CLIENT_SECRET:-secret}"
CONSUMER_MANIFEST="${ROOT_DIR}/k8s/infra/browsing-offer-consumer.yaml"

BUSINESS_DATE="$(TZ=Europe/Warsaw date +%F)"
FORWARD_PID=""

PASS=0
FAIL=0
declare -a RESULTS=()

log() { printf '[i6] %s\n' "$*" >&2; }
ok() { PASS=$((PASS + 1)); RESULTS+=("PASS  $1"); printf '[i6] PASS  %s\n' "$1" >&2; }
bad() { FAIL=$((FAIL + 1)); RESULTS+=("FAIL  $1"); printf '[i6] FAIL  %s\n' "$1" >&2; }
die() { printf '[i6] ERROR: %s\n' "$*" >&2; exit 1; }

kube() { KUBECONFIG="${KUBECONFIG}" kubectl "$@"; }

cleanup() {
    [[ -n "${FORWARD_PID}" ]] && kill "${FORWARD_PID}" 2>/dev/null || true
    pkill -f "port-forward svc/browsing-offer-consumer" 2>/dev/null || true
}
trap cleanup EXIT

ensure_keycloak() {
    if curl -fsS --max-time 5 "${KEYCLOAK_URL}/realms/${REALM}/.well-known/openid-configuration" >/dev/null 2>&1; then
        return
    fi
    log "Keycloak unreachable; port-forwarding"
    KUBECONFIG="${KUBECONFIG}" kubectl -n "${NAMESPACE}" port-forward svc/offer-management-keycloak 18081:8080 \
        >"${ROOT_DIR}/.k3s-keycloak-port-forward.log" 2>&1 &
    for _ in $(seq 1 30); do
        curl -fsS --max-time 5 "${KEYCLOAK_URL}/realms/${REALM}/.well-known/openid-configuration" >/dev/null 2>&1 && return
        sleep 2
    done
    die "Keycloak port-forward did not become reachable"
}

mint() {
    curl -fsS --max-time 10 -X POST \
        "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
        -d "grant_type=password&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}" \
        -d "username=$1&password=$1" \
        | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])'
}

ensure_consumer() {
    log "applying the consumer manifest"
    kube apply --validate=false -f "${CONSUMER_MANIFEST}" >/dev/null
    kube -n "${NAMESPACE}" rollout status deploy/browsing-offer-consumer --timeout=120s >/dev/null
    FORWARD_PID="$(kube -n "${NAMESPACE}" port-forward svc/browsing-offer-consumer "${CONSUMER_PORT}:8080" >/dev/null 2>&1 &
        echo $!)"
    sleep 2
    if curl -fsS --max-time 5 "http://localhost:${CONSUMER_PORT}/" >/dev/null 2>&1; then
        log "consumer read model reachable over HTTP on :${CONSUMER_PORT}"
    else
        log "consumer HTTP not reachable; falling back to kubectl exec for the read model"
    fi
}

# The consumer read model, queried over HTTP; falls back to the pod filesystem.
readmodel() {
    local body
    if body="$(curl -fsS --max-time 5 "http://localhost:${CONSUMER_PORT}/" 2>/dev/null)"; then
        printf '%s' "${body}"
        return
    fi
    kube -n "${NAMESPACE}" exec deploy/browsing-offer-consumer -- cat /www/readmodel.ndjson 2>/dev/null
}

# product_json <productId> -> the product's read model line
product_json() {
    readmodel | python3 -c '
import sys, json
pid = sys.argv[1]
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    try:
        doc = json.loads(line)
    except json.JSONDecodeError:
        continue
    if doc.get("productId") == pid:
        print(json.dumps(doc))
        break
' "$1"
}

# wait_product <productId> <python-expression over doc> <description> <timeout-seconds>
wait_product() {
    local pid="$1" expr="$2" desc="$3" timeout="${4:-60}" deadline doc
    deadline=$(( $(date +%s) + timeout ))
    while :; do
        doc="$(product_json "${pid}" || true)"
        if [[ -n "${doc}" ]] && python3 -c 'import json,sys;doc=json.loads(sys.argv[1]);sys.exit(0 if eval(sys.argv[2]) else 1)' "${doc}" "${expr}" 2>/dev/null; then
            printf '%s\n' "${doc}"
            return 0
        fi
        [[ "$(date +%s)" -lt "${deadline}" ]] || return 1
        sleep 2
    done
}

api() { curl -fsS --max-time 20 --retry 5 --retry-all-errors --retry-delay 2 "$@"; }

main() {
    command -v curl >/dev/null || die "curl required"
    command -v python3 >/dev/null || die "python3 required"

    ensure_keycloak
    log "minting role tokens (carla=content_manager/autor, marta=reviewer, sara=sales)"
    local carla marta sara
    carla="$(mint carla)"
    marta="$(mint marta)"
    sara="$(mint sara)"
    [[ -n "${carla}" && -n "${marta}" && -n "${sara}" ]] || die "token minting failed"

    ensure_consumer

    log "business date (Europe/Warsaw) = ${BUSINESS_DATE}"

    # ── create product ────────────────────────────────────────────────────
    local create product_id
    create="$(api -X POST "${BASE_URL}/products" -H "Authorization: Bearer ${carla}" \
        -H 'Content-Type: application/json' \
        -d "{\"title\":\"I6 boundary probe ${BUSINESS_DATE}\",\"category\":\"Ogród\"}")"
    product_id="$(printf '%s' "${create}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["productId"])')"
    log "created product ${product_id}"
    [[ -n "${product_id}" ]] || die "no productId"

    # ── save draft (private DescriptionUpdated) ───────────────────────────
    api -X PUT "${BASE_URL}/products/${product_id}/description-draft" \
        -H "Authorization: Bearer ${carla}" -H 'Content-Type: application/json' \
        -d '{"title":"Kosiarka ręczna 340 (I6)","description":"Solidna kosiarka ręczna do trawy i chwastów."}' >/dev/null

    # ── add photo (private PhotoAddedInRightFormats) ──────────────────────
    api -X POST "${BASE_URL}/products/${product_id}/description-draft/photos" \
        -H "Authorization: Bearer ${carla}" \
        -F "file=@${SCRIPT_DIR}/fixtures/photo.jpeg;type=image/jpeg" >/dev/null

    local draft version
    draft="$(api "${BASE_URL}/products/${product_id}/description-draft" -H "Authorization: Bearer ${carla}")"
    version="$(printf '%s' "${draft}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["version"])')"
    log "current draft version ${version}"

    # ── request review, approve as a different person (private) ───────────
    local review review_id
    review="$(api -X POST "${BASE_URL}/products/${product_id}/description-draft/review-requests" \
        -H "Authorization: Bearer ${carla}")"
    review_id="$(printf '%s' "${review}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["reviewRequestId"])')"
    api -X POST "${BASE_URL}/review-requests/${review_id}/approval" -H "Authorization: Bearer ${marta}" >/dev/null
    log "review ${review_id} approved by marta"

    # ── schedule price + discount effective today (feed the price sweep) ──
    api -X POST "${BASE_URL}/products/${product_id}/prices" -H "Authorization: Bearer ${sara}" \
        -H 'Content-Type: application/json' \
        -d "{\"kind\":\"PRICE\",\"amount\":{\"value\":\"259.00\",\"currency\":\"PLN\"},\"validFrom\":\"${BUSINESS_DATE}\",\"validTo\":null}" >/dev/null
    api -X POST "${BASE_URL}/products/${product_id}/prices" -H "Authorization: Bearer ${sara}" \
        -H 'Content-Type: application/json' \
        -d "{\"kind\":\"DISCOUNT\",\"percent\":\"10\",\"validFrom\":\"${BUSINESS_DATE}\",\"validTo\":null}" >/dev/null
    log "scheduled PRICE 259.00 PLN and DISCOUNT 10% effective ${BUSINESS_DATE}"

    # ── publish (ProductVersionPublishedToOffer) ──────────────────────────
    local publication
    publication="$(api -X POST "${BASE_URL}/products/${product_id}/publications" \
        -H "Authorization: Bearer ${carla}" -H 'Content-Type: application/json' \
        -d "{\"descriptionVersion\":\"${version}\",\"availableFrom\":null}")"
    log "publish response: ${publication}" >&2

    local doc
    if doc="$(wait_product "${product_id}" "doc['lastEvent']=='ProductVersionPublishedToOffer_v1' and doc['present'] is True and doc['version'] is not None" "published version reaches consumer" 45)"; then
        printf '  read model after publish: %s\n' "${doc}" >&2
        ok "ProductVersionPublishedToOffer reached the consumer; present=true, version=${version}"
    else
        bad "ProductVersionPublishedToOffer did not reach the consumer"
    fi

    # ── trigger the SCHEDULED->ACTIVE price sweep (ProductPricesChanged) ──
    log "restarting offer-management to run the startup price sweep (PT1H cadence otherwise)"
    kube -n "${NAMESPACE}" rollout restart deploy/offer-management >/dev/null
    kube -n "${NAMESPACE}" rollout status deploy/offer-management --timeout=300s >/dev/null

    if doc="$(wait_product "${product_id}" "doc['lastEvent']=='ProductPricesChanged_v1' and doc['price'] is not None and doc['discountPercent']=='10'" "price change reaches consumer" 90)"; then
        printf '  read model after price activation: %s\n' "${doc}" >&2
        ok "ProductPricesChanged reached the consumer; price=${doc}"
    else
        bad "ProductPricesChanged did not reach the consumer"
    fi

    # ── remove from offer (ProductRemovedFromOffer) ───────────────────────
    api -X DELETE "${BASE_URL}/products/${product_id}/offer-presence" \
        -H "Authorization: Bearer ${carla}" >/dev/null
    if doc="$(wait_product "${product_id}" "doc['lastEvent']=='ProductRemovedFromOffer_v1' and doc['present'] is False" "removal reaches consumer" 45)"; then
        printf '  read model after removal: %s\n' "${doc}" >&2
        ok "ProductRemovedFromOffer reached the consumer; present=false"
    else
        bad "ProductRemovedFromOffer did not reach the consumer"
    fi

    # ── boundary integrity: exactly the three event types, nothing else ───
    local raw
    raw="$(kube -n "${NAMESPACE}" exec deploy/kafka -- \
        /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server offer-management-kafka:9092 \
        --topic browsing-offer --from-beginning --timeout-ms 8000 2>/dev/null || true)"
    printf '%s\n' "${raw}" | python3 -c 'import sys,json
allowed={"ProductVersionPublishedToOffer_v1","ProductPricesChanged_v1","ProductRemovedFromOffer_v1"}
ts=set()
for l in sys.stdin:
    l=l.strip()
    if not l: continue
    try: ts.add(json.loads(l).get("@type"))
    except Exception: pass
ts={t for t in ts if t}
print("  topic event types: " + ", ".join(sorted(ts)), file=sys.stderr)
print("\n".join(sorted(ts-allowed)))' > /tmp/i6-illegal.txt
    printf '%s\n' "${raw}" | python3 -c 'import sys,json
expected={"ProductVersionPublishedToOffer_v1","ProductPricesChanged_v1","ProductRemovedFromOffer_v1"}
pid=sys.argv[1]
ts=set()
for l in sys.stdin:
    l=l.strip()
    if not l: continue
    try: d=json.loads(l)
    except Exception: continue
    if d.get("productId")==pid: ts.add(d.get("@type"))
print("\n".join(sorted(expected-{t for t in ts if t})))' "${product_id}" > /tmp/i6-missing.txt
    local illegal missing
    illegal="$(cat /tmp/i6-illegal.txt)"
    missing="$(cat /tmp/i6-missing.txt)"
    if [[ -z "${illegal}" ]]; then
        ok "no private event crossed the boundary (topic types are a subset of the three)"
    else
        bad "private event leaked to the boundary: ${illegal}"
    fi
    if [[ -z "${missing}" ]]; then
        ok "all three boundary event types were observed on topic browsing-offer"
    else
        bad "boundary event types never published to browsing-offer: ${missing//$'\n'/, }"
    fi

    printf '\n[i6] %d passed, %d failed\n' "${PASS}" "${FAIL}" >&2
    [[ "${FAIL}" -eq 0 ]]
}

main "$@"
