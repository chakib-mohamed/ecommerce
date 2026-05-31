package the.chak.ecommerce.authentication;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LombokRulesArchTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPaths("target/classes");
    }

    @Test
    @DisplayName("Fails the build if any class is annotated with Lombok @Data")
    void noClass_shouldUse_dataAnnotation() {
        // given
        ArchRule rule = noClasses()
                .should().beAnnotatedWith("lombok.Data")
                .as("@Data is banned - use @Getter/@Setter explicitly per class type")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if an entity uses Lombok @EqualsAndHashCode")
    void entities_mustNotUse_equalsAndHashCode() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().beAnnotatedWith("lombok.EqualsAndHashCode")
                .as("Entity identity must come from the database ID, not Lombok-generated equals/hashCode")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if an entity uses Lombok @ToString")
    void entities_mustNotUse_toString() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().beAnnotatedWith("lombok.ToString")
                .as("@ToString on entities can trigger lazy-loading and leak sensitive data")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if a *Config bean uses Lombok @Setter")
    void configBeans_mustNotUse_setter() {
        // given
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Config")
                .should().beAnnotatedWith("lombok.Setter")
                .as("Config beans must be immutable after injection - no @Setter allowed")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if a Panache MongoDB entity uses Lombok @Getter")
    void mongoEntities_mustNotUse_lombokGetter() {
        // given
        ArchRule rule = noClasses()
                .that().areAssignableTo(io.quarkus.mongodb.panache.PanacheMongoEntity.class)
                .should().beAnnotatedWith("lombok.Getter")
                .as("Panache MongoDB entities use public fields - Lombok @Getter is redundant")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if a Panache MongoDB entity uses Lombok @Setter")
    void mongoEntities_mustNotUse_lombokSetter() {
        // given
        ArchRule rule = noClasses()
                .that().areAssignableTo(io.quarkus.mongodb.panache.PanacheMongoEntity.class)
                .should().beAnnotatedWith("lombok.Setter")
                .as("Panache MongoDB entities use public fields - Lombok @Setter is redundant")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }
}

