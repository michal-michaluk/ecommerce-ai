package com.example.offer;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Project-wide architecture rules. Per-context isolation (a context may only be reached through
 * its exposed types) is enforced by the {@code ArchitectureOf{Context}Test} each context ships,
 * using {@link ArchitectureDescription} and its {@code sharedKernelExposed} list — see
 * {@code docs/arch/microservice-java-spring/arch-unit.md}.
 */
@AnalyzeClasses(packages = "com.example.offer", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule ONION_ARCHITECTURE = onionArchitecture()
            .domainModels("com.example.offer.auth..")
            .applicationServices("com.example.offer")
            .adapter("mediators", "com.example.offer.mediators..")
            .adapter("tools", "com.example.offer.tools..")
            .withOptionalLayers(true)
            .because("auth is the shared domain kernel, tools is shared infrastructure, "
                    + "mediators orchestrate across bounded contexts");

    @ArchTest
    static final ArchRule SHARED_KERNEL_MUST_NOT_DEPEND_ON_BOUNDED_CONTEXTS = noClasses()
            .that().resideInAnyPackage(
                    "com.example.offer",
                    "com.example.offer.auth..",
                    "com.example.offer.tools..",
                    "com.example.offer.mediators..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.example.offer.draft..",
                    "com.example.offer.pricing..",
                    "com.example.offer.offer..",
                    "com.example.offer.catalog..",
                    "com.example.offer.publishing..")
            .because("dependencies point inward: the shared kernel never depends on a bounded context");
}
