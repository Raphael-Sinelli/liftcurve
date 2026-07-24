package com.rsinelli.gymtracker.security;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@RequestScoped
public class CurrentUser {

    @Inject
    JsonWebToken jwt;

    public UUID getId() {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new IllegalStateException(
                    "CurrentUser.getId() called outside an authenticated request — is @Authenticated missing on the endpoint?");
        }
        return UUID.fromString(subject);
    }
}
