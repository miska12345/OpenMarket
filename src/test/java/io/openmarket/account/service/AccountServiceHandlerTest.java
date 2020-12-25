package io.openmarket.account.service;

import io.openmarket.account.AccountTestTemplateLocalDB;
import io.openmarket.account.grpc.AccountService.*;
import io.openmarket.account.model.Account;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class AccountServiceHandlerTest extends AccountTestTemplateLocalDB {
    RegistrationRequest TEST_REQUEST = RegistrationRequest.newBuilder().setUsername("weifeng1")
            .setPassword("123").setDisplayName("didntpay").setPortraitS3Key("sfasd").build();


    @Test
    public void can_Register_when_user_not_exist() {
        ash.register(TEST_REQUEST);

        Optional<Account> user1 = userDao.load("weifeng1");
        assertTrue(user1.isPresent());
        assertEquals("weifeng1", user1.get().getUsername());

        verify(this.transactionServiceHandler, times(1)).createWalletForUser("weifeng1");
    }

    @Test
    public void can_update_new_following() {
        ash.register(TEST_REQUEST);

        ash.updateUser(UpdateRequest.newBuilder().setUsername(TEST_REQUEST.getUsername()).setNewFollow("lookchard").build());

        GetUserResult result = ash.getUser(GetUserRequest.newBuilder().setUserId(TEST_REQUEST.getUsername()).build());

        assertEquals("lookchard", result.getUser().getFollowingList().get(0));
    }

    @Test
    public void cannot_update_already_following() {
        ash.register(TEST_REQUEST);

        ash.updateUser(UpdateRequest.newBuilder().setUsername(TEST_REQUEST.getUsername()).setNewFollow("lookchard").build());
        ash.updateUser(UpdateRequest.newBuilder().setUsername(TEST_REQUEST.getUsername()).setNewFollow("lookchard").build());

        GetUserResult result = ash.getUser(GetUserRequest.newBuilder().setUserId(TEST_REQUEST.getUsername()).build());

        assertEquals(1, result.getUser().getFollowingList().size());
    }

    @Test
    public void can_getUser_when_exist() {
        ash.register(TEST_REQUEST);

        GetUserResult result = ash.getUser(GetUserRequest.newBuilder().setUserId(TEST_REQUEST.getUsername()).build());

        assertEquals(result.getError(), GetUserResult.GetAllFollowError.NONE);
        assertEquals(result.getUser().getUserName(), TEST_REQUEST.getUsername());
    }

    @Test
    public void cannot_getUser_when_doesnot_exist() {
        GetUserResult result = ash.getUser(GetUserRequest.newBuilder().setUserId(TEST_REQUEST.getUsername()).build());

        assertEquals(result.getError(), GetUserResult.GetAllFollowError.USER_DOESNOT_EXIST);
    }

    @Test
    public void cannot_getUser_when_request_empty() {
        GetUserResult result = ash.getUser(GetUserRequest.newBuilder().build());

        assertEquals(result.getError(), GetUserResult.GetAllFollowError.USER_DOESNOT_EXIST);
    }


    @Test
    public void cannot_Register_when_user_exists() {
        ash.register(TEST_REQUEST);
        RegistrationResult result = this.ash
                .register(TEST_REQUEST);
        assertEquals(RegistrationResult.Status.USERNAME_ALREADY_EXIST, result.getRegisterStatus());
    }

    @Test
    public void cannot_Register_when_displayName_empty() {
        RegistrationResult result = ash.register(RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").build());

        assertEquals(RegistrationResult.Status.INVALID_PARAM, result.getRegisterStatus());
    }

    @Test
    public void cannot_Register_when_username_empty_or_null() {
        //null username cannot be created with builder
        RegistrationRequest emptyUser = RegistrationRequest.newBuilder().setUsername("").setPassword("123")
                .setDisplayName("didntpay").build();


        RegistrationResult emptyResult = ash.register(emptyUser);

        assertEquals(RegistrationResult.Status.INVALID_PARAM, emptyResult.getRegisterStatus());

    }

    @Test
    public void cannot_Register_when_password_empty_or_null() {
        RegistrationRequest emptyUser = RegistrationRequest.newBuilder().setUsername("wefieng1").setPassword("")
                .setDisplayName("didntpay").build();


        RegistrationResult emptyResult = ash.register(emptyUser);

        assertEquals(RegistrationResult.Status.INVALID_PARAM, emptyResult.getRegisterStatus());
    }

    @Test
    public void can_Login_when_User_Exist() {
        ash.register(TEST_REQUEST);

        LoginResult res = ash.login(LoginRequest.newBuilder().setPassword("123").setUsername("weifeng1").build());
        assertNotNull(res.getCred());
        assertEquals(LoginResult.Status.LOGIN_SUCCESS, res.getLoginStatus());
        assertEquals("weifeng1", res.getUsername());

    }

    @Test
    public void cannot_Login_when_User_Not_Exist() {
        LoginResult res = ash.login(LoginRequest.newBuilder().setPassword("123").setUsername("weifeng1").build());
        assertEquals(LoginResult.Status.LOGIN_FAIL_INCORRECT_PASSWORD_OR_USERNAME, res.getLoginStatus());
    }

    @Test
    public void cannot_Login_when_password_incorrect() {
        ash.register(RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());

        LoginResult res = ash.login(LoginRequest.newBuilder().setUsername("weifeng1")
                .setPassword("321").build());

        assertEquals(LoginResult.Status.LOGIN_FAIL_INCORRECT_PASSWORD_OR_USERNAME, res.getLoginStatus());
    }

    @Test
    public void cannot_Login_with_invalid_request() {
        ash.register(RegistrationRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").setDisplayName("didntpay").build());

        LoginRequest emptyUsername = LoginRequest.newBuilder().setUsername("").setPassword("123").build();
        LoginRequest emptyPass = LoginRequest.newBuilder().setUsername("weifeng1").build();

        LoginResult emptyUser = ash.login(emptyUsername);
        LoginResult emptyPassword = ash.login(emptyPass);

        assertEquals(LoginResult.Status.LOGIN_FAIL_INVALID_PARAM, emptyUser.getLoginStatus());
        assertEquals(LoginResult.Status.LOGIN_FAIL_INVALID_PARAM, emptyPassword.getLoginStatus());
    }


    @Test
    public void can_Update_when_User_Exist() {
        ash.register(TEST_REQUEST);
        UpdateRequest request = UpdateRequest.newBuilder().setNewDisplayName("didntpay")
                .setUsername("weifeng1").setNewPassword("321").build();
        UpdateResult result = ash.updateUser(request);

        LoginRequest login = LoginRequest.newBuilder().setUsername("weifeng1")
                .setPassword("321").build();

        LoginResult loginResult = ash.login(login);

        assertEquals("didntpay", result.getNewDisplayName());
        assertEquals(UpdateResult.Status.UPDATE_SUCCESS, result.getUpdateStatus());
        assertEquals(LoginResult.Status.LOGIN_SUCCESS, loginResult.getLoginStatus());
        assertEquals("weifeng1", loginResult.getUsername());
        assertFalse(loginResult.getCred().isEmpty());
    }

    @Test
    public void Update_would_not_change_other_field() {
        ash.register(TEST_REQUEST);

        UpdateRequest nameUpdate = UpdateRequest.newBuilder().setNewDisplayName("didntpay")
                .setUsername("weifeng1").build();
        LoginRequest login = LoginRequest.newBuilder().setUsername("weifeng1")
                .setPassword("123").build();

        UpdateResult updateResult = ash.updateUser(nameUpdate);
        LoginResult loginResult = ash.login(login);

        assertEquals("didntpay", updateResult.getNewDisplayName());
        assertEquals(LoginResult.Status.LOGIN_SUCCESS, loginResult.getLoginStatus());
    }



}
