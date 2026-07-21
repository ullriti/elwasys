package org.kabieror.elwasys.backend.ui.login;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import java.util.List;
import org.kabieror.elwasys.backend.exception.InvalidOrExpiredResetTokenException;
import org.kabieror.elwasys.backend.service.PasswordResetService;

/**
 * Öffentliche Ansicht zum Setzen eines neuen Passworts über einen per Email verschickten
 * Reset-Link (Phase 3 AP4, Testfall P19, siehe kb/05-migration-plan.md) - fachlicher
 * Nachfolger von {@code Portal/.../components/ResetPasswordWindow} (Alt-Portal), dort ein
 * modales Fenster über der bereits geladenen Portal-Seite (der Alt-Portal-Link öffnet die
 * Hauptseite mit Query-Parameter {@code ?rp=<key>}, die dieses Fenster dann selbst öffnet -
 * siehe {@code WashportalUtilities#getPasswordResetUrl}). Diese Portierung macht daraus
 * bewusst eine EIGENE Route (kein Login nötig, {@code @AnonymousAllowed}), weil Vaadin Flow
 * anders als das Alt-Portal serverseitiges Routing mit eigenen URLs kennt - fachlich
 * gleichwertig (derselbe Link-Klick führt zum selben Formular), technisch sauberer.
 *
 * <p>Der Query-Parameter heißt hier {@code key} (statt {@code rp}) - siehe
 * {@code PasswordResetService#requestReset}, das die Reset-URL mit {@code ?key=<token>}
 * baut.
 */
@Route("reset-password")
@PageTitle("Passwort zurücksetzen - Waschportal")
@AnonymousAllowed
public class ResetPasswordView extends VerticalLayout implements BeforeEnterObserver {

    private static final String QUERY_PARAM = "key";

    private final PasswordResetService passwordResetService;

    private final PasswordField tfNewPassword1 = new PasswordField("Neues Passwort");
    private final PasswordField tfNewPassword2 = new PasswordField("Wiederholung");

    private String token;

    public ResetPasswordView(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;

        addClassName("reset-password-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        this.tfNewPassword1.setMaxLength(50);
        this.tfNewPassword1.setWidthFull();

        this.tfNewPassword2.setMaxLength(50);
        this.tfNewPassword2.setWidthFull();

        FormLayout form = new FormLayout(this.tfNewPassword1, this.tfNewPassword2);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.setWidth("22em");

        Button btnSave = new Button("Passwort setzen", e -> save());
        btnSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        add(new H2("Waschportal"), new Paragraph("Bitte gib dein neues Passwort ein."), form, btnSave);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters().getParameters().get(QUERY_PARAM);
        this.token = values == null || values.isEmpty() ? null : values.get(0);

        if (this.token == null || !this.passwordResetService.isValidToken(this.token)) {
            removeAll();
            add(new H2("Waschportal"), new Paragraph(
                    "Dieser Link zum Zurücksetzen des Passworts ist ungültig oder abgelaufen. Bitte fordere über "
                            + "\"Passwort vergessen?\" auf der Login-Seite einen neuen Link an."));
        }
    }

    private void save() {
        if (this.tfNewPassword1.isEmpty() || this.tfNewPassword2.isEmpty()) {
            this.tfNewPassword1.setInvalid(this.tfNewPassword1.isEmpty());
            this.tfNewPassword2.setInvalid(this.tfNewPassword2.isEmpty());
            return;
        }
        if (!this.tfNewPassword1.getValue().equals(this.tfNewPassword2.getValue())) {
            this.tfNewPassword2.setInvalid(true);
            this.tfNewPassword2.setErrorMessage("Die Passwörter stimmen nicht überein.");
            return;
        }
        this.tfNewPassword1.setInvalid(false);
        this.tfNewPassword2.setInvalid(false);

        try {
            this.passwordResetService.resetPassword(this.token, this.tfNewPassword1.getValue());
        } catch (InvalidOrExpiredResetTokenException e) {
            showError(e.getMessage());
            return;
        }

        showSuccess("Passwort wurde erfolgreich geändert.");
        UI.getCurrent().navigate("login");
    }

    private static void showError(String message) {
        Notification notification = Notification.show(message, 5000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }

    private static void showSuccess(String message) {
        Notification notification = Notification.show(message, 4000, Notification.Position.MIDDLE);
        notification.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }
}
