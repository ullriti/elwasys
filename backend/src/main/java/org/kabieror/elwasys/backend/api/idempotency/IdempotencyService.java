package org.kabieror.elwasys.backend.api.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.function.Supplier;
import org.kabieror.elwasys.backend.api.exception.IdempotencyKeyReusedException;
import org.kabieror.elwasys.backend.api.exception.InvalidIdempotencyKeyException;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalIdempotencyKeyEntity;
import org.kabieror.elwasys.backend.repository.TerminalIdempotencyKeyRepository;
import org.kabieror.elwasys.backend.service.AdvisoryLockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedupliziert terminal-gemeldete Execution-Ereignisse (Start/Ende/Abbruch/Reset,
 * Phase 4 AP3, siehe docs/kb/05-migration-plan.md "Idempotenz + Replay" und
 * docs/kb/03-modules.md). Ein Terminal kann eine Meldung mehrfach senden (z.B. nach einem
 * Verbindungsabbruch, bevor die ursprüngliche Antwort ankam) - über den vom Terminal
 * erzeugten {@code Idempotency-Key}-Header (eine UUID pro fachlichem Ereignis, siehe
 * {@code ExecutionController}) wird die ZUERST berechnete Antwort erneut ausgeliefert,
 * ohne die fachliche Aktion (Abrechnung, Benachrichtigung, Steckdosenzustand in der
 * Persistenz, ...) ein zweites Mal auszuführen.
 *
 * <p><b>Ohne Header vollständig transparent</b>: fehlt der Header (bzw. ist er leer), wird
 * {@code action} einfach ausgeführt, ohne irgendetwas zu persistieren - das bestehende
 * Verhalten der Execution-Endpunkte (AP4) bleibt für Aufrufer ohne diesen Header 1:1
 * erhalten (additiv, siehe docs/kb/05-migration-plan.md Rahmenbedingungen).
 *
 * <p><b>Nebenläufigkeit (Issue #29, AP3)</b>: {@link #execute} serialisiert Anfragen mit
 * demselben Schlüssel per transaktionsgebundenem PostgreSQL-Advisory-Lock (siehe
 * {@link AdvisoryLockService#lockIdempotencyKey}). Von zwei tatsächlich GLEICHZEITIGEN
 * Anfragen mit demselben Schlüssel durchläuft damit immer nur EINE den "nicht gefunden"-Zweig
 * und führt {@code action} aus; die zweite wartet, sieht anschließend den gespeicherten
 * Schlüssel und liefert die gespeicherte Antwort erneut aus - kein doppelter Seiteneffekt und
 * kein HTTP 500 durch eine an der Unique-Constraint vergiftete Transaktion (der frühere
 * {@code catch (DataIntegrityViolationException)}-Notnagel entfällt dadurch).
 *
 * <p><b>Schlüssel-Validierung (Issue #29)</b>: ein Schlüssel, der die Speichergrenze der
 * Spalte ({@code VARCHAR(64)}) überschreitet, wird früh mit 400 abgelehnt
 * ({@link InvalidIdempotencyKeyException}), BEVOR {@code action} läuft - sonst scheiterte erst
 * {@code saveAndFlush} an der DB und die Operation (z.B. {@code finish}) könnte in einer
 * dauerhaften 500-Schleife nie persistiert werden.
 *
 * <p><b>Vorgangs-Bindung (Issue #41)</b>: beim Replay wird zusätzlich geprüft, ob der
 * gespeicherte {@code operation}-Wert zum aktuellen Aufruf passt; bei Abweichung 409
 * ({@link IdempotencyKeyReusedException}), damit ein versehentlich wiederverwendeter Schlüssel
 * nicht die Fremd-Antwort zurückliefert und die neue Aktion stillschweigend überspringt.
 */
@Service
public class IdempotencyService {

    /** Speichergrenze der Spalte {@code terminal_idempotency_keys.idempotency_key} (V4). */
    private static final int MAX_KEY_LENGTH = 64;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TerminalIdempotencyKeyRepository repository;

    private final ObjectMapper objectMapper;

    private final AdvisoryLockService advisoryLockService;

    public IdempotencyService(TerminalIdempotencyKeyRepository repository, ObjectMapper objectMapper,
            AdvisoryLockService advisoryLockService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.advisoryLockService = advisoryLockService;
    }

    /**
     * Führt {@code action} idempotent aus.
     *
     * @param idempotencyKey der vom Terminal erzeugte Schlüssel (i.d.R. eine UUID), oder
     *                        {@code null}/leer, wenn der Aufrufer keinen mitgeschickt hat
     *                        (dann rein transparenter Durchgriff, siehe Klassen-Javadoc)
     * @param location        der Standort des Terminal-Tokens (Scope, rein informativ)
     * @param operation       ein kurzer Bezeichner des Vorgangs (z.B.
     *                        {@code "execution-start"}) - erlaubt spätere Diagnose, welche
     *                        Art Ereignis ein Schlüssel referenziert
     * @param responseStatus  der HTTP-Status, den der Aufrufer für einen NEUEN (nicht
     *                        replayten) Aufruf zurückgibt - wird nur informativ gespeichert,
     *                        der tatsächlich gesendete Status wird vom Controller selbst
     *                        über {@code @ResponseStatus} festgelegt (siehe
     *                        {@code ExecutionController})
     * @param responseType    die Zielklasse für die Deserialisierung einer wiederhergestellten
     *                        Antwort
     * @param action          die fachliche Aktion, die nur beim ERSTEN Aufruf eines
     *                        Schlüssels ausgeführt wird. Nur ERFOLGREICHE Aufrufe werden
     *                        abgelegt - wirft {@code action} eine Exception (z.B. weil ein
     *                        Gerät zwischenzeitlich belegt wurde), wird nichts gespeichert;
     *                        ein erneuter Versuch mit demselben Schlüssel führt
     *                        {@code action} dann ganz normal erneut aus, statt dauerhaft an
     *                        einem fehlgeschlagenen Erstversuch zu kleben.
     */
    @Transactional
    public <T> IdempotentResult<T> execute(String idempotencyKey, LocationEntity location, String operation,
            int responseStatus, Class<T> responseType, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return new IdempotentResult<>(action.get(), false);
        }
        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            // Issue #29: früh ablehnen, BEVOR action läuft - sonst scheiterte erst saveAndFlush
            // an der VARCHAR(64)-Grenze und die Operation bliebe in einer 500-Schleife hängen.
            throw new InvalidIdempotencyKeyException(MAX_KEY_LENGTH);
        }

        // Issue #29: konkurrierende Anfragen mit demselben Schlüssel serialisieren, damit
        // action genau einmal läuft (siehe Klassen-Javadoc "Nebenläufigkeit"). Die Sperre wird
        // am Transaktionsende automatisch freigegeben.
        this.advisoryLockService.lockIdempotencyKey(idempotencyKey);

        Optional<TerminalIdempotencyKeyEntity> existing = this.repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            // Issue #41: der Schlüssel muss zum SELBEN Vorgang gehören - sonst bekäme ein
            // versehentlich wiederverwendeter Schlüssel die Fremd-Antwort und die neue Aktion
            // würde stillschweigend übersprungen.
            String storedOperation = existing.get().getOperation();
            if (!storedOperation.equals(operation)) {
                throw new IdempotencyKeyReusedException(storedOperation, operation);
            }
            this.logger.debug("Idempotency-Key '{}' bereits verarbeitet (operation={}) - liefere gespeicherte "
                    + "Antwort erneut aus, ohne '{}' erneut auszufuehren.", idempotencyKey, operation, operation);
            return new IdempotentResult<>(deserialize(existing.get().getResponseBody(), responseType), true);
        }

        T result = action.get();
        this.repository.saveAndFlush(new TerminalIdempotencyKeyEntity(idempotencyKey, location, operation,
                responseStatus, serialize(result)));
        return new IdempotentResult<>(result, false);
    }

    private String serialize(Object value) {
        try {
            return this.objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Konnte Antwort nicht fuer Idempotenz-Ablage serialisieren.", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return this.objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Konnte gespeicherte Idempotenz-Antwort nicht lesen.", e);
        }
    }
}
