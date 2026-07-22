package org.kabieror.elwasys.backend.ui.push;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.shared.Registration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.kabieror.elwasys.backend.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Verteilt {@link DomainEvent}s an alle offenen Vaadin-Sessions (Phase 3 AP5, siehe
 * docs/kb/05-migration-plan.md, Roadmap-Punkt "Live-Updates zwischen Sessions") - fachlicher
 * Nachfolger der {@code Portal/.../events/I*UpdatedEventListener}-Interfaces des Alt-Portals.
 *
 * <p><b>Befund zum Alt-Portal</b> (siehe docs/kb/03-modules.md, Abschnitt "Live-Updates zwischen
 * Sessions (AP5)"): trotz der Komponenten-Inventur-Zeile "Vaadin-Push" und einer
 * {@code vaadin-push}-Abhängigkeit im Alt-{@code pom.xml} setzt das Alt-Portal
 * {@code @Push} NIRGENDS ein und die {@code events/I*UpdatedEventListener}-Interfaces sind
 * reine SAME-SESSION-Callbacks: ein Dialog (z.B. {@code DeviceWindow}) ruft nach dem Speichern
 * synchron den Listener seines eigenen aufrufenden Fensters auf (z.B.
 * {@code AdminDashboardLocationPanel#onLocationUpdated}) - beides läuft im selben
 * {@code VaadinSession}/UI-Thread, es gibt dort keinen Mechanismus, der eine ANDERE offene
 * Browser-Session benachrichtigt. Diese Klasse liefert das tatsächlich: echte
 * Cross-Session-Verteilung über Vaadins {@code @Push} (siehe {@code ElwasysAppShell}) + dieses
 * klassische Broadcaster-Muster (statischer/Singleton-Verteiler, Registrierung mit der
 * jeweiligen {@link UI}, Zustellung über {@link UI#access(com.vaadin.flow.server.Command)}).
 *
 * <p><b>Ereignisquelle</b>: die Fachlogik-Services (siehe {@code service}-Paket), NICHT die UI -
 * publizieren nach jeder erfolgreichen Änderung ein {@link DomainEvent} über Springs
 * {@link org.springframework.context.ApplicationEventPublisher}. Das gilt unabhängig davon, ob
 * die Änderung vom Portal-UI oder - über die REST-API - von einem Terminal ausgelöst wurde
 * (siehe {@link org.kabieror.elwasys.backend.api.ExecutionController}, das
 * {@code ExecutionService}/{@code CreditService} genau wie das Portal-UI aufruft). Diese Klasse
 * hört über {@link TransactionalEventListener} zu (Default-Phase {@code AFTER_COMMIT}): ein
 * Ereignis wird erst verteilt, wenn die auslösende Transaktion tatsächlich committet wurde - ein
 * wegen einer Exception zurückgerollter Aufruf (z.B. {@code DuplicateCardIdException}) löst also
 * keinen Push aus. {@code fallbackExecution=true} deckt defensiv den Fall ab, dass ein Ereignis
 * außerhalb einer laufenden Transaktion publiziert wird - ohne diese Option würde ein solches
 * Ereignis sonst stillschweigend verworfen.
 *
 * <p><b>Lifecycle</b>: Views registrieren sich in {@code onAttach} mit ihrer {@link UI} und
 * melden sich in {@code onDetach} wieder ab (siehe z.B. {@code AdminDashboardView}) - sonst
 * bliebe die UI dauerhaft (bis Session-Ablauf) referenziert (Session-Leak).
 */
@Component
public class UiBroadcaster {

    private static final Logger LOGGER = LoggerFactory.getLogger(UiBroadcaster.class);

    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    /**
     * Registriert einen Listener für die gegebene UI. Der Listener wird für JEDES publizierte
     * {@link DomainEvent} aufgerufen (auch für Typen, die den Aufrufer nicht interessieren) -
     * der Aufrufer filtert selbst (typischerweise per {@code instanceof}/Pattern-Matching), wie
     * es die {@code ui}-Views tun. Der Aufruf erfolgt über
     * {@link UI#access(com.vaadin.flow.server.Command)}, damit er unabhängig vom auslösenden
     * (Nicht-UI-)Thread sicher auf den Session-/UI-Zustand zugreifen darf.
     *
     * @return eine {@link Registration}, deren {@code remove()} die Abmeldung durchführt - MUSS
     *         in {@code onDetach} der registrierenden Komponente aufgerufen werden, sonst bleibt
     *         die UI dauerhaft referenziert (Session-Leak, siehe Klassen-Javadoc).
     */
    public Registration register(UI ui, Consumer<DomainEvent> listener) {
        Subscription subscription = new Subscription(ui, listener);
        this.subscriptions.add(subscription);
        return () -> this.subscriptions.remove(subscription);
    }

    /**
     * Aktuelle Anzahl registrierter Listener - für Tests/Diagnose (z.B. um ein Session-Leak
     * nach {@code onDetach} auszuschließen).
     */
    public int subscriberCount() {
        return this.subscriptions.size();
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onDomainEvent(DomainEvent event) {
        for (Subscription subscription : this.subscriptions) {
            try {
                subscription.ui().access(() -> subscription.listener().accept(event));
            } catch (RuntimeException e) {
                // Die UI kann zwischen dem Kopieren der Subscriptions-Liste (CopyOnWriteArrayList)
                // und diesem Aufruf bereits geschlossen worden sein (Browser-Tab zu,
                // Session-Timeout) - kein fachlicher Fehler, nur best-effort-Zustellung.
                LOGGER.debug("Konnte Domain-Event {} nicht an eine UI zustellen (vermutlich bereits geschlossen).",
                        event, e);
            }
        }
    }

    private record Subscription(UI ui, Consumer<DomainEvent> listener) {
    }
}
