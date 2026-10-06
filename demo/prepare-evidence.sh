#!/usr/bin/env bash
#
# Captures the non-browser evidence that demo/record.js puts on screen:
# cluster state, the hurl contract run, the Storybook gate, the Gradle gate and its
# parsed results. Re-run this immediately before a take so the video shows current output.
#
# PW_NODE_PATH must point at a `node_modules` holding the `playwright` package;
# .storybook/ does not depend on playwright itself.
#
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
ROOT="$PWD"
EV="demo/evidence"
OUT="demo/out"
# The ambient KUBECONFIG often points at a stale cluster; this demo always targets the
# repo's k3s-in-podman kubeconfig.
KUBECONFIG="${ROOT}/offer-management/.k3s-kubeconfig"
K3S_KUBECONFIG="${KUBECONFIG}"
PW_NODE_PATH="${PW_NODE_PATH:-/opt/homebrew/lib/node_modules/@playwright/cli/node_modules}"
export KUBECONFIG K3S_KUBECONFIG

mkdir -p "${EV}" "${OUT}"

log() { printf '[evidence] %s\n' "$*" >&2; }

# ── cluster state ────────────────────────────────────────────────────────────
log "capturing pod state"
{
    printf '$ kubectl -n offer-management get pods\n'
    kubectl -n offer-management get pods --no-headers
} > "${EV}/pods.txt"

# ── hurl contract suite ──────────────────────────────────────────────────────
log "running the element-02 contract suite (hurl)"
bash offer-management/e2e/run-e2e.sh > "${OUT}/e2e.log" 2>&1 || true
{
    printf '$ bash offer-management/e2e/run-e2e.sh\n'
    awk '/^error: Assert status code/{seen=1} seen' "${OUT}/e2e.log"
} > "${EV}/e2e.txt"

# ── Storybook prototype gate ─────────────────────────────────────────────────
log "running the Storybook gate (9 stories + 4 click-through flows)"
{
    printf '$ (cd .storybook && NODE_PATH=%s node ./verify-storybook.cjs http://localhost:6006)\n' "${PW_NODE_PATH}"
    ( cd .storybook && NODE_PATH="${PW_NODE_PATH}" node ./verify-storybook.cjs http://localhost:6006 ) 2>&1
} > "${OUT}/storybook-gate.log" 2>&1 || log "storybook gate exited non-zero (recorded verbatim)"
cp "${OUT}/storybook-gate.log" "${EV}/storybook-gate.txt"

# ── browsing-offer integration run ───────────────────────────────────────────
log "running the I6 integration run (restarts the deployment; ~3 min)"
bash offer-management/e2e/run-integration.sh > "${OUT}/integration.log" 2>&1 || log "integration exited non-zero (recorded verbatim)"
{
    printf '$ bash offer-management/e2e/run-integration.sh\n'
    grep -E '^\[i6\] (PASS|FAIL)|^\[i6\] [0-9]+ passed|read model after' "${OUT}/integration.log"
} > "${EV}/integration.txt"

# ── Gradle gate ──────────────────────────────────────────────────────────────
log "running ./gradlew test --rerun-tasks (this takes ~40s)"
( cd offer-management && ./gradlew test --no-daemon --rerun-tasks > "${ROOT}/${OUT}/gradle-test.txt" 2>&1 ) \
    || log "gradle exited non-zero (recorded verbatim)"
{
    printf '$ cd offer-management && ./gradlew test --no-daemon --rerun-tasks\n'
    grep -E '^> Task :|^BUILD |^GRADLE_EXIT' "${OUT}/gradle-test.txt"
} > "${EV}/gradle-test.txt"

# ── parsed test results ──────────────────────────────────────────────────────
log "parsing build/test-results/test/*.xml"
python3 - "${ROOT}/offer-management/build/test-results/test" "${EV}/tests-summary.json" <<'PY'
import glob, json, os, sys, xml.etree.ElementTree as ET

results_dir, out_path = sys.argv[1], sys.argv[2]
totals = {"classes": 0, "tests": 0, "failures": 0, "errors": 0, "skipped": 0}
arch = []
for path in sorted(glob.glob(os.path.join(results_dir, "*.xml"))):
    root = ET.parse(path).getroot()
    totals["classes"] += 1
    for key in ("tests", "failures", "errors", "skipped"):
        totals[key] += int(root.get(key, 0))
    for case in root.iter("testcase"):
        cls = case.get("classname", "")
        if "ArchitectureOf" in cls:
            entry = next((a for a in arch if a["class"] == cls), None)
            if entry is None:
                entry = {"class": cls, "tests": 0, "failures": 0}
                arch.append(entry)
            entry["tests"] += 1
            if case.find("failure") is not None or case.find("error") is not None:
                entry["failures"] += 1

json.dump({"totals": totals, "archunit": arch}, open(out_path, "w"), indent=2)
print(json.dumps({"totals": totals, "archunit": [a["class"] for a in arch]}, indent=2))
PY

cp offer-management/e2e/fixtures/photo.jpeg "${EV}/photo.jpeg"

# run-integration.sh tears its own port-forward down on exit; the recorder needs one.
log "re-establishing the consumer port-forward on :18090"
pkill -f "port-forward svc/browsing-offer-consumer" 2>/dev/null || true
nohup kubectl -n offer-management port-forward svc/browsing-offer-consumer 18090:8080 \
    > "${OUT}/consumer-forward.log" 2>&1 &
for _ in $(seq 1 20); do
    curl -fsS --max-time 2 http://localhost:18090/ >/dev/null 2>&1 && break
    sleep 1
done
curl -fsS --max-time 2 http://localhost:18090/ >/dev/null 2>&1 \
    && log "consumer read model reachable on :18090" \
    || log "WARNING: consumer read model not reachable on :18090"

log "evidence ready in ${EV}"
