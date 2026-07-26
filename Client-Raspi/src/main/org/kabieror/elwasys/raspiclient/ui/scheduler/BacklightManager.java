package org.kabieror.elwasys.raspiclient.ui.scheduler;

import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinPwmOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.RaspiPin;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.application.ICloseListener;
import org.kabieror.elwasys.raspiclient.application.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Diese Klasse sorgt dafür, dass die Hintergrundbeleuchtung des Displays nach
 * einem Timeout ausgeschaltet wird
 *
 * @author Oliver Kabierschke
 */
public class BacklightManager implements ICloseListener {

    private static final Pin gpioPin = RaspiPin.GPIO_01;
    /**
     * The listeners to inform when the light is switched on.
     */
    private final List<LightOnEventListener> lightOnEventListeners = new CopyOnWriteArrayList<>();
    /**
     * Zeit bis zum Abdunkeln des Displays
     */
    private Duration displayTimeout = Duration.ofSeconds(60);
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Gibt an, ob die Anwendung im Begriff ist, sich zu beenden
     */
    private boolean shutdown = false;

    /**
     * Die Datei, mit der die Hintergrundbeleuchtung gesteuert werden kann
     */
    private File displayPowerFile;

    /**
     * Gibt an, ob die Displaybeleuchtung an ist
     */
    private boolean lightIsOn = true;
    private GpioPinPwmOutput gpioOut;
    /**
     * Thread, der das Licht des Displays ausschaltet
     */
    private Thread switchOffLightThread = new Thread(() -> {
        final Logger logger = LoggerFactory.getLogger(this.getClass());
        while (!this.shutdown) {
            try {
                Thread.sleep(BacklightManager.this.displayTimeout.getSeconds() * 1000);
                if (this.lightIsOn) {
                    BacklightManager.this.lightOff();
                }
            } catch (final InterruptedException ignored) {
            }
        }
    });

    /**
     * Konstruktor
     */
    public BacklightManager() {
        this.displayTimeout = ElwaManager.instance.getConfigurationManager().getDisplayTimeout();

        if (this.displayTimeout.isNegative()) {
            // Kein Display-Timeout.
            this.logger.info("Starting without display timeout.");
            return;
        }

        // Öffne GPIO-Port
        boolean success = false;
        if (Main.adafruitDisplay) {
            this.gpioOut = GpioFactory.getInstance().provisionPwmOutputPin(gpioPin);
            this.gpioOut.setPwm(-1);
            success = true;
        } else {
            File f = new File("/sys/devices/platform/rpi_backlight/backlight/rpi_backlight/bl_power");
            if (f.exists() && f.canWrite()) {
                this.displayPowerFile = f;
                success = true;
            } else {
                logger.warn("Display power file not found or not writable. Starting without Backlight control.");
                this.displayPowerFile = null;
            }
        }

        if (success) {
            // Maus-Listener installieren
            ElwaManager.instance.getPrimaryStage().addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                if (this.wakeUp()) {
                    // Wenn das Display aus war, soll die Interaktion keine
                    // Auswirkungen haben
                    e.consume();
                }
            });
            ElwaManager.instance.getPrimaryStage().addEventFilter(KeyEvent.ANY, e -> {
                if (this.wakeUp()) {
                    // Wenn das Display aus war, soll die Interaktion keine
                    // Auswirkungen haben
                    e.consume();
                }
            });

            this.lightOn();
            this.switchOffLightThread.start();
            ElwaManager.instance.listenToCloseEvent(this);
        }
    }

    /**
     * Adds a listener to the list of light on event listeners.
     *
     * @param l The listener to add to the list of objects to inform.
     */
    public void listenToLightOnEvent(LightOnEventListener l) {
        this.lightOnEventListeners.add(l);
    }

    /**
     * Entfernt einen Listener von der Liste des Licht-an-Listener
     *
     * @param l Der Listener, der nicht mehr benachrichtigt werden soll
     */
    public void stopListeningToLightOnEvent(LightOnEventListener l) {
        this.lightOnEventListeners.remove(l);
    }

    /**
     * Setzt die Zeit bis zum Abdunkeln zurück
     *
     * @return Ob das Display wieder angeschaltet wurde
     */
    private boolean wakeUp() {
        this.switchOffLightThread.interrupt();
        if (this.lightIsOn) {
            return false;
        } else {
            this.lightOn();
            return true;
        }
    }

    /**
     * Schaltet das Licht des Displays an
     */
    private void lightOn() {
        this.logger.debug("Display: Light on");
        if (Main.adafruitDisplay) {
            this.gpioOut.setPwm(-1);
        } else if (this.displayPowerFile != null) {
            writePowerFile("0");
        }
        this.lightIsOn = true;
        for (final LightOnEventListener l : this.lightOnEventListeners) {
            l.onLightOn();
        }
    }

    /**
     * Schaltet das Licht des Displays aus
     */
    private void lightOff() {
        this.logger.debug("Display: Light off");
        if (Main.adafruitDisplay) {
            this.gpioOut.setPwm(1);
        } else if (this.displayPowerFile != null) {
            writePowerFile("1");
        }
        this.lightIsOn = false;
    }

    /**
     * Beschreibt die Datei zur Steuerung der Hingergrundbeleuchtung
     *
     * @param content Der zu schreibende Inhalt
     */
    private void writePowerFile(String content) {
        try {
            Writer writer =
                    new BufferedWriter(new OutputStreamWriter(new FileOutputStream(this.displayPowerFile), "utf-8"));
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            logger.error("Cannot set display power.", e);
            return;
        }
    }

    @Override
    public void onClose(boolean restart) {
        this.logger.debug("Shutting down backlight manager");
        this.shutdown = true;
        this.switchOffLightThread.interrupt();
        if (Main.adafruitDisplay) {
            this.gpioOut.setPwm(-1);
            this.gpioOut.unexport();
        }
    }

    /**
     * Bestimmt, ob die Hintergrundbeleuchtung derzeitig aktiviert ist.
     *
     * @return Wahr, wenn die Beleuchtung aktiviert ist, sonst falsch.
     */
    public boolean isLightOn() {
        return this.lightIsOn;
    }


    public interface LightOnEventListener {
        void onLightOn();
    }

}
