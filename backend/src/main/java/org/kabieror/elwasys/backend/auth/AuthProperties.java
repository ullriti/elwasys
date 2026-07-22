package org.kabieror.elwasys.backend.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration des Auth-Moduls (AP3, siehe docs/kb/05-migration-plan.md).
 *
 * <p><b>{@code elwasys.auth.rehash-on-login}</b> (Default: {@code false}): steuert, ob ein
 * erfolgreich per Alt-Format (SHA1) verifiziertes Passwort transaktional auf Argon2id
 * migriert wird ({@link ElwasysAuthenticationProvider}). Der Default ist bewusst AUS -
 * siehe die ausführliche Begründung in docs/kb/05-migration-plan.md ("Entscheidungen": Re-Hash
 * hinter Flag). Historischer Grund: solange das Alt-Portal denselben Nutzer per direktem
 * SHA1-String-Vergleich gegen dieselbe Datenbankspalte einloggte (Parallelbetrieb, siehe
 * CLAUDE.md/kb/05 Leitplanke "Backend anfangs nur lesend/additiv"), hätte ein Re-Hash durch
 * das Backend die Spalte auf Argon2id umgestellt und denselben Nutzer im Alt-Portal
 * ausgesperrt (dessen Vergleich {@code this.password.equals(Utilities.sha1(password))}
 * wäre dann fehlgeschlagen). Das Alt-Portal ist seit Phase 5 AP1 vollständig entfernt, der
 * ursprüngliche Sperrgrund entfällt damit; ob/wann das Flag produktiv aktiviert wird, ist
 * eine Entscheidung der eigentlichen Produktivumschaltung (Phase 6, siehe
 * docs/kb/05-migration-plan.md), nicht Teil eines einzelnen Arbeitspakets - der Code-Default
 * bleibt daher bewusst unverändert AUS.
 */
@Component
@ConfigurationProperties(prefix = "elwasys.auth")
public class AuthProperties {

    private boolean rehashOnLogin = false;

    /**
     * Brute-Force-Schutz des Portal-Logins (Pre-Launch AP4, Issue #25, siehe
     * {@link ElwasysAuthenticationProvider}): Zahl der fehlgeschlagenen Anmeldeversuche pro
     * Benutzername innerhalb von {@link #loginLockoutWindow}, ab der weitere Versuche
     * (auch mit korrektem Passwort) temporär abgewiesen werden. Konservativer Default, der
     * legitime Vertipper nicht behindert, automatisiertes Durchprobieren aber ausbremst.
     */
    private int maxFailedLoginAttempts = 5;

    /**
     * Zeitfenster für {@link #maxFailedLoginAttempts}. Nach Ablauf (gerechnet ab dem ersten
     * gezählten Fehlversuch des Fensters) ist ein erneuter Login wieder möglich.
     */
    private Duration loginLockoutWindow = Duration.ofMinutes(15);

    public boolean isRehashOnLogin() {
        return this.rehashOnLogin;
    }

    public void setRehashOnLogin(boolean rehashOnLogin) {
        this.rehashOnLogin = rehashOnLogin;
    }

    public int getMaxFailedLoginAttempts() {
        return this.maxFailedLoginAttempts;
    }

    public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) {
        this.maxFailedLoginAttempts = maxFailedLoginAttempts;
    }

    public Duration getLoginLockoutWindow() {
        return this.loginLockoutWindow;
    }

    public void setLoginLockoutWindow(Duration loginLockoutWindow) {
        this.loginLockoutWindow = loginLockoutWindow;
    }
}
