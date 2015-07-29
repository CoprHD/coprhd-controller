/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.password;

import com.emc.storageos.usermanagement.setup.LocalUserMode;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangePasswordApiTest extends LocalUserMode {
    private static Logger logger = LoggerFactory.getLogger(PasswordPolicyConfigTest.class);
    private String oldPassword = "ChangeMe";  // NOSONAR
                                             // ("Suppressing: removing this hard-coded password since it's vipr's default password")
    private String newValidPassword = "Vipr1@emc.com";  // NOSONAR
                                                       // ("Suppressing: removing this hard-coded password since it's a temp vipr's password for testing")

    @Test
    public void nonLocalUser() throws Exception {
        try {
            systemClient.auth().changePassword("fred@secqe.com", "Password1", newValidPassword);
            Assert.fail("change password shouldn't success for AD users");
        } catch (ServiceErrorException se) {
            Assert.assertTrue(se.getMessage().contains("username is not valid"));
        }
    }

    @Test
    public void wrongOldPassword() throws Exception {
        try {
            systemClient.auth().changePassword("svcuser", "wrongOldPassword", newValidPassword);
            Assert.fail("should fail, as old password is wrong");
        } catch (ServiceErrorException se) {
            Assert.assertTrue(se.getMessage().contains("Old password is invalid"));
        }
    }

    @Test
    public void newPasswordTooShort() throws Exception {
        try {
            systemClient.auth().changePassword("svcuser", "ChangeMe", "aA!1");
            Assert.fail("should fail, as new password is too short");
        } catch (ServiceErrorException se) {
            Assert.assertTrue(se.getMessage().contains("characters long"));
        }
    }

    @Test
    public void newPasswordWithoutDigital() throws Exception {
        try {
            systemClient.auth().changePassword("svcuser", "ChangeMe", "abcdefghijkK");
            Assert.fail("should fail, as new password contains no numeric character");
        } catch (ServiceErrorException se) {
            Assert.assertTrue(se.getMessage().contains("numeric character"));
        }
    }

    @Test
    public void newPasswordWithoutLowercase() throws Exception {
        try {
            systemClient.auth().changePassword("svcuser", "ChangeMe", "ABCDEFGH1$");
            Assert.fail("should fail, as new password contains no lowercase character");
        } catch (ServiceErrorException se) {
            Assert.assertTrue(se.getMessage().contains("lowercase alphabetic character"));
        }
    }

    /**
     * this is test is for verify bug fix for CTRL-7658, when change proxyuser's password
     * will get a nullpointer exception.
     * 
     * @throws Exception
     */
    @Test
    public void proxyuserChangePassword() throws Exception {
        try {
            systemClient.auth().changePassword("proxyuser", "ChangeMe", "abcdefghijkK");
            Assert.fail("should fail, as new password contains no numeric character");
        } catch (ServiceErrorException se) {
            // before fixing bug CTRL-7658, it is a null pointer exception.
            // we don't want a postive test case here, since once proxyuser's passoword
            // get changed, there is no way to change it back to "ChangeMe" anymore,
            // which may fail other cases.
            Assert.assertTrue(se.getMessage().contains("numeric character"));
        }
    }

    @Test
    public void changePasswordBlockAfter10InvalideOldPassword() throws Exception {
        boolean bBlock = false;
        for (int i = 0; i < 11; i++) {
            try {
                systemClient.auth().changePassword("svcuser", "wrongOldPasswd", "newpassword");
                Assert.fail("should fail, as old password is invalid");
            } catch (ServiceErrorException se) {
                if (se.getMessage().contains("Exceeding invalid login limit from the client")) {
                    logger.info(se.getMessage());
                    bBlock = true;
                    break;
                }
            }
        }

        Assert.assertTrue(bBlock);
        logger.info("sleep 10 mins, wait for client ip be unblock");
        Thread.sleep(10 * 60 * 1000);
    }

    @Test
    public void updatePasswordBlockAfter10InvalideOldPassword() throws Exception {
        boolean bBlock = false;
        for (int i = 0; i < 11; i++) {
            try {
                systemClient.password().update("wrongOldPassword", "newPassword", false);
                Assert.fail("should fail, as old password is invalid");
            } catch (ServiceErrorException se) {
                if (se.getMessage().contains("Exceeding invalid login limit from the client")) {
                    logger.info(se.getMessage());
                    bBlock = true;
                    break;
                }
            }
        }

        Assert.assertTrue(bBlock);
        logger.info("sleep 10 mins, wait for client ip be unblock");
        Thread.sleep(10 * 60 * 1000);
    }
}
