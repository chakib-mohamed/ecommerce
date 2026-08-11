package the.chak.ecommerce.authentication;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Entities must not carry generated identity or a generated {@code toString}.
 *
 * <p>Lombok's {@code @Data} bundles {@code equals}, {@code hashCode} and {@code toString} onto a
 * class. On a JPA entity all three are wrong: identity has to come from the database id, or two
 * unsaved rows compare equal and a set of them collapses; and {@code toString} walks every field,
 * which triggers lazy loading and prints whatever the entity happens to hold.
 *
 * <p><b>These rules check the methods, not the annotation, and that is deliberate.</b> The sibling
 * services assert {@code beAnnotatedWith("lombok.Data")} against compiled classes, which can never
 * match: Lombok's annotations are {@code SOURCE}-retention and are gone by the time bytecode
 * exists. Compiling an entity with {@code @Data} and running those rules passes - verified. What
 * survives compilation is what Lombok generated, so that is what is asserted here, and it catches
 * a hand-written {@code equals} on an entity too.
 */
class LombokRulesArchTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPaths("target/classes");
    }

    @Test
    @DisplayName("Fails the build if an entity declares equals")
    void entities_mustNotDeclare_equals() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..entity..")
                .should().haveName("equals")
                .as("Entity identity must come from the database id - no generated or hand-written "
                        + "equals (this is what Lombok @Data or @EqualsAndHashCode would add)")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if an entity declares hashCode")
    void entities_mustNotDeclare_hashCode() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..entity..")
                .should().haveName("hashCode")
                .as("An entity's hashCode must not be derived from its fields - unsaved rows would "
                        + "collide and a set of them would collapse")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if an entity declares toString")
    void entities_mustNotDeclare_toString() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..entity..")
                .should().haveName("toString")
                .as("toString on an entity walks every field: it triggers lazy loading and prints "
                        + "whatever the row holds")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if a *Config bean exposes a setter")
    void configBeans_mustNotDeclare_setters() {
        noMethods()
                .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Config")
                .should().haveNameStartingWith("set")
                .as("Config beans must be immutable after injection")
                .allowEmptyShould(true)
                .check(classes);
    }
}
