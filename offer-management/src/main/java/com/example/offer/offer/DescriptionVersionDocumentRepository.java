package com.example.offer.offer;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.annotation.Primary;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
@AllArgsConstructor
class DescriptionVersionDocumentRepository implements DescriptionVersionRepository {

    private final DocumentRepository documents;

    @Override
    public Optional<DescriptionVersion> get(String productId, String version) {
        return documents.findById(key(productId, version))
                .map(DescriptionVersionDocumentEntity::getDescriptionVersion);
    }

    @Override
    public List<DescriptionVersion> forProduct(String productId) {
        return documents.findByProductId(productId).stream()
                .map(DescriptionVersionDocumentEntity::getDescriptionVersion)
                .toList();
    }

    @Override
    public void save(DescriptionVersion version) {
        String key = key(version.productId(), version.version());
        documents.save(documents.findById(key)
                .orElseGet(() -> new DescriptionVersionDocumentEntity(key, version.productId(), version.version()))
                .setDescriptionVersion(version));
    }

    private static String key(String productId, String version) {
        return productId + "::" + version;
    }

    @Repository
    interface DocumentRepository extends CrudRepository<DescriptionVersionDocumentEntity, String> {

        List<DescriptionVersionDocumentEntity> findByProductId(String productId);
    }

    @Entity
    @Table(name = "description_version_document")
    @NoArgsConstructor
    static class DescriptionVersionDocumentEntity {

        @Id
        private String id;
        private String productId;
        private String versionLabel;
        @Version
        private long lockVersion;
        @Getter
        @JdbcTypeCode(SqlTypes.JSON)
        private DescriptionVersion descriptionVersion;

        DescriptionVersionDocumentEntity(String id, String productId, String versionLabel) {
            this.id = id;
            this.productId = productId;
            this.versionLabel = versionLabel;
        }

        DescriptionVersionDocumentEntity setDescriptionVersion(DescriptionVersion descriptionVersion) {
            this.descriptionVersion = descriptionVersion;
            return this;
        }
    }
}
