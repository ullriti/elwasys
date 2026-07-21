package org.kabieror.elwasys.raspiclient.offline;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.kabieror.elwasys.raspiclient.api.dto.SnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistiert den zuletzt geladenen Standort-{@link SnapshotDto} auf dem Terminal
 * (Phase 4 AP6, siehe kb/05-migration-plan.md "Konzeptskizze: Offline-Buchungen am
 * Terminal" Punkt 1 "Lokaler Daten-Snapshot"). Datei liegt im Arbeitsverzeichnis
 * ({@code offline-snapshot.json}, analog {@code .client-uid}/{@code elwasys.properties}) -
 * bewusst UNVERSCHLÜSSELT (Auftraggeber-Entscheidung, siehe kb/05-migration-plan.md
 * "Festlegungen zu den Offline-Detailfragen": "Snapshot/Journal liegen unverschlüsselt auf
 * dem Gerät ... dokumentiertes Restrisiko"); {@link SnapshotDto} enthält ohnehin keine
 * Passwort-Hashes (siehe dessen Javadoc).
 * <p>
 * Neustartfest: wird beim Konstruieren einmalig von der Platte geladen, damit ein frisch
 * gestarteter Client sofort einen (ggf. bereits abgelaufenen) Snapshot zur Verfügung hat,
 * ohne erst auf das Backend warten zu müssen.
 */
public class OfflineSnapshotStore {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final Path file;
    private final Gson gson;
    private volatile SnapshotDto cached;

    public OfflineSnapshotStore(Path file) {
        this.file = file;
        this.gson = OfflineJsonSupport.gson();
        this.cached = loadFromDisk();
    }

    /**
     * Persistiert einen frisch geladenen Snapshot (überschreibt den vorherigen - es wird nur
     * je Standort GENAU EIN aktueller Snapshot gehalten, keine Historie).
     */
    public synchronized void save(SnapshotDto snapshot) {
        this.cached = snapshot;
        try {
            Files.writeString(this.file, this.gson.toJson(snapshot), StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            this.logger.error("Konnte den Offline-Snapshot nicht auf der Platte speichern.", e);
        }
    }

    /**
     * Der zuletzt geladene/persistierte Snapshot, oder {@code null}, wenn noch nie einer
     * geladen werden konnte (frisches Terminal, das noch nie erfolgreich mit dem Backend
     * gesprochen hat).
     */
    public SnapshotDto get() {
        return this.cached;
    }

    private SnapshotDto loadFromDisk() {
        if (!Files.exists(this.file)) {
            return null;
        }
        try {
            String json = Files.readString(this.file);
            return this.gson.fromJson(json, SnapshotDto.class);
        } catch (Exception e) {
            this.logger.warn("Konnte den persistierten Offline-Snapshot nicht lesen - starte ohne Snapshot.", e);
            return null;
        }
    }
}
