package org.kabieror.elwasys.backend.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;
import org.kabieror.elwasys.common.ConfigurationManager;
import org.kabieror.elwasys.common.DataManager;

/**
 * Baut eine Alt-Code-{@link DataManager} (aus dem Common-Modul) auf, die auf dieselbe
 * Test-Datenbank zeigt wie der neue {@code EntityManager} in den Backend-Tests (siehe
 * {@link TestPostgres}).
 *
 * <p>Zweck: Alt-vs-Neu-Äquivalenztests (siehe kb/05-migration-plan.md, AP2 "Entscheidungen")
 * - dieselbe Datenbankzeile wird einmal über den handgeschriebenen JDBC-Zugriff des
 * Alt-Codes (z.B. {@code Program#getPrice}, {@code User#getCredit}) und einmal über den
 * neuen Service (z.B. {@code PricingService}, {@code CreditService}) gelesen/berechnet; die
 * Ergebnisse müssen exakt (inkl. {@link java.math.BigDecimal}-Skala) übereinstimmen.
 *
 * <p>{@code ConfigurationManager} ist abstrakt und lädt ihre Default-Properties bereits im
 * Konstruktor der Basisklasse (bevor Felder der Unterklasse initialisiert werden können) -
 * daher der Umweg über ein {@link ThreadLocal}: {@link #create} befüllt es mit den
 * gewünschten Verbindungsdaten, bevor die {@code ConfigurationManager}-Unterklasse
 * konstruiert wird; deren überschriebenes {@code getDefaultsFileStream()} liest die Werte
 * aus dem ThreadLocal (funktioniert, weil virtuelle Methodenaufrufe während der
 * Super-Konstruktion bereits auf die Unterklassen-Implementierung dispatchen, die hier
 * bewusst nur auf statischem/ThreadLocal-Zustand basiert, nicht auf Instanzfeldern).
 */
public final class LegacyDataManagerFactory {

    private LegacyDataManagerFactory() {
    }

    /**
     * Erstellt eine neue Alt-Code-{@link DataManager}, die per JDBC gegen die per
     * {@code jdbcUrl} (Format {@code jdbc:postgresql://host:port/dbname}) beschriebene
     * Datenbank arbeitet.
     */
    public static DataManager create(String jdbcUrl, String user, String password) throws Exception {
        String withoutPrefix = jdbcUrl.substring("jdbc:postgresql://".length());
        int slash = withoutPrefix.indexOf('/');
        String hostPort = withoutPrefix.substring(0, slash);
        String rest = withoutPrefix.substring(slash + 1);
        int query = rest.indexOf('?');
        String dbName = query >= 0 ? rest.substring(0, query) : rest;

        Properties props = new Properties();
        props.setProperty("database.server", hostPort);
        props.setProperty("database.name", dbName);
        props.setProperty("database.user", user);
        props.setProperty("database.password", password);
        props.setProperty("database.useSsl", "false");

        // ConfigurationManager() nimmt keine Parameter entgegen und liest ihre Defaults
        // bereits synchron im Super-Konstruktor (über das virtuell dispatchte
        // getDefaultsFileStream()) - das ThreadLocal wird daher VOR dem Konstruktor-Aufruf
        // befüllt, nicht als Konstruktor-Argument (das ginge nicht, da der
        // Basisklassen-Konstruktor keine Parameter hat).
        LegacyTestConfigurationManager.PENDING.set(props);
        try {
            return new DataManager(new LegacyTestConfigurationManager());
        } finally {
            LegacyTestConfigurationManager.PENDING.remove();
        }
    }

    private static final class LegacyTestConfigurationManager extends ConfigurationManager {

        private static final ThreadLocal<Properties> PENDING = new ThreadLocal<>();

        LegacyTestConfigurationManager() throws Exception {
            super();
        }

        @Override
        public String getFileName() {
            // Existiert bewusst nicht: ConfigurationManager fällt dann auf die Defaults
            // (siehe getDefaultsFileStream()) zurück.
            return "/nonexistent-elwasys-backend-test-config.properties";
        }

        @Override
        public InputStream getDefaultsFileStream() {
            Properties props = PENDING.get();
            PENDING.remove();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                props.store(out, null);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new ByteArrayInputStream(out.toByteArray());
        }
    }
}
