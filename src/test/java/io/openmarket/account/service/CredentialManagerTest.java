package io.openmarket.account.service;

import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CredentialManagerTest {
    @Test
    public void can_generateToken() {
        CredentialManager cm = new CredentialManager();
        assertNotNull(cm);
        assertNotNull(cm.generateToken("boy"));
        System.out.println(cm.generateToken("boy"));
    }

    @Test
    public void can_VerifyToken() {
        CredentialManager cm = new CredentialManager();
        String token = cm.generateToken("boy");
        assertNotNull(token);
        assertTrue(cm.verifyToken(token, "boy"));
    }

}
