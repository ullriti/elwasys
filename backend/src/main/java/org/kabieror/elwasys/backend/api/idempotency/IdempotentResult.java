package org.kabieror.elwasys.backend.api.idempotency;

/**
 * Ergebnis eines {@link IdempotencyService#execute}-Aufrufs: die (ggf. aus einer früheren
 * Anfrage wiederhergestellte) Antwort sowie ein Merker, ob es sich um ein Replay handelte
 * (kein Idempotenz-Header, ein neuer Header oder eine gefundene Dublette). Aufrufer nutzen
 * {@link #replayed()}, um bei einem Replay Seiteneffekte zu unterdrücken, die nicht ein
 * zweites Mal ausgelöst werden dürfen (z.B. Benachrichtigungsversand - siehe
 * {@code ExecutionController#finishOrAbort}).
 */
public record IdempotentResult<T>(T body, boolean replayed) {
}
