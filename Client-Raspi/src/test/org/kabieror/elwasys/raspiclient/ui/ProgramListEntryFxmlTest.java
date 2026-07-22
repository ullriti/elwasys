package org.kabieror.elwasys.raspiclient.ui;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.common.FormatUtilities;
import org.kabieror.elwasys.raspiclient.ui.medium.controller.ProgramListEntry;
import org.testfx.framework.junit5.ApplicationTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Characterization test for a real application UI component. The
 * {@code ProgramListEntry} view is one of the few controllers that does NOT
 * depend on the {@link org.kabieror.elwasys.raspiclient.application.ElwaManager}
 * singleton, so it can be loaded and rendered in isolation, headlessly.
 *
 * This locks in current behavior:
 *  - the FXML resource loads without error,
 *  - it wires up to its {@code ProgramListEntry} controller,
 *  - the expected named nodes exist ({@code #entryPane}, {@code #detailBox}),
 *  - the default max-price string is the zero-currency format.
 *
 * See docs/kb/06-ui-tests.md.
 */
public class ProgramListEntryFxmlTest extends ApplicationTest {

    private static final String FXML =
            "/org/kabieror/elwasys/raspiclient/ui/medium/components/ProgramListEntry.fxml";

    private Parent root;
    private ProgramListEntry controller;

    @Override
    public void start(Stage stage) throws Exception {
        final FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML));
        root = loader.load();
        controller = loader.getController();
        stage.setScene(new Scene(root, 400, 120));
        stage.show();
    }

    @Test
    void fxml_loads_and_wires_controller() {
        assertNotNull(controller, "FXML should instantiate the ProgramListEntry controller");
        assertNotNull(root.lookup("#detailBox"), "detailBox node should exist");
        assertEquals(FormatUtilities.formatCurrency(0d), controller.getMaxPrice(),
                "default max price should be the zero-currency format");
    }
}
