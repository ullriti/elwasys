package org.kabieror.elwasys.raspiclient.ui.small;

import javafx.application.Platform;

import org.kabieror.elwasys.raspiclient.model.ClientExecution;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.kabieror.elwasys.raspiclient.ui.IMainFormStateManager;
import org.kabieror.elwasys.raspiclient.ui.MainFormState;
import org.kabieror.elwasys.raspiclient.ui.MainFormStateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Diese Klasse verwaltet den Zustandsautomaten des Hauptfensters
 *
 * @author Oliver Kabierschke
 *
 */
class MainFormStateManager implements IMainFormStateManager {
    private static final String TEXT_CREDIT_INSUFFICENT = "Guthaben reicht nicht aus!";
    private static final String TEXT_USER_BLOCKED = "Diese Karte ist gesperrt!";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    /**
     * Der Controller des Hauptfensters
     */
    private final MainFormController controller;

    /**
     * Erlaubte Zustandsübergänge
     */
    private final Map<MainFormStateTransition, Runnable> stateTransitions =
            new HashMap<MainFormStateTransition, Runnable>();
    /**
     * Aktueller Zustand
     */
    private MainFormState state;
    /**
     * Der Zustand des Haupformulars vorm Fehlerzustand
     */
    private MainFormState stateBeforeError;
    /**
     * Der Aktualisierungvorgang der Info-Seite
     */
    private ScheduledFuture<?> infoPaneUpdateSchedule;

    /**
     *
     * @param controller
     */
    public MainFormStateManager(MainFormController controller) {
        this.controller = controller;

        // Zustandsübergänge hinzufügen
        this.startupTransitions();
        this.deviceSelectionTransitions();
        this.infoPageTransitions();
        this.programSelectionTransitions();
        this.confirmationTransitions();

        this.state = MainFormState.STARTUP;
    }

    /**
     * Gibt den aktuellen Zustand des Hauptformulars zurück
     *
     * @return Den aktuellen Zustand des Hauptformulars
     */
    public MainFormState getState() {
        return this.state;
    }

    /**
     * Fügt Zustandsübergänge für den Systemstart hinzu
     */
    private void startupTransitions() {
        this.stateTransitions.put(
                new MainFormStateTransition(MainFormState.STARTUP, MainFormState.SELECT_DEVICE),
                () -> {
                    this.controller.startupPane.setVisible(false);
                    this.controller.devicePane.setVisible(true);
                });
    }

