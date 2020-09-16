package io.openmarket.account.service;

import com.google.common.hash.Hashing;
import io.openmarket.account.dynamodb.UserDao;
import io.openmarket.account.grpc.AccountService.*;
import io.openmarket.account.model.Account;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.Date;
import java.util.Optional;
import java.util.Random;

@Log4j2
public final class AccountServiceHandler {

    private final UserDao userDao;
    private final CredentialManager credentialManager;

    @Inject
    public AccountServiceHandler(UserDao userDao, CredentialManager cm) {
        this.userDao = userDao;
        this.credentialManager = cm;
        log.info("AccountServiceHandler started");
    }

    public LoginResult login(LoginRequest loginRequest) {
        if (loginRequest == null) throw new IllegalArgumentException();

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

        log.info("User " + username + "logged in with token" + token);
        return LoginResult.newBuilder().setUsername(user.getUsername())
                .setLoginStatus(LoginResult.Status.LOGIN_SUCCESS)
                .setCred(token).build();
    }

    public RegistrationResult register(RegistrationRequest request) {
        if (request == null) throw new NullPointerException();

        if (request.getPassword().isEmpty() || request.getDisplayName().isEmpty()
            ||request.getUsername().isEmpty()){
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
                .displayName(displayName).build();
        this.userDao.save(newUser);

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
