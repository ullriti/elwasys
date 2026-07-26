package org.kabieror.elwasys.raspiclient.ui.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Ein Job, welcher nach einer gegebenen Zeit der Inaktivität ausgeführt wird.
 *
 * @author Oliver Kabierschke
 */
class InactivityJob implements Runnable {

    final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final int rate;
    private final InactivityFuture future;
    private final int limit;
    private final Runnable job;
    boolean shutdown = false;
    private final List<IInactivityJobDoneListener> finishedListeners = new CopyOnWriteArrayList<>();
    private int counter = 0;

    /**
     * Erzeugt einen neuen Job, der bei Inaktivität ausgeführt wird.
     *
     * @param future   Das Anzeite-Objekt über die Aktivität des Jobs.
     * @param rate     Die Zeit zwischen zwei Wiederholungen.
     * @param timeUnit Die Zeiteinheit der Zeitangabe.
     * @param limit    Die Anzahl der Ausführungen, die gestartet werden sollen.
     */
    public InactivityJob(InactivityFuture future, int rate, TimeUnit timeUnit, int limit, Runnable job) {
        this.future = future;
        this.limit = limit;
        this.job = job;

        switch (timeUnit) {
            case NANOSECONDS:
                this.rate = rate / (1000 * 1000);
                break;
            case MICROSECONDS:
                this.rate = rate / 1000;
                break;
            case MILLISECONDS:
                this.rate = rate;
                break;
            default:
            case SECONDS:
                this.rate = rate * 1000;
                break;
            case MINUTES:
                this.rate = rate * 60000;
                break;
            case HOURS:
                this.rate = rate * 1000 * 3600;
                break;
        }
    }

    @Override
    public void run() {
        while (!this.shutdown) {
            try {
                Thread.sleep(rate);

                synchronized (this.future) {
                    if (this.shutdown || this.future.isCancelled()) {
                        // Cancel
                        logger.debug("Cancelling inactivity job " + this.getName());
                        this.future.cancel();
                        this.future.setDone();
                        break;
                    }

                    logger.trace("Executing inactivity job " + this.getName());
                    try {
                        this.job.run();
                    } catch (Exception e) {
                        logger.error("Error during execution of inactivity job" + this.getName(), e);
                    }

                    if (limit > 0 && ++counter >= limit) {
                        logger.debug(String.format("Done after %d executions. Exiting inactivity job %s", counter - 1,
                                this.getName()));
                        this.future.setDone();
                        break;
                    }
                }

            } catch (final InterruptedException ignored) {
                // Setzte Zeit bis zur nächsten Ausführung zurück, nachdem Aktivität festgestellt wurde.
            }
        }
        for (IInactivityJobDoneListener l : this.finishedListeners) {
            l.onInactivityJobDone(this.future);
        }
    }

    public void listenToFinishedEvent(IInactivityJobDoneListener l) {
        this.finishedListeners.add(l);
    }

    public String getName() {
        return (this.future.getName() != null) ? this.future.getName() : this.job.getClass().getName();
    }
}
