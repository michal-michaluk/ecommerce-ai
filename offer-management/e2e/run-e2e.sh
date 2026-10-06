#!/usr/bin/env bash
#
# Runs the element-02 frontend API contract (e2e/offer-management.hurl) against a running
# offer-management: mints the Keycloak tokens, ensures the Keycloak port-forward, runs
# `hurl --test`, and exits non-zero on any contract failure.
#
# Env:
#   BASE_URL       the offer-management ingress            (default http://localhost:8080)
#   KEYCLOAK_URL   the Keycloak realm base                 (default http://localhost:18081)
#   KEYCLOAK_PORT  local port for the port-forward         (default 18081)
#   KUBECONFIG     kubeconfig for the port-forward         (default <root>/.k3s-kubeconfig)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
HURL_FILE="${SCRIPT_DIR}/offer-management.hurl"

BASE_URL="${BASE_URL:-http://localhost:8080}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:18081}"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-18081}"
KUBECONFIG="${KUBECONFIG:-${ROOT_DIR}/.k3s-kubeconfig}"
NAMESPACE="${NAMESPACE:-offer-management}"
REALM="${REALM:-iot}"
CLIENT_ID="${CLIENT_ID:-iot-service}"
CLIENT_SECRET="${CLIENT_SECRET:-secret}"

log() { printf '[run-e2e] %s\n' "$*" >&2; }
die() { printf '[run-e2e] ERROR: %s\n' "$*" >&2; exit 1; }

require() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }

realm_ready() {
    curl -fsS --max-time 5 "${KEYCLOAK_URL}/realms/${REALM}/.well-known/openid-configuration" >/dev/null 2>&1
}

ensure_keycloak() {
    if realm_ready; then
        log "Keycloak reachable at ${KEYCLOAK_URL}"
        return
    fi
    require kubectl
    [[ -f "${KUBECONFIG}" ]] || die "Keycloak unreachable at ${KEYCLOAK_URL} and no kubeconfig at ${KUBECONFIG} to port-forward"
    log "Keycloak unreachable; port-forwarding svc/offer-management-keycloak to ${KEYCLOAK_PORT}"
    KUBECONFIG="${KUBECONFIG}" kubectl -n "${NAMESPACE}" \
        port-forward svc/offer-management-keycloak "${KEYCLOAK_PORT}:8080" \
        >"${ROOT_DIR}/.k3s-keycloak-port-forward.log" 2>&1 &
    local pid=$!
    for _ in $(seq 1 30); do
        realm_ready && { KEYCLOAK_URL="http://localhost:${KEYCLOAK_PORT}"; log "Keycloak reachable at ${KEYCLOAK_URL}"; return; }
        kill -0 "${pid}" 2>/dev/null || break
        sleep 2
    done
    die "Keycloak port-forward did not become reachable; see ${ROOT_DIR}/.k3s-keycloak-port-forward.log"
}

mint() {
    local username="$1" password="$2" token
    token="$(curl -fsS --max-time 10 -X POST \
        "${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token" \
        -d "grant_type=password&client_id=${CLIENT_ID}&client_secret=${CLIENT_SECRET}" \
        -d "username=${username}&password=${password}" \
        | python3 -c 'import sys,json;print(json.load(sys.stdin)["access_token"])')" \
        || die "could not mint a token for ${username}"
    [[ -n "${token}" ]] || die "empty token for ${username}"
    printf '%s' "${token}"
}

seed_fixture() {
    local product_id photo_id
    product_id="$(curl -fsS --max-time 10 -X POST "${BASE_URL}/products" -H "Authorization: Bearer ${content_manager_token}" -H 'Content-Type: application/json' -d '{"title":"E2E photo fixture","category":"Ogród"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["productId"])')"
    [[ -n "${product_id}" ]] || die "could not seed the photo fixture product"
    photo_id="$(curl -fsS --max-time 10 -X POST "${BASE_URL}/products/${product_id}/description-draft/photos" -H "Authorization: Bearer ${content_manager_token}" -F "file=@${SCRIPT_DIR}/fixtures/photo.jpeg;type=image/jpeg" | python3 -c 'import sys,json;print(json.load(sys.stdin)["photoId"])')"
    [[ -n "${photo_id}" ]] || die "could not seed the photo fixture"
    printf '%s %s\n' "${product_id}" "${photo_id}"
}

# The rejection pair must decide a real pending review (RULE-19 makes a decision terminal),
# so the harness opens a fresh pending review on its own draft — the same fixture pattern as
# the photo above. The .hurl then rejects it and asserts the rejected body.
seed_reject_fixture() {
    local product_id review_request_id
    product_id="$(curl -fsS --max-time 10 -X POST "${BASE_URL}/products" -H "Authorization: Bearer ${content_manager_token}" -H 'Content-Type: application/json' -d '{"title":"E2E reject fixture","category":"Ogród"}' | python3 -c 'import sys,json;print(json.load(sys.stdin)["productId"])')"
    [[ -n "${product_id}" ]] || die "could not seed the reject fixture product"
    review_request_id="$(curl -fsS --max-time 10 -X POST "${BASE_URL}/products/${product_id}/description-draft/review-requests" -H "Authorization: Bearer ${content_manager_token}" | python3 -c 'import sys,json;print(json.load(sys.stdin)["reviewRequestId"])')"
    [[ -n "${review_request_id}" ]] || die "could not seed the reject fixture review"
    printf '%s\n' "${review_request_id}"
}

main() {
    require hurl
    require curl
    require python3
    [[ -f "${HURL_FILE}" ]] || die "suite not found: ${HURL_FILE}"

    ensure_keycloak

    log "minting tokens (content_manager=author=carla, reviewer=marta, sales=sara)"
    content_manager_token="$(mint carla carla)"
    author_token="$(mint carla carla)"
    reviewer_token="$(mint marta marta)"
    sales_token="$(mint sara sara)"

    # The photo-removal pair must delete a real photo: the main flow keeps its only
    # photo for the publish gate, so the harness seeds a dedicated fixture product.
    log "seeding the photo-removal fixture"
    read -r fixture_product_id fixture_photo_id < <(seed_fixture)
    [[ -n "${fixture_product_id}" && -n "${fixture_photo_id}" ]] || die "photo fixture seeding failed"

    # The rejection pair must decide a real pending review (RULE-19: a decision is terminal).
    log "seeding the pending rejection fixture"
    reject_review_id="$(seed_reject_fixture)"
    [[ -n "${reject_review_id}" ]] || die "reject fixture seeding failed"

    log "running hurl --test against ${BASE_URL}"
    ( cd "${SCRIPT_DIR}" && hurl --test --continue-on-error \
        --variable "BASE_URL=${BASE_URL}" \
        --variable "content_manager_token=${content_manager_token}" \
        --variable "author_token=${author_token}" \
        --variable "reviewer_token=${reviewer_token}" \
        --variable "sales_token=${sales_token}" \
        --variable "fixture_product_id=${fixture_product_id}" \
        --variable "fixture_photo_id=${fixture_photo_id}" \
        --variable "reject_review_id=${reject_review_id}" \
        "${HURL_FILE}" )
}

main "$@"
