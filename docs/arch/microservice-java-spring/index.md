## Mandatory Workflow

This repository uses architecture-first development. Before coding, load the relevant docs from `docs/arch` and follow them strictly.

1. Identify the task type (domain change, API, persistence, projection, cross-context flow, tests).
2. Read matching doc files listed below.
3. Implement only through existing architectural patterns.
4. Add or update tests in the same context package.
5. Verify ArchUnit constraints remain valid.

If a task touches multiple contexts, read `context-boundaries.md` and `adapter-mediator.md` first.

### Document Map

| File | What it covers |
|------|----------------|
| `code-structure.md` | Package layout, naming conventions, module organization |
| `ports.md` | Primary/secondary ports and mediator-based cross-context contracts |
| `domain-model.md` | Aggregates, value objects, domain events, invariant placement |
| `policy.md` | Business calculations and decision policies |
| `adapter-http-command.md` | Write-side HTTP controllers and command DTO mapping |
| `adapter-http-query.md` | Read-side HTTP endpoints and pagination patterns |
| `adapter-persistence-event-sourcing.md` | Event-sourcing repositories and replay/save flow |
| `adapter-persistence-document.md` | Document snapshot persistence with history |
| `adapter-persistence-normalizing.md` | Normalized relational mapping adapter |
| `adapter-projection.md` | Event-driven projection/read-model update patterns |
| `adapter-mediator.md` | Cross-context orchestration adapter rules |
| `context-boundaries.md` | Bounded contexts, shared kernel, dependency policy |
| `security.md` | Current security posture and authN/authZ decisions |
| `testing.md` | Test pyramid, fixtures, integration tests, custom asserts |
| `arch-unit.md` | Architecture rules and how to add context architecture tests |

### Hard Constraints

- Keep bounded context internals package-private by default.
- Access the aggregate lifecycle through services, not directly from adapters.
- Do not call sibling context repositories/services directly; use mediator + contract.
- Do not expose new shared kernel types without an explicit architecture test update.
- Keep controllers thin: validation + mapping + service call only.
- Keep business invariants in aggregates.
- Keep repository ports domain-oriented (no JPA entities in port signatures).

### Implementation NOGO

- No cross-context imports of internal model classes.
- No business logic inside controllers, projections, or mediators.
- No direct write-model queries for read API when projection exists.
- No weakening ArchUnit rules to make code compile.
- No security assumptions from dependencies only; each new endpoint needs explicit security decision.

### Testing NOGO

- No skipping architecture tests for new contexts.
- No testing domain logic only through integration tests.
- No mutable shared fixture instances across tests.

### Quick Task Routing

- Add or change aggregate behavior → `domain-model.md`, `ports.md`, `testing.md`
- Add or change business calculation → `policy.md`, `domain-model.md`, `testing.md`
- Add or change REST write endpoint → `adapter-http-command.md`, `security.md`, `testing.md`
- Add or change REST read endpoint → `adapter-http-query.md`, `adapter-projection.md`, `testing.md`
- Add new persistence strategy → matching `adapter-persistence-*.md`, `ports.md`, `testing.md`
- Add cross-context use-case → `adapter-mediator.md`, `context-boundaries.md`, `arch-unit.md`
- Add new bounded context → `code-structure.md`, `context-boundaries.md`, `arch-unit.md`, `testing.md`

### Implementation Workflow

When implementing a new bounded context, follow this strict phased
workflow. Each phase must be complete (all gates green) before moving to the next.

| Phase | Activity | Gates | NOGO |
|-------|----------|-------|------|
| **1. Domain** | aggregate, value objects, domain events, repository ports | unit tests at 100% coverage on domain logic, complexity fix (CCN ≤ 15) | no domain logic leaks to application layer |
| **2. Application** | application services (thin orchestration, no business rules) | — | no business invariants in services |
| **3. Adapters** | controllers, JPA entities, projections, DB migration (Liquibase) | persistence tests covering 100% of cases (save/load/query edge cases) | no JPA in domain ports; no business logic in controllers |
| **4. Security** | endpoint-level auth rules (permitAll / authenticated / scoped) | — | no endpoint left without explicit auth decision |
| **5. Integration** | end-to-end flows via testcontainers (PostgreSQL, Kafka, Keycloak) | all integration tests pass | no mutable shared fixtures |
| **6. Coverage** | JaCoCo at template threshold (80% strict, 0% basic) | add unit/integration tests until threshold is met | **NEVER** lower the threshold — escalate to user if coverage cannot be reached |
| **7. Static Analysis** | SpotBugs + findsecbugs | all high-priority bugs resolved or explicitly excluded (only false positives) | no weakening of include/exclude filters without justification |
| **8. Mutation** | PITest | test strength ≥ 80% (add tests for uncovered mutations) | no exclusion of mutation targets without justification |
| **9. SCA** | Trivy filesystem scan | 0 HIGH/CRITICAL vulnerabilities; 0 secrets; 0 misconfigurations | no ignoring findings without justification |
| **10. Complexity** | review-complexity (lizard) on full source | all methods CCN ≤ 15 (hard limit) | no refactoring that breaks domain invariants |
| **11. e2e** | Hurl smoke tests against running service | healthcheck + critical path respond correctly | — |

**Golden rule:** Never reduce a quality gate threshold. If a gate cannot be satisfied,
escalate to the user with specific data (what passed, what failed, what would be needed
to pass). The thresholds exist to keep architecture and code quality in check — lowering
them defeats the purpose of the eval.
