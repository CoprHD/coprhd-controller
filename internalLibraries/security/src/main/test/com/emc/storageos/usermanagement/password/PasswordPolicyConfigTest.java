/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.password;

import com.emc.storageos.coordinator.service.impl.Main;
import com.emc.storageos.model.property.PropertyInfoUpdate;
import com.emc.storageos.model.property.PropertyList;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.security.password.ValidatorFactory;
import com.emc.storageos.usermanagement.setup.LocalUserMode;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.emc.vipr.model.sys.ClusterInfo;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class PasswordPolicyConfigTest extends LocalUserMode {
    private static Logger logger = LoggerFactory.getLogger(PasswordPolicyConfigTest.class);

    @Test
    public void testGetAndSetMinLength() throws Exception {
        logger.info("get original value:");
        String original_minLength = getViprProperty(Constants.PASSWORD_MIN_LENGTH);
        logger.info(Constants.PASSWORD_MIN_LENGTH + ": " + original_minLength);

        String new_min_length = "14";
        logger.info("set to new value:");
        setViprProperty(Constants.PASSWORD_MIN_LENGTH, new_min_length);
        String lenghth = getViprProperty(Constants.PASSWORD_MIN_LENGTH);
        logger.info(Constants.PASSWORD_MIN_LENGTH + ": " + lenghth);
        Assert.assertTrue(lenghth.equalsIgnoreCase(new_min_length));


        new_min_length = "";
        logger.info("set to empty space:");
        try {
            setViprProperty(Constants.PASSWORD_MIN_LENGTH, new_min_length);
            Assert.fail("should fail to set new_min_length to space");
        } catch (Exception e) {
            logger.info(e.getMessage());
            Assert.assertTrue(e.getMessage().contains("does not match one of the allowable values"));
        }

        logger.info("restore original value:");
        setViprProperty(Constants.PASSWORD_MIN_LENGTH, original_minLength);
        logger.info(Constants.PASSWORD_MIN_LENGTH + ": " + getViprProperty(Constants.PASSWORD_MIN_LENGTH));
    }

    @Test
    public void testSetChangeInterval() throws Exception {
        String too_large = "2000";
        String nomal = "1440";

        String original = getViprProperty(Constants.PASSWORD_CHANGE_INTERVAL);

        // test large value
        try {
            waitForClusterStable();
            setViprProperty(Constants.PASSWORD_CHANGE_INTERVAL, too_large);
            Assert.fail("setting password interval to a value larger than 1440, should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("change interval value must be in range"));
        }

        // test normal value
        waitForClusterStable();
        setViprProperty(Constants.PASSWORD_CHANGE_INTERVAL, nomal);

        // restore orignal value
        waitForClusterStable();
        setViprProperty(Constants.PASSWORD_CHANGE_INTERVAL, original);
    }

    @Test
    public void testSetExpireDays() throws Exception {
        String too_large = "366";
        String too_small = "10";
        String nomal = "60";

        String original = getViprProperty(Constants.PASSWORD_EXPIRE_DAYS);

        // test large value
        try {
            waitForClusterStable();
            setViprProperty(Constants.PASSWORD_EXPIRE_DAYS, too_large);
            Assert.fail("setting password expire days to a large value, should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("Expire days is invalid, it has to be in range"));
        }

        try {
            waitForClusterStable();
            setViprProperty(Constants.PASSWORD_EXPIRE_DAYS, too_small);
            Assert.fail("setting password expire days to a small value, should fail");
        } catch (ServiceErrorException see) {
            logger.info("error code: " + see.getCode());
            logger.info("error message: " + see.getMessage());
            Assert.assertEquals(see.getCode(), 1008);
            Assert.assertTrue(see.getMessage().contains("Expire days is invalid, it has to be in range"));
        }


        // test normal value
        waitForClusterStable();
        setViprProperty(Constants.PASSWORD_EXPIRE_DAYS, nomal);

        // restore original value
        waitForClusterStable();
        setViprProperty(Constants.PASSWORD_EXPIRE_DAYS, original);
    }

    @Test
    public void testResetProperties() throws Exception {
        String property = Constants.PASSWORD_MIN_LENGTH;
        String defaultValue = "8";
        logger.info("get original value:");
        String originalValue = getViprProperty(property);
        logger.info(property + ": " + originalValue);

        String new_min_length = "14";
        logger.info("set to new value:");
        setViprProperty(Constants.PASSWORD_MIN_LENGTH, new_min_length);
        String lenghth = getViprProperty(Constants.PASSWORD_MIN_LENGTH);
        logger.info(Constants.PASSWORD_MIN_LENGTH + ": " + lenghth);
        Assert.assertTrue(lenghth.equalsIgnoreCase(new_min_length));

        logger.info("reset properties");
        resetViprProperty(property);

        logger.info("make sure the property value restored");
        String resetValue = getViprProperty(property);
        logger.info(property + ": " + resetValue);
        Assert.assertTrue(resetValue.equals(defaultValue));

        logger.info("restore original value:");
        setViprProperty(property, originalValue);
        logger.info(property + ": " + getViprProperty(property));
    }


    private String getViprProperty(String name) {
        return systemClient.config().getProperties().getProperty(name);
    }

    private void setViprProperty(String name, String value) throws Exception{
        waitForClusterStable();
        Map<String, String> properties = new HashMap<String, String>();
        properties.put(name, value);
        PropertyInfoUpdate propertyInfoUpdate = new PropertyInfoUpdate();
        propertyInfoUpdate.setProperties(properties);
        systemClient.config().setProperties(propertyInfoUpdate);
    }

    private void resetViprProperty(String name) throws Exception{
        waitForClusterStable();
        PropertyList properties = new PropertyList();
        ArrayList<String> names = new ArrayList<String>();
        names.add(name);
        properties.setPropertyList(names);
        systemClient.config().resetProps(properties);
    }
}
