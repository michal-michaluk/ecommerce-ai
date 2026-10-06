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
class PublicationDocumentRepository implements PublicationRepository {

    private final DocumentRepository documents;

    @Override
    public Optional<Publication> get(String publicationId) {
        return documents.findById(publicationId).map(PublicationDocumentEntity::getPublication);
    }

    @Override
    public List<Publication> forProduct(String productId) {
        return documents.findByProductId(productId).stream()
                .map(PublicationDocumentEntity::getPublication)
                .toList();
    }

    @Override
    public void save(Publication publication) {
        documents.save(documents.findById(publication.publicationId())
                .orElseGet(() -> new PublicationDocumentEntity(publication.publicationId(), publication.productId()))
                .setPublication(publication));
    }

    @Repository
    interface DocumentRepository extends CrudRepository<PublicationDocumentEntity, String> {

        List<PublicationDocumentEntity> findByProductId(String productId);
    }

    @Entity
    @Table(name = "publication_document")
    @NoArgsConstructor
    static class PublicationDocumentEntity {

        @Id
        private String publicationId;
        private String productId;
        @Version
        private long lockVersion;
        @Getter
        @JdbcTypeCode(SqlTypes.JSON)
        private Publication publication;

        PublicationDocumentEntity(String publicationId, String productId) {
            this.publicationId = publicationId;
            this.productId = productId;
        }

        PublicationDocumentEntity setPublication(Publication publication) {
            this.publication = publication;
            return this;
        }
    }
}
