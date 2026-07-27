package org.kabieror.elwasys.backend.ui.login;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.kabieror.elwasys.backend.service.PasswordResetService;

/**
 * Öffentlicher Login-Bildschirm (Phase 3 AP1, siehe docs/kb/05-migration-plan.md) - Nachfolger von
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
 *
 * <p><b>UI-Redesign v2 (siehe docs/specs/0002-ui-design/v2/MAPPING.md, "Login")</b>: aus der
 * zentrierten Karte auf grauem Grund wird ein zweigeteilter Bildschirm - links eine
 * Markenfläche in der Sidebar-Farbe (Logo-Mark, Wortmarke, ein Satz Fließtext), rechts das
 * unveränderte Formular. Beide Hälften sind {@code flex: 1 1 26rem} (portal-theme.css), damit
 * sie bei schmalem Fenster untereinander umbrechen. Am Formular selbst ändert sich nichts: die
 * {@link LoginI18n}-Texte, {@code action="login"} und der "Passwort vergessen?"-Listener sind
 * dieselben wie zuvor. Den Prototyp-Umschalter "Als normaler Benutzer anmelden" gibt es hier
 * bewusst nicht - in Flow entscheidet weiterhin {@code ui/RootView} anhand der Rolle.
 */
@Route("login")
@PageTitle("Login - Waschportal")
@AnonymousAllowed
public class LoginView extends HorizontalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView(PasswordResetService passwordResetService) {
        addClassName("login-view");
        setSizeFull();
        setSpacing(false);
        setPadding(false);

        this.loginForm.setAction("login");
        this.loginForm.setI18n(buildGermanI18n());
        this.loginForm.setForgotPasswordButtonVisible(true);
        this.loginForm.addForgotPasswordListener(e -> new PasswordForgotDialog(passwordResetService).open());

        add(buildBrandPanel(), buildFormPanel());
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        // Spring Securitys Standard-Fehlschlagverhalten hängt "?error" an die
        // Login-Processing-URL an ("/login?error") - dieselbe Route, unter der diese View
        // registriert ist (siehe VaadinSecurityConfigurer#loginView in SecurityConfig).
        boolean hasError = event.getLocation().getQueryParameters().getParameters().containsKey("error");
        this.loginForm.setError(hasError);
    }

    /**
     * Linke Hälfte: Markenfläche. Abstände und Flächen kommen komplett aus portal-theme.css,
     * deshalb sind Padding und Spacing der Layouts hier abgeschaltet.
     */
    private static VerticalLayout buildBrandPanel() {
        // Reine Deko (abgerundetes Quadrat mit Kreis, in portal-theme.css gezeichnet).
        Div mark = new Div();
        mark.addClassName("login-brand-mark");
        mark.getElement().setAttribute("aria-hidden", "true");

        Span wordmark = new Span("Waschportal");
        wordmark.addClassName("login-brand-title");

        HorizontalLayout markRow = new HorizontalLayout(mark, wordmark);
        markRow.addClassName("login-brand-row");
        markRow.setSpacing(false);
        markRow.setPadding(false);
        markRow.setAlignItems(Alignment.CENTER);

        Paragraph claim = new Paragraph("Standorte, Geräte, Programme und Guthaben an einer Stelle verwalten.");
        claim.addClassName("login-brand-text");

        VerticalLayout brand = new VerticalLayout(markRow, claim);
        brand.addClassName("login-brand");
        brand.setSpacing(false);
        brand.setPadding(false);
        brand.setJustifyContentMode(JustifyContentMode.CENTER);
        return brand;
    }

    /** Rechte Hälfte: das unveränderte Anmeldeformular, mittig auf weißem Grund. */
    private VerticalLayout buildFormPanel() {
        VerticalLayout panel = new VerticalLayout(this.loginForm);
        panel.addClassName("login-form-panel");
        panel.setSpacing(false);
        panel.setPadding(false);
        panel.setAlignItems(Alignment.CENTER);
        panel.setJustifyContentMode(JustifyContentMode.CENTER);
        return panel;
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
        // Bugfix (Phase 3 AP6, gefunden bei der E2E-Portierung, siehe docs/kb/05-migration-plan.md):
        // ohne diese Zeile blieb der "Passwort vergessen?"-Knopf beim Vaadin-Default "Forgot
        // password" (Englisch) hängen, obwohl alle anderen Formulartexte hier bewusst
        // eingedeutscht sind (1:1 wie das Alt-Portal, siehe Klassen-Javadoc).
        form.setForgotPassword("Passwort vergessen?");

        LoginI18n.ErrorMessage errorMessage = i18n.getErrorMessage();
        errorMessage.setTitle("Login fehlgeschlagen");
        errorMessage.setMessage("Bitte prüfen Sie die Anmeldedaten und versuchen Sie es erneut.");
        i18n.setErrorMessage(errorMessage);

        return i18n;
    }
}
