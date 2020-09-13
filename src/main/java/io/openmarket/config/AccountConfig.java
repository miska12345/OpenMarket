package io.openmarket.config;

public class AccountConfig {

    /**
     * ------------------------------------------------------
     * DDB Configurations.
     * ------------------------------------------------------
     */

    /**
     * The DDB table name for Transaction service.
     */
    public static final String USER_DDB_TABLE_NAME = "User";

    /**
     * The DDB attribute name for unique Id of the transaction.
     */
    public static final String USER_DDB_ATTRIBUTE_ID = "UserId";

    public static final String USER_DDB_ATTRIBUTE_USERNAME = "username";

    public static final int ACCOUNT_USERNAME_LENGTH_LIMIT = 255;
    /**
     * The DDB attribute name for transaction type.
     */
    public static final String USER_DDB_ATTRIBUTE_PORTRAITKEY = "portraitKey";

    public static final String USER_DDB_ATTRIBUTE_PASSWORDHASH = "passwordHash";

    public static final String USER_DDB_ATTRIBUTE_PASSWORDSALT = "passwordSalt";

    public static final String USER_DDB_ATTRIBUTE_DISPLAYNAME = "displayName";

    public static final String USER_DDB_ATTRIBUTE_COINS       = "coins";

    public static final String USER_DDB_ATTRIBUTE_CREATEAT     = "createAt";

    public static final String USER_DDB_ATTRIBUTE_LASTUPDATEDAT = "lastUpdatedAt";
}
