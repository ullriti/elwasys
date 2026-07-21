package org.kabieror.elwasys.raspiclient.api.dto;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import org.kabieror.elwasys.common.ProgramType;

/**
 * Gegenstück zu {@code backend.api.dto.ProgramDto} (Phase 4 AP4). {@code timeUnit} wird
 * direkt in {@link ChronoUnit} deserialisiert (Gson matcht Enum-Konstanten per Name; die
 * Backend-Werte SECONDS/MINUTES/HOURS existieren wortgleich in {@link ChronoUnit}) - ein
 * eigenes Zeiteinheiten-Enum ist daher nicht nötig. {@code type} wird in das bereits
 * vorhandene, DB-freie {@link ProgramType} aus {@code Common} deserialisiert (dieses kennt
 * zusätzlich {@code OPEN_DOOR} für das rein lokale "Tür öffnen"-Programm, das die API nie
 * liefert - siehe {@link org.kabieror.elwasys.raspiclient.model.ClientProgram#openDoor()}).
 */
public record ProgramDto(Integer id, String name, ProgramType type, int maxDurationSeconds, int freeDurationSeconds,
        BigDecimal flagfall, BigDecimal rate, ChronoUnit timeUnit, boolean autoEnd, int earliestAutoEndSeconds,
        boolean enabled, BigDecimal priceAtMaxDuration) {
}
