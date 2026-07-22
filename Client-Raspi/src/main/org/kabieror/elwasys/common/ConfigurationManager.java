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

    // Die frueheren getSmtp*-Getter wurden entfernt (Issue #61): seit Phase 4 AP4 versendet der
    // Terminal-Client keine Mails mehr (Benachrichtigungen laufen zentral im Backend), die
    // SMTP-Konfiguration war damit toter Code.
}
