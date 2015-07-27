/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.password;

import com.emc.storageos.security.password.Password;
import com.emc.storageos.usermanagement.setup.LocalUserMode;
import com.emc.vipr.client.AuthClient;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;

import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationApiTest extends LocalUserMode {

    private static Logger logger = LoggerFactory.getLogger(ValidationApiTest.class);

    private static ViPRSystemClient svcuserClient;
    private static String password="ChangeMe"; //NOSONAR ("Suppressing Sonar violation of removing this hard-coded password since it's default ViPR password.")

    @BeforeClass
    public synchronized static void setupPasswordValidation() throws Exception {
        svcuserClient = new ViPRSystemClient(controllerNodeEndpoint, true).withLogin("svcuser", password);
    }

    @AfterClass
    public static void teardownPasswordValidation() throws Exception {
        if (svcuserClient != null) {
            svcuserClient.auth().logout();
        }
    }

    @Test
    public void PasswordUpdateValidation() throws Exception {

        // wrong old password
        try {
            svcuserClient.password().validateUpdate("wrong-old-password", "password");
            Assert.fail("should fail, wrong old password");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("Old password is invalid"));
        }

        // new password too short
        try {
            svcuserClient.password().validateUpdate(password, "short");
            Assert.fail("should fail, new password too short");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 8 characters long"));
        }

        // new password no numbers
        try {
            svcuserClient.password().validateUpdate(password, "NoNumbers");
            Assert.fail("should fail, contains no digital");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 1 numeric character"));
        }

        // new password no upppercase char
        try {
            svcuserClient.password().validateUpdate(password, "nouppercase");
            Assert.fail("should fail, contains no uppercase");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 1 uppercase alphabetic"));
        }

        // new password no lowercase char
        try {
            svcuserClient.password().validateUpdate(password, "NOLOWERCASE");
            Assert.fail("should fail, contains no lower case");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 1 lowercase alphabetic"));
        }


        // positive test, should be no exception
        svcuserClient.password().validateUpdate(password, "ChangeMe1!");

    }

    @Test
    public void PasswordChangeValidation() throws Exception {
        AuthClient authClient = new AuthClient(controllerNodeEndpoint);

        try {
            authClient.validatePasswordChange("svcuser", "wrong-old-password", "password");
            Assert.fail("should fail, wrong old password");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("Old password is invalid"));
        }

        // new password too short
        try {
            authClient.validatePasswordChange("svcuser", password, "short");
            Assert.fail("should fail, new password too short");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 8 characters long"));
        }

        // new password no numbers
        try {
            authClient.validatePasswordChange("svcuser", password, "NoNumbers");
            Assert.fail("should fail, contains no digital");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 1 numeric character"));
        }

        // new password no upppercase char
        try {
            authClient.validatePasswordChange("svcuser", password, "nouppercase");
            Assert.fail("should fail, contains no uppercase");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 1 uppercase alphabetic"));
        }

        // new password no lowercase char
        try {
            authClient.validatePasswordChange("svcuser", password, "NOLOWERCASE");
            Assert.fail("should fail, contains no lower case");
        } catch (ServiceErrorException see) {
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("at least 1 lowercase alphabetic"));
        }


        // positive test, should be no exception
        authClient.validatePasswordChange("svcuser", password, "ChangeMe1!");
    }



}
