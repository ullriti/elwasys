package org.kabieror.elwasys.raspiclient.offline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Gemeinsame Gson-Konfiguration für {@link OfflineSnapshotStore} und {@link OfflineJournal}
 * (Phase 4 AP6, siehe docs/kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am
 * Terminal"). Derselbe {@link LocalDateTime}-Adapter wie {@code api.ApiClient}, dupliziert
 * statt geteilt, weil {@code ApiClient} ihn bewusst privat hält (kein öffentlicher
 * JSON-Vertrag dieser Klasse).
 */
final class OfflineJsonSupport {

    private OfflineJsonSupport() {
    }

    static Gson gson() {
        return new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class,
                        (com.google.gson.JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) -> src == null
                                ? null
                                : new com.google.gson.JsonPrimitive(DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(src)))
                .registerTypeAdapter(LocalDateTime.class,
                        (com.google.gson.JsonDeserializer<LocalDateTime>) (json, typeOfT, context) -> json
                                .isJsonNull() ? null
                                        : LocalDateTime.parse(json.getAsString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .create();
    }
}
