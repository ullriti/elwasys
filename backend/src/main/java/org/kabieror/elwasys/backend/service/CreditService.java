package org.kabieror.elwasys.backend.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.kabieror.elwasys.backend.domain.CreditAccountingEntryEntity;
import org.kabieror.elwasys.backend.domain.ExecutionEntity;
import org.kabieror.elwasys.backend.domain.ProgramEntity;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.kabieror.elwasys.backend.events.CreditChangedEvent;
import org.kabieror.elwasys.backend.exception.NotEnoughCreditException;
import org.kabieror.elwasys.backend.repository.CreditAccountingEntryRepository;
import org.kabieror.elwasys.backend.repository.ExecutionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1:1-Portierung der Guthaben-/Abrechnungslogik aus
 * {@code org.kabieror.elwasys.common.User} (Methoden {@code loadCredit}, {@code
 * payExecution}, {@code inpayment}, {@code payout}) - siehe docs/kb/05-migration-plan.md, AP2.
 * Buchungen ({@link CreditAccountingEntryEntity}) sind unveränderlich: dieser Service
 * fügt ausschließlich neue Einträge hinzu, nie Änderungen an bestehenden.
 */
@Service
public class CreditService {

    private final CreditAccountingEntryRepository creditAccountingEntryRepository;
    private final ExecutionRepository executionRepository;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;

