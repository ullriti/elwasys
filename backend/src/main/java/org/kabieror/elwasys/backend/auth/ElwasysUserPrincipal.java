package org.kabieror.elwasys.backend.auth;

import java.util.List;
import org.kabieror.elwasys.backend.domain.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Spring-Security-Principal eines erfolgreich authentifizierten elwasys-Benutzers (siehe
 * {@link ElwasysAuthenticationProvider}). Trägt bewusst keinen echten Passwort-Hash
 * ({@link #getPassword()} liefert immer einen leeren String) - das Passwort wird nur genau
 * einmal beim Login geprüft, nie im {@code SecurityContext}/in der Session gehalten.
 *
 * <p>Die Konten-Flags ({@link #isEnabled()} etc.) sind konstant {@code true}: gelöschte und
 * gesperrte Benutzer werden bereits vom {@link ElwasysAuthenticationProvider} VOR der
 * Erzeugung dieses Principals abgewiesen (siehe dessen Javadoc für die exakten Regeln),
 * dieser Typ repräsentiert daher nur bereits geprüfte, anmeldbare Benutzer.
 */
public final class ElwasysUserPrincipal implements UserDetails {

    private final Integer userId;

    private final String username;

    private final String name;

    private final boolean admin;

    public ElwasysUserPrincipal(UserEntity user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.name = user.getName();
        this.admin = user.isAdmin();
    }

    /**
     * Die ID des Benutzers in der Bestandstabelle {@code users} - für spätere
     * Arbeitspakete (REST-API/Vaadin-Portal), um vom Principal aus die volle
     * {@link UserEntity} nachzuladen, ohne die Entity selbst über die Session zu halten.
     */
    public Integer getUserId() {
        return this.userId;
    }

    public String getName() {
        return this.name;
    }

    public boolean isAdmin() {
        return this.admin;
    }

    @Override
    public List<GrantedAuthority> getAuthorities() {
        return this.admin ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
