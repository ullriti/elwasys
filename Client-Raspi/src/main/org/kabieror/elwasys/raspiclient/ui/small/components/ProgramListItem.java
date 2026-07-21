package org.kabieror.elwasys.raspiclient.ui.small.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.GridPane;

import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.ui.Icons;

import java.text.NumberFormat;

/**
 * Diese Klasse stellt einen Eintrag in der Liste der verfügbaren Programme
 * eines Geräts dar.
 * 
 * @author Oliver Kabierschke
 *
 */
public class ProgramListItem extends ListCell<ClientProgram> {
    private final Label icon;
    private final Label caption;
    private final Label price;
    private final Label user;
    private GridPane grid;

    public ProgramListItem() {
        this.getStyleClass().add("device-list-item");

        this.icon = new Label();
        this.icon.getStyleClass().add("icon");

        this.caption = new Label();
        this.caption.getStyleClass().add("caption");

        this.price = new Label();
        this.price.getStyleClass().add("price");

        this.user = new Label();
        this.user.getStyleClass().add("user");
    }

    /**
     * Updates the available fields in the grid layout.
     */
    private void generateLayout() {
        this.grid = new GridPane();

        this.grid.setHgap(10);
        this.grid.setVgap(4);
        this.grid.setPadding(new Insets(0, 10, 0, 10));

        this.grid.add(this.icon, 0, 0, 1, 2);
        this.grid.add(this.caption, 1, 0);
        this.grid.add(this.price, 1, 1);
    }

    /**
     * Updates the contents of the fields.
     */
    private void updateContents(ClientProgram program) {
        this.icon.setText(Icons.ICON_SLIDERS);
        this.caption.setText(program.getName());
        this.price.setText(NumberFormat.getCurrencyInstance().format(program.getPriceAtMaxDuration()));
    }

    /**
     * Löscht alle Inhalte von Textfeldern.
     */
    private void clearContents() {
        this.icon.setText(null);
        this.caption.setText(null);
        this.price.setText(null);
    }

    @Override
    protected void updateItem(ClientProgram program, boolean empty) {
        super.updateItem(program, empty);
        this.generateLayout();
        this.setGraphic(this.grid);
        if (!empty) {
            this.updateContents(program);
        } else {
            this.clearContents();
        }
    }
}
