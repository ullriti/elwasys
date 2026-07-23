package org.kabieror.elwasys.backend.service;

import java.time.LocalDateTime;
import org.kabieror.elwasys.backend.repository.TerminalIdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Täglicher Aufräum-Job für die Tabelle {@code terminal_idempotency_keys} (Issue #32 -
 * Betriebskonzept Dauerbetrieb). Die Tabelle bekommt einen Eintrag je terminal-gemeldetem
 * Execution-Ereignis (Start/Ende/Abbruch/Reset, siehe
 * {@code V4__create_terminal_idempotency_keys.sql}) und wuchs bisher unbegrenzt. Ein Replay
 * eines Terminals kann maximal so alt wie dessen Offline-Journal sein (Nachmeldung offline
 * getätigter Buchungen) - Einträge jenseits der Aufbewahrungsfrist werden nie wieder für eine
 * Deduplizierung gebraucht und können gefahrlos entfernt werden.
 *
 * <p>Das {@code @Scheduled} wird - wie beim {@link org.kabieror.elwasys.backend.ws.TerminalHeartbeatScheduler}
 * (Heartbeat) - durch das {@code @EnableScheduling} auf
 * {@link org.kabieror.elwasys.backend.ws.TerminalWebSocketConfig} aktiviert (dort bewusst per
 * {@code @Profile("!token-cli & !admin-cli")} für die einmaligen CLI-Läufe abgeschaltet). Ein
 * eigenes {@code @EnableScheduling} ist daher nicht nötig.
 */
@Component
public class IdempotencyKeyRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyKeyRetentionScheduler.class);

    private final TerminalIdempotencyKeyRepository repository;

    /**
     * Aufbewahrungsfrist in Tagen ({@code elwasys.idempotency.retention-days}, Default 30):
     * Einträge, deren {@code created_at} älter ist, werden beim nächsten Lauf gelöscht. Der
     * Default liegt bewusst deutlich über der maximalen Offline-Journal-Lebensdauer eines
     * Terminals, damit ein spätes Replay noch dedupliziert werden kann.
     */
    private final long retentionDays;

    public IdempotencyKeyRetentionScheduler(TerminalIdempotencyKeyRepository repository,
            @Value("${elwasys.idempotency.retention-days:30}") long retentionDays) {
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    /**
     * Läuft täglich um 03:00 Uhr (Cron über {@code elwasys.idempotency.purge-cron}
     * überschreibbar, Default nachts zu einer verkehrsarmen Zeit). Löscht alle Einträge, die
     * älter als die konfigurierte Aufbewahrungsfrist sind.
     */
    @Scheduled(cron = "${elwasys.idempotency.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purgeExpiredKeys() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(this.retentionDays);
        int removed = purgeOlderThan(threshold);
        if (removed > 0) {
            LOG.info("Idempotenz-Aufräumung: {} Schlüssel älter als {} Tage (vor {}) gelöscht.", removed,
                    this.retentionDays, threshold);
        } else {
            LOG.debug("Idempotenz-Aufräumung: keine Schlüssel älter als {} Tage (vor {}).", this.retentionDays,
                    threshold);
        }
    }

    /**
     * Löscht alle Idempotenz-Schlüssel mit {@code created_at} vor {@code threshold} und liefert
     * die Anzahl. Bewusst öffentlich und mit explizitem Schwellwert-Parameter, damit die
     * Löschgrenze im Test deterministisch (ohne Wanduhr) gesetzt werden kann.
     *
     * @return Anzahl der gelöschten Zeilen
     */
    @Transactional
    public int purgeOlderThan(LocalDateTime threshold) {
        return this.repository.deleteByCreatedAtBefore(threshold);
    }
}
