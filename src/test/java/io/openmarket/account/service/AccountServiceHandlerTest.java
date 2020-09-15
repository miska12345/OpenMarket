package io.openmarket.account.service;

import io.openmarket.account.model.Account;
import io.openmarket.account.AccountTestTemplateLocalDB;
import io.openmarket.accountx.grpc.AccountService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;


public class AccountServiceHandlerTest extends AccountTestTemplateLocalDB {
    @Test
    public void can_Register_when_user_not_exist() {
        ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
        .setPassword("123").setDisplayName("didntpay").build());

        Optional<Account> user1 = userDao.load("weifeng1");
        assertTrue(user1.isPresent());
        assertEquals("weifeng1", user1.get().getUsername());

    }

    @Test
    public void cannot_Register_when_user_exists() {
        ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());
        AccountService.RegistrationResult result = this.ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());
        assertEquals(AccountService.RegistrationResult.Status.USERNAME_ALREADY_EXIST, result.getRegisterStatus());
    }

    @Test
    public void cannot_Register_when_displayName_empty() {
        AccountService.RegistrationResult result = ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").build());

        assertEquals(AccountService.RegistrationResult.Status.INVALID_PARAM, result.getRegisterStatus());
    }

    @Test
    public void cannot_Register_when_username_empty_or_null() {
        //null username cannot be created with builder
        AccountService.RegistrationRequest emptyUser = AccountService.RegistrationRequest.newBuilder().setUsername("").setPassword("123")
                .setDisplayName("didntpay").build();


        AccountService.RegistrationResult emptyResult = ash.register(emptyUser);

        assertEquals(AccountService.RegistrationResult.Status.INVALID_PARAM, emptyResult.getRegisterStatus());

    }

    @Test
    public void cannot_Register_when_password_empty_or_null() {
        AccountService.RegistrationRequest emptyUser = AccountService.RegistrationRequest.newBuilder().setUsername("wefieng1").setPassword("")
                .setDisplayName("didntpay").build();


        AccountService.RegistrationResult emptyResult = ash.register(emptyUser);

        assertEquals(AccountService.RegistrationResult.Status.INVALID_PARAM, emptyResult.getRegisterStatus());
    }

    @Test
    public void can_Login_when_User_Exist() {
        ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());

        AccountService.LoginResult res = ash.login(AccountService.LoginRequest.newBuilder().setPassword("123").setUsername("weifeng1").build());
        assertNotNull(res.getCred());
        assertEquals(AccountService.LoginResult.Status.LOGIN_SUCCESS, res.getLoginStatus());
        assertEquals("weifeng1", res.getUsername());

    }

    @Test
    public void cannot_Login_when_User_Not_Exist() {
        AccountService.LoginResult res = ash.login(AccountService.LoginRequest.newBuilder().setPassword("123").setUsername("weifeng1").build());
        assertEquals(AccountService.LoginResult.Status.LOGIN_FAIL_INCORRECT_PASSWORD_OR_USERNAME, res.getLoginStatus());
    }

    @Test
    public void cannot_Login_when_password_incorrect() {
        ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());

        AccountService.LoginResult res = ash.login(AccountService.LoginRequest.newBuilder().setUsername("weifeng1")
                .setPassword("321").build());

        assertEquals(AccountService.LoginResult.Status.LOGIN_FAIL_INCORRECT_PASSWORD_OR_USERNAME, res.getLoginStatus());
    }

    @Test
    public void cannot_Login_with_invalid_request() {
        ash.register(AccountService.RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());

        AccountService.LoginRequest emptyUsername = AccountService.LoginRequest.newBuilder().setUsername("").setPassword("123").build();
        AccountService.LoginRequest emptyPass = AccountService.LoginRequest.newBuilder().setUsername("weifeng1").build();

        AccountService.LoginResult emptyUser = ash.login(emptyUsername);
        AccountService.LoginResult emptyPassword = ash.login(emptyPass);

        assertEquals(AccountService.LoginResult.Status.LOGIN_FAIL_INVALID_PARAM, emptyUser.getLoginStatus());
        assertEquals(AccountService.LoginResult.Status.LOGIN_FAIL_INVALID_PARAM, emptyPassword.getLoginStatus());
    }

}
