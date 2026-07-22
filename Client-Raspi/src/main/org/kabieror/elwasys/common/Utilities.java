package org.kabieror.elwasys.common;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

/**
 * Diese Klasse stellt häufig gebrauchte Funktionalitäten zur Verfügung.
 *
 * @author Oliver Kabierschke
 *
 */
public class Utilities {

    public static final String APP_VERSION = "0.0.0-local-development";

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final ConfigurationManager config;

    public Utilities(ConfigurationManager config) {
        this.config = config;
    }

    /**
     * Erzeugt einen SHA-1-Hash einer gegebenen Zeichenkette.
     *
     * @param s
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static String sha1(String s) throws NoSuchAlgorithmException {
        MessageDigest md = null;
        md = MessageDigest.getInstance("SHA-1");
        final byte[] b = md.digest(s.getBytes());
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
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
}
