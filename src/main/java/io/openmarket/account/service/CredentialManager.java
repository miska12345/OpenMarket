package io.openmarket.account.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import io.openmarket.utils.TimeUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static io.openmarket.config.EnvironmentConfig.ENV_VAR_TOKEN_DURATION;

@Singleton
@Log4j2
public final class CredentialManager {
    private static final int KEY_LENGTH = 256;
    private static final Algorithm serverKey = Algorithm.HMAC256(getSecureServerKey());
    private static final String ISSUER = "OpenMarket";

    /**
     * The name for username claimable attribute.
     */
    public static final String CLAIM_USERNAME = "Username";

    /**
     * The name for userId claimable attribute.
     */
    public static final String CLAIM_USER_ID = "UserId";

    private final int tokenDuration;

    @Inject
    public CredentialManager(@Named(ENV_VAR_TOKEN_DURATION) final int tokenDuration) {
        this.tokenDuration = tokenDuration;
        log.info("CredentialManager started");
    }

    public Map<String, Claim> getClaims(@NonNull final String token) {
        try {
            final JWTVerifier verifier = JWT.require(serverKey)
                    .withIssuer(ISSUER)
                    .build();
            //if the jwt is not valid, a verification exception would be thrown
            return verifier.verify(token).getClaims();
        }catch (JWTVerificationException e) {
            log.error("Token '{}' failed to validate: {}", token, e.getMessage());
        }
        return Collections.emptyMap();
    }

    public Map<String, Claim> getClaimsUnverified(@NonNull final String token) {
        try {
            return JWT.decode(token).getClaims();
        } catch (Exception e) {
            log.error("Failed to decode token {}", token);
            return Collections.emptyMap();
        }
    }

    public String generateToken(@NonNull final String username,
                                @NonNull final String userId,
                                @NonNull final Date today) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withClaim(CLAIM_USERNAME, username)
                .withClaim(CLAIM_USER_ID, userId)
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
