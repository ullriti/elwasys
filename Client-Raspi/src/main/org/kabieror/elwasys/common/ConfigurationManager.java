package org.kabieror.elwasys.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Diese Klasse verwaltet die Konfiguration
 * 
 * @author Oliver Kabierschke
 *
 */
public abstract class ConfigurationManager {

    protected final Properties props;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Constructor
     * 
     * @throws Exception
     */
    public ConfigurationManager() throws Exception {

        final Properties defaults = new Properties();
        // final InputStream defaultsStream =
        // Thread.currentThread().getContextClassLoader()
        // .getResourceAsStream(this.getDefaultsFileName());
        try {
            defaults.load(this.getDefaultsFileStream());
        } catch (final IOException | NullPointerException e) {
            this.logger.error("Cannot load default configuration file.", e);
            throw new Exception("Cannot load default configuration file.");
        }

        this.logger.debug("Loading configuration from " + this.getFileName());
        this.props = new Properties(defaults);

        final File f = new File(this.getFileName());
        if (!f.exists()) {
            this.logger.warn("Configuration file not found. Using default values.");
        } else if (!f.canRead()) {
            this.logger.warn("Configuration file not readable. Using default values.");
        } else {
            try {
                final FileReader r = new FileReader(this.getFileName());
                this.props.load(r);
            } catch (final IOException e) {
                this.logger.error("Could not load the configuration file. Using default values.",
                        e);
            }
        }
    }

    /**
     * Gibt den Dateinamen zurück, in welcher die Konfiguration abgelegt wird.
     * 
     * @return
     */
    public abstract String getFileName();

    public abstract InputStream getDefaultsFileStream();

    /**
     * Gibt den Datenbankserver zurück
     * 
     * @return Den Datenbankserver
     */
    public String getDatabaseServer() {
        return this.props.getProperty("database.server");
    }

    /**
     * Gibt den Namen der Datenbank zurück
     * 
     * @return Den Namen der Datenbank
     */
    public String getDatabaseName() {
        return this.props.getProperty("database.name");
    }

    /**
     * Gibt den Datenbankbenutzer zurück
     * 
     * @return Den Datenbankbenutzer
     */
    public String getDatabaseUser() {
        return this.props.getProperty("database.user");
    }

    /**
     * Gibt das Passwort zur Datenbank zurück
     * 
     * @return Das Passwort zur Datenbank
     */
    public String getDatabasePassword() {
        return this.props.getProperty("database.password");
    }

    /**
     * Gibt an, ob die Datenbankverbindung verschlüsselt aufgebaut werden soll.
     *
     * @return True, wenn die Datenbankverbindung verschlüssel aufgebaut werden soll.
     */
    public Boolean getDatabaseUseSsl() {
        return Boolean.parseBoolean(this.props.getProperty("database.useSsl"));
    }

    /**
     * Der zu verwendende SMTP-Server
     * 
     * @return Der zu verwendende SMTP-Server
     */
    public String getSmtpServer() {
        return this.props.getProperty("smtp.server");
    }

    /**
     * Der Port, auf dem der SMTP-Server anzusprechen ist.
     * 
     * @return Der Port, auf dem der SMTP-Server anzusprechen ist.
     */
    public int getSmtpPort() {
        return Integer.parseInt(this.props.getProperty("smtp.port"));
    }

    /**
     * Der Benutzername zur Authentifizierung beim SMTP-Server.
     * 
     * @return Der Benutzername zur Authentifizierung beim SMTP-Server.
     */
    public String getSmtpUser() {
        return this.props.getProperty("smtp.user");
    }

    /**
     * Das Passwort zur Authentifizierung beim SMTP-Server.
     * 
     * @return Das Passwort zur Authentifizierung beim SMTP-Server.
     */
    public String getSmtpPassword() {
        return this.props.getProperty("smtp.password");
    }

    /**
     * Gibt an, ob SSL bei der Verbindung mit dem SMTP-Server verwendet werden
     * soll.
     * 
     * @return ob SSL bei der Verbindung mit dem SMTP-Server verwendet werden
     *         soll.
     */
    public boolean getSmtpUseSsl() {
        return Boolean.parseBoolean(this.props.getProperty("smtp.useSSL"));
    }

    /**
     * Die Absender-Adresse, die ins "Von"-Feld von Emails geschrieben werden
     * soll.
     * 
     * @return Die Absender-Adresse, die ins "Von"-Feld von Emails geschrieben
     *         werden soll.
     */
    public String getSmtpSenderAddress() {
        return this.props.getProperty("smtp.senderAddress");
    }
}
