package org.kabieror.elwasys.raspiclient.ui.medium.controller;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.kabieror.elwasys.common.FormatUtilities;
import org.kabieror.elwasys.raspiclient.model.ClientProgram;
import org.kabieror.elwasys.raspiclient.ui.ComponentControlInstance;
import org.kabieror.elwasys.raspiclient.ui.medium.IViewController;
import org.kabieror.elwasys.raspiclient.ui.medium.MainFormController;
import org.kabieror.elwasys.raspiclient.ui.medium.state.ToolbarState;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Ein Eintrag in der Liste aller verfügbarer Programme.
 *
 * @author Oliver Kabierschke
 */
public class ProgramListEntry implements IViewController, Initializable {

    private ConfirmationViewController controller;

    private ObjectProperty<ClientProgram> program = new SimpleObjectProperty<>();

    private StringProperty maxPrice = new SimpleStringProperty(FormatUtilities.formatCurrency(0d));

    @FXML
    private GridPane entryPane;

    @FXML
    private FlowPane detailBox;

    /**
     * Erzeugt eine neue Instanz eines DeviceListEntry.
     */
    static ComponentControlInstance<ProgramListEntry> createInstance() {
        try {
            FXMLLoader loader = new FXMLLoader(ProgramListEntry.class
                    .getResource("/org/kabieror/elwasys/raspiclient/ui/medium/components/ProgramListEntry.fxml"));
            return new ComponentControlInstance<>(loader.load(), loader.getController());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }

    @Override
    public void onStart(MainFormController mfc) {
        // Lade Programmdetails (Preis kommt bereits fertig vom Backend berechnet, siehe
        // ClientProgram#getPriceAtMaxDuration()).
        switch (program.get().getType()) {
            case DYNAMIC:
                // Höchstpreis
                this.maxPrice.set("max. " + FormatUtilities.formatCurrency(program.get().getPriceAtMaxDuration()));

                // Spezielle Daten
                this.detailBox.getChildren().add(createKeyValueControl("Grundgebühr",
                        FormatUtilities.formatCurrency(program.get().getFlagfall())));
                this.detailBox.getChildren().add(createKeyValueControl("Zeitpreis",
                        FormatUtilities.formatCurrency(program.get().getRate())));

                String unitStr;
                switch (program.get().getTimeUnit()) {
                    case DAYS:
                        unitStr = "T";
                        break;
                    case HOURS:
                        unitStr = "h";
                        break;
                    case MINUTES:
                        unitStr = "min";
                        break;
                    case SECONDS:
                        unitStr = "s";
                        break;
                    default:
                        unitStr = "?";
                        break;
                }

                this.detailBox.getChildren().add(createKeyValueControl("Zeiteinheit", "1 " + unitStr));
                break;
            case FIXED:
            default:
                // Genauer Preis
                this.maxPrice.set(FormatUtilities.formatCurrency(program.get().getPriceAtMaxDuration()));
                break;
        }

        // Maximaldauer
        this.detailBox.getChildren().add(createKeyValueControl("Maximaldauer",
                FormatUtilities.formatDuration(program.get().getMaxDuration(), true)));

    }

    @Override
    public void onTerminate() {

    }

    @Override
    public void onActivate() {

    }

    @Override
    public void onDeactivate() {

    }

    @Override
    public void onReturnFromError() {

    }

    @Override
    public ToolbarState getToolbarState() {
        return null;
    }

    public void setController(ConfirmationViewController controller) {
        this.controller = controller;
    }

    public ClientProgram getProgram() {
        return program.get();
    }

    public void setProgram(ClientProgram program) {
        this.program.set(program);
    }

    public ObjectProperty<ClientProgram> programProperty() {
        return program;
    }

    public String getMaxPrice() {
        return maxPrice.get();
    }

    public void setMaxPrice(String maxPrice) {
        this.maxPrice.set(maxPrice);
    }

    public StringProperty maxPriceProperty() {
        return maxPrice;
    }

    void select() {
        if (!this.entryPane.getStyleClass().contains("selected")) {
            this.entryPane.getStyleClass().add("selected");
        }
    }

    void unselect() {
        if (this.entryPane.getStyleClass().contains("selected")) {
            this.entryPane.getStyleClass().remove("selected");
        }
    }

    /**
     * Erzeugt ein Key-Value-Paar für die Detailanzeige in der Programme-Liste.
     *
     * @param key   Der anzuzeigende Schlüssel
     * @param value Der anzuzeigende Wert
     * @return Die erzeugte Key-Value-Komponente.
     */
    private HBox createKeyValueControl(String key, String value) {
        HBox container = new HBox();
        container.getStyleClass().add("key-value-pair");

        Label keyLbl = new Label();
        keyLbl.getStyleClass().add("key-label");
        keyLbl.setText(key);

        Label valueLbl = new Label();
        valueLbl.getStyleClass().add("value-label");
        valueLbl.setText(value);

        container.getChildren().addAll(keyLbl, valueLbl);
        return container;
    }

    public void onClicked(MouseEvent mouseEvent) {
        this.controller.selectProgram(this.program.get());
    }
}
