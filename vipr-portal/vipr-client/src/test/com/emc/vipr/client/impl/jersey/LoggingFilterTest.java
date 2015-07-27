/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.impl.jersey;

import org.junit.Test;
import static org.junit.Assert.*;

public class LoggingFilterTest {
    public static final String SOMETHING_WITH_PASSWORDS = "<password>MYPASSWORD</password>";//NOSONAR ("Suppressing Sonar violation of Field names should comply with naming convention, also this field  is not holding sensitive data")
    public static final String SOMETHING_WITH_PASSWORD_MAP = "<map><key>password</key><value>MYPASSWORD</value></map>"; //NOSONAR ("Suppressing Sonar violation of Field names should comply with naming convention, also this field  is not holding sensitive data")
    public static final String SECRET_KEYS_XML = "<user_secret_keys><secret_key_1>YIgjoGlMFelh3X9IBpbo2MbWtJtD4bt5aj8epNSB</secret_key_1><secret_key_2>FOO</secret_key_2><key_timestamp_1>2014-09-10 19:05:19.362</key_timestamp_1><key_timestamp_2></key_timestamp_2><link rel=\"self\" href=\"/object/secret-keys\"/></user_secret_keys>";

    @Test
    public void protectPasswordTest() {
        String result = LoggingFilter.protectPasswords(SECRET_KEYS_XML);
        assertFalse(result.contains("YIgjoGlMFelh3X9IBpbo2MbWtJtD4bt5aj8epNSB"));
        assertFalse(result.contains("FOO"));

        result = LoggingFilter.protectPasswords(SOMETHING_WITH_PASSWORDS);
        assertFalse(result.contains("MYPASSWORD"));

        result = LoggingFilter.protectPasswords(SOMETHING_WITH_PASSWORD_MAP);
        assertFalse(result.contains("MYPASSWORD"));
    }
}
