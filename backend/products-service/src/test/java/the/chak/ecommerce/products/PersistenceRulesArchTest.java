package the.chak.ecommerce.products;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Keeps persistence queries out of the control (service) layer. ArchUnit cannot read JPQL/SQL
 * strings, so instead of inspecting query literals we forbid the control layer from touching the
 * persistence DSL types and the EntityManager: any {@code repository.find(...)} chain leaks a
 * {@code PanacheQuery} into control, and {@code em.merge(...)} requires injecting an
 * {@code EntityManager}. The string-argument {@code delete}/{@code update} overloads return
 * primitives (no {@code PanacheQuery}), so they get a dedicated method-call rule.
 *
 * <p>Deliberately NOT covered: raw {@code mongoClient.startSession()} /
 * {@code repository.mongoCollection()} access used for cross-aggregate outbox transactions. Those
 * span two repositories within a single {@code ClientSession} and are an intentional exception, not
 * a stray query.
 */
class PersistenceRulesArchTest {

    static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter().importPaths("target/classes");
    }

    @Test
    @DisplayName("Fails the build if the control layer depends on the Hibernate Panache query DSL")
    void control_mustNotDependOn_ormPanacheQuery() {
        noClasses()
                .that().resideInAPackage("..control..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("io.quarkus.hibernate.orm.panache.PanacheQuery")
                .as("Control must not build queries - move find/list/stream calls to the repository")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if the control layer depends on the MongoDB Panache query DSL")
    void control_mustNotDependOn_mongoPanacheQuery() {
        noClasses()
                .that().resideInAPackage("..control..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("io.quarkus.mongodb.panache.PanacheQuery")
                .as("Control must not build queries - move find/list/stream calls to the repository")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if the control layer injects or uses the EntityManager")
    void control_mustNotDependOn_entityManager() {
        noClasses()
                .that().resideInAPackage("..control..")
                .should().dependOnClassesThat()
                .haveFullyQualifiedName("jakarta.persistence.EntityManager")
                .as("Control must not use the EntityManager - move persist/merge calls to the repository")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    @DisplayName("Fails the build if the control layer calls a string-query delete/update on a repository")
    void control_mustNotCall_stringQueryMutations() {
        noClasses()
                .that().resideInAPackage("..control..")
                .should().callMethodWhere(STRING_QUERY_MUTATION)
                .as("Control must not run bulk delete/update queries - move them to the repository")
                .allowEmptyShould(true)
                .check(classes);
    }

    /**
     * Matches {@code delete(String, ...)} / {@code update(String, ...)} on a Panache repository -
     * the bulk-query overloads that take a JPQL/HQL string first. The entity-argument overloads
     * ({@code delete(entity)}) are left untouched.
     */
    private static final DescribedPredicate<JavaMethodCall> STRING_QUERY_MUTATION =
            new DescribedPredicate<>("a string-query delete/update on a Panache repository") {
                @Override
                public boolean test(JavaMethodCall call) {
                    var target = call.getTarget();
                    String name = target.getName();
                    if (!"delete".equals(name) && !"update".equals(name)) {
                        return false;
                    }
                    var params = target.getRawParameterTypes();
                    if (params.isEmpty()
                            || !"java.lang.String".equals(params.get(0).getName())) {
                        return false;
                    }
                    JavaClass owner = target.getOwner();
                    return owner.isAssignableTo("io.quarkus.hibernate.orm.panache.PanacheRepositoryBase")
                            || owner.isAssignableTo("io.quarkus.mongodb.panache.PanacheMongoRepositoryBase");
                }
            };
}
