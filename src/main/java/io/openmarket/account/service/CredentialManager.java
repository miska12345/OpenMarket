package io.openmarket.account.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.AccessLevel;
import lombok.Getter;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Date;

import static io.openmarket.config.EnvironmentConfig.ENV_VAR_RPC_USE_VALIDATION;

public final class CredentialManager {
    @Getter(AccessLevel.PROTECTED)
    private static final Algorithm serverKey = Algorithm.HMAC256(String.valueOf(System.currentTimeMillis()));

    private final boolean isEnabled;

    @Inject
    public CredentialManager(@Named(ENV_VAR_RPC_USE_VALIDATION) boolean enableValidation) {
        this.isEnabled = enableValidation;
    }

    public boolean verifyToken(String token, String username) {
        if (!isEnabled)
            return true;

        try {
            JWTVerifier verifier = JWT.require(serverKey).withIssuer(username).build();
            //if the jwt is not valid, a verification exception would be thrown
            DecodedJWT jwt = verifier.verify(token);
        }catch (JWTVerificationException e) {
            return false;
        }
        return true;
    }

    public String generateToken(String username) {
        return JWT.create().withIssuer(username).withIssuedAt(new Date()).sign(serverKey);
    }


}
