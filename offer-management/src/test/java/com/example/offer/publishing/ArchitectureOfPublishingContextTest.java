package com.example.offer.publishing;

import com.example.offer.ArchitectureDescription;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;

@AnalyzeClasses(packages = ArchitectureOfPublishingContextTest.PACKAGE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureOfPublishingContextTest {

    static final String PACKAGE = "com.example.offer.publishing";

    static final DescribedPredicate<JavaClass> sharedKernelExposed = belongToAnyOf(
            Outbox.class, IntegrationEvent.class, IntegrationEvent.ProductPricesChanged.class,
            IntegrationEvent.PriceView.class);

    static final DescribedPredicate<JavaClass> sharedKernelUsed = belongToAnyOf(
            Identity.class, Audit.class);

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
