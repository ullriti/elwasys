package org.kabieror.elwasys.raspiclient.application;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Startpunkt der Anwendung. Kommandozeilen-Parameter: -f Startet die Anwendung
 * im Vollbildmodus.
 *
 * @author Oliver Kabierschke
 *
 */
public class Main extends Application {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Gibt die Art des Anzeigegeräts an, auf dem diese Instanz des Waschwächters läuft
     */
    public static ApplicationInterfaceType applicationInterfaceType;
    /**
     * Gibt an, ob die Anwendung auf einem RaspberryPi ausgeführt wird.
     */
    public static boolean adafruitDisplay = false;
    /**
     * Gibt an, ob der PowerManager ohne Funktion bleiben soll.
     */
    public static boolean dry = false;
    /**
     * Gibt an, ob die Anwendung im Vollbildmodus starten soll.
     */
    private static boolean fullscreen = false;

    /**
     * Einstiegspunkt des Java-Programms
     *
     * @param args
     *            Befehlszeilenparameter
     */
    public static void main(String[] args) {
        for (final String a : args) {
            if (a.equals("-f")) {
                fullscreen = true;
            } else if (a.equals("-adafruitDisplay")) {
                adafruitDisplay = true;
            } else if (a.equals("-dry")) {
                dry = true;
            } else if (a.equals("-xsDisplay")) {
                applicationInterfaceType = ApplicationInterfaceType.TOUCH_SMALL;
            } else if (a.equals("-mdDisplay")) {
                applicationInterfaceType = ApplicationInterfaceType.TOUCH_MEDIUM;
            }
        }

        // Vor dem Start der Oberfläche: unbehandelte Fehler aus JEDEM Thread gehören ins
        // Anwendungs-Log (siehe logUncaughtException).
        Thread.setDefaultUncaughtExceptionHandler(Main::logUncaughtException);

        launch(args);

    }

    /**
     * Schreibt einen unbehandelten Fehler ins Anwendungs-Log.
     * <p>
     * Vorher liefen solche Fehler nur über den Standard-Handler der JVM nach
     * {@code System.err} - und damit in die Datei {@code log/errout}, die die Fernwartung
     * (LOG_REQUEST) NICHT ausliefert. Genau deshalb blieb der Absturz des Layout-Durchlaufs
     * am 2026-09-24 drei Tage unentdeckt, obwohl die Bestätigungsseite des Terminals
     * seitdem eingefroren war (Ursache und Wirkung in {@code UiUtilities#setLabelText}).
     * Ein Fehler im JavaFX-Thread beendet die Anwendung nicht, kann die Oberfläche aber
     * teilweise unbrauchbar machen - er muss deshalb auffindbar sein, ohne sich auf die
     * Konsole des Geräts zu verlassen.
     *
     * @param thread Der Thread, in dem der Fehler auftrat.
     * @param error  Der unbehandelte Fehler.
     */
    private static void logUncaughtException(Thread thread, Throwable error) {
        logger.error("Unbehandelter Fehler im Thread '{}'. Die Oberfläche kann dadurch teilweise "
                + "stehen bleiben - Terminal im Zweifel neu starten.", thread.getName(), error);
    }

    /**
     * Startpunkt der Anwendung. Wird von JavaFX aufgerufen, sobald das
     * Hauptfenster erzeugt werden soll.
     */
    @Override
    public void start(Stage primaryStage) {
        // Der JavaFX-Thread wird nicht von uns erzeugt; hier laufen wir zum ersten Mal auf ihm
        // und können ihm den Handler mitgeben. Der Default-Handler aus main() würde zwar auch
        // greifen, aber nur solange ihn niemand (JavaFX, eine Bibliothek) thread-eigen
        // überschreibt - für den einen Thread, an dem die gesamte Oberfläche hängt, ist die
        // ausdrückliche Zuweisung die Mühe wert.
        Thread.currentThread().setUncaughtExceptionHandler(Main::logUncaughtException);

        // Erkenne notwenige Größe der Anwendung anhand der Größe der Anzeige
        if (applicationInterfaceType == null) {
            if (Screen.getPrimary().getBounds().getWidth() < 500) {
                applicationInterfaceType = ApplicationInterfaceType.TOUCH_SMALL;
            } else {
                applicationInterfaceType = ApplicationInterfaceType.TOUCH_MEDIUM;
            }
        }

        // Icons laden
        Font.loadFont(
                this.getClass().getResourceAsStream(
                        "/org/kabieror/elwasys/raspiclient/resources/fonts/fontawesome-webfont.ttf"),
                12);

        // Manager über Start benachrichtigen
        ElwaManager.instance.onPrimaryStageStart(primaryStage);

        try {
            // Fenster erzeugen.
            final Scene scene;
            if (applicationInterfaceType == ApplicationInterfaceType.TOUCH_SMALL) {
                Parent root = FXMLLoader.load(this.getClass()
                        .getResource("/org/kabieror/elwasys/raspiclient/ui/small/MainForm.fxml"));
                scene = new Scene(root, 320, 240);
            } else {
                Parent root = FXMLLoader.load(this.getClass()
                        .getResource("/org/kabieror/elwasys/raspiclient/ui/medium/MainForm.fxml"));
                scene = new Scene(root, 800, 480);
            }
            primaryStage.setTitle(ElwaManager.APP_NAME);
            primaryStage.setScene(scene);
            primaryStage.setOnCloseRequest(ElwaManager.instance::onCloseRequest);

            if (fullscreen) {
                primaryStage.setFullScreen(true);
            }

            primaryStage.show();
        } catch (final Exception e) {
            // Ueber den Logger statt printStackTrace: nur so landet der Startfehler in der per
            // Fernwartung (LOG_REQUEST) abrufbaren Logdatei (#91).
            logger.error("Failed to start up the main window.", e);
        }
    }
}
