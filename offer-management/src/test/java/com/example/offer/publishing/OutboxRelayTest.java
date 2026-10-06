package com.example.offer.publishing;

import com.example.offer.IntegrationTest;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.pricing.DateRange;
import com.example.offer.pricing.Money;
import com.example.offer.pricing.PricingService;
import com.example.offer.publishing.IntegrationEvent.PhotoView;
import com.example.offer.publishing.IntegrationEvent.PriceView;
import com.example.offer.publishing.IntegrationEvent.ProductPricesChanged;
import com.example.offer.publishing.IntegrationEvent.ProductRemovedFromOffer;
import com.example.offer.publishing.IntegrationEvent.ProductVersionPublishedToOffer;
import com.example.offer.JsonAssert;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class OutboxRelayTest {

    private static final Audit AUDIT = new Audit(new Identity("s.zielinski"), Instant.parse("2019-06-20T11:00:00Z"));

    @Autowired
    Outbox outbox;
    @Autowired
    OutboxRepository repository;
    @Autowired
    OutboxRelay relay;
    @Autowired
    PricingService pricingService;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    TransactionTemplate transactionTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    String bootstrapServers;
    @Value("${outbox.topic:browsing-offer}")
    String topic;

    @Test
    void onlyTheThreeIntegrationEventsMayCrossTheBoundary() {
        assertThat(IntegrationEvent.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(ProductVersionPublishedToOffer.class,
                        ProductPricesChanged.class, ProductRemovedFromOffer.class);
    }

    @Test
    void payloadsCarryExactlyTheElement03Fields() {
        String pricesJson = OutboxRepository.json(pricesEvent("p-2019-0442", "pr-2", null));
        JsonAssert.assertThat(pricesJson).isExactlyLike("""
                {"@type":"ProductPricesChanged_v1","productId":"p-2019-0442",
                 "effectiveFrom":"2019-07-01","price":{"value":"259.00","currency":"PLN"},
                 "discountPercent":null,
                 "audit":{"who":{"subject":"s.zielinski"},"at":"2019-06-20T11:00:00Z"}}
                """);
        assertThat(pricesJson).doesNotContain("entryId");

        String discountOnlyJson = OutboxRepository.json(new ProductPricesChanged("p-2019-0442",
                LocalDate.parse("2019-07-01"), null, "10", "pr-3", AUDIT));
        JsonAssert.assertThat(discountOnlyJson).isExactlyLike("""
                {"@type":"ProductPricesChanged_v1","productId":"p-2019-0442",
                 "effectiveFrom":"2019-07-01","price":null,"discountPercent":"10",
                 "audit":{"who":{"subject":"s.zielinski"},"at":"2019-06-20T11:00:00Z"}}
                """);

        String versionJson = OutboxRepository.json(versionEvent("p-2019-0442"));
        JsonAssert.assertThat(versionJson).isExactlyLike("""
                {"@type":"ProductVersionPublishedToOffer_v1","productId":"p-2019-0442","version":"v3",
                 "availableFrom":"2019-07-01","title":"Kosiarka","description":"Solidna",
                 "attributes":{"category":"Ogrod","manualUrl":null},
                 "photos":[{"photoId":"ph-1","mime":"image/jpeg","width":1200,"height":1200}],
                 "audit":{"who":{"subject":"s.zielinski"},"at":"2019-06-20T11:00:00Z"}}
                """);

        String removalJson = OutboxRepository.json(removalEvent("p-2019-0442"));
        JsonAssert.assertThat(removalJson).isExactlyLike("""
                {"@type":"ProductRemovedFromOffer_v1","productId":"p-2019-0442",
                 "audit":{"who":{"subject":"s.zielinski"},"at":"2019-06-20T11:00:00Z"}}
                """);
    }

    @Test
    void duplicateAppendIsRejectedAndStoredOnce() {
        String productId = "p-dup-" + UUID.randomUUID();
        ProductPricesChanged event = pricesEvent(productId, "pr-2", null);

        assertThat(outbox.append(event)).isTrue();
        assertThat(outbox.append(event)).isFalse();

        assertThat(jdbc.queryForObject("select count(*) from outbox where partition_key = ?",
                Integer.class, productId)).isEqualTo(1);
    }

    @Test
    void twoEntriesCrossingOnTheSameDateKeepTheContractKeyAndProduceTwoRows() {
        String productId = "p-key-" + UUID.randomUUID();
        ProductPricesChanged price = pricesEvent(productId, "pr-2", null);
        ProductPricesChanged discount = pricesEvent(productId, "pr-3", "10");

        assertThat(price.idempotencyKey()).isEqualTo(discount.idempotencyKey());
        assertThat(price.outboxKey()).isNotEqualTo(discount.outboxKey());
        assertThat(outbox.append(price)).isTrue();
        assertThat(outbox.append(discount)).isTrue();

        assertThat(outboxRows(productId)).isEqualTo(2);
    }

    @Test
    void relayPublishesEachEventKeyedByProductIdAndMarksItPublished() {
        String productId = "p-relay-" + UUID.randomUUID();
        outbox.append(versionEvent(productId));
        outbox.append(pricesEvent(productId, "pr-2", "10"));
        outbox.append(removalEvent(productId));

        relay.relay(100);

        List<ConsumerRecord<String, String>> records = consume(productId, 3);
        assertThat(records).hasSize(3);
        assertThat(records).allSatisfy(record -> assertThat(record.key()).isEqualTo(productId));
        assertThat(records).extracting(ConsumerRecord::value)
                .anySatisfy(value -> assertThat(value).contains("ProductVersionPublishedToOffer_v1"))
                .anySatisfy(value -> assertThat(value).contains("ProductPricesChanged_v1"))
                .anySatisfy(value -> assertThat(value).contains("ProductRemovedFromOffer_v1"));

        assertThat(jdbc.queryForObject("select count(*) from outbox where partition_key = ? and published_at is null",
                Integer.class, productId)).isZero();
    }

    @Test
    void theOutboxRowIsCommittedInTheSameTransactionAsTheStateChange() {
        String productId = "p-tx-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            pricingService.schedulePrice(productId, "pr-2", Money.of("259.00", "PLN"),
                    DateRange.from(LocalDate.parse("2019-07-01")), AUDIT);
            outbox.append(pricesEvent(productId, "pr-2", null));
        });

        assertThat(rows("price_schedule_document", productId)).isEqualTo(1);
        assertThat(outboxRows(productId)).isEqualTo(1);
    }

    @Test
    void aRolledBackTransactionLeavesNeitherTheStateChangeNorTheOutboxRow() {
        String productId = "p-rollback-" + UUID.randomUUID();

        transactionTemplate.executeWithoutResult(status -> {
            pricingService.schedulePrice(productId, "pr-2", Money.of("259.00", "PLN"),
                    DateRange.from(LocalDate.parse("2019-07-01")), AUDIT);
            outbox.append(pricesEvent(productId, "pr-2", null));
            status.setRollbackOnly();
        });

        assertThat(rows("price_schedule_document", productId)).isZero();
        assertThat(outboxRows(productId)).isZero();
    }

    @Test
    void aFailedSendLeavesTheRowPendingForRetry() {
        String productId = "p-fail-" + UUID.randomUUID();
        outbox.append(pricesEvent(productId, "pr-2", null));
        long id = jdbc.queryForObject("select id from outbox where partition_key = ?", Long.class, productId);

        OutboxRelay failingRelay = new OutboxRelay(repository, unreachableKafka(), topic, 1000);

        assertThatThrownBy(() -> failingRelay.relay(1000)).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select published_at from outbox where id = ?", Timestamp.class, id)).isNull();
    }

    private static KafkaTemplate<String, String> unreachableKafka() {
        Map<String, Object> properties = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:1",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 500,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 500);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(properties));
    }

    private List<ConsumerRecord<String, String>> consume(String productId, int expected) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "outbox-relay-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            Instant deadline = Instant.now().plusSeconds(30);
            List<ConsumerRecord<String, String>> found = new ArrayList<>();
            while (found.size() < expected && Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                    if (productId.equals(record.key())) {
                        found.add(record);
                    }
                }
            }
            return found;
        }
    }

    private int rows(String table, String productId) {
        return jdbc.queryForObject("select count(*) from " + table + " where product_id = ?",
                Integer.class, productId);
    }

    private int outboxRows(String productId) {
        return jdbc.queryForObject("select count(*) from outbox where partition_key = ?",
                Integer.class, productId);
    }

    private static ProductPricesChanged pricesEvent(String productId, String entryId, String discountPercent) {
        return new ProductPricesChanged(productId, LocalDate.parse("2019-07-01"),
                new PriceView("259.00", "PLN"), discountPercent, entryId, AUDIT);
    }

    private static ProductVersionPublishedToOffer versionEvent(String productId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("category", "Ogrod");
        attributes.put("manualUrl", null);
        return new ProductVersionPublishedToOffer(productId, "v3", LocalDate.parse("2019-07-01"),
                "Kosiarka", "Solidna", attributes,
                List.of(new PhotoView("ph-1", "image/jpeg", 1200, 1200)), AUDIT);
    }

    private static ProductRemovedFromOffer removalEvent(String productId) {
        return new ProductRemovedFromOffer(productId, AUDIT);
    }
}
