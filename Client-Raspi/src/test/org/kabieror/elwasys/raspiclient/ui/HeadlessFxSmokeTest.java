package org.kabieror.elwasys.raspiclient.ui;

import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.control.LabeledMatchers.hasText;

/**
 * Smoke test proving the headless JavaFX/TestFX pipeline works in the
 * (remote) build environment. Deliberately touches NO elwasys classes so it
 * cannot be broken by application-level dependencies (e.g. the ElwaManager
 * singleton, which requires a configuration file and DB access).
 *
 * Run headless under Xvfb, e.g.:
 *   xvfb-run -a mvn -f Client-Raspi/pom.xml test -Dtest=HeadlessFxSmokeTest
 *
 * See docs/kb/06-ui-tests.md for the overall UI-test strategy.
 */
public class HeadlessFxSmokeTest extends ApplicationTest {

    @Override
    public void start(Stage stage) {
        final Label label = new Label("elwasys-ui-test-ok");
        label.setId("smoke-label");
        stage.setScene(new Scene(new StackPane(label), 200, 100));
        stage.show();
    }

    @Test
    void scene_renders_in_headless_environment() {
        verifyThat("#smoke-label", hasText("elwasys-ui-test-ok"));
    }
}
