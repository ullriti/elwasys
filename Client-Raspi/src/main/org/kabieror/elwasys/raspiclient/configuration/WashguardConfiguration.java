package org.kabieror.elwasys.raspiclient.configuration;

import org.kabieror.elwasys.common.ConfigurationManager;
import org.kabieror.elwasys.common.Utilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Diese Klasse verwaltet die Konfiguration
 *
 * @author Oliver Kabierschke
 */
public class WashguardConfiguration extends ConfigurationManager {
    private static final String DS = System.getProperty("file.separator");
    private static final String FILE_NAME = System.getProperty("user.dir") + DS + "elwasys.properties";
    private static final String DEFAULTS_FILE_NAME =
            "/org/kabieror/elwasys/raspiclient/resources/defaultconfig.properties";

    private final File uidFile = new File(System.getProperty("user.dir") + DS + ".client-uid");
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private String uid = null;
    /**
     * Constructor
     *
     * @throws Exception
     */
    public WashguardConfiguration() throws Exception {
        super();
    }

    @Override
    public String getFileName() {
        return FILE_NAME;
    }

    @Override
    public InputStream getDefaultsFileStream() {
        return WashguardConfiguration.class.getResourceAsStream(DEFAULTS_FILE_NAME);
    }

    /**
     * The DeConz server address.
     * If this value is not specified, then a fhem connection will be opened.
     */
    public String getDeconzServer() {
        final var raw = this.props.getProperty("deconz.server");
        // When no deConz server is configured, return a blank value so that
        // callers fall back to fhem (see the javadoc above). Previously an empty
        // value was turned into "http://", which is not blank and therefore
        // always selected the deConz gateway (and a missing key threw an NPE).
        if (raw == null || raw.isBlank()) {
            return "";
        }
        var server = raw.trim();
        if (!Pattern.matches("^https?://.*", server)) {
            server = "http://" + server;
        }
        return server;
    }

    /**
     * The username to use for authentication at DeConz
     */
    public String getDeconzUser() {
        return this.props.getProperty("deconz.user");
    }

    /**
     * The password to use for authentication at DeConz
     */
    public String getDeconzPassword() {
        return this.props.getProperty("deconz.password");
    }

    /**
     * Gibt die Adresse, unter welcher der zu verwendende FHEM-Server erreichbar
     * ist.
     *
     * @return Die Adresse, unter welcher der zu verwendende FHEM-Server erreichbar ist.
     */
    public String getFhemConnectionString() {
        return this.props.getProperty("fhem.server");
    }

    /**
     * Gibt den TCP-Port zurück, auf welchem der FHEM-Server hört.
     *
     * @return Der TCP-Port, auf welchem der FHEM-Server hört.
     */
    public int getFhemPort() {
        return Integer.parseInt(this.props.getProperty("fhem.port"));
    }

    /**
     * Gibt den Name des Standorts des Waschwächters zurück (z.B. Waschküche1)
     *
     * @return Den Namen des Standortes des Waschwächters
     */
    public String getLocationName() {
        return this.props.getProperty("location");
    }

    /**
     * Gibt die Zeit bis zum Abdunkeln des Displays zurück
     *
     * @return Die Zeit bis zum Abdunkeln des Displays
     */
    public Duration getDisplayTimeout() {
        long secs;
        try {
            secs = Long.parseLong(this.props.getProperty("displayTimeout"));
        } catch (final NumberFormatException e) {
            this.logger.warn("The configuration value displayTimeout has an invalid format. Using 60 seconds instead.");
            return Duration.ofSeconds(60);
        }
        return Duration.ofSeconds(secs);
    }

    /**
     * Gibt die Zeit zurück, für die der Startbildschirm auf jeden Fall
     * angezeigt wird
     *
     * @return Die Zeit, die der Startbildschirm auf jeden Fall angezeigt wird
     */
    public Duration getStartupDelay() {
        long secs;
        try {
            secs = Long.parseLong(this.props.getProperty("startupDelay"));
        } catch (final NumberFormatException e) {
            this.logger.warn("The configuration value startupDelay has an invalid format. Using 2 seconds instead.");
            return Duration.ofSeconds(2);
        }
        return Duration.ofSeconds(secs);
    }

    /**
     * Die Basis-URL des Backends (Phase 4 AP4, siehe kb/05-migration-plan.md
     * "Client-Cutover"), z. B. {@code http://localhost:8080/}. Ersetzt die frühere
     * Direkt-DB-Anbindung als primären Datenzugriffspfad des Terminals.
     *
     * @return Die Basis-URL des Backends.
     */
    public String getBackendUrl() {
        return this.props.getProperty("backend.url");
    }

    /**
     * Der Standort-Token dieses Terminals für die Backend-API v1
     * ({@code Authorization: Bearer <token>}, siehe {@code backend.auth.terminal
     * .TerminalTokenService}) - ersetzt die frühere Anmeldung mit DB-Zugangsdaten. Erzeugt
     * über {@code token-cli} (siehe kb/04-build-and-run.md).
     *
     * @return Den Standort-Token dieses Terminals.
     */
    public String getBackendToken() {
        return this.props.getProperty("backend.token");
    }

    /**
     * The port on which the application should listen to prevent multiple instances of itself
     * @return
     */
    public int getSingleInstancePort() {
        int res;
        try {
            res = Integer.parseInt(this.props.getProperty("instance.port"));
        } catch (final NumberFormatException e) {
            this.logger.warn("The configuration value instance.port is not specified or has an invalid format. Using the default 8271 instead.");
            return 8271;
        }
        return res;
    }

    /**
     * Die Zeit nach der letzten Aktion eines Benutzers, nach der der angemeldete Benutzer automatisch abgemeldet werden
     * soll.
     */
    public int getAutoLogoutSeconds() {
        int time;
        try {
            time = Integer.parseInt(this.props.getProperty("sessionTimeout"));
        } catch (final NumberFormatException e) {
            this.logger
                    .warn("The configuration value 'sessionTimeout' has an invalid format. Using the default value " +
                            "instead.");
            return 20;
        }
        return time;
    }

    /**
     * Die URL des Online-Portals
     */
    public String getPortalUrl() {
        final String url = this.props.getProperty("portalUrl");
        if (url == null || url.isEmpty()) {
            this.logger.warn("The configuration value 'portalUrl' is not defined.");
            return "";
        }
        return url;
    }

    /**
     * Gibt die eindeutige ID dieses Clients zurück.
     *
     * @return Die eindeutige ID dieses Clients.
     */
    public String getUid() {
        if (this.uid == null) {
            if (this.uidFile.exists()) {
                try {
                    this.uid = Files.readAllLines(this.uidFile.toPath()).get(0).trim();
                    this.logger.info("Using the previously stored uid " + this.uid);
                } catch (final IOException e) {
                    this.logger.error("Could not read the uid file " + this.uidFile.getPath());
                    this.uid = Utilities.generateUid();
                }
            } else {
                this.uid = Utilities.generateUid();
                this.logger.info("Generated uid " + this.uid);
                try {
                    Files.write(this.uidFile.toPath(), this.uid.getBytes(), StandardOpenOption.CREATE);
                } catch (final IOException e) {
                    this.logger.error("Could not write the uid to the file " + this.uidFile.getPath());
                }
            }
        }
        return this.uid;
    }
}