    public CreditService(CreditAccountingEntryRepository creditAccountingEntryRepository,
            ExecutionRepository executionRepository, PricingService pricingService,
            ApplicationEventPublisher eventPublisher) {
        this.creditAccountingEntryRepository = creditAccountingEntryRepository;
        this.executionRepository = executionRepository;
        this.pricingService = pricingService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 1:1-Portierung von {@code User#loadCredit} (dort: privat + im Konstruktor/bei jedem
     * {@code update()} aufgerufen und zwischengespeichert; hier bewusst bei jedem Aufruf
     * neu berechnet statt gecacht - siehe docs/kb/05-migration-plan.md, "Entscheidungen": der
     * 5-Sekunden-Cache des Alt-Codes ({@code DataManager.UPDATE_DELAY}) ist reine
     * DB-Lastreduktion des Einzelplatz-Clients, kein fachliches Verhalten, das das Backend
     * nachbilden müsste - im Gegenteil würde ein serverseitiger Cache dieser Art in einem
     * Mehrbenutzer-Backend zu falschen/veralteten Guthabenständen führen).
     *
     * <p>Summiert alle Buchungen des Benutzers und zieht davon den Maximalpreis (Preis bei
     * {@code program.getMaxDuration()}) jeder noch nicht abgeschlossenen Ausführung ab -
     * UNABHÄNGIG davon, ob diese bereits gestartet wurde (siehe
     * {@link org.kabieror.elwasys.backend.repository.ExecutionRepository#findByUser_IdAndFinishedFalse(Integer)}).
     */
    @Transactional(readOnly = true)
    public BigDecimal getCredit(UserEntity user) {
        BigDecimal credit = this.creditAccountingEntryRepository.sumAmountByUserId(user.getId());
        if (credit == null) {
            credit = new BigDecimal("0.00");
        }

        List<ExecutionEntity> unfinished = this.executionRepository.findByUser_IdAndFinishedFalse(user.getId());
        for (ExecutionEntity execution : unfinished) {
            ProgramEntity program = execution.getProgram();
            if (program == null) {
                // Defensiv, wie im Alt-Code (dort wird eine Ausführung ohne gültiges Programm
                // geloggt und übersprungen). In der Praxis durch die FK-Constraints der DB
                // nicht erreichbar, siehe docs/kb/05-migration-plan.md ("Beobachtungen").
                continue;
            }
            Duration maxDuration = Duration.ofSeconds(program.getMaxDurationSeconds());
            BigDecimal price = this.pricingService.getPrice(program, maxDuration, user);
            credit = credit.subtract(price);
        }
        return credit;
    }

    /**
     * 1:1-Portierung von {@code User#canAfford}.
     */
    public boolean canAfford(UserEntity user, BigDecimal price) {
        return getCredit(user).compareTo(price) >= 0;
    }

    /**
     * 1:1-Portierung von {@code User#payExecution}. Der Preis wird von
     * {@code ExecutionService} übergeben (dort berechnet {@code getPrice(ExecutionEntity)}
     * 1:1 wie {@code Execution#getPrice()} - siehe dort für den Aufrufzeitpunkt, i.d.R.
     * unmittelbar nach dem Setzen von {@code stop}/{@code finished}).
     *
     * <p>Beobachtung (siehe docs/kb/05-migration-plan.md): der Alt-Code prüft
     * {@code e.getPrice().equals(BigDecimal.ZERO)} - ein {@link BigDecimal#equals} statt
     * {@link BigDecimal#compareTo}, das ZUSÄTZLICH zum Wert auch die Skala vergleicht. Ein
     * FIXED-Programm mit einer in der DB als {@code 0.00} (Skala 2) gepflegten
     * Grundgebühr erzeugt daher TROTZDEM einen Buchungssatz über {@code 0.00} (die
     * NUMERIC-Spalte behält ihre Skala), während ein durch die Freiminuten-Regel auf
     * {@code BigDecimal.ZERO} (Skala 0) reduzierter Preis KEINEN Buchungssatz erzeugt.
     * Dieses Detail wird hier bewusst exakt übernommen, nicht durch {@code compareTo}
     * "korrigiert".
     */
    @Transactional
    public void payExecution(ExecutionEntity execution, BigDecimal price) {
        UserEntity user = execution.getUser();
        if (user.getId() == null || user.getId() >= 0) {
            if (price.equals(BigDecimal.ZERO)) {
                // A free execution has not to be payed.
                return;
            }
            String description = execution.getProgram().getName() + " auf " + execution.getDevice().getName() + " ("
                    + execution.getDevice().getLocation().getName() + ") bezahlt von " + user.getName() + ".";
            this.creditAccountingEntryRepository.save(
                    new CreditAccountingEntryEntity(user, execution, price.negate(), LocalDateTime.now(),
                            description));
            this.eventPublisher.publishEvent(new CreditChangedEvent(user.getId()));
        }
    }

    /**
     * 1:1-Portierung von {@code User#inpayment(BigDecimal, String)}.
     */
    @Transactional
    public CreditAccountingEntryEntity inpayment(UserEntity user, BigDecimal amount, String text) {
        CreditAccountingEntryEntity entry = this.creditAccountingEntryRepository.save(
                new CreditAccountingEntryEntity(user, null, amount, LocalDateTime.now(), text));
        this.eventPublisher.publishEvent(new CreditChangedEvent(user.getId()));
        return entry;
    }

    /**
     * 1:1-Portierung von {@code User#inpayment(BigDecimal)} (Default-Buchungstext).
     */
    @Transactional
    public CreditAccountingEntryEntity inpayment(UserEntity user, BigDecimal amount) {
        return inpayment(user, amount, "Inpayment from Washportal");
    }

    /**
     * 1:1-Portierung von {@code User#payout(BigDecimal, String)}.
     *
     * @throws NotEnoughCreditException wenn das Guthaben des Benutzers den Betrag
     *                                  unterschreitet
     */
    @Transactional
    public CreditAccountingEntryEntity payout(UserEntity user, BigDecimal amount, String text) {
        if (getCredit(user).compareTo(amount) < 0) {
            throw new NotEnoughCreditException();
        }
        CreditAccountingEntryEntity entry = this.creditAccountingEntryRepository.save(
                new CreditAccountingEntryEntity(user, null, amount.negate(), LocalDateTime.now(), text));
        this.eventPublisher.publishEvent(new CreditChangedEvent(user.getId()));
        return entry;
    }

    /**
     * 1:1-Portierung von {@code User#payout(BigDecimal)} (Default-Buchungstext).
     */
    @Transactional
    public CreditAccountingEntryEntity payout(UserEntity user, BigDecimal amount) {
        return payout(user, amount, "Payout from Washportal");
    }

    /**
     * Entspricht {@code DataManager#getAccountingEntries(User)} - die vollständige, nie
     * veränderte Buchungshistorie eines Benutzers, neueste zuerst. Fachlicher Nachfolger von
     * {@code Portal/.../components/CreditAccountingWindow} (Alt-Portal, "Umsätze ansehen")
     * sowie des Buchungsteils von {@code Portal/.../views/UsersDashboardView} (Testfall P15,
     * Phase 3 AP3, siehe docs/kb/05-migration-plan.md). Liest nur - Buchungen werden hier wie
     * überall in diesem Service niemals verändert.
     */
    @Transactional(readOnly = true)
    public List<CreditAccountingEntryEntity> getAccountingEntries(UserEntity user) {
        return this.creditAccountingEntryRepository.findByUser_IdOrderByDateDesc(user.getId());
    }

    /**
     * 1:1-Portierung von {@code DataManager#getLastInpayment(User)} - die letzte positive
     * Buchung (Einzahlung) eines Benutzers, für die "Letzte Einzahlung"-Kachel des
     * Benutzer-Dashboards ({@code Portal/.../views/UsersDashboardView}, Phase 3 AP3).
     */
    @Transactional(readOnly = true)
    public Optional<CreditAccountingEntryEntity> getLastInpayment(UserEntity user) {
        return this.creditAccountingEntryRepository.findFirstByUser_IdAndAmountGreaterThanOrderByDateDesc(
                user.getId(), BigDecimal.ZERO);
    }
}
