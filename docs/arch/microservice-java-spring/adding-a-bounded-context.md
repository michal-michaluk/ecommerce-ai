# How to Add a Bounded Context

This guide walks you through adding a new bounded context to your microservice generated from this blueprint. Expected time: < 15 minutes.

Architecture pattern docs are in `arch/` — read the referenced files for deeper understanding of each pattern.

---

### Step 1: Create the package

Create the main and test packages under the root package:

```
src/main/java///
src/test/java///
```

For example, if `` = `com.example.ordering` and `` = `product`:

```
src/main/java/com/example/ordering/product/
src/test/java/com/example/ordering/product/
```

Every class in this context stays within these packages. No cross-context imports (enforced by ArchUnit — see Step 8).

---

### Step 2: Aggregate + Value Objects

Define the domain model inside the context package. Read `arch/domain-model.md` for the full pattern.

Create a package-private aggregate class using plain Java (no Spring stereotypes):

```java
// src/main/java///Product.java
@AllArgsConstructor
class Product {
    final String productId;
    final List<DomainEvent> events;
    private Money price;
    private boolean available;

    static Product newProduct(String productId, String name, String description, Money price) {
        return new Product(productId, new ArrayList<>(), name, description, price, true);
    }

    void changePrice(Money newPrice) {
        if (!Objects.equals(this.price, newPrice)) {
            this.price = newPrice;
            events.add(new DomainEvent.PriceChanged(productId, newPrice));
        }
    }

    void archive() {
        this.available = false;
        events.add(new DomainEvent.ProductArchived(productId));
    }

    ProductSnapshot toSnapshot() {
        return new ProductSnapshot(productId, name, description, price, available);
    }
}
```

Value objects are public records used in port signatures:

```java
// src/main/java///Money.java
public record Money(String currency, BigDecimal amount) {
    public static Money of(String currency, BigDecimal amount) {
        return new Money(currency, amount);
    }
}
```

Domain events are records implementing a marker interface:

```java
// src/main/java///DomainEvent.java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
    @JsonSubTypes.Type(value = DomainEvent.PriceChanged.class, name = "PriceChanged_v1"),
    @JsonSubTypes.Type(value = DomainEvent.ProductArchived.class, name = "ProductArchived_v1")
})
public interface DomainEvent {
    record PriceChanged(String productId, Money newPrice) implements DomainEvent {}
    record ProductArchived(String productId) implements DomainEvent {}
}
```

The read-model snapshot is a public record returned by controllers:

```java
// src/main/java///ProductSnapshot.java
public record ProductSnapshot(
    String productId, String name, String description, Money price, boolean available
) {}
```

**Key rules** (from `arch/domain-model.md`):
- Aggregate is package-private — never public
- Every state change emits a domain event
- Business invariants are enforced inside the aggregate, not in services
- Value objects are immutable records
- The aggregate exposes a `toSnapshot()` method instead of field getters

---

### Step 3: Ports

Define two ports inside the context package. Read `arch/ports.md` for the full pattern.

**Repository port** (secondary port, package-private):

```java
// src/main/java///ProductRepository.java
interface ProductRepository {
    Optional<Product> get(String productId);
    void save(Product product);
}
```

**Service** (primary port, public):

```java
// src/main/java///ProductService.java
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;

    public Optional<ProductSnapshot> getProduct(String productId) {
        return repository.get(productId).map(Product::toSnapshot);
    }

    public ProductSnapshot createProduct(String productId, String name, String description, Money price) {
        Product product = Product.newProduct(productId, name, description, price);
        repository.save(product);
        return product.toSnapshot();
    }

    public Optional<ProductSnapshot> changePrice(String productId, Money newPrice) {
        return repository.get(productId).map(product -> {
            product.changePrice(newPrice);
            repository.save(product);
            return product.toSnapshot();
        });
    }
}
```

**Key rules** (from `arch/ports.md`):
- Repository port uses domain types only — no JPA entities
- Service is `@Service @Transactional @RequiredArgsConstructor` — injects only the repository port
- Controllers depend on services, never on repositories directly

---

### Step 4: Adapter + Controller

Create a REST controller for command and query endpoints. Read `arch/adapter-http-command.md` and `arch/adapter-http-query.md`.

**Command DTO** (public record with `@Builder`, PATCH semantics):

```java
// src/main/java///UpdateProduct.java
@Builder
public record UpdateProduct(
    String name,
    String description,
    @Valid Money price
) {
    public void apply(Product product) {
        if (price != null) product.changePrice(price);
    }
}
```

**Controller** (package-private `@RestController`):

