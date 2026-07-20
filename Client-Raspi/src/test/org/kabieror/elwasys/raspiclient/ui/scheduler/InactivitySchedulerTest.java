package org.kabieror.elwasys.raspiclient.ui.scheduler;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests für den InactivityScheduler
 *
 * @author Oliver Kabierschke
 */
public class InactivitySchedulerTest {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private AtomicInteger executionCounter = new AtomicInteger(0);

    @Test
    public void testTimeUnits() throws InterruptedException {
        InactivityScheduler sched = new InactivityScheduler();

        logger.info("Testing time unit nanoseconds");
        testTimeUnit(sched, 55, TimeUnit.NANOSECONDS, 2);
        this.executionCounter.set(0);

        logger.info("Testing time unit microseconds");
        testTimeUnit(sched, 10, TimeUnit.MICROSECONDS, 2);
        this.executionCounter.set(0);

        logger.info("Testing time unit milliseconds");
        testTimeUnit(sched, 5, TimeUnit.MILLISECONDS, 6);
        this.executionCounter.set(0);

        logger.info("Testing time unit seconds");
        testTimeUnit(sched, 1, TimeUnit.SECONDS, 1002);
        this.executionCounter.set(0);

        sched.shutdown();
    }

    private void testTimeUnit(InactivityScheduler sched, int rate, TimeUnit timeUnit, int waitMs)
            throws InterruptedException {
        InactivityFuture future = sched.scheduleJob(() -> executionCounter.incrementAndGet(), rate, timeUnit, 1);
        Thread.sleep(waitMs);
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());
        Assertions.assertEquals(1, executionCounter.get());

        sched.shutdown();
    }

    @Test
    public void testMultipleExecutions() throws InterruptedException {
        this.executionCounter.set(0);

        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future =
                sched.scheduleJob(() -> this.executionCounter.incrementAndGet(), 50, TimeUnit.MILLISECONDS, 5);
        Thread.sleep(52);
        Assertions.assertEquals(1, this.executionCounter.get());
        Assertions.assertFalse(future.isDone());
        Assertions.assertFalse(future.isCancelled());
        Thread.sleep(50);
        Assertions.assertEquals(2, this.executionCounter.get());
        Assertions.assertFalse(future.isDone());
        Assertions.assertFalse(future.isCancelled());
        Thread.sleep(50);
        Assertions.assertEquals(3, this.executionCounter.get());
        Assertions.assertFalse(future.isDone());
        Assertions.assertFalse(future.isCancelled());
        Thread.sleep(50);
        Assertions.assertEquals(4, this.executionCounter.get());
        Assertions.assertFalse(future.isDone());
        Assertions.assertFalse(future.isCancelled());
        Thread.sleep(50);
        Assertions.assertEquals(5, this.executionCounter.get());
        Assertions.assertTrue(future.isDone());
        Assertions.assertFalse(future.isCancelled());

        sched.shutdown();
    }

    @Test
    public void testActivity() throws InterruptedException {
        this.executionCounter.set(0);
        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future =
                sched.scheduleJob(() -> this.executionCounter.incrementAndGet(), 50, TimeUnit.MILLISECONDS, 1);
        Thread.sleep(40);
        sched.onActivityDetected();
        Thread.sleep(20);
        Assertions.assertFalse(future.isDone());
        Assertions.assertEquals(0, this.executionCounter.get());
        sched.onActivityDetected();
        Thread.sleep(45);
        Assertions.assertFalse(future.isDone());
        Assertions.assertEquals(0, this.executionCounter.get());
        Thread.sleep(10);
        Assertions.assertTrue(future.isDone());
        Assertions.assertEquals(1, this.executionCounter.get());

        sched.shutdown();
    }

    @Test
    public void testCancel() throws InterruptedException {
        this.executionCounter.set(0);
        InactivityScheduler sched = new InactivityScheduler();
        InactivityFuture future =
                sched.scheduleJob(() -> this.executionCounter.incrementAndGet(), 50, TimeUnit.MILLISECONDS, 1);
        Thread.sleep(40);
        future.cancel();
        Thread.sleep(20);
        Assertions.assertEquals(0, this.executionCounter.get());
        Assertions.assertTrue(future.isDone());
        Assertions.assertTrue(future.isCancelled());

        sched.shutdown();
    }
}
