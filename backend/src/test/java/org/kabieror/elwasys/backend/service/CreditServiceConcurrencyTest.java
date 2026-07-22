package org.kabieror.elwasys.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.kabieror.elwasys.backend.domain.DiscountType;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.domain.UserGroupEntity;
import org.kabieror.elwasys.backend.exception.NotEnoughCreditException;
import org.kabieror.elwasys.backend.repository.UserGroupRepository;
import org.kabieror.elwasys.backend.repository.UserRepository;
import org.kabieror.elwasys.backend.support.AbstractBackendIT;
import org.kabieror.elwasys.backend.support.Fixtures;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Nebenläufigkeitsschutz der Geldpfade (Issue #20 - AP3): das pessimistische Sperren der
 * Nutzer-Zeile ({@code UserRepository#findWithLockById}) serialisiert Guthabenprüfung und
 * Buchung, sodass parallele Auszahlungen das Guthaben nicht unter 0 drücken. Ohne die Sperre
 * läsen beide Auszahlungen denselben Ausgangsstand und buchten beide - genau die Race, die im
 * Alt-System durch den Einzelplatz-Client je Gerät strukturell unmöglich war.
 */
class CreditServiceConcurrencyTest extends AbstractBackendIT {

    @Autowired
    private UserGroupRepository userGroupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CreditService creditService;

    private UserEntity newUserWithCredit(String credit) {
        UserGroupEntity group = this.userGroupRepository.save(
                new UserGroupEntity(Fixtures.unique("group"), DiscountType.NONE, 0));
        UserEntity user = this.userRepository.save(
                new UserEntity(Fixtures.unique("User"), Fixtures.unique("user"), group));
        this.creditService.inpayment(user, new BigDecimal(credit));
        return user;
    }

    @Test
    void concurrentPayoutsNeverOverdrawTheAccount() throws Exception {
        // Guthaben reicht für GENAU eine Auszahlung von 5.00.
        UserEntity user = newUserWithCredit("5.00");
        int threads = 6;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        Callable<Void> task = () -> {
            ready.countDown();
            go.await();
            try {
                this.creditService.payout(user, new BigDecimal("5.00"));
                successes.incrementAndGet();
            } catch (NotEnoughCreditException e) {
                rejections.incrementAndGet();
            }
            return null;
        };
        Future<?>[] futures = new Future<?>[threads];
        for (int i = 0; i < threads; i++) {
            futures[i] = pool.submit(task);
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();

        // Genau eine Auszahlung darf durchgehen, alle anderen werden abgelehnt - und das
        // Guthaben bleibt bei 0.00 (nie negativ).
        assertThat(successes.get()).isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(threads - 1);
        assertThat(this.creditService.getCredit(user)).isEqualByComparingTo("0.00");
    }
}
