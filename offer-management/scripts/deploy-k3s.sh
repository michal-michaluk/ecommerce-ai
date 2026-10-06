#!/usr/bin/env bash
#
# Deploy offer-management to a local k3s that runs as a privileged podman
# container (A14). There is no docker daemon and no k3d on this machine, so
# every container operation is podman and the app image is imported directly
# into the k3s containerd (A17).
#
# Usage: deploy-k3s.sh {deploy|run|all|teardown}
#   deploy    boot/reuse the k3s container, deploy PostgreSQL + app, wait for rollout,
#             port-forward Keycloak
#   run       port-forward Keycloak and check the app readiness endpoint (cluster already up)
#   all       deploy + readiness check
#   teardown  remove the k3s container, kubeconfig and port-forward
#
# Env: POSTGRES_MODE=helm|manifest (default helm), KEYCLOAK_PORT (default 18081).
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

CLUSTER_NAME="${CLUSTER_NAME:-offer-management-k3s}"
K3S_IMAGE="${K3S_IMAGE:-rancher/k3s:v1.35.5-k3s1}"
KUBECONFIG_FILE="${ROOT_DIR}/.k3s-kubeconfig"
FORWARD_PID_FILE="${ROOT_DIR}/.k3s-keycloak-forward.pid"
NAMESPACE="offer-management"
APP_IMAGE="offer-management:latest"
POSTGRES_RELEASE="offer-management-db"
POSTGRES_SERVICE="offer-management-db-postgresql"
POSTGRES_CHART="${POSTGRES_CHART:-oci://registry-1.docker.io/bitnamicharts/postgresql}"
# helm (A16, default) or manifest (k8s/infra/postgresql.yaml, no Helm required).
POSTGRES_MODE="${POSTGRES_MODE:-helm}"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-18081}"
APP_URL="${APP_URL:-http://localhost:8080}"

log() { printf '[deploy-k3s] %s\n' "$*" >&2; }
die() { printf '[deploy-k3s] ERROR: %s\n' "$*" >&2; exit 1; }
require() { command -v "$1" >/dev/null 2>&1 || die "$1 is required but not on PATH"; }

# k3s needs to create cgroups for its pods. A rootless podman machine runs the
# container inside a user namespace where /sys/fs/cgroup is not writable, so every
# pod sandbox fails with:
#   runc create failed: unable to apply cgroup configuration:
#   mkdir /sys/fs/cgroup/k8s.io: permission denied
# Fail fast with the remediation instead of leaving a broken cluster behind.
require_rootful_podman() {
    local rootful
    rootful="$(podman machine inspect --format '{{.Rootful}}' 2>/dev/null | head -1)"
    if [[ "${rootful}" != "true" ]]; then
        die "the podman machine is rootless (Rootful=${rootful:-unknown}); k3s pods cannot create cgroups. Remediation: 'podman machine set --rootful && podman machine stop && podman machine start', then rerun this script."
    fi
}

KUBECONFIG="${KUBECONFIG_FILE}"
kube() { KUBECONFIG="${KUBECONFIG_FILE}" kubectl "$@"; }

cluster_running() {
    podman container inspect -f '{{.State.Status}}' "${CLUSTER_NAME}" 2>/dev/null | grep -q running
}

cluster_exists() {
    podman container exists "${CLUSTER_NAME}" 2>/dev/null
}

boot_cluster() {
    require podman
    require_rootful_podman
    if cluster_running; then
        log "k3s container '${CLUSTER_NAME}' is already running"
        return
    fi
    if cluster_exists; then
        log "starting existing k3s container '${CLUSTER_NAME}'"
        podman start "${CLUSTER_NAME}" >/dev/null
    else
        log "creating k3s container '${CLUSTER_NAME}' from ${K3S_IMAGE}"
        podman run -d --privileged --name "${CLUSTER_NAME}" --cgroupns=host \
            -p 6443:6443 -p 8080:80 "${K3S_IMAGE}" server >/dev/null
    fi
}

wait_for_kubeconfig() {
    log "waiting for k3s to write /etc/rancher/k3s/k3s.yaml"
    for _ in $(seq 1 60); do
        if podman exec "${CLUSTER_NAME}" test -f /etc/rancher/k3s/k3s.yaml >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    die "k3s did not write its kubeconfig; inspect 'podman logs ${CLUSTER_NAME}'"
}

extract_kubeconfig() {
    log "extracting kubeconfig to ${KUBECONFIG_FILE}"
    podman exec "${CLUSTER_NAME}" cat /etc/rancher/k3s/k3s.yaml > "${KUBECONFIG_FILE}"
    sed -e 's#https://127.0.0.1:6443#https://localhost:6443#' \
        "${KUBECONFIG_FILE}" > "${KUBECONFIG_FILE}.tmp"
    mv "${KUBECONFIG_FILE}.tmp" "${KUBECONFIG_FILE}"
    chmod 600 "${KUBECONFIG_FILE}"
}

wait_for_cluster() {
    require kubectl
    log "waiting for the k3s API"
    for _ in $(seq 1 90); do
        if kube get --raw='/readyz' >/dev/null 2>&1; then
            kube wait --for=condition=Ready node --all --timeout=120s
            return
        fi
        sleep 2
    done
    die "the k3s API did not become ready; inspect 'podman logs ${CLUSTER_NAME}'"
}

