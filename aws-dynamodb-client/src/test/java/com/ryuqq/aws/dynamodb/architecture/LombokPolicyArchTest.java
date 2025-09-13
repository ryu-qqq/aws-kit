package com.ryuqq.aws.dynamodb.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit tests to enforce Lombok usage policies across the AWS Kit project.
 * 
 * Policy Summary:
 * - @Data is forbidden entirely (too permissive)
 * - @Builder allowed only on types/DTO classes
 * - @Setter forbidden on configuration/properties classes
 * - @RequiredArgsConstructor allowed in limited cases
 * - @Slf4j is always allowed for logging
 * - @EqualsAndHashCode forbidden on entities
 */
@DisplayName("Lombok Usage Policy Enforcement")
class LombokPolicyArchTest {

    private JavaClasses importedClasses;

    @BeforeEach
    void setUp() {
        importedClasses = new ClassFileImporter()
            .importPackages("com.ryuqq.aws..");
    }

    @Test
    @DisplayName("@Data annotation should be forbidden entirely")
    void dataAnnotationShouldBeForbidden() {
        ArchRule rule = noClasses()
            .should().beAnnotatedWith("lombok.Data")
            .because("@Data is too permissive and creates maintenance issues. " +
                     "Use specific annotations like @Getter, @RequiredArgsConstructor instead.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("@Builder should only be used on types/DTO classes")
    void builderAnnotationShouldOnlyBeUsedOnTypes() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("lombok.Builder")
            .should().resideInAnyPackage("..types..", "..dto..", "..request..", "..response..")
            .because("@Builder should only be used on DTOs, types, and data transfer objects, " +
                     "not on service classes, configurations, or entities.")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("@Setter should be forbidden on configuration classes")
    void setterAnnotationShouldBeForbiddenOnConfigurationClasses() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..config..", "..configuration..", "..properties..")
            .should().beAnnotatedWith("lombok.Setter")
            .because("Configuration classes should be immutable after construction. " +
                     "Use @ConfigurationProperties with constructor binding instead.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("@EqualsAndHashCode should be forbidden on entity classes")
    void equalsAndHashCodeShouldBeForbiddenOnEntities() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Entity")
            .or().resideInAnyPackage("..entity..", "..domain..")
            .should().beAnnotatedWith("lombok.EqualsAndHashCode")
            .because("Entity classes require careful equals/hashCode implementation " +
                     "considering JPA proxy objects and database identity.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("@RequiredArgsConstructor should be used cautiously on service classes")
    void requiredArgsConstructorShouldBeUsedCarefully() {
        ArchRule rule = classes()
            .that().areAnnotatedWith("lombok.RequiredArgsConstructor")
            .and().resideInAnyPackage("..service..", "..component..")
            .should().haveOnlyFinalFields()
            .because("Service classes with @RequiredArgsConstructor should have only final fields " +
                     "to ensure immutability and thread safety.")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("@Slf4j is allowed everywhere (positive test)")
    void slf4jIsAllowedEverywhere() {
        // This is a positive test - we just verify @Slf4j usage doesn't violate other rules
        // The rule passes if classes with @Slf4j exist and don't violate other constraints
        ArchRule rule = classes()
            .that().areAnnotatedWith("lombok.Slf4j")
            .should().notBeInterfaces()
            .because("@Slf4j should be used on concrete classes for logging capability.")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Properties classes should use constructor binding instead of @Setter")
    void propertiesClassesShouldUseConstructorBinding() {
        ArchRule rule = classes()
            .that().haveSimpleNameEndingWith("Properties")
            .or().resideInAnyPackage("..properties..")
            .should().notBeAnnotatedWith("lombok.Setter")
            .because("Properties classes should be immutable with constructor binding " +
                     "rather than using setters for configuration values.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Service classes should not use @Setter")
    void serviceClassesShouldNotUseSetter() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Service")
            .or().resideInAnyPackage("..service..")
            .should().beAnnotatedWith("lombok.Setter")
            .because("Service classes should be immutable after construction " +
                     "to ensure thread safety and proper dependency injection.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Configuration classes should not use @Setter")
    void configurationClassesShouldNotUseSetter() {
        ArchRule rule = noClasses()
            .that().haveSimpleNameEndingWith("Config")
            .or().haveSimpleNameEndingWith("Configuration")
            .or().resideInAnyPackage("..config..")
            .should().beAnnotatedWith("lombok.Setter")
            .because("Configuration classes should be immutable to prevent " +
                     "runtime configuration changes that could cause inconsistent state.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Type classes should not use @Setter for immutable design")
    void typeClassesShouldNotUseSetter() {
        ArchRule rule = noClasses()
            .that().resideInAnyPackage("..types..")
            .and().areNotInterfaces()
            .should().beAnnotatedWith("lombok.Setter")
            .because("Type classes should be immutable value objects. " +
                     "Use @Builder for construction, avoid @Setter for mutability.");

        rule.check(importedClasses);
    }

    @Test
    @DisplayName("Classes should not combine @Data with other Lombok annotations")
    void classesShouldNotCombineDataWithOtherAnnotations() {
        ArchRule rule = noClasses()
            .that().areAnnotatedWith("lombok.Data")
            .should().beAnnotatedWith("lombok.Getter")
            .orShould().beAnnotatedWith("lombok.Setter")
            .orShould().beAnnotatedWith("lombok.ToString")
            .orShould().beAnnotatedWith("lombok.EqualsAndHashCode")
            .because("@Data already includes @Getter, @Setter, @ToString, and @EqualsAndHashCode. " +
                     "Combining them is redundant and indicates misunderstanding of @Data scope.")
            .allowEmptyShould(true);

        rule.check(importedClasses);
    }

    // Helper method to extend ArchUnit with custom conditions
    private static com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass> haveOnlyFinalFields() {
        return new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>("have only final fields") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass javaClass, com.tngtech.archunit.lang.ConditionEvents events) {
                javaClass.getFields().stream()
                    .filter(field -> !field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC))
                    .filter(field -> !field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.FINAL))
                    .forEach(field -> {
                        events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(
                            field, 
                            String.format("Field %s in class %s is not final", 
                                field.getName(), 
                                javaClass.getName())
                        ));
                    });
            }
        };
    }
}