package the.chak.ecommerce.authentication;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class TransactionalRulesArchTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPaths("target/classes");
    }

    @Test
    @DisplayName("Fails the build if a boundary class is annotated with @Transactional")
    void noClass_inBoundary_shouldBeAnnotatedWith_transactional() {
        noClasses()
                .that().resideInAPackage("..boundary..")
                .should().beAnnotatedWith("jakarta.transaction.Transactional")
                .as("@Transactional must not appear on boundary classes — use control layer")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if an entity class is annotated with @Transactional")
    void noClass_inEntity_shouldBeAnnotatedWith_transactional() {
        noClasses()
                .that().resideInAPackage("..entity..")
                .should().beAnnotatedWith("jakarta.transaction.Transactional")
                .as("@Transactional must not appear on entity classes — use control layer")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if a boundary method is annotated with @Transactional")
    void noMethod_inBoundary_shouldBeAnnotatedWith_transactional() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..boundary..")
                .should().beAnnotatedWith("jakarta.transaction.Transactional")
                .as("@Transactional must not appear on boundary methods — use control layer")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if an entity method is annotated with @Transactional")
    void noMethod_inEntity_shouldBeAnnotatedWith_transactional() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..entity..")
                .should().beAnnotatedWith("jakarta.transaction.Transactional")
                .as("@Transactional must not appear on entity methods — use control layer")
                .allowEmptyShould(true)
                .check(classes);
    }
}
