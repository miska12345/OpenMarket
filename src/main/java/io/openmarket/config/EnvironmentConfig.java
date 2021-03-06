package io.openmarket.config;

public class EnvironmentConfig {
    private EnvironmentConfig() {}

    /**
     * --------------------------------------------------------------
     * Environmental Variable Configuration.
     * --------------------------------------------------------------
     */

    /**
     * The environment variable name for the SQS queue URL for transaction.
     */
    public static final String ENV_VAR_TRANSAC_QUEUE_URL = "TransacQueueURL";

    /**
     * The environment variable name for the port to use by server.
     */
    public static final String ENV_VAR_SERVER_PORT = "Port";

    /**
     * The environment variable name for whether to use validation or not.
     */
    public static final String ENV_VAR_RPC_USE_VALIDATION = "UseValidation";

    /**
     * The environment variable name for how long a token is valid for (in hours).
     */
    public static final String ENV_VAR_TOKEN_DURATION = "TokenDuration";

    public static final String ENV_VAR_DB_URL = "DB_URL";
}
