package com.rsinelli.gymtracker.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordHasher {

    public String hash(String plainPassword) {
        return BcryptUtil.bcryptHash(plainPassword);
    }

    public boolean matches(String plainPassword, String hash) {
        return BcryptUtil.matches(plainPassword, hash);
    }
}
