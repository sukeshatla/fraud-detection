package com.fraudplatform.alerts.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Enforces the hexagonal layering from the constitution.
 *
 * <p>Uses plain Jupiter tests + {@code rule.check(...)} instead of ArchUnit's own JUnit engine,
 * so it runs on any JUnit Platform version.
 */
class HexagonalArchTest {

    private static final String DOMAIN = "..alerts.domain..";
    private static final String APPLICATION = "..alerts.application..";
    private static final String API = "..alerts.api..";
    private static final String INFRASTRUCTURE = "..alerts.infrastructure..";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.fraudplatform.alerts");

    @Test
    @DisplayName("AC-000-03: layers depend only inward; api and infrastructure never see each other")
    void layersOnlyDependInward() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Api").definedBy(API)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .whereLayer("Api").mayNotBeAccessedByAnyLayer()
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Api", "Infrastructure")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Api", "Infrastructure")
                .check(CLASSES);
    }

    @Test
    @DisplayName("AC-000-02: domain depends on nothing but the JDK")
    void domainIsPureJava() {
        classes().that().resideInAPackage(DOMAIN)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN)
                .because("the domain must not know about Spring, Jackson, Kafka or adapters")
                .check(CLASSES);
    }

    @Test
    @DisplayName("AC-000-02: application layer is framework-free")
    void applicationIsFrameworkFree() {
        classes().that().resideInAPackage(APPLICATION)
                .should().onlyDependOnClassesThat().resideInAnyPackage("java..", DOMAIN, APPLICATION)
                .because("use cases are wired in infrastructure and unit-tested without a container")
                .check(CLASSES);
    }

    @Test
    @DisplayName("Constructor injection only — no @Autowired fields")
    void noFieldInjection() {
        noFields().should().beAnnotatedWith(Autowired.class)
                .because("constructor injection makes dependencies explicit and objects immutable")
                .check(CLASSES);
    }
}
