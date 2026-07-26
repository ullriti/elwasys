package org.kabieror.elwasys.raspiclient.ui.scheduler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Tests für den InactivityScheduler
 *
 * @author Oliver Kabierschke
 *
 * <p>Wartestrategie (Issue #88, finale Review R6): Die Testfälle warteten früher exakt eine
 * Periode plus 1-2 ms und prüften dann sofort - auf einem ausgelasteten CI-Läufer reicht das
 * nicht, der Scheduler arbeitet mit echten Threads und {@code Thread.sleep}. Deshalb gilt jetzt:
 * Ein <em>erwartetes</em> Ereignis wird über {@link #awaitUntil} auf die BEDINGUNG gewartet
 * (großzügiges Zeitlimit, schnelles Ende im Normalfall); ein <em>noch nicht</em> erwartetes
 * Ereignis wird gegen eine deutlich längere Periode geprüft, sodass die Marge nicht mehr im
 * Millisekundenbereich liegt. Eine injizierte Uhr wäre die reinere Lösung, hieße aber, das
 * {@code Thread.sleep} im Produktivcode ({@link InactivityJob#run()}) durch eine Zeitabstraktion
 * zu ersetzen - eine Verhaltensänderung am Terminal-Kern allein zugunsten des Tests.
 */
public class InactivitySchedulerTest {

    /**
     * Zeitlimit für erwartete Ereignisse. Weit über der jeweiligen Periode, damit ein
     * Scheduling-Aussetzer den Test nicht rot macht; im Normalfall wird es nie ausgeschöpft.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicInteger executionCounter = new AtomicInteger(0);

    @Test
    public void testTimeUnits() throws InterruptedException {
        logger.info("Testing time unit nanoseconds");
        testTimeUnit(55, TimeUnit.NANOSECONDS);
        this.executionCounter.set(0);

        logger.info("Testing time unit microseconds");
        testTimeUnit(10, TimeUnit.MICROSECONDS);
        this.executionCounter.set(0);

        logger.info("Testing time unit milliseconds");
        testTimeUnit(5, TimeUnit.MILLISECONDS);
        this.executionCounter.set(0);

        logger.info("Testing time unit seconds");
        testTimeUnit(1, TimeUnit.SECONDS);
        this.executionCounter.set(0);
    }

    private void testTimeUnit(int rate, TimeUnit timeUnit) throws InterruptedException {
        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future = sched.scheduleJob(this.executionCounter::incrementAndGet, rate, timeUnit, 1);

        Assertions.assertTrue(awaitUntil(future::isDone),
                "Der Auftrag sollte in der Zeiteinheit " + timeUnit + " ausgeführt worden sein");
        Assertions.assertFalse(future.isCancelled());
        Assertions.assertEquals(1, executionCounter.get());

        sched.shutdown();
    }

    @Test
    public void testMultipleExecutions() throws InterruptedException {
        this.executionCounter.set(0);

        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future =
                sched.scheduleJob(this.executionCounter::incrementAndGet, 50, TimeUnit.MILLISECONDS, 5);

        for (int expected = 1; expected < 5; expected++) {
            final int target = expected;
            Assertions.assertTrue(awaitUntil(() -> this.executionCounter.get() >= target),
                    "Ausführung " + target + " von 5 sollte stattgefunden haben");
            Assertions.assertFalse(future.isCancelled());
        }
        Assertions.assertTrue(awaitUntil(future::isDone), "Nach 5 Ausführungen sollte der Auftrag fertig sein");
        Assertions.assertEquals(5, this.executionCounter.get(), "Das Ausführungslimit darf nicht überschritten werden");
        Assertions.assertFalse(future.isCancelled());

        sched.shutdown();
    }

    @Test
    public void testActivity() throws InterruptedException {
        this.executionCounter.set(0);
        // Bewusst 500 ms statt der früheren 50 ms: die Prüfung "läuft nach erkannter Aktivität
        // NOCH nicht" braucht Abstand zur Periode, sonst entscheidet die Maschinenlast.
        final int period = 500;
        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future =
                sched.scheduleJob(this.executionCounter::incrementAndGet, period, TimeUnit.MILLISECONDS, 1);

        Thread.sleep(period / 2);
        sched.onActivityDetected();
        // Kurz nach der Aktivität: die Wartezeit muss von vorn laufen, also darf noch nichts
        // ausgeführt sein - obwohl ohne die Aktivität nur noch die halbe Periode gefehlt hätte.
        Thread.sleep(period / 2);
        Assertions.assertFalse(future.isDone(), "Erkannte Aktivität muss die Wartezeit zurücksetzen");
        Assertions.assertEquals(0, this.executionCounter.get());

        Assertions.assertTrue(awaitUntil(future::isDone), "Ohne weitere Aktivität sollte der Auftrag laufen");
        Assertions.assertEquals(1, this.executionCounter.get());

        sched.shutdown();
    }

    @Test
    public void testCancel() throws InterruptedException {
        this.executionCounter.set(0);
        final int period = 500;
        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future =
                sched.scheduleJob(this.executionCounter::incrementAndGet, period, TimeUnit.MILLISECONDS, 1);

        Thread.sleep(period / 2);
        future.cancel();

        Assertions.assertTrue(awaitUntil(future::isDone), "Ein abgebrochener Auftrag sollte fertig gemeldet werden");
        Assertions.assertTrue(future.isCancelled());
        Assertions.assertEquals(0, this.executionCounter.get(), "Ein Abbruch darf den Auftrag nicht mehr ausführen");

        sched.shutdown();
    }

    /**
     * Wartet, bis die Bedingung zutrifft (oder {@link #TIMEOUT} abläuft).
     */
    private static boolean awaitUntil(BooleanSupplier condition) throws InterruptedException {
        final Instant deadline = Instant.now().plus(TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(5);
        }
        return condition.getAsBoolean();
    }
}
