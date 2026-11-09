package dev.harbordesk.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.harbordesk.persistence.TenantSession;
import dev.harbordesk.persistence.TenantTx;

@AnalyzeClasses(packages = "dev.harbordesk", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Web, domain, security and rate limiting know nothing about JDBC. */
    @ArchTest
    static final ArchRule noJdbcOutsidePersistence = noClasses()
            .that().resideInAnyPackage("dev.harbordesk.web..", "dev.harbordesk.domain..", "dev.harbordesk.security..",
                    "dev.harbordesk.ratelimit..")
            .should().dependOnClassesThat().resideInAnyPackage("java.sql..", "javax.sql..", "org.springframework.jdbc..",
                    "org.flywaydb..", "org.springframework.transaction..")
            .as("web and domain must not access JDBC directly");

    /** Controllers go through the domain services. */
    @ArchTest
    static final ArchRule webDoesNotTouchPersistence = noClasses()
            .that().resideInAPackage("dev.harbordesk.web..")
            .should().dependOnClassesThat().resideInAPackage("dev.harbordesk.persistence..")
            .as("web talks to the domain only");

    @ArchTest
    static final ArchRule domainDoesNotDependOnWeb = noClasses()
            .that().resideInAPackage("dev.harbordesk.domain..")
            .should().dependOnClassesThat().resideInAPackage("dev.harbordesk.web..");

    /** Only domain services use repositories. */
    @ArchTest
    static final ArchRule repositoriesOnlyUsedByDomain = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .should().onlyBeAccessed().byAnyPackage("dev.harbordesk.domain..", "dev.harbordesk.persistence..");

    /** Every public repository method needs a TenantSession, so it cannot be called outside a tenant transaction. */
    @ArchTest
    static final ArchRule repositoryMethodsTakeTenantSession = methods()
            .that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Repository")
            .and().arePublic()
            .should(new ArchCondition<JavaMethod>("have a TenantSession as first parameter") {
                @Override
                public void check(JavaMethod m, ConditionEvents events) {
                    var params = m.getRawParameterTypes();
                    if (params.isEmpty() || !params.get(0).isEquivalentTo(TenantSession.class)) {
                        events.add(SimpleConditionEvent.violated(m, m.getFullName() + " has no TenantSession parameter"));
                    }
                }
            });

    /** Only TenantTx creates sessions, and TenantTx is the one place that sets the tenant on the transaction. */
    @ArchTest
    static final ArchRule onlyTenantTxCreatesSessions = noClasses()
            .that().areNotAssignableTo(TenantTx.class).and().doNotHaveFullyQualifiedName(TenantSession.class.getName())
            .should().callConstructor(TenantSession.class, dev.harbordesk.domain.TenantInfo.class,
                    org.springframework.jdbc.core.simple.JdbcClient.class);

    /** JdbcClient stays inside the persistence package. */
    @ArchTest
    static final ArchRule jdbcClientOnlyInPersistence = noClasses()
            .that().resideOutsideOfPackage("dev.harbordesk.persistence..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient");
}
