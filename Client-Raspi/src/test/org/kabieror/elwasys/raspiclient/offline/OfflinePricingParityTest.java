package org.kabieror.elwasys.raspiclient.offline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.kabieror.elwasys.common.ProgramType;
import org.kabieror.elwasys.raspiclient.api.dto.DiscountType;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotProgramDto;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotUserGroupDto;

/**
 * Preis-Parität zum Backend (Issue #91, finale Review R3b): {@link OfflinePricing} ist eine
 * 1:1-Portierung von {@code backend.service.PricingService} - weicht eine der beiden Seiten ab,
 * rechnet das Terminal offline anders ab als das Backend online, also mit echtem Geld falsch.
 * Bis hierher gab es dagegen keine Absicherung: beide Implementierungen waren nur durch den
 * Klassenkommentar aneinander gebunden.
 *
 * <p>Die Fälle stehen in {@code test-fixtures/pricing-parity.csv} - derselben Datei, gegen die
 * {@code PricingServiceParityTest} im Backend läuft. Wer eine Seite ändert, ohne die andere
 * nachzuziehen, bekommt einen roten Test statt einer stillen Divergenz.
 */
class OfflinePricingParityTest {

    /**
     * Das Arbeitsverzeichnis der Tests ist das Modulverzeichnis (Surefire-Default), die
     * gemeinsame Datei liegt eine Ebene darüber in der Repo-Wurzel.
     */
    private static final Path FIXTURE = Path.of("..", "test-fixtures", "pricing-parity.csv");

    @TestFactory
    List<DynamicTest> offlinePriceMatchesTheSharedParityFixture() throws IOException {
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
        assertTrue(tests.size() >= 10, "Die Paritäts-Fixture sollte alle Fälle liefern");
        return tests;
    }

    private static void assertCase(String line) {
        String[] c = line.split(";");
        SnapshotProgramDto program = new SnapshotProgramDto(1, "Parität", ProgramType.valueOf(c[0]),
                Integer.parseInt(c[5]), Integer.parseInt(c[4]), new BigDecimal(c[1]), new BigDecimal(c[2]),
                ChronoUnit.valueOf(c[3]), false, 0, true, List.of());
        DiscountType discountType = DiscountType.valueOf(c[6]);
        SnapshotUserGroupDto group = discountType == DiscountType.NONE ? null
                : new SnapshotUserGroupDto(1, "Parität", discountType, Double.parseDouble(c[7]));

        BigDecimal actual = OfflinePricing.price(program, Duration.ofSeconds(Long.parseLong(c[5])), group);

        assertEquals(0, new BigDecimal(c[8]).compareTo(actual),
                "Erwartet " + c[8] + ", berechnet " + actual);
    }
}
