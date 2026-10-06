package com.example.offer.offer;

import com.example.offer.ArchitectureDescription;
import com.example.offer.ApiErrors;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.mediators.DecisionDenied;
import com.example.offer.mediators.OfferLifecycleMediator;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;

@AnalyzeClasses(packages = ArchitectureOfOfferContextTest.PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureOfOfferContextTest {

    static final String PACKAGE = "com.example.offer.offer";

    static final DescribedPredicate<JavaClass> sharedKernelExposed = belongToAnyOf(
            DescriptionVersion.class, Publication.class, PublicationState.class,
            OfferPresence.class, OfferState.class, VisibleVersion.class,
            ProductSnapshot.class, DraftState.class, DomainEvent.class,
            Completeness.class, Completeness.MissingRequirement.class,
            CompletenessPolicy.class, Decisions.class, Decisions.Decision.class,
            TextCheck.class, TextIssue.class);

    static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf(
            Identity.class, Audit.class, DraftSnapshot.class, ApiErrors.class,
            com.example.offer.draft.DraftState.class,
            OfferLifecycleMediator.class, DecisionDenied.class);

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