    /**
     * Fügt Zustandsübergänge für die Geräteauswahl hinzu
     */
    private void deviceSelectionTransitions() {
        // Geräteauswahl --> Programmauswahl
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.SELECT_DEVICE,
                MainFormState.SELECT_PROGRAM), () -> {
                    this.controller.program_labelDevice
                            .setText(this.controller.selectedDevice.getName());

                    // Liste mit Programmen befüllen
                    this.controller.programListData.clear();
                    this.controller.programListData
                            .addAll(this.controller.selectedDevice.getPrograms());

                    this.controller.program_buttonForward.disableProperty().set(true);

                    this.controller.devicePane.setVisible(false);
                    this.controller.programPane.setVisible(true);
                });
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.SELECT_DEVICE,
                MainFormState.CONFIRMATION_WAIT_FOR_CARD), () -> {
                    this.initiateConfirmationPage();

                    this.controller.devicePane.setVisible(false);
                    this.controller.confirmationPane.setVisible(true);
                });
        this.stateTransitions.put(
                new MainFormStateTransition(MainFormState.SELECT_DEVICE, MainFormState.DEVICE_INFO),
                () -> {
                    final Runnable refresh = () -> {
                        this.logger.trace("Refreshing info pane");
                        this.controller.info_labelDevice
                                .setText(this.controller.selectedDevice.getName());
                        final ClientExecution currentExecution = ElwaManager.instance.getExecutionManager()
                                        .getRunningExecution(this.controller.selectedDevice);
                        if (currentExecution == null) {
                            this.controller.info_labelEndTime.setText("Gerät ist frei");
                            this.controller.info_labelRemaining.setText("");
                            this.controller.info_labelUser.setText("");
                            return;
                        }
                        this.controller.info_labelUser
                                .setText(currentExecution.getUser().getName());

                        // Berechne verbleibende Zeit in Stunden,
                        // Minuten und
                        // Sekunden
                        final Duration remTime = currentExecution.getRemainingTime();
                        final long hours = remTime.toHours();
                        final long minutes = remTime.toMinutes() % 60;
                        final long seconds = remTime.getSeconds() % 60;

                        final NumberFormat format = NumberFormat.getIntegerInstance();
                        format.setMinimumIntegerDigits(2);

                        String remainingTime = "Verbleibend: ";
                        if (hours > 0) {
                            remainingTime = format.format(hours) + ":" + format.format(minutes)
                                    + ":" + format.format(seconds) + " h";
                        } else if (minutes > 0) {
                            remainingTime =
                                    format.format(minutes) + ":" + format.format(seconds) + " min";
                        } else {
                            remainingTime = format.format(seconds) + " s";
                        }
                        this.controller.info_labelRemaining.setText(remainingTime);

                        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
                        this.controller.info_labelEndTime
                                .setText(formatter.format(currentExecution.getEndDate()) + " Uhr");
                    };

                    // Aktualisierung starten
                    this.infoPaneUpdateSchedule = this.controller.updateService.scheduleAtFixedRate(
                            () -> Platform.runLater(refresh), 0, 1, TimeUnit.SECONDS);

                    this.controller.devicePane.setVisible(false);
                    this.controller.infoPane.setVisible(true);
                });
    }

    /**
     * Fügt Zustandsübergänge für die Info-Seite hinzu
     */
    private void infoPageTransitions() {
        this.stateTransitions.put(
                new MainFormStateTransition(MainFormState.DEVICE_INFO, MainFormState.SELECT_DEVICE),
                () -> {
                    // Aktualisierung beenden
                    this.infoPaneUpdateSchedule.cancel(false);

                    this.controller.infoPane.setVisible(false);
                    this.controller.devicePane.setVisible(true);
                });
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.DEVICE_INFO,
                MainFormState.CONFIRM_PROGRAM_ABORTION), () -> {
                    this.controller.infoPane.setVisible(false);
                    this.controller.confirmAbortionPane.setVisible(true);
                });
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRM_PROGRAM_ABORTION, MainFormState.DEVICE_INFO), () -> {
                    this.controller.confirmAbortionPane.setVisible(false);
                    this.controller.infoPane.setVisible(true);
                });
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRM_PROGRAM_ABORTION, MainFormState.SELECT_DEVICE), () -> {
                    // Aktualisierung beenden
                    this.infoPaneUpdateSchedule.cancel(false);

                    this.controller.confirmAbortionPane.setVisible(false);
                    this.controller.devicePane.setVisible(true);
                });
    }

    /**
     * Fügt Zustandsübergänge für die Programmauswahl hinzu
     */
    private void programSelectionTransitions() {
        // Programmauswahl abbrechen
        final Runnable abortProgramSelection = () -> {
            this.controller.selectedDevice = null;

            this.controller.programPane.setVisible(false);
            this.controller.devicePane.setVisible(true);
        };

        // Geräteauswahl <-- Programmauswahl
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.SELECT_PROGRAM,
                MainFormState.SELECT_DEVICE), abortProgramSelection);

        // Programmauswahl --> Programm ausgewählt
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.SELECT_PROGRAM,
                MainFormState.PROGRAM_SELECTED), () -> {
                    this.controller.program_buttonForward.disableProperty().set(false);
                });

        // Geräteauswahl <-- Programm ausgewählt
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.PROGRAM_SELECTED,
                MainFormState.SELECT_DEVICE), abortProgramSelection);

        // Programm ausgewählt --> Warte auf Karte
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.PROGRAM_SELECTED,
                MainFormState.CONFIRMATION_WAIT_FOR_CARD), () -> {
                    this.initiateConfirmationPage();

                    this.controller.programPane.setVisible(false);
                    this.controller.confirmationPane.setVisible(true);
                });
    }

    /**
     * Fügt Zustandsübergänge für die Bestätigungsseite hinzu
     */
    private void confirmationTransitions() {
        // Bestätigung abbrechen
        final Runnable abortConfirmation = () -> {
            this.controller.confirmationPane.setVisible(false);
            this.controller.programPane.setVisible(true);
        };
        // Bestätigung abbrechen
        final Runnable backToDeviceSelection = () -> {
            this.controller.confirmationPane.setVisible(false);
            this.controller.devicePane.setVisible(true);
        };

        // Benutzer registriert
        final Runnable userRegistered = () -> {
            this.controller.confirmation_username.setText(this.controller.registeredUser.getName());
            if (this.controller.confirmation_username.getStyleClass().contains("username-error")) {
                this.controller.confirmation_username.getStyleClass().remove("username-error");
            }
            if (this.controller.confirmation_userIcon.getStyleClass()
                    .contains("no-user-registered")) {
                this.controller.confirmation_userIcon.getStyleClass().remove("no-user-registered");
            }

            this.controller.confirmation_credit.setText(NumberFormat.getCurrencyInstance()
                    .format(this.controller.registeredUser.getCredit()));

            this.controller.confirmation_cost.setText(NumberFormat.getCurrencyInstance()
                    .format(this.controller.selectedProgram.getPriceAtMaxDuration()));

            this.controller.confirmation_remainingCredit.setText(NumberFormat.getCurrencyInstance()
                    .format((this.controller.registeredUser.getCredit()
                            .subtract(this.controller.selectedProgram.getPriceAtMaxDuration()))));

            this.controller.confirmation_credit.setVisible(true);
            this.controller.confirmation_remainingCredit.setVisible(true);
        };

        final Runnable cardUnknown = () -> {
            this.controller.confirmation_username.setText("Karte unbekannt");
            if (!this.controller.confirmation_username.getStyleClass().contains("username-error")) {
                this.controller.confirmation_username.getStyleClass().add("username-error");
            }
            if (!this.controller.confirmation_userIcon.getStyleClass()
                    .contains("no-user-registered")) {
                this.controller.confirmation_userIcon.getStyleClass().add("no-user-registered");
            }
            this.controller.confirmation_credit
                    .setText(NumberFormat.getCurrencyInstance().format(0));
            this.controller.confirmation_cost.setText(
                    NumberFormat.getCurrencyInstance().format(this.controller.selectedProgram.getPriceAtMaxDuration()));
            this.controller.confirmation_remainingCredit.setText(
                    NumberFormat.getCurrencyInstance().format(this.controller.selectedProgram.getPriceAtMaxDuration().negate()));
            this.controller.confirmation_remainingCredit.getStyleClass().clear();

            this.controller.confirmation_credit.setVisible(false);
            this.controller.confirmation_remainingCredit.setVisible(false);

        };

        final Runnable creditSufficent = () -> {
            if (this.controller.confirmation_remainingCredit.getStyleClass()
                    .contains("remaining-error")) {
                this.controller.confirmation_remainingCredit.getStyleClass()
                        .remove("remaining-error");
            }
            this.controller.confirmation_errorMessage.setVisible(false);
        };

        final Runnable creditInsufficent = () -> {
            if (!this.controller.confirmation_remainingCredit.getStyleClass()
                    .contains("remaining-error")) {
                this.controller.confirmation_remainingCredit.getStyleClass().add("remaining-error");
            }
            this.controller.confirmation_errorMessage.setText(TEXT_CREDIT_INSUFFICENT);
            this.controller.confirmation_errorMessage.setVisible(true);
        };

        final Runnable userBlocked = () -> {
            this.controller.confirmation_errorMessage.setText(TEXT_USER_BLOCKED);
            this.controller.confirmation_errorMessage.setVisible(true);
        };

        final Runnable userNotBlocked = () -> {
            this.controller.confirmation_errorMessage.setVisible(false);
        };

        // Geräteauswahl <-- Warte auf Karte
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_WAIT_FOR_CARD,
                        MainFormState.SELECT_DEVICE), backToDeviceSelection);
        // Geräteauswahl <-- Karte nicht erkannt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CARD_UNKNOWN,
                        MainFormState.SELECT_DEVICE), backToDeviceSelection);
        // Geräteauswahl <-- Guthaben nicht ausreichend
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT,
                        MainFormState.SELECT_DEVICE), backToDeviceSelection);
        // Geräteauswahl <-- Benutzer gesperrt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_USER_BLOCKED,
                        MainFormState.SELECT_DEVICE), backToDeviceSelection);
        // Geräteauswahl <-- Bereit zum Programmstart
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.CONFIRMATION_READY,
                MainFormState.SELECT_DEVICE), backToDeviceSelection);

        // Programmauswahl <-- Warte auf Karte
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_WAIT_FOR_CARD,
                        MainFormState.PROGRAM_SELECTED), abortConfirmation);
        // Programmauswahl <-- Karte nicht erkannt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CARD_UNKNOWN,
                        MainFormState.PROGRAM_SELECTED), abortConfirmation);
        // Programmauswahl <-- Guthaben nicht ausreichend
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT,
                        MainFormState.PROGRAM_SELECTED), abortConfirmation);
        // Programmauswahl <-- Benutzer gesperrt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_USER_BLOCKED,
                        MainFormState.PROGRAM_SELECTED), abortConfirmation);
        // Programmauswahl <-- Bereit zum Programmstart
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.CONFIRMATION_READY,
                MainFormState.PROGRAM_SELECTED), abortConfirmation);

        // Warte auf Karte --> Bereit zum Programmstart
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_WAIT_FOR_CARD, MainFormState.CONFIRMATION_READY), () -> {
                    userRegistered.run();
                    this.controller.confirmation_buttonDoor.visibleProperty().set(false);
                    this.controller.confirmation_buttonStart.visibleProperty().set(true);
                });
        // Karte nicht erkannt --> Bereit zum Programmstart
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_CARD_UNKNOWN, MainFormState.CONFIRMATION_READY), () -> {
                    userRegistered.run();
                    this.controller.confirmation_buttonDoor.visibleProperty().set(false);
                    this.controller.confirmation_buttonStart.visibleProperty().set(true);
                });
        // Guthaben nicht ausreichend --> Bereit zum Programmstart
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT,
                        MainFormState.CONFIRMATION_READY), () -> {
                            creditSufficent.run();
                            userRegistered.run();
                            this.controller.confirmation_buttonDoor.visibleProperty().set(false);
                            this.controller.confirmation_buttonStart.visibleProperty().set(true);

                        });
        // Benutzer gesperrt --> Bereit zum Programmstart
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_USER_BLOCKED, MainFormState.CONFIRMATION_READY), () -> {
                    userNotBlocked.run();
                    userRegistered.run();
                    this.controller.confirmation_buttonDoor.visibleProperty().set(false);
                    this.controller.confirmation_buttonStart.visibleProperty().set(true);
                });

        // Warte auf Karte --> Karte nicht erkannt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_WAIT_FOR_CARD,
                        MainFormState.CONFIRMATION_CARD_UNKNOWN), () -> {
                            cardUnknown.run();
                        });
        // Guthaben nicht ausreichend --> Karte nicht erkennt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT,
                        MainFormState.CONFIRMATION_CARD_UNKNOWN), () -> {
                            creditSufficent.run();
                            cardUnknown.run();
                        });
        // Benutzer gesperrt --> Karte nicht erkannt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_USER_BLOCKED,
                        MainFormState.CONFIRMATION_CARD_UNKNOWN), () -> {
                            userNotBlocked.run();
                            cardUnknown.run();
                        });

        // Warte auf Karte --> Guthaben nicht ausreichend
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_WAIT_FOR_CARD,
                        MainFormState.CONFIRMATION_CREDIT_INSUFFICENT), () -> {
                            userRegistered.run();
                            creditInsufficent.run();
                        });
        // Karte nicht erkannt --> Guthaben nicht ausreichend
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CARD_UNKNOWN,
                        MainFormState.CONFIRMATION_CREDIT_INSUFFICENT), () -> {
                            userRegistered.run();
                            creditInsufficent.run();
                        });
        // Benutzer gesperrt --> Guthaben nicht ausreichend
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_USER_BLOCKED,
                        MainFormState.CONFIRMATION_CREDIT_INSUFFICENT), () -> {
                            userNotBlocked.run();
                            creditInsufficent.run();
                        });

        // Warte auf Karte --> Benutzer gesperrt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_WAIT_FOR_CARD,
                        MainFormState.CONFIRMATION_USER_BLOCKED), () -> {
                            userRegistered.run();
                            userBlocked.run();
                        });
        // Karte nicht erkannt --> Benutzer gesperrt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CARD_UNKNOWN,
                        MainFormState.CONFIRMATION_USER_BLOCKED), () -> {
                            userRegistered.run();
                            userBlocked.run();
                        });
        // Guthaben nicht ausreichend --> Benutzer gesperrt
        this.stateTransitions
                .put(new MainFormStateTransition(MainFormState.CONFIRMATION_CREDIT_INSUFFICENT,
                        MainFormState.CONFIRMATION_USER_BLOCKED), () -> {
                            creditSufficent.run();
                            userBlocked.run();
                        });

        // Warte auf Karte --> Tür öffnen
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_WAIT_FOR_CARD, MainFormState.OPEN_DOOR), () -> {
                    this.controller.confirmationPane.setVisible(false);
                    this.controller.doorOpenPane.setVisible(true);
                });
        // Guthaben nicht ausreichend --> Tür öffnen
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_CREDIT_INSUFFICENT, MainFormState.OPEN_DOOR), () -> {
                    this.controller.confirmationPane.setVisible(false);
                    this.controller.doorOpenPane.setVisible(true);
                });
        // Karte nicht erkannt --> Tür öffnen
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_CARD_UNKNOWN, MainFormState.OPEN_DOOR), () -> {
                    this.controller.confirmationPane.setVisible(false);
                    this.controller.doorOpenPane.setVisible(true);
                });
        // Benutzer gesperrt --> Tür öffnen
        this.stateTransitions.put(new MainFormStateTransition(
                MainFormState.CONFIRMATION_USER_BLOCKED, MainFormState.OPEN_DOOR), () -> {
                    this.controller.confirmationPane.setVisible(false);
                    this.controller.doorOpenPane.setVisible(true);
                });

        // Tür öffnen --> Geräteauswahl
        this.stateTransitions.put(
                new MainFormStateTransition(MainFormState.OPEN_DOOR, MainFormState.SELECT_DEVICE),
                () -> {
                    this.controller.doorOpenPane.setVisible(false);
                    this.controller.devicePane.setVisible(true);
                });

        // Bereit zum Programmstart --> (start) --> Geräteauswahl
        this.stateTransitions.put(new MainFormStateTransition(MainFormState.CONFIRMATION_READY,
                MainFormState.SELECT_DEVICE), () -> {
                    this.controller.confirmationPane.setVisible(false);
                    this.controller.devicePane.setVisible(true);
                });
    }

    /**
     * Initialisiert die Bestätigungs-Seite
     */
    private void initiateConfirmationPage() {
        assert(this.controller.selectedProgram != null);

        // Sichtbarer Button: Tür öffnen
        this.controller.confirmation_buttonStart.visibleProperty().set(false);
        this.controller.confirmation_buttonDoor.visibleProperty().set(true);

        // Gerätenamen und Programmdauer als Beschriftung setzen
        this.controller.confirmation_labelDevice.setText(this.controller.selectedDevice.getName()
                + " (" + (this.controller.selectedProgram.getMaxDuration().toMinutes()) + " min)");
        this.controller.confirmation_cost
                .setText(NumberFormat.getCurrencyInstance().format(this.controller.selectedProgram.getPriceAtMaxDuration()));
        this.controller.confirmation_remainingCredit
                .setText(NumberFormat.getCurrencyInstance().format(this.controller.selectedProgram.getPriceAtMaxDuration().negate()));

        this.controller.confirmation_remainingCredit.getStyleClass().clear();
        this.controller.confirmation_errorMessage.setVisible(false);
        this.controller.confirmation_credit.setVisible(false);
        this.controller.confirmation_remainingCredit.setVisible(false);

        this.controller.confirmation_username.setText("Bitte Karte auflegen");
        this.controller.confirmation_username.getStyleClass().clear();
        this.controller.confirmation_username.getStyleClass().add("username");

        if (!this.controller.confirmation_userIcon.getStyleClass().contains("no-user-registered")) {
            this.controller.confirmation_userIcon.getStyleClass().add("no-user-registered");
        }

        this.controller.confirmation_credit.setText(NumberFormat.getCurrencyInstance().format(0));
    }

    /**
     * Führt eine Zustandsänderung des Hauptformulars durch, falls erlaubt
     *
     * @param newState
     *            Neuer Zustand
     */
    public void gotoState(MainFormState newState) {
        this.logger.trace("Changing state: " + this.state.name() + " -> " + newState.name());
        if (newState.equals(this.state)) {
            // Selber Zustand
            return;
        }

        if (newState.equals(MainFormState.STARTUP)) {
            // Start-Zustand ist von jedem anderen aus erreichbar
            this.controller.confirmAbortionPane.setVisible(false);
            this.controller.confirmationPane.setVisible(false);
            this.controller.devicePane.setVisible(false);
            this.controller.doorOpenPane.setVisible(false);
            this.controller.errorPane.setVisible(false);
            this.controller.infoPane.setVisible(false);
            this.controller.programPane.setVisible(false);
            this.controller.waitPane.setVisible(false);

            this.controller.startupPane.setVisible(true);
            this.state = newState;
            return;
        }

        if (newState.equals(MainFormState.ERROR)) {
            // Fehlerzustand ist von jedem anderen aus erreichbar
            this.controller.errorPane.setVisible(true);
            this.controller.error_buttonRetry.setDisable(true);

            this.stateBeforeError = this.state;
            this.state = newState;
            return;
        }

        if (newState.equals(MainFormState.ERROR_RETRYABLE)) {
            // Fehlerzustand ist von jedem anderen aus erreichbar
            this.controller.errorPane.setVisible(true);
            this.controller.error_buttonRetry.setDisable(false);

            this.stateBeforeError = this.state;
            this.state = newState;
            return;
        }

        final Runnable transMethod =
                this.stateTransitions.get(new MainFormStateTransition(this.state, newState));
        if (transMethod == null) {
            // Zustandsänderung nicht erlaubt
            throw new IllegalStateException("Die Zustandsänderung von '" + this.state.name()
                    + "' nach '" + newState.name() + "' ist nicht erlaubt.");
        } else {
            // Zustandsänderung durchführen
            Platform.runLater(transMethod);
            this.state = newState;
            if (newState.equals(MainFormState.SELECT_DEVICE)) {
                // Bedienbereit erreicht -> Readiness-Marker fuer den Auto-Update-Watchdog (Phase 6 AP5).
                org.kabieror.elwasys.raspiclient.application.TerminalReadinessMarker.markReady();
            }
        }
    }

    /**
     * Geht zum Zustand zurück, der vor dem aufgetretenen Fehler vorherrschte
     */
    public void gotoStateBeforeError() {
        this.logger.trace("Going back to non-error state");
        this.controller.errorPane.setVisible(false);
        this.state = this.stateBeforeError;
    }

    /**
     * Geht nach einem Fehler zum Startzustand
     */
    public void resetAfterError() {
        this.controller.errorPane.setVisible(false);
        this.state = this.stateBeforeError;
        this.gotoState(MainFormState.SELECT_DEVICE);
    }
}
