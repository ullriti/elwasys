package org.kabieror.elwasys.webportal;

import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Title;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.Responsive;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.UI;
import org.kabieror.elwasys.common.User;
import org.kabieror.elwasys.webportal.SessionManager.AuthorizedType;
import org.kabieror.elwasys.webportal.components.ResetPasswordWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.annotation.WebServlet;
import java.sql.SQLException;

@SuppressWarnings("serial")
@Theme("waschportal")
@Title("Waschportal")
public class WaschportalUI extends UI {

    protected final static String VIEW_LOGIN = "login";
    protected final static String VIEW_START = "start";
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    protected void init(VaadinRequest request) {
        /**
         * Initiate WashportalManager
         */
        WashportalManager.instance.initIfNecessary();
        this.setLocale(request.getLocale());

        Responsive.makeResponsive(this);

        this.loadSessionContent();

        // Passwort-Reset
        final String resetParam = request.getParameter("rp");
        if (resetParam != null) {
            User user = null;
            try {
                user = WashportalManager.instance.getDataManager()
                        .getUserByPasswordResetKey(resetParam);
            } catch (final SQLException e) {
                WashportalManager.instance.showDatabaseError(e);
            }
            if (user == null) {
                WashportalManager.instance.showError("Fehler",
                        "Der Schlüssel zum Zurücksetzen des Passworts ist ungültig. Bitte neuen erzeugen.");
            } else {
                try {
                    UI.getCurrent().addWindow(new ResetPasswordWindow(user));
                } catch (IllegalArgumentException | NullPointerException e) {
                    this.logger.error("Could not show the password reset window.", e);
                    WashportalManager.instance.showError("Interner Fehler",
                            e.getLocalizedMessage());
                } catch (final SQLException e) {
                    this.logger.error("Could not show the password reset window.", e);
                    WashportalManager.instance.showDatabaseError(e);
                }
            }
        }
    }

    public void loadSessionContent() {
        final AuthorizedType at =
                WashportalManager.instance.getSessionManager().getAuthorizedType();
        if (at == AuthorizedType.ADMINISTRATOR) {
            this.setContent(new AdministratorLayout(this));
        } else if (at == AuthorizedType.USER) {
            this.setContent(new UserLayout(this));
        } else {
            this.setContent(new PublicLayout(this));
        }
    }

    @WebServlet(value = "/*",
            asyncSupported = true)
    @VaadinServletConfiguration(widgetset = "com.vaadin.DefaultWidgetSet",
            productionMode = false,
            ui = WaschportalUI.class)
    public static class Servlet extends VaadinServlet {

    }

}