package org.kabieror.elwasys.backend.ui.login;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.kabieror.elwasys.backend.service.PasswordResetService;

/**
 * Öffentlicher Login-Bildschirm (Phase 3 AP1, siehe kb/05-migration-plan.md) - Nachfolger von
 * {@code Portal/.../PublicLayout} (Alt-Portal). Nutzt Vaadins eingebaute {@link LoginForm}, die
 * per {@code action="login"} den Standard-Formular-Login von Spring Security auslöst (siehe
 * {@link org.kabieror.elwasys.backend.auth.SecurityConfig}, wo diese View über
 * {@code VaadinSecurityConfigurer#loginView(Class)} als Login-Ziel registriert ist -
 * authentifiziert letztlich weiterhin über
 * {@link org.kabieror.elwasys.backend.auth.ElwasysAuthenticationProvider}, unverändert seit
 * AP3).
 *
 * <p>Verhalten wie im Alt-Portal ({@code SessionManager#login} + {@code PublicLayout}):
 * Anmeldung per Benutzername+Passwort; ein Fehlschlag (falsches Passwort, unbekannter
 * Benutzer, gelöschter Benutzer ODER - bewusste Verschärfung seit AP3, siehe
 * {@code ElwasysAuthenticationProvider}-Javadoc - gesperrter Benutzer) zeigt die deutsche
 * Meldung "Login fehlgeschlagen" mit demselben Hinweistext wie im Alt-Portal. Nach
 * erfolgreichem Login landet der Benutzer über {@link org.kabieror.elwasys.backend.ui.RootView}
 * je nach Rolle im Admin- oder Benutzer-Dashboard (siehe deren Javadoc).
 *
 * <p><b>Phase 3 AP4</b>: der "Passwort vergessen?"-Knopf (bis AP4 über
 * {@code setForgotPasswordButtonVisible(false)} deaktiviert, siehe Änderungslog "Phase 3
 * AP1") ist jetzt aktiv und öffnet {@link PasswordForgotDialog} - fachlicher Nachfolger von
 * {@code Portal/.../components/PasswordForgotWindow} (Testfall P19).
 */
@Route("login")
@PageTitle("Login - Waschportal")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView(PasswordResetService passwordResetService) {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        this.loginForm.setAction("login");
        this.loginForm.setI18n(buildGermanI18n());
        this.loginForm.setForgotPasswordButtonVisible(true);
        this.loginForm.addForgotPasswordListener(e -> new PasswordForgotDialog(passwordResetService).open());

        add(new H2("Waschportal"), this.loginForm);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Securitys Standard-Fehlschlagverhalten hängt "?error" an die
        // Login-Processing-URL an ("/login?error") - dieselbe Route, unter der diese View
        // registriert ist (siehe VaadinSecurityConfigurer#loginView in SecurityConfig).
        boolean hasError = event.getLocation().getQueryParameters().getParameters().containsKey("error");
        this.loginForm.setError(hasError);
    }

    private static LoginI18n buildGermanI18n() {
        LoginI18n i18n = LoginI18n.createDefault();

        LoginI18n.Header header = new LoginI18n.Header();
        header.setTitle("Waschportal");
        header.setDescription("Bitte melden Sie sich an.");
        i18n.setHeader(header);

        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Login");
        form.setUsername("Benutzername");
        form.setPassword("Passwort");
        form.setSubmit("Login");

        LoginI18n.ErrorMessage errorMessage = i18n.getErrorMessage();
        errorMessage.setTitle("Login fehlgeschlagen");
        errorMessage.setMessage("Bitte prüfen Sie die Anmeldedaten und versuchen Sie es erneut.");
        i18n.setErrorMessage(errorMessage);

        return i18n;
    }
}
