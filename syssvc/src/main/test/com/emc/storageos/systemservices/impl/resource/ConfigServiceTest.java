/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.resource;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.vipr.model.sys.eventhandler.ConnectEmcEmail;
import com.emc.vipr.model.sys.eventhandler.ConnectEmcFtps;

public class ConfigServiceTest {

    @Test
    public void testEmailUsingAuth() {

        ConnectEmcEmail email = new ConnectEmcEmail();
        email.setEmailSender("DONOTREPLY@customer.com");
        email.setEmailServer("mailhub.lss.emc.com");
        email.setNotifyEmailAddress("joe.customer@customer.com");
        email.setPrimaryEmailAddress("emailalertesg@emc.com");
        email.setSafeEncryption("no");
        email.setUserName("root");
        email.setPassword("ChangeMe");
        try {
            email.setSmtpAuthType("login");
        } catch (Exception e) {
            Assert.fail();
        }

        PropertyInfoUpdate propInfo = ConfigService.ConfigureConnectEmc.configureEmail(email);
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_server"), email.getEmailServer());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_emcto"), email.getPrimaryEmailAddress());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_from"), email.getEmailSender());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_to"), email.getNotifyEmailAddress());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_enabletls"), email.getStartTls());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_encrypt"), email.getSafeEncryption());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_authtype"), email.getSmtpAuthType());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_username"), email.getUserName());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_password"), email.getPassword());
    }

    @Test
    public void testEmailUsingNoAuth() {

        ConnectEmcEmail email = new ConnectEmcEmail();
        email.setEmailSender("DONOTREPLY@customer.com");
        email.setEmailServer("mailhub.lss.emc.com");
        email.setNotifyEmailAddress("joe.customer@customer.com");
        email.setPrimaryEmailAddress("emailalertesg@emc.com");
        email.setSafeEncryption("no");
        email.setStartTls("no");

        PropertyInfoUpdate propInfo = ConfigService.ConfigureConnectEmc.configureEmail(email);
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_server"), email.getEmailServer());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_emcto"), email.getPrimaryEmailAddress());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_from"), email.getEmailSender());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_to"), email.getNotifyEmailAddress());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_enabletls"), email.getStartTls());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_encrypt"), email.getSafeEncryption());
        Assert.assertNull(propInfo.getProperty("system_connectemc_smtp_authtype"));
        Assert.assertNull(propInfo.getProperty("system_connectemc_smtp_username"));
        Assert.assertNull(propInfo.getProperty("system_connectemc_smtp_password"));
    }

    @Test
    public void testEmailUsingAuthWithNoUsernamePassword() {

        ConnectEmcEmail email = new ConnectEmcEmail();
        email.setEmailSender("DONOTREPLY@customer.com");
        email.setEmailServer("mailhub.lss.emc.com");
        email.setNotifyEmailAddress("joe.customer@customer.com");
        email.setPrimaryEmailAddress("emailalertesg@emc.com");
        email.setSafeEncryption("no");
        email.setSmtpAuthType("login");

        PropertyInfoUpdate propInfo = null;

        try {
            propInfo = ConfigService.ConfigureConnectEmc.configureEmail(email);
        } catch (Exception e) {
            Assert.assertNull(propInfo);
            return;
        }

        Assert.fail();
    }

    @Test
    public void testEmailUsingAuthAndCerts() {

        ConnectEmcEmail email = new ConnectEmcEmail();
        email.setEmailSender("DONOTREPLY@customer.com");
        email.setEmailServer("mailhub.lss.emc.com");
        email.setNotifyEmailAddress("joe.customer@customer.com");
        email.setPrimaryEmailAddress("emailalertesg@emc.com");
        email.setSafeEncryption("no");
        email.setUserName("root");
        email.setPassword("ChangeMe");

        try {
            email.setStartTls("yes");
            email.setSmtpAuthType("login");
        } catch (Exception e) {
            Assert.fail();
        }

        PropertyInfoUpdate propInfo = ConfigService.ConfigureConnectEmc.configureEmail(email);
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_server"), email.getEmailServer());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_emcto"), email.getPrimaryEmailAddress());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_from"), email.getEmailSender());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_to"), email.getNotifyEmailAddress());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_enabletls"), email.getStartTls());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_encrypt"), email.getSafeEncryption());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_authtype"), email.getSmtpAuthType());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_username"), email.getUserName());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_password"), email.getPassword());
    }

    @Test
    public void testInvalidAuthType() {

        ConnectEmcEmail email = new ConnectEmcEmail();
        email.setEmailSender("DONOTREPLY@customer.com");
        email.setEmailServer("mailhub.lss.emc.com");
        email.setNotifyEmailAddress("joe.customer@customer.com");
        email.setPrimaryEmailAddress("emailalertesg@emc.com");
        email.setSafeEncryption("no");
        email.setUserName("root");
        email.setPassword("ChangeMe");
        email.setSmtpAuthType("Null");
    }

    @Test
    public void testInvalidTlsValue() {

        ConnectEmcEmail email = new ConnectEmcEmail();
        email.setEmailSender("DONOTREPLY@customer.com");
        email.setEmailServer("mailhub.lss.emc.com");
        email.setNotifyEmailAddress("joe.customer@customer.com");
        email.setPrimaryEmailAddress("emailalertesg@emc.com");
        email.setSafeEncryption("no");
        email.setUserName("root");
        email.setPassword("ChangeMe");
        email.setStartTls("Maybe");
    }

    @Test
    public void testFtps() {

        ConnectEmcFtps ftps = new ConnectEmcFtps();
        ftps.setSafeEncryption("no");
        ftps.setEmailServer("mailhub.lss.emc.com");
        ftps.setNotifyEmailAddress("joe.customer@customer.com");

        try {
            ftps.setHostName("corpusfep3.emc.com");
        } catch (Exception e) {
            Assert.fail();
        }

        PropertyInfoUpdate propInfo = ConfigService.ConfigureConnectEmc.configureFtps(ftps);
        Assert.assertEquals(propInfo.getProperty("system_connectemc_encrypt"), ftps.getSafeEncryption());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_ftps_hostname"), ftps.getHostName());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_server"), ftps.getEmailServer());
        Assert.assertEquals(propInfo.getProperty("system_connectemc_smtp_to"), ftps.getNotifyEmailAddress());
    }
}