```java
// src/main/java///ProductController.java
@RestController
@RequiredArgsConstructor
class ProductController {
    private final ProductService service;

    @PostMapping("/products")
    ProductSnapshot create(@RequestBody @Valid CreateProductRequest request) {
        return service.createProduct(
            UUID.randomUUID().toString(),
            request.name(),
            request.description(),
            request.price()
        );
    }

    @GetMapping("/products/{productId}")
    ProductSnapshot get(@PathVariable String productId) {
        return service.getProduct(productId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }

    @PatchMapping("/products/{productId}")
    ProductSnapshot patch(@PathVariable String productId, @RequestBody @Valid UpdateProduct update) {
        return service.changePrice(productId, update.price())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }
}
```

**Security note:** OAuth2 is enabled by default. New endpoints are authenticated unless explicitly whitelisted in `AppConfiguration.filterChain()`. Use `@RolesAllowed` or `@PreAuthorize` for fine-grained access:

```java
@RolesAllowed("ADMIN")
@PostMapping("/products")
ProductSnapshot create(...) { ... }
```

Read `arch/security.md` for the security configuration pattern.

**Key rules**:
- Controllers are thin: validate → call service → return result
- Controllers never access repositories directly
- Separate read controllers for CQRS (see `arch/adapter-http-query.md`)

---

### Step 5: Service

The service layer orchestrates aggregate lifecycle. Read `arch/policy.md` if your context has stateless business calculations.

```java
@Service
@Transactional
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository repository;

    public ProductSnapshot createProduct(String productId, String name, String description, Money price) {
        Product product = Product.newProduct(productId, name, description, price);
        repository.save(product);
        return product.toSnapshot();
    }

    public Optional<ProductSnapshot> changePrice(String productId, Money newPrice) {
        return repository.get(productId).map(product -> {
            product.changePrice(newPrice);
            repository.save(product);
            return product.toSnapshot();
        });
    }

    public void archiveProduct(String productId) {
        repository.get(productId).ifPresent(product -> {
            product.archive();
            repository.save(product);
        });
    }
}
```

**Key rules** (from `arch/policy.md`):
- Business logic stays in the aggregate, not in services
- Services load, mutate, and save — nothing more
- For stateless calculation rules, define a policy record + thin service wrapper

---

### Step 6: Liquibase changeset

Create a new Liquibase YAML file in `src/main/resources/db/` and include it in `db.changelog.yaml`.

```yaml
# src/main/resources/db/0009-product.yaml
databaseChangeLog:
  - changeSet:
      id: 0009-product
      author: your.name
      changes:
        - createTable:
            tableName: product
            columns:
              - column:
                  name: product_id
                  type: varchar(255)
                  constraints:
                    primaryKey: true
                    nullable: false
              - column:
                  name: version
                  type: bigint
                  defaultValueNumeric: 1
                  constraints:
                    nullable: false
              - column:
                  name: name
                  type: varchar(255)
                  constraints:
                    nullable: false
              - column:
                  name: description
                  type: varchar(1000)
              - column:
                  name: currency
                  type: varchar(3)
                  constraints:
                    nullable: false
              - column:
                  name: amount
                  type: numeric(19, 2)
                  constraints:
                    nullable: false
              - column:
                  name: available
                  type: boolean
                  defaultValueBoolean: true
                  constraints:
                    nullable: false
```

Include it in the main changelog:

```yaml
# src/main/resources/db/db.changelog.yaml
databaseChangeLog:
  - include:
      file: db/0009-product.yaml
```

**Naming conventions:**
- Table names: `snake_case` — singular, matching the domain concept (`product`, not `products`)
- Changeset IDs: sequential zero-padded number + hyphen + context name (`0009-product`)
- Primary key: `{table_name}_id` (e.g. `product_id`) as `varchar(255)` by default
- Version column: `version bigint DEFAULT 1` for optimistic locking
- JSONB columns for complex value objects that don't need relational querying

---

### Step 7: Tests

Write unit tests, service tests with a fake repository, and integration tests. Read `arch/testing.md` for the full testing pyramid.

**Fixture** (public class with static factory methods):

```java
// src/test/java///ProductFixture.java
public class ProductFixture {
    public static String randomId() {
        return UUID.randomUUID().toString();
    }

    public static Product givenProduct() {
        return new Product(
            randomId(), new ArrayList<>(), "Widget", "A widget", Money.of("USD", BigDecimal.TEN), true
        );
    }

    public static Money priceOf(BigDecimal amount) {
        return Money.of("USD", amount);
    }
}
```

**Unit test** (domain model — no Spring):

