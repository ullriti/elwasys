package org.kabieror.elwasys.raspiclient.ui.scheduler;

import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import org.kabieror.elwasys.raspiclient.application.ElwaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Plant Aufgaben, die nach einer Dauer der Inaktivität des Benutzers ausgeführt werden.
 *
 * @author Oliver Kabierschke
 */
public class InactivityScheduler implements IInactivityJobDoneListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private Map<InactivityFuture, InactivityJob> runningJobs = new HashMap<>();
    private Map<InactivityJob, Thread> runningThreads = new HashMap<>();

    public InactivityScheduler() {
        // Aktivitäts-Listener installieren
        if (ElwaManager.instance.getPrimaryStage() != null) {
            ElwaManager.instance.getPrimaryStage().addEventFilter(MouseEvent.MOUSE_CLICKED, e -> {
                this.onActivityDetected();
            });
            ElwaManager.instance.getPrimaryStage().addEventFilter(KeyEvent.ANY, e -> {
                this.onActivityDetected();
            });
        }
    }

    /**
     * Setze Timer der geplanten Jobs zurück
     */
    void onActivityDetected() {
        for (InactivityJob job : this.runningJobs.values()) {
            this.runningThreads.get(job).interrupt();
        }
    }

    /**
     * Plant einen Job, welcher nach einem Zeitraum der Inaktivität ausgeführt wird.
     *
     * @param job            Der auszuführende Job.
     * @param rate           Die Zeit der Inaktivität, nach welcher der Job ausgeführt werden soll.
     * @param timeUnit       Die Zeiteinheit der Zeitangabe.
     * @param executionLimit Die Anzahl an Ausführungen, nach denen der Job abgeschlossen ist.
     * @return Die Aktivitätsanzeige des gestarteten Jobs.
     */
    public InactivityFuture scheduleJob(Runnable job, int rate, TimeUnit timeUnit, int executionLimit) {
        InactivityFuture future = new InactivityFuture();
        InactivityJob iJob = new InactivityJob(future, rate, timeUnit, executionLimit, job);
        iJob.listenToFinishedEvent(this);
        this.runningJobs.put(future, iJob);
        Thread thread = new Thread(iJob);
        thread.setName("InactivityJob");

        this.runningThreads.put(iJob, thread);

        thread.start();
        return future;
    }

    public void shutdown() {
        this.logger.debug("Shutting down inactivity manager");

        for (InactivityJob job : this.runningJobs.values()) {
            Thread t = this.runningThreads.get(job);
            job.shutdown = true;
            t.interrupt();
        }
    }

    @Override
    public void onInactivityJobDone(InactivityFuture future) {
        this.runningThreads.remove(this.runningJobs.get(future));
        this.runningJobs.remove(future);
    }
}
