package org.kabieror.elwasys.backend.auth.terminal;

import java.util.List;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.repository.LocationRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Minimaler, dokumentierter Weg zum Erzeugen/Widerrufen von Standort-Tokens in Phase 2 (siehe
 * kb/05-migration-plan.md, AP4: "kein Admin-UI [Phase 3], ein dokumentierter Weg
 * [CLI-Runner/SQL-Anleitung] genügt"). Läuft NUR unter dem Profil {@code token-cli} (siehe
 * {@code application-token-cli.yml}, das zusätzlich {@code spring.main.web-application-type:
 * none} setzt, damit dieser einmalige CLI-Aufruf keinen Webserver hochfährt und der Prozess
 * nach Abschluss von selbst beendet).
 *
 * <p><b>Anlegen</b> (siehe kb/04-build-and-run.md für das vollständige Kommando):
 * <pre>
 * java -jar backend/target/elwasys-backend.jar \
 *     --spring.profiles.active=token-cli \
 *     --location=Default \
 *     --label=terminal-kueche
 * </pre>
 * Das Klartext-Token wird EINMALIG auf {@code stdout} ausgegeben (siehe
 * {@link TerminalTokenService}, Klassen-Javadoc: es wird nirgends gespeichert) - sofort
 * sicher übernehmen (z.B. in die Terminal-Konfiguration), ein erneutes Anzeigen ist nicht
 * möglich.
 *
 * <p><b>Widerrufen</b> (Rotation - altes Token nach Umstellung des Terminals deaktivieren):
 * <pre>
 * java -jar backend/target/elwasys-backend.jar \
 *     --spring.profiles.active=token-cli \
 *     --revoke-token-id=42
 * </pre>
 */
@Component
@Profile("token-cli")
public class TerminalTokenCliRunner implements ApplicationRunner {

    private final LocationRepository locationRepository;

    private final TerminalTokenService terminalTokenService;

    public TerminalTokenCliRunner(LocationRepository locationRepository, TerminalTokenService terminalTokenService) {
        this.locationRepository = locationRepository;
        this.terminalTokenService = terminalTokenService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> revokeIds = args.getOptionValues("revoke-token-id");
        if (revokeIds != null && !revokeIds.isEmpty()) {
            revoke(Integer.parseInt(revokeIds.get(0)));
            return;
        }

        List<String> locationNames = args.getOptionValues("location");
        if (locationNames == null || locationNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "Usage: --spring.profiles.active=token-cli --location=<Standortname> [--label=<Beschriftung>]"
                            + " ODER --spring.profiles.active=token-cli --revoke-token-id=<Id>");
        }
        String locationName = locationNames.get(0);
        List<String> labels = args.getOptionValues("label");
        String label = labels == null || labels.isEmpty() ? null : labels.get(0);

        create(locationName, label);
    }

    private void create(String locationName, String label) {
        LocationEntity location = this.locationRepository.findByName(locationName).orElseThrow(
                () -> new IllegalArgumentException("Unknown location '" + locationName + "'."));
        IssuedTerminalToken issued = this.terminalTokenService.createToken(location, label);

        System.out.println("================================================================");
        System.out.println("Neues Standort-Token erzeugt.");
        System.out.println("  Standort: " + location.getName() + " (id=" + location.getId() + ")");
        System.out.println("  Token-Id: " + issued.entity().getId());
        System.out.println("  Token:    " + issued.rawToken());
        System.out.println();
        System.out.println("Dieses Token wird nur EINMAL angezeigt und nirgends gespeichert -");
        System.out.println("jetzt sicher in der Terminal-Konfiguration hinterlegen.");
        System.out.println("================================================================");
    }

    private void revoke(int tokenId) {
        boolean found = this.terminalTokenService.revoke(tokenId);
        if (found) {
            System.out.println("Token " + tokenId + " wurde widerrufen.");
        } else {
            System.out.println("Kein Token mit id=" + tokenId + " gefunden.");
        }
    }
}
