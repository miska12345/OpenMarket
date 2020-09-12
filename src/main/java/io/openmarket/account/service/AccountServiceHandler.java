package io.openmarket.account.service;

import io.openmarket.account.dao.UserDao;
import io.openmarket.account.grpc.AccountService;
import io.openmarket.account.model.Account;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;

public final class AccountServiceHandler {

    @Inject
    UserDao userDao;

    @Inject
    public AccountServiceHandler() {}


    public AccountService.RegistrationResult register(AccountService.RegistrationRequest request) {
        Optional<Account> chekcDuplicate = userDao.getUser(request.getUsername(), "username");

        //user name already taken
        if (chekcDuplicate.isPresent()) {
            return AccountService.RegistrationResult.newBuilder()
                    .setRegisterStatus(AccountService.RegistrationResult.Status.USERNAME_ALREADY_EXIST).build();
        }

        String username = request.getUsername();
        String password = request.getPassword();
        String salt     = this.generateSalt();
        String displayName = request.getDisplayName();
        byte[] hashedPass = this.hash(password, salt);


        Account newUser = Account.builder().username(username)
                .passwordHash(hashedPass).createAt(Instant.now().toString()).displayName(displayName).build();
       this.userDao.save(newUser);
    }

    /****************************
     *
     * @param inputs
     * @return byte array of the hashed data
     */
    public byte[] hash(String... inputs) {
        String hashed = "";
        for (String x : inputs) {
            hashed += x;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(hashed.getBytes(StandardCharsets.UTF_8));
        } catch(NoSuchAlgorithmException e) {
            e.printStackTrace();
        } finally {
            //TODO change this to some kind of exception
            return null;
        }

    }

    public String generateSalt() {
        Random rd = new Random();
        byte[] result = new byte[32];

        rd.nextBytes(result);

        return new String(result, StandardCharsets.UTF_8);

    }


}
