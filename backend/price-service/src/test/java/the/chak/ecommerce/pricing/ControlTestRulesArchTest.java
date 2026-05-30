package the.chak.ecommerce.pricing;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ControlTestRulesArchTest {

    static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        testClasses = new ClassFileImporter().importPaths("target/test-classes");
    }

    @Test
    @DisplayName("Fails the build if a control-layer *ServiceTest is annotated with @QuarkusTest")
    void controlServiceTests_mustNotUse_quarkusTest() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage("..control..")
                .and().haveSimpleNameEndingWith("ServiceTest")
                .should().beAnnotatedWith("io.quarkus.test.junit.QuarkusTest")
                .as("Control *ServiceTest must use plain JUnit 5 + Mockito, not @QuarkusTest")
                .allowEmptyShould(true);

        // then
        rule.check(testClasses);
    }
}