```java
// src/test/java///ProductTest.java
class ProductTest {
    @Test
    void changePrice() {
        Product product = ProductFixture.givenProduct();
        product.changePrice(Money.of("USD", BigDecimal.valueOf(20)));

        ProductSnapshot snapshot = product.toSnapshot();
        assertThat(snapshot.price().amount()).isEqualByComparingTo("20.00");
        assertThat(product.events).hasSize(1);
    }

    @Test
    void archive() {
        Product product = ProductFixture.givenProduct();
        product.archive();

        assertThat(product.toSnapshot().available()).isFalse();
    }
}
```

**Service test** (with fake repository — no Spring):

```java
// src/test/java///ProductServiceTest.java
class ProductServiceTest {
    final Map<String, Product> store = new HashMap<>();
    final ProductService service = new ProductService(new FakeRepo());

    @Test
    void createProduct() {
        ProductSnapshot snapshot = service.createProduct("p1", "Widget", "A widget", Money.of("USD", BigDecimal.TEN));

        assertThat(snapshot.name()).isEqualTo("Widget");
        assertThat(snapshot.available()).isTrue();
    }

    class FakeRepo implements ProductRepository {
        public Optional<Product> get(String id) {
            return Optional.ofNullable(store.get(id));
        }
        public void save(Product product) {
            store.put(product.productId, product);
        }
    }
}
```

**Integration test** (extends `IntegrationTest` — Testcontainers PostgreSQL + OAuth2):

```java
// src/test/java///ProductIntegrationTest.java
@IntegrationTest
@Transactional
class ProductIntegrationTest {
    @Autowired
    ProductRepository repository;

    @Autowired
    WebTestClient webClient;

    @Test
    void createAndRetrieve() {
        String productId = transactional(() -> {
            Product product = ProductFixture.givenProduct();
            repository.save(product);
            return product.productId;
        });

        webClient.get().uri("/products/{id}", productId)
            .headers(headers -> headers.setBearerAuth(token))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.name").isEqualTo("Widget");
    }
}
```

**Key rules** (from `arch/testing.md`):
- Domain logic tested through unit tests, not only through integration tests
- Service tests use an inline fake repository — no Spring context
- Integration tests use `@IntegrationTest` + `@Transactional` for rollback isolation
- Test data comes from fixture static factory methods — no mutable shared instances

---

### Step 8: Architecture Test

Create a per-context ArchUnit test. Read `arch/arch-unit.md` for the full rule set.

```java
// src/test/java///ArchitectureOfProductContextTest.java
@AnalyzeClasses(packages = "", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureOfProductContextTest {
    public static final String PACKAGE = ".";

    public static final DescribedPredicate<JavaClass> sharedKernelExposed = belongToAnyOf(
        ProductSnapshot.class, Money.class
    );
    public static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf();

    @ArchTest
    public static final ArchRule adaptersDependencies =
        ArchitectureDescription.adaptersDependencies(PACKAGE, sharedKernelUsed);
    @ArchTest
    public static final ArchRule servicesDependencies =
        ArchitectureDescription.servicesDependencies(PACKAGE, sharedKernelUsed);
    @ArchTest
    public static final ArchRule modelDependencies =
        ArchitectureDescription.modelDependencies(PACKAGE, sharedKernelUsed);
    @ArchTest
    public static final ArchRule adaptersIsolation =
        ArchitectureDescription.adaptersIsolation(PACKAGE);
    @ArchTest
    public static final ArchRule servicesIsolation =
        ArchitectureDescription.servicesIsolation(PACKAGE, ArchitectureDescription.mediators);
    @ArchTest
    public static final ArchRule modelIsolation =
        ArchitectureDescription.modelIsolation(PACKAGE, sharedKernelExposed);
}
```

The generic `ArchitectureTest.java` (shipped with the template) already enforces context isolation — this per-context test adds specific dependency checks for your new context.

**Key rules** (from `arch/arch-unit.md`):
- Define `sharedKernelExposed` = types this context makes accessible to others
- Define `sharedKernelUsed` = types from other contexts this context may import
- Six rules enforced: adapters dependencies, services dependencies, model dependencies, adapters isolation, services isolation, model isolation
- Do not add `allowEmptyShould(true)` — only use when a context genuinely has no services or adapters

---

**Files created summary:**

| Step | Files |
|------|-------|
| 1 | `src/main/java///` + test equivalent |
| 2 | `Product.java`, `Money.java`, `ProductSnapshot.java`, `DomainEvent.java` |
| 3 | `ProductRepository.java`, `ProductService.java` |
| 4 | `UpdateProduct.java`, `CreateProductRequest.java`, `ProductController.java` |
| 5 | `ProductService.java` (same as step 3) |
| 6 | `src/main/resources/db/0009-product.yaml`, update `db.changelog.yaml` |
| 7 | `ProductFixture.java`, `ProductTest.java`, `ProductServiceTest.java`, `ProductIntegrationTest.java` |
| 8 | `ArchitectureOfProductContextTest.java` |
