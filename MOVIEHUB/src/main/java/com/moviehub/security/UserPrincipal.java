package com.moviehub.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.moviehub.user.Role;
import com.moviehub.user.User;

/**
 * The authenticated user as seen by Spring Security. Inject it in controllers with
 * {@code @AuthenticationPrincipal UserPrincipal principal}.
 *
 * <p>{@code passwordHash} is only set when loaded from the database during login;
 * principals rebuilt from a JWT carry no password.
 */
public record UserPrincipal(Long id, String email, Role role, String passwordHash, boolean active)
        implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole(), user.getPasswordHash(), user.isActive());
    }

    public static UserPrincipal fromToken(Long id, String email, Role role) {
        return new UserPrincipal(id, email, role, null, true);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
