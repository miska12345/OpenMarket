package io.openmarket.config;

public class AccountConfig {

    /**
     * ------------------------------------------------------
     * DDB Configurations.
     * ------------------------------------------------------
     */

    /**
     * The DDB table name for Account service.
     */
    public static final String USER_DDB_TABLE_NAME = "User";

    /**
     * The DDB attribute name for id of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_ID = "UserId";

    /**
     * The DDB attribute name for the unique username of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_USERNAME = "Username";

    /**
     * The length constraint for username of Account.
     */
    public static final int ACCOUNT_USERNAME_LENGTH_LIMIT = 255;

    /**
     * The DDB attribute for portraitKey of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_PORTRAITKEY = "PortraitKey";

    /**
     * The DDB attribute name for the hashed password of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_PASSWORDHASH = "PasswordHash";

    /**
     * The DDB attribute name for the salt of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_PASSWORDSALT = "PasswordSalt";

    /**
     * The DDB attribute name for the display name of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_DISPLAYNAME = "DisplayName";

    /**
     * The DDB attribute name for the create time of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_CREATEAT     = "createAt";

    /**
     * The DDB attribute name for the last update time of Account.
     */
    public static final String USER_DDB_ATTRIBUTE_LASTUPDATEDAT = "lastUpdatedAt";

    /**
     * The DDB config for enabling the credential verification
     */

    public static final boolean TOKEN_VERIFICATION_ENABLE = false;
}
