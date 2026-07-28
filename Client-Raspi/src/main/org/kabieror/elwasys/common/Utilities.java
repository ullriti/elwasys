package org.kabieror.elwasys.common;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Diese Klasse stellt häufig gebrauchte Funktionalitäten zur Verfügung.
 *
 * @author Oliver Kabierschke
 *
 */
public class Utilities {

    /**
     * Version, die gilt, solange die Anwendung nicht aus einem gebauten Jar läuft (IDE,
     * {@code target/classes}, Testlauf). Zugleich der Sentinel, den der Helm-Chart-Guard und
     * die Release-Prüfungen als "kein echtes Release" erkennen.
     */
    static final String LOCAL_DEVELOPMENT_VERSION = "0.0.0-local-development";

    /**
     * Version der laufenden Anwendung. Wird aus dem Jar-Manifest gelesen
     * ({@code Implementation-Version}, von Maven aus {@code ${project.version}} gefüllt, siehe
     * {@code Client-Raspi/pom.xml}) – vorher war das eine feste Konstante, die nur der
     * Release-Workflow per {@code sed} ersetzte. Dadurch meldeten alle anderen Builds
     * dauerhaft den Sentinel, auch dort, wo die Version nutzersichtbar wird (die
     * {@code clientVersion} im WebSocket-Hello, die das Portal anzeigt).
     */
    public static final String APP_VERSION = resolveAppVersion(readManifestVersion());

    /**
     * Liest {@code Implementation-Version} aus dem Manifest des Jars, aus dem diese Klasse
     * geladen wurde. Liefert {@code null}, wenn ohne Jar gestartet wurde (dann ist das Package
     * nicht aus einem Archiv definiert) – der Aufrufer fällt darauf auf den Sentinel zurück.
     */
    private static String readManifestVersion() {
        final Package pkg = Utilities.class.getPackage();
        return pkg == null ? null : pkg.getImplementationVersion();
    }

    /**
     * Wählt zwischen Manifest-Version und Sentinel. Paketprivat, damit der Fallback ohne
     * gebautes Jar testbar ist.
     *
     * @param manifestVersion Die Version aus dem Jar-Manifest (darf {@code null}/leer sein).
     * @return Die Manifest-Version, sonst {@link #LOCAL_DEVELOPMENT_VERSION}.
     */
    static String resolveAppVersion(String manifestVersion) {
        return manifestVersion == null || manifestVersion.isBlank()
                ? LOCAL_DEVELOPMENT_VERSION
                : manifestVersion;
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ConfigurationManager config;

    public Utilities(ConfigurationManager config) {
        this.config = config;
    }

    /**
     * Generiert ein zufälliges Passwort.
     *
     * @return Ein zufälliges Passwort.
     */
    public static String generatePassword() {
        final char[] chars = ("abcdefghijklmnopqrstuvwxyz" + "ABCDEFGEHIJKLMNOPQRSTUVWXYZ"
                + "0123456789" + "-_!?=()#").toCharArray();
        return RandomStringUtils.random(12, chars);
    }

    /**
     * Generiert eine zufällige UID.
     *
     * @return Eine zufällige UID.
     */
    public static String generateUid() {
        return RandomStringUtils.randomAlphanumeric(20);
    }

    /**
     * Maskiert eine RFID-Karten-Id für die Log-Ausgabe: es bleiben höchstens die letzten vier
     * Zeichen sichtbar, der Rest wird durch {@code *} ersetzt. Die Kartennummer ist das einzige,
     * klonbare Terminal-Login-Merkmal und darf darum nicht im Klartext in ein per Fernwartung
     * abrufbares Log gelangen (Issue #56).
     *
     * @param cardId Die zu maskierende Karten-Id (darf {@code null} sein).
     * @return Die maskierte Karten-Id, oder {@code null}, wenn die Eingabe {@code null} war.
     */
    public static String maskCardId(String cardId) {
        if (cardId == null) {
            return null;
        }
        final int visible = 4;
        if (cardId.length() <= visible) {
            // Kurze Ids komplett maskieren - sonst wäre die gesamte Id sichtbar.
            return "*".repeat(cardId.length());
        }
        return "*".repeat(cardId.length() - visible) + cardId.substring(cardId.length() - visible);
    }

    /**
     * Die aktuelle Logdatei für die Fernwartung ({@code LOG_REQUEST}). Liefert deterministisch
     * das INFO-Log (Appender-Name {@code "FILE"} in der von {@code setup.sh} erzeugten
     * logback-Konfiguration) und NICHT das DEBUG-Log: die per Portal abrufbare Datei soll keine
     * DEBUG-Details (z. B. Karten-Ids) enthalten (Issue #56). Existiert kein so benannter
     * Appender (z. B. in Testumgebungen), fällt sie auf den ersten gefundenen FileAppender zurück
     * (bisheriges Verhalten).
     */
    public static String getCurrentLogFile() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        String firstFileAppender = null;
        for (final ch.qos.logback.classic.Logger logger : context.getLoggerList()) {
            for (final Iterator<Appender<ILoggingEvent>> index =
                    logger.iteratorForAppenders(); index.hasNext();) {
                final Appender<ILoggingEvent> appender = index.next();
                if (appender instanceof FileAppender<?>) {
                    final FileAppender<ILoggingEvent> fileAppender =
                            (FileAppender<ILoggingEvent>) appender;
                    if ("FILE".equals(fileAppender.getName())) {
                        return fileAppender.getFile();
                    }
                    if (firstFileAppender == null) {
                        firstFileAppender = fileAppender.getFile();
                    }
                }
            }
        }
        return firstFileAppender;
    }

