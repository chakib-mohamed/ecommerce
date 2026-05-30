package the.chak.ecommerce.products;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;

class LazyRelationshipsTest {

    @Test
    @DisplayName("Fails the build if a JPA relationship field does not declare fetch = LAZY")
    void jpaRelationships_entityFields_mustUseLazyFetch() {
        // given
        JavaClasses classes = new ClassFileImporter()
                .importPackages("the.chak.ecommerce.products.entity");

        // when
        var manyToOneRule = fields().that().areAnnotatedWith(ManyToOne.class).should(beLazy(ManyToOne.class)).allowEmptyShould(true);
        var oneToOneRule = fields().that().areAnnotatedWith(OneToOne.class).should(beLazy(OneToOne.class)).allowEmptyShould(true);
        var oneToManyRule = fields().that().areAnnotatedWith(OneToMany.class).should(beLazy(OneToMany.class)).allowEmptyShould(true);
        var manyToManyRule = fields().that().areAnnotatedWith(ManyToMany.class).should(beLazy(ManyToMany.class)).allowEmptyShould(true);

        // then
        manyToOneRule.check(classes);
        oneToOneRule.check(classes);
        oneToManyRule.check(classes);
        manyToManyRule.check(classes);
    }

    private static <A extends Annotation> ArchCondition<JavaField> beLazy(Class<A> annotationType) {
        return new ArchCondition<>("declare fetch = FetchType.LAZY") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                try {
                    A ann = field.getAnnotationOfType(annotationType);
                    FetchType fetch = (FetchType) annotationType.getMethod("fetch").invoke(ann);
                    events.add(new SimpleConditionEvent(field, fetch == FetchType.LAZY,
                            field.getFullName() + " has fetch=" + fetch + " but must be FetchType.LAZY"));
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
