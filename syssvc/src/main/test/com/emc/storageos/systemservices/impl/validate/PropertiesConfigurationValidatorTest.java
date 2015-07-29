/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.validate;

import java.io.UnsupportedEncodingException;

import org.apache.commons.codec.binary.Base64;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import com.emc.storageos.db.client.model.EncryptionProvider;
import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

@ContextConfiguration(locations = { "/sys-metadata-var.xml" })
public class PropertiesConfigurationValidatorTest extends
        AbstractJUnit4SpringContextTests {

    PropertiesMetadata data = null;
    PropertiesConfigurationValidator validator;

    @Before
    public void setUp() {

        validator = new PropertiesConfigurationValidator();
        data = (PropertiesMetadata) applicationContext.getBean("metadata");
        validator.setPropertiesMetadata(data);
        validator.setEncryptionProvider(new TestEncryptionProvider());
    }

    @Test
    public void testIpv4Addr() {
        validator.getValidPropValue("network_gateway",
                "10.247.96.1", false);
        try {
            validator.getValidPropValue("network_gateway",
                    "10.247.96.256", false);
            Assert.fail();
        } catch (BadRequestException e) {
            Assert.assertTrue(true);
        }
        try {
            validator.getValidPropValue("network_gateway",
                    "xxx", false);
            Assert.fail();
        } catch (BadRequestException e) {
            Assert.assertTrue(true);
        }
        try {
            validator.getValidPropValue("network_gateway",
                    "2620:0:170:2842::1", false);
            Assert.fail();
        } catch (BadRequestException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testIpv6Addr() {
        validator.getValidPropValue("network_gateway6", "2620:0:170:2842::1", false);
        try {
            validator.getValidPropValue("network_gateway6", "G620:0:170:2842::1", false);
            Assert.fail();
        } catch (BadRequestException e) {
            Assert.assertTrue(true);
        }
        try {
            validator.getValidPropValue("network_gateway6", "xxxx", false);
            Assert.fail();
        } catch (BadRequestException e) {
            Assert.assertTrue(true);
        }
        try {
            validator.getValidPropValue("network_gateway6", "10.247.100.11", false);
            Assert.fail();
        } catch (BadRequestException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testUrl() {

        Assert.assertTrue(validator.getValidPropValue("system_update_repo",
                "http://lglaf020.lss.emc.com/ovf/Bourne/", true) != null);
        try {
            validator.getValidPropValue("system_update_repo",
                    "lglaf020.lss.emc.com/ovf/Bourne/", true);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testEmail() {

        Assert.assertTrue(validator.getValidPropValue("system_connectemc_smtp_to",
                "DONOTREPLY@emc.com", true) != null);
        try {
            validator.getValidPropValue("system_connectemc_smtp_to",
                    "noemail", true);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testMaxLength() {

        String longString =
                ("1234567890123456789012345678901234567890123456789012345678901234567890");
        String validString = ("10.247.11.9");
        try {
            validator.getValidPropValue("network_ntpservers",
                    longString, true);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
        Assert.assertTrue(validator.getValidPropValue("network_ntpservers",
                validString, true) != null);
    }

    @Test
    public void testMinLength() {

        String validString = ("10.247.11.9");
        String shortString = ("123456");
        try {
            validator.getValidPropValue("network_ntpservers", shortString, true);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }

        Assert.assertTrue(validator.getValidPropValue("network_ntpservers",
                validString, true) != null);
    }

    @Test
    public void testNonMutableField() {

        try {
            validator.getValidPropValue("config_version", "bad version", true);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testValidHostName() {
        Assert.assertTrue(PropertiesConfigurationValidator.validateHostName("corpusfep3.emc.com"));
    }

    @Test
    public void testHostNameStartingWithPeriod() {
        Assert.assertFalse(PropertiesConfigurationValidator.validateHostName(".corpusfep3.emc.com"));
    }

    @Test
    public void testHostNameStartingWithUnderbar() {
        Assert.assertFalse(PropertiesConfigurationValidator.validateHostName("_abc.corpusfep3.emc.com"));
    }

    @Test
    public void testHostNameStartingWithLableGreaterThan63() {
        String label = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 64 chars.
        String hostname = label + ".emc.com";
        Assert.assertFalse(PropertiesConfigurationValidator.validateHostName(hostname));
    }

    @Test
    public void testHostNameStartingGreaterThan255() {
        String label = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"; // 63 chars.
        String hostname = label + "." + label + "." + label + "." + label + ".extrachars";
        Assert.assertFalse(PropertiesConfigurationValidator.validateHostName(hostname));
    }

    @Test
    public void testHostNameUsingValidIpAddress() {
        Assert.assertTrue(PropertiesConfigurationValidator.validateHostName("10.247.96.1"));
    }

    @Test
    public void testEncryptedString() throws UnsupportedEncodingException {
        TestEncryptionProvider provider = new TestEncryptionProvider();
        String encryptedString = validator.getValidPropValue("system_update_password", "password", true);
        Assert.assertEquals(provider.getEncryptedString("ENCRYPTED"), encryptedString);
    }

    @Test
    public void testUint16() {
        Assert.assertTrue(PropertiesConfigurationValidator.validateUint16("0"));
        Assert.assertTrue(PropertiesConfigurationValidator.validateUint16("65535"));
        Assert.assertFalse(PropertiesConfigurationValidator.validateUint16("65536"));
        Assert.assertFalse(PropertiesConfigurationValidator.validateUint16("-1"));
        Assert.assertFalse(PropertiesConfigurationValidator.validateUint16("x"));
    }

    @Test
    public void testUint8() {
        Assert.assertTrue(PropertiesConfigurationValidator.validateUint8("0"));
        Assert.assertTrue(PropertiesConfigurationValidator.validateUint8("255"));
        Assert.assertFalse(PropertiesConfigurationValidator.validateUint8("256"));
        Assert.assertFalse(PropertiesConfigurationValidator.validateUint8("-1"));
        Assert.assertFalse(PropertiesConfigurationValidator.validateUint8("x"));
    }

    private class TestEncryptionProvider implements EncryptionProvider {

        @Override
        public void start() {
        }

        @Override
        public byte[] encrypt(String input) {
            try {
                return "ENCRYPTED".getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String getEncryptedString(String input) {
            byte[] data = encrypt(input);
            try {
                return new String(Base64.encodeBase64(data), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // All JVMs must support UTF-8, this really can never happen
                throw new RuntimeException(e);
            }
        }

        @Override
        public String decrypt(byte[] input) {
            return null;
        }

    }
}
