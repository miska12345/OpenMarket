package io.openmarket.account.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.openmarket.utils.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class CredentialManagerTest {
    private static final String USERNAME = "user";
    private static final String USERID = "123";
    private static final int TOKEN_DURATION = 24;
    private CredentialManager credentialManager;

    @BeforeEach
    public void setup() {
        credentialManager = new CredentialManager(TOKEN_DURATION);
    }

    @Test
    public void can_GenerateToken() {
        Date today = new Date();
        String token = credentialManager.generateToken(USERNAME, USERID, today);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    public void can_Get_Claims() {
        Date today = new Date();
        String token = credentialManager.generateToken(USERNAME, USERID, today);
        assertNotNull(token);
        assertEquals(USERNAME, credentialManager.getClaims(token).get(CredentialManager.CLAIM_USERNAME).asString());
    }

    @Test
    public void cannot_Claim_Expired_Token() {
        Date today = TimeUtils.parseDate("2020-07-01T12:00:00Z");
        String token = credentialManager.generateToken(USERNAME, USERID, today);
        assertTrue(credentialManager.getClaims(token).isEmpty());
    }

    @Test
    public void cannot_Claim_Tampered_Token() {
        Date today = new Date();
        String token = credentialManager.generateToken(USERNAME, USERID, today);
        DecodedJWT decoded = JWT.decode(token);
        String maliciousToken = String.format("%s.%s.%s", decoded.getHeader(),
                "eyJVc2VybmFtZSI6ImFiYyIsImlzcyI6Ik9wZW5NYXJrZXQiLCJleHAiOjE2OTk5OTk5OTksImlhdCI6MTYwMDI3OTMzOX0",
                decoded.getSignature());
        assertTrue(credentialManager.getClaims(maliciousToken).isEmpty());
    }

    @Test
    public void can_Generate_Server_Key() {
        assertDoesNotThrow(CredentialManager::getSecureServerKey);
    }
}
