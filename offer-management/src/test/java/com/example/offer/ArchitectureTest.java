package com.example.offer;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

@AnalyzeClasses(packages = "com.example.offer")
class ArchitectureTest {

    @ArchTest
    static final ArchRule ONION_ARCHITECTURE = onionArchitecture()
            .domainModels("..tools..")
            .domainServices("..tools..")
            .applicationServices("..")
            .adapter("mediators", "..mediators..")
            .adapter("tools", "..tools..")
            .withOptionalLayers(true)
            .ignoreDependency(simpleName("JsonAssert"), simpleName("JsonConfiguration"))
            .because("Clean onion architecture with tools as shared kernel and mediators for cross-context orchestration");

    @ArchTest
    static final ArchRule NO_CYCLES_BETWEEN_TOOLS = noClasses()
            .that()
            .resideOutsideOfPackage("..mediators..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("..", "java..", "lombok..", "org.springframework..", "jakarta..", "javax..", "..tools..")
            .because("Bounded contexts should only communicate through mediators");
}
