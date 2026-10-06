package com.example.offer.catalog;

import com.example.offer.ArchitectureDescription;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.draft.DraftAttributes;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.draft.ReviewRequest;
import com.example.offer.draft.Title;
import com.example.offer.offer.Completeness;
import com.example.offer.offer.CompletenessPolicy;
import com.example.offer.offer.DescriptionVersion;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferState;
import com.example.offer.offer.ProductSnapshot;
import com.example.offer.offer.Publication;
import com.example.offer.offer.PublicationState;
import com.example.offer.offer.VisibleVersion;
import com.example.offer.pricing.PriceScheduleSnapshot;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;

@AnalyzeClasses(packages = ArchitectureOfCatalogContextTest.PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureOfCatalogContextTest {

    static final String PACKAGE = "com.example.offer.catalog";

    /**
     * The read context consumes the public snapshots/values of the three write contexts; nothing
     * private is reachable from here (blueprint {@code adapter-projection.md}).
     */
    static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf(
            Identity.class, Audit.class,
            DraftSnapshot.class, DraftState.class, DraftAttributes.class, Title.class, ReviewRequest.class,
            ProductSnapshot.class, OfferPresence.class, OfferState.class, Publication.class,
            PublicationState.class, VisibleVersion.class, DescriptionVersion.class,
            Completeness.class, Completeness.MissingRequirement.class, CompletenessPolicy.class,
            PriceScheduleSnapshot.class);

    static final DescribedPredicate<JavaClass> sharedKernelExposed = DescribedPredicate.alwaysFalse();

    @ArchTest
    static final ArchRule adaptersDependencies =
            ArchitectureDescription.adaptersDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    static final ArchRule servicesDependencies = ArchitectureDescription
            .servicesDependencies(PACKAGE, sharedKernelUsed)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modelDependencies =
            ArchitectureDescription.modelDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    static final ArchRule adaptersIsolation =
            ArchitectureDescription.adaptersIsolation(PACKAGE);

    @ArchTest
    static final ArchRule servicesIsolation = ArchitectureDescription
            .servicesIsolation(PACKAGE, ArchitectureDescription.mediators)
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule modelIsolation =
            ArchitectureDescription.modelIsolation(PACKAGE, sharedKernelExposed);
}
