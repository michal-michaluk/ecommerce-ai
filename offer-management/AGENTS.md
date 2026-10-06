# offer management bounded context

**Stack:** Spring Boot 4 + Java 25 + PostgreSQL + Kafka + OAuth2/Keycloak
**Architecture:** Vertical slices — bounded contexts as sub-packages

Generated from the `microservice-java-spring` blueprint.

## Build toolchain

- **podman only** (no docker daemon): `trivyScan`/`trivyScanImage` run via `podman run`; the image is
  built with `jibBuildTar` (OCI tar at `build/jib-image.tar`) and loaded with `podmanLoad`.
- `jacocoTestCoverageVerification` is scoped to the domain contexts (`draft`, `pricing`, `offer`);
  `tools`, `auth` and the framework wiring are excluded. Threshold: `INSTRUCTION` covered ratio 0.8.

