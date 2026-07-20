package org.kabieror.elwasys.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Basisklasse für Backend-Integrationstests gegen ein echtes PostgreSQL (siehe
 * {@link TestPostgres}: Testcontainers standardmäßig, lokaler Override via
 * {@code ELWASYS_TEST_JDBC_URL} in dieser Docker-losen Sandbox-Umgebung -
 * {@code backend/run-backend-tests.sh}).
 *
 * <p>Bewusst OHNE {@code @Transactional}: mehrere Tests in diesem Arbeitspaket (siehe
 * kb/05-migration-plan.md, AP2) vergleichen Alt-Code (eigene, autocommittende JDBC-
 * Connection über {@link LegacyDataManagerFactory}) mit dem neuen Service (Spring-Data-
 * Repositories). Ein rollenderes {@code @Transactional} auf Testebene würde vom Alt-Code
 * nicht gesehene, unfertige Daten erzeugen (jede sichtbare Verbindung ist eine eigene DB-
 * Sitzung). Jeder Repository-Aufruf committet daher für sich (Spring Data JPAs eigene
 * {@code @Transactional} pro Methode) - Testdaten bekommen daher pro Testklasse
 * eindeutige Namen (siehe Testklassen), damit parallele/wiederholte Läufe nicht
 * kollidieren.
 *
 * <p><b>Phase 3 AP1 (Vaadin-Integration, siehe kb/05-migration-plan.md)</b>: Vaadins eigene
 * Spring-Boot-Autokonfigurationsklassen (siehe {@code vaadin-spring}s
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}:
 * {@code SpringBootAutoConfiguration}, {@code SpringSecurityAutoConfiguration},
 * {@code VaadinScopesConfig}) sind NICHT auf {@code @ConditionalOnWebApplication}
 * eingeschränkt und versuchen daher auch in diesem Nicht-Web-Kontext
 * ({@code webEnvironment=NONE}) u.a. einen {@code WebApplicationContext} bzw. eine
 * {@code ServletRegistrationBean<SpringServlet>} zu autowiren - das schlägt hier
 * zwangsläufig fehl, weil es in einem reinen {@code AnnotationConfigApplicationContext}
 * keine solchen Beans gibt. Da diese Tests ohnehin keine Web-/Vaadin-Schicht brauchen (reine
 * Service-/Repository-/Auth-Tests), werden alle drei hier explizit ausgeschlossen statt sie
 * funktionsuntüchtig mitzuladen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=com.vaadin.flow.spring.SpringBootAutoConfiguration,"
                + "com.vaadin.flow.spring.SpringSecurityAutoConfiguration,com.vaadin.flow.spring.VaadinScopesConfig")
public abstract class AbstractBackendIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TestPostgres::jdbcUrl);
        registry.add("spring.datasource.username", TestPostgres::username);
        registry.add("spring.datasource.password", TestPostgres::password);
    }
}
