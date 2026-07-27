package org.kabieror.elwasys.backend.ui.component;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.PageTitle;
import java.util.List;

/**
 * Name der gerade angezeigten Ansicht im Kopfbalken, rechts vom Produktnamen "Waschportal"
 * (UI-Redesign v2 AP1, siehe docs/specs/0002-ui-design/v2/MAPPING.md, Abschnitt "Rahmen") - im
 * Alt-Portal und in der v1-Fassung des Flow-Portals gab es diese Orientierungshilfe nicht.
 * Wird von {@link org.kabieror.elwasys.backend.ui.admin.AdminLayout} und
 * {@link org.kabieror.elwasys.backend.ui.user.UserLayout} in die Navbar gehängt.
 *
 * <p><b>Warum {@link AfterNavigationObserver} und nicht {@code HasDynamicTitle}:</b> alle
 * gerouteten Views tragen bereits eine {@link PageTitle}-Annotation der Form
 * "&lt;Ansichtsname&gt; - Waschportal" (siehe {@code AdminDashboardView} &amp; Co.) - der
 * Ansichtsname steht also schon fest und muss nirgends dupliziert werden.
 * {@code HasDynamicTitle} wäre der falsche Hebel: Vaadin wertet es ausschließlich am
 * Navigationsziel selbst aus (nicht an dessen Layouts), es würde also die vorhandenen
 * {@code @PageTitle}-Annotationen der Views ERSETZEN statt sie zu lesen, und dafür müsste jede
 * einzelne View angefasst werden. {@code AfterNavigationObserver} wird dagegen für jede
 * Komponente im aktiven Baum ausgelöst (siehe {@code EventUtil#collectAfterNavigationObservers}),
 * also auch für diese Komponente im Navbar-Slot des Layouts, und liefert über
 * {@link AfterNavigationEvent#getActiveChain()} das Navigationsziel mit.
 */
public class NavbarViewName extends Span implements AfterNavigationObserver {

    /**
     * Gemeinsamer Namenszusatz aller {@code @PageTitle}-Werte des Portals. Er benennt das
     * Produkt, das im Kopfbalken links daneben ohnehin schon steht - im Ansichtsnamen wäre er
     * doppelt.
     */
    private static final String TITLE_SUFFIX = " - Waschportal";

    public NavbarViewName() {
        addClassName("navbar-view-name");
    }

    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        setText(viewNameOf(event.getActiveChain()));
    }

    /**
     * Der anzuzeigende Ansichtsname zu einer aktiven Navigationskette. Nimmt die Kette statt des
     * {@link AfterNavigationEvent} entgegen, damit die Ableitung des Namens ohne laufende
     * Anwendung prüfbar ist (dasselbe Muster wie {@code AbstractFormDialog#failureText}): ein
     * {@code AfterNavigationEvent} lässt sich nur mit einem echten {@code Router} bauen (es ist
     * ein {@link java.util.EventObject}, dessen Quelle nicht {@code null} sein darf) - die Kette
     * ist ohnehin das Einzige, was diese Methode aus dem Ereignis liest.
     */
    static String viewNameOf(List<HasElement> activeChain) {
        if (activeChain.isEmpty()) {
            return "";
        }
        // Das erste Element der aktiven Kette ist das Navigationsziel selbst, danach folgen
        // seine Layouts (siehe AfterNavigationEvent#getActiveChain).
        String title = pageTitleOf(activeChain.get(0).getClass());
        return title.endsWith(TITLE_SUFFIX) ? title.substring(0, title.length() - TITLE_SUFFIX.length()) : title;
    }

    /**
     * Liest den {@code @PageTitle}-Wert einer View. Die Annotation ist nicht
     * {@code @Inherited}, die Klassenhierarchie wird deshalb selbst abgelaufen - so trägt auch
     * eine View, die den Titel von einer Basisklasse erbt (oder als Proxy vorliegt), den
     * richtigen Namen. Ohne Annotation bleibt der Kopfbalken schlicht ohne Ansichtsnamen.
     */
    private static String pageTitleOf(Class<?> viewClass) {
        for (Class<?> current = viewClass; current != null; current = current.getSuperclass()) {
            PageTitle pageTitle = current.getAnnotation(PageTitle.class);
            if (pageTitle != null) {
                return pageTitle.value();
            }
        }
        return "";
    }
}
