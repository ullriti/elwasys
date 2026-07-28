package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.grid.Grid;
import java.util.List;

/**
 * Das Befüllen einer Portal-Tabelle - an einer Stelle, damit die Begründung für die
 * Neuvermessung nicht in jeder Ansicht als eigene Kopie steht (sie stand zuletzt dreimal
 * wortgleich da: {@code AbstractAdminListView}, {@code ExpiredExecutionsDialog},
 * {@code TerminalTokenDialog}).
 */
public final class PortalGrids {

    private PortalGrids() {
        // Utility-Klasse
    }

    /**
     * Setzt die Zeilen und misst die Spaltenbreiten neu.
     *
     * <p>{@code setAutoWidth} misst den Zellinhalt, sobald er gerendert ist - die Zeilen kommen
     * aber erst mit diesem Aufruf. Ohne die Neuvermessung behielte insbesondere die
     * Aktionsspalte die Breite, die sie beim leeren Grid hatte.
     */
    public static <T> void setItems(Grid<T> grid, List<T> items) {
        grid.setItems(items);
        grid.recalculateColumnWidths();
    }
}
