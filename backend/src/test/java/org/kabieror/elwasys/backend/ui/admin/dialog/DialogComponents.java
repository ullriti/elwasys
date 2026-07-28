package org.kabieror.elwasys.backend.ui.admin.dialog;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Element;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Sucht Bedienelemente eines Dialogs - der Weg, auf dem die Dialog-Komponententests (ohne
 * Browser) an ihre Knöpfe und Felder kommen. Bewusst über den Aufbau des Dialogs statt über
 * paketsichtbar geöffnete Felder: so prüfen die Tests denselben Baum, den auch der Browser zu
 * sehen bekommt, und die Dialoge brauchen keine nur für den Test vorhandene Naht.
 *
 * <p>Gesucht wird über den ELEMENT-Baum, nicht über {@code Component#getChildren}: Kopf- und
 * Fußzeile eines Vaadin-Dialogs hängen nicht unter dessen Komponenten. {@code Dialog#getFooter}
 * baut einen eigenen Element-Baum und hängt ihn als VIRTUELLES Kind ein - die Fußzeilen-Knöpfe
 * ("Abbrechen"/Primäraktion) sind über den Komponentenbaum deshalb nicht erreichbar.
 */
final class DialogComponents {

    private DialogComponents() {
        // Test-Hilfsklasse
    }

    /** Die Schaltfläche mit dieser Beschriftung - auch in Kopf- und Fußzeile. */
    static Button button(Dialog dialog, String caption) {
        return find(dialog, Button.class, btn -> caption.equals(btn.getText()))
                .orElseThrow(() -> new AssertionError("Schaltfläche \"" + caption + "\" nicht gefunden"));
    }

    /** Das Textfeld mit diesem Feldlabel. */
    static TextField textField(Dialog dialog, String label) {
        return find(dialog, TextField.class, field -> label.equals(field.getLabel()))
                .orElseThrow(() -> new AssertionError("Textfeld \"" + label + "\" nicht gefunden"));
    }

    private static <T extends Component> Optional<T> find(Dialog dialog, Class<T> type, Predicate<T> matches) {
        return Stream.of(dialog.getElement(), dialog.getHeader().getElement(), dialog.getFooter().getElement())
                .map(root -> find(root, type, matches)).flatMap(Optional::stream).findFirst();
    }

    private static <T extends Component> Optional<T> find(Element root, Class<T> type, Predicate<T> matches) {
        Optional<T> here = root.getComponent().filter(type::isInstance).map(type::cast).filter(matches);
        if (here.isPresent()) {
            return here;
        }
        return root.getChildren().map(child -> find(child, type, matches)).flatMap(Optional::stream).findFirst();
    }
}
