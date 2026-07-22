package org.kabieror.elwasys.backend.api.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.function.Supplier;
import org.kabieror.elwasys.backend.domain.LocationEntity;
import org.kabieror.elwasys.backend.domain.TerminalIdempotencyKeyEntity;
import org.kabieror.elwasys.backend.repository.TerminalIdempotencyKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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
 * <p><b>Bekannte Grenze (dokumentiert, nicht Teil dieses Arbeitspakets)</b>: dies ist KEINE
 * verteilte Sperre. Zwei tatsächlich GLEICHZEITIGE Anfragen mit demselben Schlüssel können
 * beide den "nicht gefunden"-Zweig durchlaufen und {@code action} je einmal ausführen,
 * bevor der zweite {@code INSERT} an der Unique-Constraint scheitert (siehe
 * {@link #execute}, catch-Zweig) - der Seiteneffekt (z.B. eine Guthabenbuchung) wäre dann
 * bereits zweimal ausgelöst. In der Praxis meldet ein einzelnes Terminal ein Ereignis
 * sequenziell (kein paralleles Doppel-Senden derselben Meldung), das Risiko ist damit gering;
 * eine vollständige Lösung (z.B. per DB-Advisory-Lock) ist für ein künftiges Arbeitspaket
 * vorgemerkt, falls die Offline-Replay-Vertiefung (AP6) sie braucht.
 */
@Service
public class IdempotencyService {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final TerminalIdempotencyKeyRepository repository;

    private final ObjectMapper objectMapper;

    public IdempotencyService(TerminalIdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
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

        Optional<TerminalIdempotencyKeyEntity> existing = this.repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            this.logger.debug("Idempotency-Key '{}' bereits verarbeitet (operation={}) - liefere gespeicherte "
                    + "Antwort erneut aus, ohne '{}' erneut auszufuehren.", idempotencyKey, operation, operation);
            return new IdempotentResult<>(deserialize(existing.get().getResponseBody(), responseType), true);
        }

        T result = action.get();
        try {
            this.repository.saveAndFlush(new TerminalIdempotencyKeyEntity(idempotencyKey, location, operation,
                    responseStatus, serialize(result)));
        } catch (DataIntegrityViolationException e) {
            // Race zweier gleichzeitiger Anfragen mit demselben Schluessel - siehe
            // Klassen-Javadoc "Bekannte Grenze". Die Aktion ist bereits ausgefuehrt; wir
            // geben trotzdem das gerade berechnete Ergebnis zurueck (statt eines Fehlers),
            // damit DIESE Anfrage nicht scheitert.
            this.logger.warn("Idempotency-Key '{}' wurde parallel bereits gespeichert (operation={}).",
                    idempotencyKey, operation);
        }
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
