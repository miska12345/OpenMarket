package io.openmarket.account.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import io.openmarket.utils.TimeUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Date;

import static io.openmarket.config.EnvironmentConfig.ENV_VAR_RPC_USE_VALIDATION;
import static io.openmarket.config.EnvironmentConfig.ENV_VAR_TOKEN_DURATION;

@Log4j2
public final class CredentialManager {
    private static final int KEY_LENGTH = 256;
    private static final Algorithm serverKey = Algorithm.HMAC256(getSecureServerKey());
    private static final String ISSUER = "OpenMarket";
    private static final String CLAIM_USERNAME = "Username";

    private final boolean isEnabled;
    private final int tokenDuration;

    @Inject
    public CredentialManager(@Named(ENV_VAR_RPC_USE_VALIDATION) final boolean enableValidation,
                             @Named(ENV_VAR_TOKEN_DURATION) final int tokenDuration) {
        this.isEnabled = enableValidation;
        this.tokenDuration = tokenDuration;
        log.info("CredentialManager started");
    }

    public boolean verifyToken(@NonNull final String token, @NonNull final String username) {
        if (!isEnabled)
            return true;

        try {
            final JWTVerifier verifier = JWT.require(serverKey)
                    .withIssuer(ISSUER)
                    .withClaim(CLAIM_USERNAME, username)
                    .build();
            //if the jwt is not valid, a verification exception would be thrown
            verifier.verify(token);
        }catch (JWTVerificationException e) {
            log.error("Token '{}' failed to validate with username '{}': {}", token, username, e.getMessage());
            return false;
        }
        return true;
    }

    public String generateToken(@NonNull final String username, @NonNull final Date today) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withClaim(CLAIM_USERNAME, username)
                .withIssuedAt(today)
                .withExpiresAt(TimeUtils.getDateAfter(today, tokenDuration))
                .sign(serverKey);
    }

    public static byte[] getSecureServerKey() {
        final byte[] bytes = new byte[KEY_LENGTH];
        try {
            SecureRandom.getInstanceStrong().nextBytes(bytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate random key.", e);
        }
        return bytes;
    }
}
