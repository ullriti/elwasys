package org.kabieror.elwasys.backend.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import com.vaadin.flow.component.HasElement;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Der Ansichtsname im Kopfbalken (UI-Redesign v2 AP1, siehe {@link NavbarViewName}). Reiner
 * Unit-Test der Ableitung aus der {@code @PageTitle}-Annotation des Navigationsziels - die
 * Komponente selbst bräuchte eine laufende Navigation, die Namensableitung nicht.
 *
 * <p>Warum geprüft: der Name ist eine reine Ableitung aus vorhandenen Annotationen. Fällt der
 * Namenszusatz "{@code - Waschportal}" nicht ab, stünde der Produktname im Kopfbalken zweimal
 * (links das Logo, rechts noch einmal) - ein Fehler, den nur ein Blick auf jede einzelne Seite
 * fände.
 */
class NavbarViewNameTest {

    /** Eine geroutete View, wie sie das Portal schreibt: "&lt;Ansichtsname&gt; - Waschportal". */
    @PageTitle("Benutzer - Waschportal")
    private static class UsersLikeView extends Div {
    }

    /** Erbt ihren Titel - {@code @PageTitle} ist nicht {@code @Inherited}, siehe pageTitleOf. */
    private static class InheritingView extends UsersLikeView {
    }

    /** Eine Komponente ohne Titel - z. B. ein Fehler-/Umleitungsziel. */
    private static class UntitledView extends Div {
    }

    @Test
    void theProductSuffixIsStrippedFromThePageTitle() {
        assertThat(NavbarViewName.viewNameOf(List.<HasElement>of(new UsersLikeView()))).isEqualTo("Benutzer");
    }

    @Test
    void onlyTheNavigationTargetDecidesTheName() {
        // Das erste Element der aktiven Kette ist das Navigationsziel, danach folgen seine
        // Layouts - deren eigene Titel dürfen den Ansichtsnamen nicht überschreiben.
        List<HasElement> chain = List.of(new UsersLikeView(), new UntitledView());

        assertThat(NavbarViewName.viewNameOf(chain)).isEqualTo("Benutzer");
    }

    @Test
    void aTitleInheritedFromASuperclassCountsToo() {
        assertThat(NavbarViewName.viewNameOf(List.<HasElement>of(new InheritingView()))).isEqualTo("Benutzer");
    }

    @Test
    void aViewWithoutAPageTitleLeavesTheHeaderWithoutAViewName() {
        assertThat(NavbarViewName.viewNameOf(List.<HasElement>of(new UntitledView()))).isEmpty();
    }

    @Test
    void anEmptyChainLeavesTheHeaderWithoutAViewName() {
        assertThat(NavbarViewName.viewNameOf(List.of())).isEmpty();
    }
}
