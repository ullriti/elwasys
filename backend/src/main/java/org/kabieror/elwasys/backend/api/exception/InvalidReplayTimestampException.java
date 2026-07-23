package org.kabieror.elwasys.backend.api.exception;

import org.springframework.http.HttpStatus;

/**
 * Der privilegierte Nachbuchungs-Pfad ({@code replay}, Issue #16) wurde mit einem nicht
 * plausiblen Original-Zeitstempel aufgerufen (Defense-in-Depth-Härtung, Issue #67): eine echte
 * Offline-Nachmeldung trägt immer den ORIGINAL-Zeitpunkt der bereits offline gebuchten
 * Ausführung, der deutlich in der Vergangenheit liegt. Fehlt der Zeitstempel oder liegt er
 * "jetzt"/in der Zukunft (bzw. absurd weit in der Vergangenheit), ist der Aufruf verdächtig -
 * das Terminal umgeht mit dem {@code replay}-Flag die fachlichen Wächter, ohne dass eine echte
 * vorherige Offline-Buchung stattgefunden haben muss. Solche Aufrufe werden abgelehnt (statt
 * still auf Serverzeit ersetzt wie bei einer regulären Live-/Nachmeldung, siehe
 * {@link org.kabieror.elwasys.backend.offline.ClientTimestampPolicy#resolve}).
 *
 * <p>{@code 422 Unprocessable Entity}: die Anfrage ist syntaktisch gültig, aber fachlich nicht
 * verarbeitbar. Bewusst ein FACHLICHER Fehler (kein Kommunikationsfehler), damit ein solcher
 * Eintrag beim Terminal-Replay ins Dead-Letter wandert und das Journal nicht verklemmt (Issue
 * #17/#67).
 */
public class InvalidReplayTimestampException extends ApiException {

    public InvalidReplayTimestampException(String detail) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-replay-timestamp",
                "Nachmeldung mit unplausiblem Zeitstempel", detail);
    }
}
