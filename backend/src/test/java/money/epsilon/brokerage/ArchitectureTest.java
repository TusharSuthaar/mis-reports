package money.epsilon.brokerage;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;

/**
 * Module boundaries of the modular monolith. A module is a top-level package under money.epsilon.brokerage
 * (org, client, partner, ..., platform). Modules talk through each other's services, never their tables.
 */
@AnalyzeClasses(packages = ArchitectureTest.BASE, importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    static final String BASE = "money.epsilon.brokerage";

    @ArchTest
    static final ArchRule modulesHaveNoCycles = slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule entitiesAndRepositoriesStayInsideTheirModule = classes()
            .that().areAnnotatedWith(Entity.class).or().areAssignableTo(Repository.class)
            .should(beUsedOnlyFromTheirOwnModule());

    private static ArchCondition<JavaClass> beUsedOnlyFromTheirOwnModule() {
        return new ArchCondition<>("be used only from their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                String module = moduleOf(target);
                for (Dependency d : target.getDirectDependenciesToSelf()) {
                    JavaClass origin = d.getOriginClass();
                    if (origin.getPackageName().startsWith(BASE + ".") && !moduleOf(origin).equals(module)) {
                        events.add(SimpleConditionEvent.violated(d, d.getDescription()));
                    }
                }
            }
        };
    }

    private static String moduleOf(JavaClass c) {
        String rest = c.getPackageName().substring(Math.min(c.getPackageName().length(), BASE.length() + 1));
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
