package the.chak.ecommerce.authentication;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class BceArchitectureTest {

    private static final String ROOT_PACKAGE = "the.chak.ecommerce.authentication";

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPaths("target/classes");
    }

    @Test
    @DisplayName("Fails the build if an entity class depends on the boundary or control layer")
    void entity_mustNotDependOn_boundaryOrControl() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage("..entity..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..boundary..", "..control..")
                .as("Entity classes must not depend on boundary or control")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if the repository layer depends on the boundary or control layer")
    void repository_mustNotDependOn_boundaryOrControl() {
        // given
        ArchRule rule = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..boundary..", "..control..")
                .as("Repositories must not depend on boundary or control - "
                        + "accept and return neutral query types instead")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if control depends on boundary classes other than DTOs")
    void control_mustNotDependOn_nonDtoBoundaryClasses() {
        // given
        DescribedPredicate<JavaClass> ownNonDtoBoundary =
                resideInAPackage(ROOT_PACKAGE + ".boundary..")
                        .and(not(resideInAPackage("..boundary.dto..")));

        ArchRule rule = noClasses()
                .that().resideInAPackage("..control..")
                .should().dependOnClassesThat(ownNonDtoBoundary)
                .as("Control must not depend on boundary infrastructure; only boundary.dto is allowed")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if a @Path resource lives outside the boundary package")
    void pathAnnotatedClasses_mustResideIn_boundary() {
        // given
        ArchRule rule = classes()
                .that().areAnnotatedWith("jakarta.ws.rs.Path")
                .and().areNotAnnotatedWith("org.eclipse.microprofile.rest.client.inject.RegisterRestClient")
                .should().resideInAPackage("..boundary..")
                .as("JAX-RS resources (@Path) must reside in the boundary package")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if a persistence entity lives outside the entity package")
    void persistenceEntities_mustResideIn_entityPackage() {
        // given
        ArchRule rule = classes()
                .that().areAnnotatedWith("jakarta.persistence.Entity")
                .or().areAnnotatedWith("io.quarkus.mongodb.panache.common.MongoEntity")
                .should().resideInAPackage("..entity..")
                .as("Persistence entities must reside in the entity package")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }

    @Test
    @DisplayName("Fails the build if an exception class lives outside the control package")
    void exceptionClasses_mustResideIn_control() {
        // given
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Exception")
                .should().resideInAPackage("..control..")
                .as("Exception classes must reside in the control package")
                .allowEmptyShould(true);

        // then
        rule.check(classes);
    }
}
