package the.chak.ecommerce.products;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LombokRulesArchTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPaths("target/classes");
    }

    @Test
    void noClass_shouldUse_dataAnnotation() {
        // given
        ArchRule rule = noClasses()
                .should().beAnnotatedWith("lombok.Data")
                .as("@Data is banned — use @Getter/@Setter explicitly per class type")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
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
    void configBeans_mustNotUse_setter() {
        // given
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Config")
                .should().beAnnotatedWith("lombok.Setter")
                .as("Config beans must be immutable after injection — no @Setter allowed")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }
}

