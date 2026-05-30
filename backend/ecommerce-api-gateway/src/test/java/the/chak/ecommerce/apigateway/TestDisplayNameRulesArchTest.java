package the.chak.ecommerce.apigateway;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;

class TestDisplayNameRulesArchTest {

    static JavaClasses testClasses;

    @BeforeAll
    static void importClasses() {
        testClasses = new ClassFileImporter().importPaths("target/test-classes");
    }

    @Test
    @DisplayName("Fails the build if a @Test method is missing a @DisplayName description")
    void everyTestMethod_mustBeAnnotatedWith_displayName() {
        ArchRule rule = methods()
                .that().areAnnotatedWith("org.junit.jupiter.api.Test")
                .should().beAnnotatedWith("org.junit.jupiter.api.DisplayName")
                .as("Every @Test method must declare a @DisplayName description")
                .allowEmptyShould(true);

        rule.check(testClasses);
    }
}