create_namespaces() {
    kube apply -f "${ROOT_DIR}/k8s/overlays/k3s/namespace.yaml"
    kube create namespace devices --dry-run=client -o yaml | kube apply -f - >/dev/null 2>&1 || true
}

deploy_postgres() {
    case "${POSTGRES_MODE}" in
        helm)
            require helm
            log "deploying PostgreSQL via Helm (${POSTGRES_CHART}) as '${POSTGRES_RELEASE}'"
            helm upgrade --install "${POSTGRES_RELEASE}" "${POSTGRES_CHART}" \
                --namespace "${NAMESPACE}" --create-namespace \
                --set fullnameOverride="${POSTGRES_SERVICE}" \
                --set architecture=standalone \
                --set auth.username=offer-management \
                --set auth.password=offer-management \
                --set auth.database=offer-management \
                --set primary.persistence.enabled=false \
                --wait --timeout 5m
            ;;
        manifest)
            log "deploying PostgreSQL from k8s/infra/postgresql.yaml"
            kube apply -f "${ROOT_DIR}/k8s/infra/postgresql.yaml"
            kube -n "${NAMESPACE}" rollout status deploy/offer-management-db-postgresql --timeout=300s
            ;;
        *) die "POSTGRES_MODE must be 'helm' or 'manifest', got '${POSTGRES_MODE}'" ;;
    esac
}

build_and_import_image() {
    log "building the OCI image tar with jibBuildTar"
    ( cd "${ROOT_DIR}" && ./gradlew jibBuildTar --no-daemon )
    log "loading the image into podman"
    podman load -i "${ROOT_DIR}/build/jib-image.tar" >/dev/null
    log "importing ${APP_IMAGE} into the k3s containerd"
    podman save "${APP_IMAGE}" | podman exec -i "${CLUSTER_NAME}" ctr -n k8s.io images import -
}

apply_manifests() {
    log "applying infrastructure manifests"
    kube apply -f "${ROOT_DIR}/k8s/infra/kafka.yaml"
    kube apply -f "${ROOT_DIR}/k8s/infra/keycloak.yaml"
    kube apply -f "${ROOT_DIR}/k8s/infra/otel-collector.yaml"
    log "applying the application overlay k8s/overlays/k3s"
    kube apply -k "${ROOT_DIR}/k8s/overlays/k3s"
}

wait_for_rollout() {
    log "waiting for the application rollout"
    kube -n "${NAMESPACE}" rollout status deploy/offer-management --timeout=300s
    kube -n "${NAMESPACE}" rollout status deploy/keycloak --timeout=300s
    kube -n "${NAMESPACE}" rollout status deploy/kafka --timeout=300s
}

run_forward() {
    require kubectl
    if [[ -f "${FORWARD_PID_FILE}" ]] && kill -0 "$(cat "${FORWARD_PID_FILE}")" 2>/dev/null; then
        log "Keycloak port-forward already running (pid $(cat "${FORWARD_PID_FILE}"))"
    else
        log "port-forwarding Keycloak to localhost:${KEYCLOAK_PORT}"
        KUBECONFIG="${KUBECONFIG_FILE}" kubectl -n "${NAMESPACE}" \
            port-forward svc/offer-management-keycloak "${KEYCLOAK_PORT}:8080" \
            >"${ROOT_DIR}/.k3s-keycloak-port-forward.log" 2>&1 &
        echo $! > "${FORWARD_PID_FILE}"
    fi
    for _ in $(seq 1 30); do
        if curl -fsS "http://localhost:${KEYCLOAK_PORT}/realms/iot/.well-known/openid-configuration" >/dev/null 2>&1; then
            log "Keycloak reachable on http://localhost:${KEYCLOAK_PORT}"
            return 0
        fi
        sleep 2
    done
    die "Keycloak port-forward did not become reachable; see ${ROOT_DIR}/.k3s-keycloak-port-forward.log"
}

health_check() {
    log "checking ${APP_URL}/actuator/health/readiness"
    curl -fsS "${APP_URL}/actuator/health/readiness"
    printf '\n'
}

teardown() {
    if [[ -f "${FORWARD_PID_FILE}" ]]; then
        kill "$(cat "${FORWARD_PID_FILE}")" 2>/dev/null || true
        rm -f "${FORWARD_PID_FILE}"
    fi
    podman rm -f "${CLUSTER_NAME}" >/dev/null 2>&1 || true
    rm -f "${KUBECONFIG_FILE}" "${ROOT_DIR}/.k3s-keycloak-port-forward.log"
    log "torn down"
}

deploy() {
    boot_cluster
    wait_for_kubeconfig
    extract_kubeconfig
    wait_for_cluster
    create_namespaces
    deploy_postgres
    build_and_import_image
    apply_manifests
    wait_for_rollout
    run_forward
    log "deployed"
}

case "${1:-}" in
    deploy) deploy ;;
    run) run_forward; health_check ;;
    all) deploy; health_check ;;
    teardown) teardown ;;
    *) die "usage: $(basename "$0") {deploy|run|all|teardown}" ;;
esac
