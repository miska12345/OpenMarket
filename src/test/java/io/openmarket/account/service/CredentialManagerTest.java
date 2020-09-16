package io.openmarket.account.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CredentialManagerTest {
    private static final boolean ENABLE_VALIDATION = true;
    @Test
    public void can_generateToken() {
        CredentialManager cm = new CredentialManager(ENABLE_VALIDATION);
        assertNotNull(cm);
        assertNotNull(cm.generateToken("boy"));
        System.out.println(cm);
        System.out.println(cm.generateToken("boy"));
    }

    @Test
    public void can_VerifyToken() {
        CredentialManager cm = new CredentialManager(ENABLE_VALIDATION);
        String token = cm.generateToken("boy");
        assertNotNull(token);
        assertTrue(cm.verifyToken(token, "boy"));
    }

}