    /**
     * Liest das ENDE einer Logdatei für die Fernwartung ({@code LOG_REQUEST}). Bewusst nicht die
     * ganze Datei: die Antwort geht als EIN WebSocket-Text-Frame zum Backend, und die
     * logback-Konfiguration der Terminals rollt nur täglich ohne Größenlimit (siehe
     * {@code setup.sh}) - ein Tageslog sprengte jede vernünftige Frame-Grenze und riss damit die
     * gesamte Fernwartungsverbindung ab (siehe ADR 0024). Für die Diagnose zählt ohnehin das
     * Ende des Logs.
     * <p>
     * Gelesen wird nur der Datei-Schwanz (Sprung ans Ende statt {@code readAllLines}), damit eine
     * sehr große Logdatei nicht komplett in den Speicher wandert. Wurde gekürzt, ist die erste
     * gelieferte Zeile ein Hinweis darauf - der Admin im Portal soll nicht stillschweigend ein
     * unvollständiges Log sehen.
     *
     * @param fileName Pfad der Logdatei, {@code null} erlaubt (dann leere Liste)
     * @param maxLines Höchstzahl gelieferter Logzeilen (ohne Hinweiszeile)
     * @param maxBytes Höchstzahl vom Dateiende gelesener Bytes - deckelt auch eine Datei mit
     *                 wenigen, dafür sehr langen Zeilen
     */
    public static List<String> readLogTail(String fileName, int maxLines, long maxBytes) throws IOException {
        if (fileName == null) {
            return new ArrayList<>();
        }
        final Path path = Path.of(fileName);
        if (!Files.isReadable(path)) {
            return new ArrayList<>();
        }
        final long size = Files.size(path);
        final long from = Math.max(0, size - Math.min(maxBytes, Integer.MAX_VALUE));

        final byte[] buffer = new byte[(int) (size - from)];
        final ByteBuffer target = ByteBuffer.wrap(buffer);
        try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
            channel.position(from);
            while (target.hasRemaining() && channel.read(target) > 0) {
                // Bis der Puffer voll ist bzw. die Datei endet - ein einzelnes read() muss den
                // angeforderten Bereich nicht vollständig liefern.
            }
        }

        // Nur den tatsächlich gelesenen Teil dekodieren: rollt logback die Datei genau zwischen
        // Größenabfrage und Lesen weg, ist der Puffer nicht voll - der Rest wären NUL-Bytes.
        final List<String> lines = new ArrayList<>(
                Arrays.asList(new String(buffer, 0, target.position(), StandardCharsets.UTF_8).split("\n", -1)));
        // Ein Sprung mitten in die Datei trifft fast nie eine Zeilengrenze - die angeschnittene
        // erste Zeile wird verworfen, statt sie halbiert auszuliefern.
        boolean truncated = from > 0;
        if (truncated && !lines.isEmpty()) {
            lines.remove(0);
        }
        // Endet die Datei mit einem Zeilenumbruch (der Normalfall), liefert split() ein leeres
        // Schlusselement - das ist keine Logzeile.
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        if (lines.size() > maxLines) {
            truncated = true;
            lines.subList(0, lines.size() - maxLines).clear();
        }
        if (truncated) {
            // Bewusst die Dateigröße nennen: der Admin soll sehen, WIE viel er nicht sieht. Die
            // Grenzen stehen dahinter, damit erkennbar ist, warum gekürzt wurde.
            lines.add(0, String.format("--- gekürzt: Logdatei %d KiB, gezeigt werden die letzten %d Zeilen "
                    + "(höchstens %d KiB) ---", size / 1024, maxLines, maxBytes / 1024));
        }
        return lines;
    }
}
