package org.kabieror.elwasys.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Konfiguration des Auth-Moduls (AP3, siehe kb/05-migration-plan.md).
 *
 * <p><b>{@code elwasys.auth.rehash-on-login}</b> (Default: {@code false}): steuert, ob ein
 * erfolgreich per Alt-Format (SHA1) verifiziertes Passwort transaktional auf Argon2id
 * migriert wird ({@link ElwasysAuthenticationProvider}). Der Default ist bewusst AUS -
 * siehe die ausführliche Begründung in kb/05-migration-plan.md ("Entscheidungen": Re-Hash
 * hinter Flag). Kurzfassung: solange das Alt-Portal denselben Nutzer per direktem
 * SHA1-String-Vergleich gegen dieselbe Datenbankspalte einloggt (Parallelbetrieb, siehe
 * CLAUDE.md/kb/05 Leitplanke "Backend anfangs nur lesend/additiv"), würde ein Re-Hash durch
 * das Backend die Spalte auf Argon2id umstellen und denselben Nutzer im Alt-Portal
 * aussperren (dessen Vergleich {@code this.password.equals(Utilities.sha1(password))}
 * schlägt dann fehl). Das Flag wird erst beim Portal-Cutover (Phase 3, wenn das Alt-Portal
 * abgeschaltet wird) aktiviert.
 */
@Component
@ConfigurationProperties(prefix = "elwasys.auth")
public class AuthProperties {

    private boolean rehashOnLogin = false;

    public boolean isRehashOnLogin() {
        return this.rehashOnLogin;
    }

    public void setRehashOnLogin(boolean rehashOnLogin) {
        this.rehashOnLogin = rehashOnLogin;
    }
}
