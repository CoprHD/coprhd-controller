/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password;


public class Constants {

    // keys in storageos-properties-config.def
    public static final String PASSWORD_CHANGE_INTERVAL = "password_change_interval";
    public static final String PASSWORD_MIN_LENGTH = "password_min_length";
    public static final String PASSWORD_LOWERCASE_NUMBER = "password_lowercase_alphabet";
    public static final String PASSWORD_UPPERCASE_NUMBER = "password_uppercase_alphabet";
    public static final String PASSWORD_NUMERIC_NUMBER = "password_numeric_character";
    public static final String PASSWORD_SPECIAL_NUMBER = "password_special_character";
    public static final String PASSWORD_REPEATING_NUMBER = "password_repeating_character";
    public static final String PASSWORD_CHANGED_NUMBER = "password_changed_character";
    public static final String PASSWORD_PREVENT_DICTIONARY = "password_dictionary_rule";
    public static final String PASSWORD_REUSE_NUMBER = "password_history_rule";
    public static final String PASSWORD_EXPIRE_DAYS = "password_expire_days";
    public static final String ROOT_EXPIRY_DAYS = "system_root_expiry_date";
    public static final String SVCUSER_EXPIRY_DAYS = "system_svcuser_expiry_date";

    // prompt information shown in changePassword.html
    public static final String[][] PASSWORD_CHANGE_PROMPT = new String[][]{
            {PASSWORD_MIN_LENGTH, "at least {0} characters"},
            {PASSWORD_LOWERCASE_NUMBER, "at least {0} lowercase"},
            {PASSWORD_UPPERCASE_NUMBER, "at least {0} uppercase"},
            {PASSWORD_NUMERIC_NUMBER,  "at least {0} numeric"},
            {PASSWORD_SPECIAL_NUMBER, "at least {0} special character"},
            {PASSWORD_REPEATING_NUMBER, "no more than {0} consecutive repeating"},
            {PASSWORD_CHANGED_NUMBER, "at least change {0} characters"},
            {PASSWORD_REUSE_NUMBER,  "NOT in last {0} change iterations" }
    };

    public static final String[] PASSWORD_RESET_PROMPT = new String[]{
            PASSWORD_MIN_LENGTH,
            PASSWORD_LOWERCASE_NUMBER,
            PASSWORD_UPPERCASE_NUMBER,
            PASSWORD_NUMERIC_NUMBER,
            PASSWORD_SPECIAL_NUMBER,
            PASSWORD_REPEATING_NUMBER,
            PASSWORD_CHANGED_NUMBER,
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
    public static final int[] NOTIFICATION_DAYS = new int[] {14,7,6,5,4,3,2,1};

    public static final String SYSTEM_PASSWORD_EXPIRY_FORMAT = "system_%s_expiry_date";

}
