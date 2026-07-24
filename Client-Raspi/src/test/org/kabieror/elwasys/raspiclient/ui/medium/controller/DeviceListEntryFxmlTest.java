package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.raspiclient.api.dto.DeviceOverviewDto;
import org.kabieror.elwasys.raspiclient.model.ClientDevice;
import org.testfx.framework.junit5.ApplicationTest;

/**
 * Regressionstest für Issue #82 (H3): der {@code switch} in {@code DeviceListEntry#refresh}
 * hatte beim {@code DISABLED}-Zweig kein {@code break} und fiel in {@code UNREGISTERED} durch -
 * die "deaktiviert"-Darstellung (Style, Text, gesperrte Kachel) wurde dadurch sofort wieder von
 * der "Keine Steckdose"-Darstellung überschrieben. Der Test lädt die reale FXML-Komponente
 * (wie {@code ProgramListEntryFxmlTest}) und ruft {@code refresh()} direkt auf - ohne
 * {@code onStart()}, damit keine {@code ElwaManager}-Singleton-Initialisierung nötig ist
 * (siehe docs/kb/06-ui-tests.md).
 */
public class DeviceListEntryFxmlTest extends ApplicationTest {

    private static final String FXML =
            "/org/kabieror/elwasys/raspiclient/ui/medium/components/DeviceListEntry.fxml";

    private Parent root;
    private DeviceListEntry controller;

    @Override
    public void start(Stage stage) throws Exception {
        final FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root, 300, 300));
        stage.show();
    }

    @Test
    void refreshOnADisabledDeviceRendersTheDisabledStateNotUnregistered() {
        // Gerät ist beim Backend als deaktiviert markiert (kein DeviceRegistrationService
        // gesetzt, da onStart() bewusst nicht aufgerufen wird - refresh() muss trotzdem robust
        // in den DISABLED-Zustand wechseln, siehe ClientDevice#isEnabled()).
        ClientDevice device = new ClientDevice(1);
        device.updateFrom(new DeviceOverviewDto(1, "Waschmaschine 1", 0, false, false, null, null, null, null, null,
                null, null, 0f, 0, java.util.List.of()));
        controller.setDevice(device);

        // refresh() setzt JavaFX-Properties (StringProperty#set) - das muss auf dem FX
        // Application Thread laufen, sonst wirft JavaFX eine IllegalStateException.
        interact(controller::refresh);

        assertTrue(root.getStyleClass().contains("status-disabled"),
                "the disabled style class must be set");
        assertFalse(root.getStyleClass().contains("status-unregistered"),
                "the unregistered style class must NOT fall through onto the disabled tile");
        assertEquals("deaktiviert", controller.getStatusText(),
                "the disabled status text must not be overwritten by the unregistered text");
        assertTrue(root.isDisable(), "a disabled device tile must stay non-interactive");
    }
}
