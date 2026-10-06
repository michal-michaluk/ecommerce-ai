package com.example.offer.draft;

import com.example.offer.ArchitectureDescription;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.ApiErrors;
import com.example.offer.mediators.DecisionDenied;
import com.example.offer.mediators.OfferLifecycleMediator;
import com.example.offer.offer.Completeness;
import com.example.offer.offer.TextIssue;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;

@AnalyzeClasses(packages = ArchitectureOfDraftContextTest.PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureOfDraftContextTest {

    static final String PACKAGE = "com.example.offer.draft";

    static final DescribedPredicate<JavaClass> sharedKernelExposed = belongToAnyOf(
            DraftSnapshot.class, UpdateDraft.class, DraftState.class, Title.class,
            Description.class, ReviewRequest.class, Photo.class, DraftAttributes.class);

    static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf(
            Identity.class, Audit.class, ApiErrors.class,
            OfferLifecycleMediator.class, DecisionDenied.class,
            Completeness.class, Completeness.MissingRequirement.class, TextIssue.class);

    @ArchTest
    static final ArchRule adaptersDependencies =
            ArchitectureDescription.adaptersDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    static final ArchRule servicesDependencies =
            ArchitectureDescription.servicesDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    static final ArchRule modelDependencies =
            ArchitectureDescription.modelDependencies(PACKAGE, sharedKernelUsed);

    @ArchTest
    static final ArchRule adaptersIsolation =
            ArchitectureDescription.adaptersIsolation(PACKAGE);

    @ArchTest
    static final ArchRule servicesIsolation =
            ArchitectureDescription.servicesIsolation(PACKAGE, ArchitectureDescription.mediators);

    @ArchTest
    static final ArchRule modelIsolation =
            ArchitectureDescription.modelIsolation(PACKAGE, sharedKernelExposed);
}
