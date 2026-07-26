package org.kabieror.elwasys.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.ProgramType;
import org.kabieror.elwasys.backend.domain.TimeUnitType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;

/**
 * Preis-Parität zum Terminal (Issue #91, finale Review R3b): {@code OfflinePricing} im
 * Client-Raspi-Modul ist eine 1:1-Portierung dieses {@link PricingService} für den
 * Offline-Betrieb. Weicht eine der beiden Seiten ab, rechnet das Terminal offline anders ab als
 * das Backend online - also mit echtem Geld falsch.
 *
 * <p>Die Fälle stehen in {@code test-fixtures/pricing-parity.csv} - derselben Datei, gegen die
 * {@code OfflinePricingParityTest} im Terminal-Modul läuft. Ein Modul-übergreifender Test ist
 * nicht möglich (die Module kennen einander nicht, bewusst - siehe docs/kb/01-architecture.md);
 * die gemeinsame Fixture ist die Klammer, die beide Seiten zusammenhält.
 */
class PricingServiceParityTest {

    /**
     * Das Arbeitsverzeichnis der Tests ist das Modulverzeichnis (Surefire-Default), die
     * gemeinsame Datei liegt eine Ebene darüber in der Repo-Wurzel.
     */
    private static final Path FIXTURE = Path.of("..", "test-fixtures", "pricing-parity.csv");

    private final PricingService pricingService = new PricingService();

    @TestFactory
    List<DynamicTest> onlinePriceMatchesTheSharedParityFixture() throws IOException {
        List<DynamicTest> tests = new ArrayList<>();
        for (String line : Files.readAllLines(FIXTURE, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            tests.add(DynamicTest.dynamicTest(trimmed, () -> assertCase(trimmed)));
        }
        // Schutz gegen ein stillschweigend leeres Ergebnis (z.B. verschobene Fixture-Datei):
        // ein Test, der nichts prüft, sähe sonst wie ein grüner Lauf aus.
        assertTrue(tests.size() >= 18, "Die Paritäts-Fixture sollte alle Fälle liefern");
        return tests;
    }

    private void assertCase(String line) {
        String[] c = line.split(";");

        ProgramEntity program = new ProgramEntity("Parität", ProgramType.valueOf(c[0]), Integer.parseInt(c[5]));
        program.setFlagfall(new BigDecimal(c[1]));
        program.setRate(new BigDecimal(c[2]));
        program.setTimeUnit(TimeUnitType.valueOf(c[3]));
        program.setFreeDurationSeconds(Integer.parseInt(c[4]));

        // groupPresent unterscheidet "kein Benutzer" (hier: null) von "Benutzer mit rabattfreier
        // Gruppe" - zwei Wege zu demselben Preis, die beide Implementierungen verschieden
        // ausdrücken (Backend über den else-Zweig, Terminal über eine Vorab-Prüfung).
        UserEntity user = "yes".equals(c[9])
                ? new UserEntity("Parität", "paritaet",
                        new UserGroupEntity("Parität", DiscountType.valueOf(c[7]), Double.parseDouble(c[8])))
                : null;

        BigDecimal actual = this.pricingService.getPrice(program,
                Duration.ofSeconds(Long.parseLong(c[6])), user);

        assertEquals(0, new BigDecimal(c[10]).compareTo(actual), "Erwartet " + c[10] + ", berechnet " + actual);
    }
}
