/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.security.password;

public class Constants {

    // keys in storageos-properties-config.def
    public static final String PASSWORD_CHANGE_INTERVAL = "password_change_interval"; // NOSONAR
                                                                                      // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_MIN_LENGTH = "password_min_length"; // NOSONAR
                                                                            // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_LOWERCASE_NUMBER = "password_lowercase_alphabet"; // NOSONAR
                                                                                          // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_UPPERCASE_NUMBER = "password_uppercase_alphabet"; // NOSONAR
                                                                                          // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_NUMERIC_NUMBER = "password_numeric_character"; // NOSONAR
                                                                                       // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_SPECIAL_NUMBER = "password_special_character"; // NOSONAR
                                                                                       // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_REPEATING_NUMBER = "password_repeating_character"; // NOSONAR
                                                                                           // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_CHANGED_NUMBER = "password_changed_character"; // NOSONAR
                                                                                       // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_PREVENT_DICTIONARY = "password_dictionary_rule"; // NOSONAR
                                                                                         // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_REUSE_NUMBER = "password_history_rule"; // NOSONAR
                                                                                // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String PASSWORD_EXPIRE_DAYS = "password_expire_days"; // NOSONAR
                                                                              // ("Suppressing: removing this hard-coded password since it's just the name of attribute")
    public static final String ROOT_EXPIRY_DAYS = "system_root_expiry_date";
    public static final String SVCUSER_EXPIRY_DAYS = "system_svcuser_expiry_date";
    public static final String SYSTEM_UPDATE_CHECK_FREQUENCY_HOURS = "system_update_check_frequency_hours";

    // prompt information shown in changePassword.html
    public static final String[][] PASSWORD_CHANGE_PROMPT = new String[][] {
            { PASSWORD_MIN_LENGTH, "at least {0} characters" },
            { PASSWORD_LOWERCASE_NUMBER, "at least {0} lowercase" },
            { PASSWORD_UPPERCASE_NUMBER, "at least {0} uppercase" },
            { PASSWORD_NUMERIC_NUMBER, "at least {0} numeric" },
            { PASSWORD_SPECIAL_NUMBER, "at least {0} special character" },
            { PASSWORD_REPEATING_NUMBER, "no more than {0} consecutive repeating characters" },
            { PASSWORD_CHANGED_NUMBER, "at least change {0} characters" },
            { PASSWORD_REUSE_NUMBER, "cannot be the last {0} passwords used" }
    };

    public static final String[][] PASSWORD_VALID_PROMPT = new String[][] {
            { PASSWORD_MIN_LENGTH, "at least {0} characters" },
            { PASSWORD_LOWERCASE_NUMBER, "at least {0} lowercase" },
            { PASSWORD_UPPERCASE_NUMBER, "at least {0} uppercase" },
            { PASSWORD_NUMERIC_NUMBER, "at least {0} numeric" },
            { PASSWORD_SPECIAL_NUMBER, "at least {0} special character" },
            { PASSWORD_REPEATING_NUMBER, "no more than {0} consecutive repeating characters" }
    };

    public static final String[][] PASSWORD_UPDATE_PROMPT = new String[][] {
            { PASSWORD_MIN_LENGTH, "at least {0} characters" },
            { PASSWORD_LOWERCASE_NUMBER, "at least {0} lowercase" },
            { PASSWORD_UPPERCASE_NUMBER, "at least {0} uppercase" },
            { PASSWORD_NUMERIC_NUMBER, "at least {0} numeric" },
            { PASSWORD_SPECIAL_NUMBER, "at least {0} special character" },
            { PASSWORD_REPEATING_NUMBER, "no more than {0} consecutive repeating characters" },
            { PASSWORD_CHANGED_NUMBER, "at least change {0} characters" },
            { PASSWORD_REUSE_NUMBER, "cannot be the last {0} passwords used" },
            { PASSWORD_CHANGE_INTERVAL, "cannot be changed more than once in every {0} minutes" }
    };

    /*
     * it is the day when the first mail sent to user to notify their password to be expired.
     */
    public static final int GRACE_DAYS = 14;

    public static final int MIN_PASSWORD_CHANGE_INTERVAL_IN_MINUTES = 0;
    public static final int MAX_PASSWORD_CHANGE_INTERVAL_IN_MINUTES = 1440;
    public static final int MAX_PASSWORD_EXPIRY_IN_DAYS = 365;

    public static final String CRYPT_SHA_512 = "$6$";

    // time to check password expiration and send mail: 3 am every day
    public static final int MAIL_SEND_HOUR = 3;

    // send notify mail at the following days before password expire
    public static final int[] NOTIFICATION_DAYS = new int[] { 14, 7, 6, 5, 4, 3, 2, 1 };

    public static final String SYSTEM_PASSWORD_EXPIRY_FORMAT = "system_%s_expiry_date"; // NOSONAR

    // Login attempts and lockout constants
    public static final String AUTH_LOGIN_ATTEMPTS = "max_auth_login_attempts";
    public static final int MIN_AUTH_LOGIN_ATTEMPTS = 0;
    public static final int MAX_AUTH_LOGIN_ATTEMPTS = 20;
    public static final int DEFAULT_AUTH_LOGIN_ATTEMPTS = 10;

    public static final String AUTH_LOGOUT_TIMEOUT = "auth_lockout_time_in_minutes";
    public static final int MIN_AUTH_LOCKOUT_TIME_IN_MINUTES = 0;
    public static final int MAX_AUTH_LOCKOUT_TIME_IN_MINUTES = 1440;
    public static final int DEFAULT_AUTH_LOCKOUT_TIME_IN_MINUTES = 10;
}
