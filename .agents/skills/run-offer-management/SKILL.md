---
name: run-offer-management
description: Build, deploy, run and tear down the offer-management service on a local k3s running as a privileged podman container. Use when asked to run, deploy, start, stop or tear down offer-management locally.
version: 1.0.0
license: MIT
---

# Skill: run-offer-management

Deploy and run the `offer-management` Spring Boot service together with its in-cluster
infrastructure (PostgreSQL, Kafka, Keycloak, OTel collector) on local **k3s in a privileged
podman container**. There is no docker daemon and no k3d — every container operation is podman
and the app image is imported into the k3s containerd (spec assumptions A14/A16/A17).

## Prerequisites

- Rootful podman machine. `deploy-k3s.sh` fails fast on a rootless machine because k3s pods
  cannot create cgroups. Fix once with:
  ```bash
  podman machine set --rootful && podman machine stop && podman machine start
  ```
- `podman`, `kubectl`, `helm` on PATH.

## Commands

All commands run from the repository root:

```bash
bash offer-management/scripts/deploy-k3s.sh deploy     # cluster + Postgres + app + Keycloak forward
bash offer-management/scripts/deploy-k3s.sh run        # Keycloak forward + readiness check (cluster up)
bash offer-management/scripts/deploy-k3s.sh all        # deploy + readiness check
bash offer-management/scripts/deploy-k3s.sh teardown   # remove cluster, kubeconfig, forward
```

- Kubeconfig: `offer-management/.k3s-kubeconfig` (host rewritten `127.0.0.1` → `localhost`).
- App: `http://localhost:8080` (traefik ingress, host port 8080).
- Health: `curl -fsS http://localhost:8080/actuator/health/readiness`.
- Keycloak: `http://localhost:18081` (realm `iot`; clients/users `carla` = content-manager, `sara` = sales, password = username).
- PostgreSQL: Helm by default; set `POSTGRES_MODE=manifest` to apply `k8s/infra/postgresql.yaml`.
- Overlays: `offer-management/k8s/overlays/k3s` (in-cluster), `.../dev` (local process).

## Inspect

```bash
KUBECONFIG=offer-management/.k3s-kubeconfig kubectl -n offer-management get pods -o wide
KUBECONFIG=offer-management/.k3s-kubeconfig kubectl -n offer-management logs deploy/offer-management
bash offer-management/scripts/trace.sh --requests   # OTel traces from the collector (podman)
```

## Security context

Do not weaken the pod securityContext (non-root, read-only root fs, dropped capabilities)
or the liveness/readiness/startup probes in `k8s/base` and `k8s/overlays/k3s`.
