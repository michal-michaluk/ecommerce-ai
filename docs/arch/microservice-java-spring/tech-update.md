# Tech Update — Dependency & Infrastructure Upgrade Guide

This document describes the process for keeping project dependencies, build plugins, Docker base images, and infrastructure manifests up to date.

## Prerequisites

- **Java 25** (`JAVA_HOME` set to a JDK 25 distribution)
- **Docker** running (required for Testcontainers, Trivy, and jib)
- **gcu** (`gradle-check-updates`) installed globally:
  ```bash
  npm install -g gradle-check-updates
  # or
  brew install gcu   # if available
  ```

## Quick start

```bash
# Check what's outdated (report only)
gcu

# Apply all available updates
gcu -u

# Run full quality gates to verify nothing broke
./gradlew build -x test --no-daemon
./gradlew test --no-daemon
./gradlew jacocoTestReport spotbugsMain trivyScan pitest jibDockerBuild --no-daemon
```

---

## Step-by-step

### 1. Check for outdated dependencies

```bash
gcu
```

This scans `build.gradle`, fetches latest versions from Maven Central and the Gradle Plugin Portal, and prints a report like:

```
au.com.dius.pact.consumer:junit5            4.7.1  ->  4.7.3
org.springframework.boot                    4.0.6  ->  4.1.0
com.google.cloud.tools.jib                  3.4.5  ->  3.5.4
```

### 2. Apply updates

```bash
gcu -u
```

This rewrites `build.gradle` in-place. Only the version string changes — formatting and comments are preserved.

### 3. Update Docker base image

The jib container uses `eclipse-temurin:25-jdk`. To check for a newer JDK 25 tag:

```bash
# List available tags
skopeo list-tags docker://docker.io/library/eclipse-temurin | grep '^25-jdk'
# or browse https://hub.docker.com/_/eclipse-temurin/tags
```

If a newer patch is available, update the version in the `jib.from.image` property inside `build.gradle`:

```groovy
jib {
    from {
        image = "eclipse-temurin:25-jdk"  # update tag here
    }
}
```

### 4. Run full quality gates

```bash
# Compile only (no Docker)
./gradlew build -x test --no-daemon

# Full test suite (requires Docker — Testcontainers)
./gradlew test --no-daemon

# Coverage + static analysis + security + mutation + image
./gradlew jacocoTestReport spotbugsMain trivyScan pitest jibDockerBuild --no-daemon
```

All gates must pass before committing.

### 5. If quality gates fail

When a dependency upgrade breaks the build, tests, or gates:

1. **Read the error** — identify which gate failed and why (compile error, test failure, Trivy finding, PIT mutation, SpotBugs, etc.).

2. **Use available tools** to investigate and fix:
   - **web_search** / **exa-search** — search for known issues, migration guides, or changelogs for the failing dependency
   - **web_fetch** — read release notes, breaking change docs, or GitHub issues
   - **grep** / **ast-grep** — find affected code patterns across the project
   - **opencode Task tool** — delegate complex analysis to sub-agents

3. **Fix the issue** — adapt code, configuration, or tests to work with the new version.

4. **Re-run quality gates** to confirm the fix.

### 6. Commit

```bash
git add -A
git commit -m "chore: update dependencies"
```

---

## Strict rules

1. **Never skip or disable quality gates.** Every gate (G0–G6) must pass before committing. Do not change thresholds, add exclusions, or disable checks to make an upgrade pass.

2. **Never reduce application functionality.** An upgrade must preserve all existing features, endpoints, security constraints, and business behaviour.

3. **Never weaken architecture rules.** ArchUnit rules, package isolation, and context boundaries remain in effect. Do not modify architecture tests to "fix" a failing import — fix the import instead.

---

## Fallback: deferring an upgrade

It is acceptable to leave a dependency at its current version if:

- A **known bug in the newer version** would block the upgrade (regression, broken API, CVEs introduced rather than fixed).
- A **security issue exists in a transitive dependency** that the library maintainer has not yet addressed — wait for the fix upstream.
- The **upgrade requires a framework or language version change** (e.g., Spring Boot 5 requires Java 26) that is not yet supported.

In these cases:

1. Document the decision in a comment in `build.gradle`:
   ```groovy
   implementation("com.example:lib:1.2.3") {
       because "1.3.0 introduces a regression in X — waiting for 1.3.1"
   }
   ```

2. Leave the version pinned. It will be re-evaluated on the next `gcu -u` run.

3. Continue with other updates that do pass.
