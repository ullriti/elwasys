package org.kabieror.elwasys.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressionstest zu ADR 0024: {@link Utilities#readLogTail} liefert nur das ENDE der Logdatei.
 * Vorher ging die ganze Datei als ein WebSocket-Frame zum Backend, was die Fernwartungs-
 * verbindung ab wenigen Minuten Terminal-Betrieb mit {@code 1009} abreißen ließ.
 */
class UtilitiesLogTailTest {

    @TempDir
    Path tempDir;

    @Test
    void returns_only_the_last_lines_and_marks_the_truncation() throws IOException {
        Path log = writeLines("many.log", 5000);

        List<String> lines = Utilities.readLogTail(log.toString(), 1000, 128L * 1024);

        // 1000 Logzeilen + Hinweiszeile.
        assertEquals(1001, lines.size());
        assertTrue(lines.get(0).startsWith("--- gekürzt:"), "Eine Kürzung muss sichtbar sein: " + lines.get(0));
        // Die Hinweiszeile nennt die Dateigröße - der Admin soll sehen, wie viel er NICHT sieht.
        assertTrue(lines.get(0).contains("Logdatei"), "Die Hinweiszeile soll die Dateigröße nennen: " + lines.get(0));
        assertEquals("line 4001", lines.get(1), "Es soll das ENDE des Logs geliefert werden");
        assertEquals("line 5000", lines.get(lines.size() - 1));
    }

    @Test
    void returns_the_whole_file_without_a_marker_when_it_fits() throws IOException {
        Path log = writeLines("small.log", 12);

        List<String> lines = Utilities.readLogTail(log.toString(), 1000, 128L * 1024);

        assertEquals(12, lines.size(), "Eine kleine Datei soll vollständig geliefert werden");
        assertEquals("line 1", lines.get(0));
        assertEquals("line 12", lines.get(11));
        assertFalse(lines.get(0).startsWith("--- gekürzt:"), "Ohne Kürzung darf kein Hinweis erscheinen");
    }

    /**
     * Der Byte-Deckel greift auch dort, wo das Zeilenlimit nicht greift - sonst könnte eine
     * Datei mit wenigen, dafür sehr langen Zeilen die Frame-Grenze weiterhin sprengen.
     */
    @Test
    void the_byte_budget_caps_few_but_very_long_lines() throws IOException {
        Path log = tempDir.resolve("long-lines.log");
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 20; i++) {
            content.append(String.valueOf((char) ('a' + i % 26)).repeat(10_000)).append('\n');
        }
        Files.writeString(log, content.toString(), StandardCharsets.UTF_8);

        List<String> lines = Utilities.readLogTail(log.toString(), 1000, 32L * 1024);

        assertTrue(lines.get(0).startsWith("--- gekürzt:"));
        long payloadBytes = String.join("\n", lines.subList(1, lines.size())).getBytes(StandardCharsets.UTF_8).length;
        assertTrue(payloadBytes <= 32L * 1024, "Der Byte-Deckel muss eingehalten werden, war: " + payloadBytes);
    }

    /**
     * Der Sprung ans Dateiende trifft fast nie eine Zeilengrenze - die angeschnittene erste
     * Zeile darf nicht halbiert ausgeliefert werden.
     */
    @Test
    void drops_the_partial_first_line() throws IOException {
        Path log = tempDir.resolve("partial.log");
        Files.writeString(log, "aaaaaaaaaa\nbbbbbbbbbb\ncccccccccc\n", StandardCharsets.UTF_8);

        // 25 Bytes vom Ende: mitten in die erste Zeile hinein.
        List<String> lines = Utilities.readLogTail(log.toString(), 1000, 25L);

        assertEquals(List.of("bbbbbbbbbb", "cccccccccc"), lines.subList(1, lines.size()));
    }

    @Test
    void missing_or_unset_file_yields_an_empty_list() throws IOException {
        assertTrue(Utilities.readLogTail(null, 1000, 128L * 1024).isEmpty());
        assertTrue(Utilities.readLogTail(tempDir.resolve("does-not-exist.log").toString(), 1000, 128L * 1024)
                .isEmpty());
    }

    @Test
    void an_empty_file_yields_an_empty_list() throws IOException {
        Path log = tempDir.resolve("empty.log");
        Files.writeString(log, "", StandardCharsets.UTF_8);

        assertTrue(Utilities.readLogTail(log.toString(), 1000, 128L * 1024).isEmpty());
    }

    private Path writeLines(String name, int count) throws IOException {
        Path log = tempDir.resolve(name);
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            content.append("line ").append(i).append('\n');
        }
        Files.writeString(log, content.toString(), StandardCharsets.UTF_8);
        return log;
    }
}
