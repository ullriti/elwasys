package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * Gemeinsame Basis für die Platzhalter-Views des Phase-3-Grundgerüsts (AP1, siehe
 * docs/kb/05-migration-plan.md): zeigt nur Titel + Hinweistext, bis die jeweilige View in einem
 * späteren Arbeitspaket (AP2/AP3) mit echten Inhalten (CRUD-Tabellen etc.) gefüllt wird.
 * Beweist bereits jetzt Routing, Layout-Einbettung und Rollen-Zugriffsschutz je Sicht.
 */
public class PlaceholderView extends VerticalLayout {

    protected PlaceholderView(String title, String hint) {
        addClassName("placeholder-view");
        add(new H2(title), new Paragraph(hint));
    }
}
