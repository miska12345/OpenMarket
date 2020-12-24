package io.openmarket.account.service;

import com.google.common.hash.Hashing;
import io.openmarket.account.dynamodb.UserDao;
import io.openmarket.account.grpc.AccountService.*;
import io.openmarket.account.model.Account;
import io.openmarket.transaction.service.TransactionServiceHandler;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;

@Log4j2
public final class AccountServiceHandler {

    private final UserDao userDao;
    private final CredentialManager credentialManager;
    private final TransactionServiceHandler transactionServiceHandler;

    @Inject
    public AccountServiceHandler(UserDao userDao,
                                 CredentialManager cm,
                                 TransactionServiceHandler transactionServiceHandler) {
        this.userDao = userDao;
        this.credentialManager = cm;
        this.transactionServiceHandler = transactionServiceHandler;
        log.info("AccountServiceHandler started");
    }

    public LoginResult login(@Nonnull final LoginRequest loginRequest) {

        if (loginRequest.getUsername().isEmpty() || loginRequest.getPassword().isEmpty()) {
            log.info("Reqeuset contains invalid param");
            return LoginResult.newBuilder()
                    .setLoginStatus(LoginResult.Status.LOGIN_FAIL_INVALID_PARAM).build();
        }
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        Optional<Account> potentialUser = this.userDao.load(username);

        if (!potentialUser.isPresent()) {
            //Todo set this to a new status call user not found
            log.info("User " + username + " is not found");
            return LoginResult.newBuilder()
                    .setLoginStatus(LoginResult.Status.LOGIN_FAIL_INCORRECT_PASSWORD_OR_USERNAME)
                    .build();
        }

        Account user = potentialUser.get();
        String salt  = user.getPasswordSalt();
        String passwd = user.getPasswordHash();
        String submittedPasswd = hash(password, salt);

        if (!passwd.equals(submittedPasswd)) {
            log.info("Login failed due to incorrect password or username");
            return LoginResult.newBuilder()
                    .setLoginStatus(LoginResult.Status.LOGIN_FAIL_INCORRECT_PASSWORD_OR_USERNAME)
                    .build();
        }

        String token = credentialManager.generateToken(user.getUsername(), user.getUserId(), new Date());

        log.info("User " + username + "logged in with token " + token);
        return LoginResult.newBuilder().setUsername(user.getUsername())
                .setLoginStatus(LoginResult.Status.LOGIN_SUCCESS)
                .setCred(token).build();
    }

    public GetUserResult getUser(GetUserRequest request) {
        String userName = request.getUserId();

        if (userName.isEmpty()) {
            log.error("Empty userName is not allowed");
            return GetUserResult.newBuilder().setError(GetUserResult.GetAllFollowError.USER_DOESNOT_EXIST).build();
        }
        Optional<Account> user = this.userDao.load(userName);

        if (!user.isPresent()) {
            log.error("Can not find user upon get user request");
            return GetUserResult.newBuilder().setError(GetUserResult.GetAllFollowError.USER_DOESNOT_EXIST).build();
        }

        Account account = user.get();


        User.Builder result = User.newBuilder()
                .setDisplayName(account.getDisplayName())
                .setPortraitKey(account.getPortraitS3Key())
                .setUserName(account.getUsername());

        if (account.getAllFollowing() != null)
            result.addAllFollowing(account.getAllFollowing());

        return GetUserResult.newBuilder()
                .setError(GetUserResult.GetAllFollowError.NONE)
                .setUser(result.build()).build();
    }

    public RegistrationResult register(@Nonnull final RegistrationRequest request) {
        if (request.getPassword().isEmpty() || request.getDisplayName().isEmpty()
            ||request.getUsername().isEmpty() || request.getPortraitS3Key().isEmpty()){
            log.info("Registration failed due to invalid parameter");
            return RegistrationResult.newBuilder()
                    .setRegisterStatus(RegistrationResult.Status.INVALID_PARAM).build();
        }


        Optional<Account> chekcDuplicate = userDao.load(request.getUsername());

        //user name already taken
        if (chekcDuplicate.isPresent()) {
            log.info("Registration failed because username " + request.getUsername() + "is taken");
            return RegistrationResult.newBuilder()
                    .setRegisterStatus(RegistrationResult.Status.USERNAME_ALREADY_EXIST).build();
        }

        String username = request.getUsername();
        String password = request.getPassword();
        String salt     = generateSalt();
        String displayName = request.getDisplayName();
        String hashedPass = hash(password, salt);
        Account newUser = Account.builder().username(username)
                .passwordHash(hashedPass).passwordSalt(salt)
                .displayName(displayName)
                .portraitS3Key(request.getPortraitS3Key())
                .build();

        this.userDao.save(newUser);

        transactionServiceHandler.createWalletForUser(username);

        log.info("Registration completed, welcome " + username);
        return RegistrationResult.newBuilder()
               .setRegisterStatus(RegistrationResult.Status.REGISTER_SUCCESS)
               .build();
    }

    /******
     * Update user display name or password
     */
    public UpdateResult updateUser(io.openmarket.account.grpc.AccountService.UpdateRequest request) {
        String username = request.getUsername();
        validateParam(username);

        Optional<Account> potentialAccount = this.userDao.load(username);

        if (!potentialAccount.isPresent()) {
            return UpdateResult.newBuilder()
                    .setUpdateStatus(UpdateResult.Status.UPDATE_FAILED_USER_NOT_FOUND)
                    .build();
        }

        Account existingAccount = potentialAccount.get();

        if (!request.getNewDisplayName().isEmpty()) existingAccount.setDisplayName(request.getNewDisplayName());
        if (!request.getNewFollow().isEmpty()) {
            if (existingAccount.getAllFollowing() == null) {
                existingAccount.setAllFollowing(new HashSet<>());
            }
            existingAccount.getAllFollowing().add(request.getNewFollow());
        }
        if (!request.getNewPassword().isEmpty()) {
            String salt = existingAccount.getPasswordSalt();
            existingAccount.setPasswordHash(hash(request.getNewPassword(), salt));
        }

        this.userDao.save(existingAccount);
        return io.openmarket.account.grpc.AccountService.UpdateResult.newBuilder().setNewDisplayName(request.getNewDisplayName())
                .setUpdateStatus(UpdateResult.Status.UPDATE_SUCCESS).build();
    }


    private void validateParam(String input) {
        if (input == null || input.isEmpty())
            throw new InvalidParameterException("Request parameter(s) is empty or null");

    }


    /****************************
     *
     * @param inputs
     * @return byte array of the hashed data
     */
    private String hash(String... inputs) {
        String hashed = "";

        for (String input : inputs) {
            hashed += input;
        }

        return Hashing.sha256().hashString(hashed, StandardCharsets.UTF_8).toString();
    }

    private String generateSalt() {
        Random rd = new Random();
        byte[] result = new byte[32];

        rd.nextBytes(result);

        return new String(result, StandardCharsets.UTF_8);

    }


}
