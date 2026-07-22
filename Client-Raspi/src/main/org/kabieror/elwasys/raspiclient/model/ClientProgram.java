package org.kabieror.elwasys.raspiclient.model;

import org.kabieror.elwasys.common.ProgramType;
import org.kabieror.elwasys.raspiclient.api.dto.ProgramDto;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Client-seitiges Gegenstück zu {@code Common.Program} (Phase 4 AP4, siehe
 * docs/kb/05-migration-plan.md "Client-Cutover"). Anders als das Alt-{@code Program} (Active
 * Record mit eigenem DB-Zugriff für Preisberechnung/Persistenz) ist dies ein reiner,
 * unveränderlicher Datencontainer, befüllt aus der API-Antwort ({@link ProgramDto}) - die
 * eigentliche Preisberechnung (inkl. Gruppenrabatt) läuft jetzt serverseitig
 * ({@code PricingService}), der Client übernimmt nur noch das Ergebnis
 * ({@link #getPriceAtMaxDuration()}).
 * <p>
 * <b>Entscheidung Common-Modelle vs. eigene Client-DTOs</b>: {@code Common.Program} (wie die
 * übrigen Alt-Modellklassen) ist untrennbar mit {@code DataManager}/JDBC verwoben (Laden via
 * {@code ResultSet}, Preisberechnung/Persistenz-Methoden greifen direkt auf die Verbindung
 * zu) - eine Wiederverwendung hätte bedeutet, entweder Common anzufassen (verboten) oder die
 * DB-Methoden mit Attrappen zu unterlaufen. Eigene, schlanke Modellklassen sind der
 * kleinere, sauberere Eingriff. Wiederverwendet wird dagegen {@code Common.ProgramType} (ein
 * reines, DB-freies Enum ohne DataManager-Kopplung) statt es zu duplizieren.
 */
public class ClientProgram {

    private final int id;
    private String name;
    private ProgramType type;
    private Duration maxDuration;
    private Duration freeDuration;
    private BigDecimal flagfall;
    private BigDecimal rate;
    private ChronoUnit timeUnit;
    private boolean autoEnd;
    private Duration earliestAutoEnd;
    private boolean enabled;
    private BigDecimal priceAtMaxDuration;

    private ClientProgram(int id) {
        this.id = id;
    }

    public static ClientProgram of(ProgramDto dto) {
        ClientProgram p = new ClientProgram(dto.id());
        p.name = dto.name();
        p.type = dto.type();
        p.maxDuration = Duration.ofSeconds(dto.maxDurationSeconds());
        p.freeDuration = Duration.ofSeconds(dto.freeDurationSeconds());
        p.flagfall = dto.flagfall();
        p.rate = dto.rate();
        p.timeUnit = dto.timeUnit();
        p.autoEnd = dto.autoEnd();
        p.earliestAutoEnd = Duration.ofSeconds(dto.earliestAutoEndSeconds());
        p.enabled = dto.enabled();
        p.priceAtMaxDuration = dto.priceAtMaxDuration();
        return p;
    }

    /**
     * Entspricht {@code Common.Program#getDoorOpenProgram()}: ein rein lokales, nie an das
     * Backend übertragenes "Tür öffnen"-Programm (30 Sekunden, kostenlos). Wird nur für die
     * client-lokale "Tür öffnen"-Funktion der Gerätekacheln verwendet (siehe
     * {@link ClientExecution#offline}).
     */
    public static ClientProgram doorOpen() {
        ClientProgram p = new ClientProgram(-1);
        p.type = ProgramType.OPEN_DOOR;
        p.flagfall = BigDecimal.ZERO;
        p.rate = BigDecimal.ZERO;
        p.timeUnit = ChronoUnit.SECONDS;
        p.maxDuration = Duration.ofSeconds(30);
        p.freeDuration = Duration.ZERO;
        p.autoEnd = false;
        p.earliestAutoEnd = Duration.ZERO;
        p.enabled = true;
        p.priceAtMaxDuration = BigDecimal.ZERO;
        return p;
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return this.name;
    }

    public ProgramType getType() {
        return this.type;
    }

    public Duration getMaxDuration() {
        return this.maxDuration;
    }

    public boolean isAutoEnd() {
        return this.autoEnd;
    }

    public Duration getEarliestAutoEnd() {
        return this.earliestAutoEnd;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public BigDecimal getFlagfall() {
        return this.flagfall;
    }

    public BigDecimal getRate() {
        return this.rate;
    }

    public ChronoUnit getTimeUnit() {
        return this.timeUnit;
    }

    /**
     * Der vom Backend für dieses Programm berechnete Preis für die Maximaldauer (inkl.
     * etwaigem Gruppenrabatt des Benutzers, für den das umschließende {@code DeviceDto}
     * geladen wurde - bzw. OHNE Rabatt, wenn dieses Programm aus der anonymen
     * Geräteübersicht stammt, siehe {@code DeviceOverviewDto}).
     * <p>
     * Alle Aufrufstellen im UI riefen historisch stets {@code Program#getPrice(maxDuration,
     * user)} auf (nie mit einer anderen Dauer) - dieses vorab vom Server berechnete Feld
     * deckt daher jeden tatsächlichen Aufrufer ab.
     */
    public BigDecimal getPriceAtMaxDuration() {
        return this.priceAtMaxDuration;
    }
}
