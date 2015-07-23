/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.password;

import com.emc.storageos.usermanagement.setup.LocalUserMode;
import com.emc.vipr.client.ViPRSystemClient;
import com.emc.vipr.client.exceptions.ServiceErrorException;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PasswordValidationTest extends LocalUserMode {
    private static Logger logger = LoggerFactory.getLogger(PasswordValidationTest.class);

    private static ViPRSystemClient svcuserClient;
    private static String svcuserOldPassword = "ChangeMe"; // NOSONAR ("Suppressing: removing this hard-coded password since it's vipr's default password")
    private static String svcuserPassword = "Emc2@southborough"; // NOSONAR ("Suppressing: removing this hard-coded password since it's temp vipr's password")

    @BeforeClass
    public synchronized static void setupPasswordValidation() throws Exception {
        svcuserClient = new ViPRSystemClient(controllerNodeEndpoint, true).withLogin("svcuser", svcuserPassword);
    }

    @AfterClass
    public static void teardownPasswordValidation() throws Exception {
        if (svcuserClient != null) {
            svcuserClient.auth().logout();
        }
    }

    @Test
    public void PasswordLengthValidation() throws Exception {
        try {
            waitForClusterStable();
            svcuserClient.password().update(svcuserOldPassword, "1111", false, false);
            Assert.fail("should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("characters long"));
        }
    }

    @Test
    public void PasswordLowercaseValidation() throws Exception {
        try {
            waitForClusterStable();
            svcuserClient.password().update(svcuserOldPassword, "1234567890123", false,  false);
            Assert.fail("should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertTrue(see.getMessage().contains("lowercase alphabetic character"));
            Assert.assertEquals(see.getCode(), 1008);
        }
    }

    @Test
    public void PasswordUppercaseValidation() throws Exception {
        try {
            waitForClusterStable();
            svcuserClient.password().update(svcuserOldPassword, "abcdefg!@12345", false, false);
            Assert.fail("should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertTrue(see.getMessage().contains("uppercase alphabetic character"));
            Assert.assertEquals(see.getCode(), 1008);
        }
    }

    @Test
    public void PasswordNumericValidation() throws Exception {
        try {
            waitForClusterStable();
            svcuserClient.password().update(svcuserOldPassword, "aAbBcdefgijk", false, false);
            Assert.fail("should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertTrue(see.getMessage().contains("numeric character"));
            Assert.assertEquals(see.getCode(), 1008);
        }
    }

    @Test
    public void PasswordSpecialValidation() throws Exception {
        try {
            waitForClusterStable();
            svcuserClient.password().update(svcuserOldPassword, "aAbBcde12345", false, false);
            Assert.fail("should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertEquals(see.getCode(), 1008);
        }
    }

    @Test
    public void PasswordRepeatingValidation() throws Exception {
        try {
            waitForClusterStable();
            svcuserClient.password().update(svcuserOldPassword, "abBde!12345aaa", false, false);
            Assert.fail("should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertTrue(see.getMessage().contains("consecutive repeating characters"));
            Assert.assertEquals(see.getCode(), 1008);
        }
    }

}
