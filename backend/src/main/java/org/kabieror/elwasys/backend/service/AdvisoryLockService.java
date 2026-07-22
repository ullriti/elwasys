package org.kabieror.elwasys.backend.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transaktionsgebundene PostgreSQL-Advisory-Locks für die Geld-/Belegungspfade (Issue #20,
 * #29 - AP3, siehe docs/kb/05-migration-plan.md). Serialisiert genau die Read-then-Write-
 * Sequenzen, die im Alt-System durch den Einzelplatz-Client je Gerät strukturell unmöglich
 * waren und im Mehrbenutzer-Backend (Portal + n Terminals) zu realen Races werden.
 *
 * <p>Verwendet {@code pg_advisory_xact_lock}: die Sperre wird am Transaktionsende automatisch
 * freigegeben (kein manuelles Unlock, kein Leak bei einem Fehler). Deshalb MUSS jede Methode
 * innerhalb einer bestehenden Transaktion laufen - {@link Propagation#MANDATORY} erzwingt das
 * und deckt einen versehentlichen Aufruf im Autocommit-Modus (die Sperre würde sofort wieder
 * freigegeben und wäre wirkungslos) sofort als Fehler auf.
 *
 * <p><b>Namensräume:</b> die zweiargumentige Form {@code pg_advisory_xact_lock(key1, key2)}
 * bildet einen eigenen Sperrraum, getrennt von der einargumentigen {@code bigint}-Form. Der
 * erste Parameter dient hier als Namensraum, damit sich Geräte-Ids und (gehashte)
 * Idempotenz-Schlüssel niemals gegenseitig blockieren.
 */
@Service
public class AdvisoryLockService {

    /** Namensraum für die geräteweite Belegungs-Serialisierung (Issue #20). */
    private static final int NAMESPACE_DEVICE = 1;

    /** Namensraum für die schlüsselweise Idempotenz-Serialisierung (Issue #29). */
    private static final int NAMESPACE_IDEMPOTENCY = 2;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Serialisiert alle konkurrierenden Start-/Belegungsentscheidungen für dasselbe Gerät
     * (Issue #20, Szenario "Doppelstart/Überbuchung"): so laufen die Belegungsprüfung
     * ({@code getRunningExecution}) und der anschließende Insert atomar, ohne dass zwei
     * parallele {@code POST /executions} beide ein freies Gerät sehen.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockDevice(int deviceId) {
        this.entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, :id)")
                .setParameter("ns", NAMESPACE_DEVICE).setParameter("id", deviceId).getSingleResult();
    }

    /**
     * Serialisiert konkurrierende Anfragen mit demselben Idempotenz-Schlüssel (Issue #29): so
     * kann von zwei tatsächlich gleichzeitigen Requests immer nur einer den "nicht
     * gefunden"-Zweig durchlaufen und die fachliche Aktion ausführen; der zweite wartet, sieht
     * anschließend den gespeicherten Schlüssel und liefert die gespeicherte Antwort erneut aus
     * (kein doppelter Seiteneffekt, kein 500 durch eine vergiftete Transaktion).
     *
     * <p>{@code hashtext} bildet den (variabel langen) Schlüssel auf einen {@code int4} ab.
     * Eine Hash-Kollision zweier unterschiedlicher Schlüssel serialisiert diese lediglich
     * unnötig - die Korrektheit bleibt unberührt.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockIdempotencyKey(String idempotencyKey) {
        this.entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(:ns, hashtext(:key))")
                .setParameter("ns", NAMESPACE_IDEMPOTENCY).setParameter("key", idempotencyKey).getSingleResult();
    }
}
