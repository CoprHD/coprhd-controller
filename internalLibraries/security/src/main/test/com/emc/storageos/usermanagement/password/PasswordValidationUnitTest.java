/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.password;

import com.emc.storageos.db.client.model.LongMap;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.Password;
import com.emc.storageos.security.password.PasswordUtils;
import com.emc.storageos.security.password.rules.ChangedNumberRule;
import com.emc.storageos.security.password.rules.ExpireRule;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.List;

public class PasswordValidationUnitTest {

    private static Logger logger = LoggerFactory.getLogger(PasswordValidationUnitTest.class);

    @Test
    public void ChangedNumberRule() {
        ChangedNumberRule rule = new ChangedNumberRule(4);

        Password password = new Password("1122334455", "1122334455");
        try {
            rule.validate(password);
            Assert.fail("old password same as new password, should fail");
        } catch (BadRequestException e) {
            logger.info(e.getServiceCode().toString());
            logger.info(e.getMessage());
            Assert.assertTrue(e.getMessage().contains("characters be changed between the old and new passwords"));
        }

        try {
            password = new Password("aab2334455", "1122334455");
            rule.validate(password);
            Assert.fail("old password 3 characters differ than new password, should fail");
        } catch (BadRequestException e) {
            logger.info(e.getServiceCode().toString());
            logger.info(e.getMessage());
            Assert.assertTrue(e.getMessage().contains("characters be changed between the old and new passwords"));
        }

        // test change number of characters between passwords applies Levenshtein Distance
        try {
            password = new Password("ChangeMe", "hangeMe");
            rule.validate(password);
            Assert.fail("only remove 1 character from front, should fail");
        } catch (BadRequestException e) {
            logger.info(e.getServiceCode().toString());
            logger.info(e.getMessage());
            Assert.assertTrue(e.getMessage().contains("characters be changed between the old and new passwords"));
        }

        try {
            password = new Password("ChangeMe", "ChIangeMe");
            rule.validate(password);
            Assert.fail("only insert 1 character in the middle, should fail");
        } catch (BadRequestException e) {
            logger.info(e.getServiceCode().toString());
            logger.info(e.getMessage());
            Assert.assertTrue(e.getMessage().contains("characters be changed between the old and new passwords"));
        }

        password = new Password("aabb334455", "1122334455");
        rule.validate(password);
    }

    @Test
    public void testExtractDetails() {
        String message = "<error><code>1008</code><description>Parameter was provided but invalid</description><details>Old password is invalid</details><retryable>false</retryable></error>";
        message = message.replaceAll(".*<details>(.*)</details>.*", "$1");
        logger.info(message);
        Assert.assertTrue(message.equals("Old password is invalid"));
    }

    @Test
    public void testExpireRule() {
        ExpireRule expireRule = new ExpireRule(1);
        long current = System.currentTimeMillis();
        long twoDaysAgo = current - 2 * 24 * 60 * 60 * 1000;
        Password password = new Password("svcuser", "oldpassword", "password");
        PasswordHistory passwordHistory = new PasswordHistory();
        LongMap map = new LongMap();
        map.put("hashedPassword", twoDaysAgo);
        passwordHistory.setUserPasswordHash(map);
        password.setPasswordHistory(passwordHistory);

        logger.info("current=" + current + ", 2daysAgo = " + twoDaysAgo);
        try {
            expireRule.validate(password);
            Assert.fail("password already expired, should fail");
        } catch (BadRequestException e) {
            logger.info(e.getServiceCode().toString());
            logger.info(e.getMessage());
        }
    }

    @Test
    public void testPasswordHistorySort() {
        long current = System.currentTimeMillis();
        long oneDayAgo = current - 1 * 24 * 60 * 60 * 1000;
        long twoDaysAgo = current - 2 * 24 * 60 * 60 * 1000;
        long threeDaysAgo = current - 3 * 24 * 60 * 60 * 1000;
        logger.info("oneDayAgo = " + oneDayAgo);
        logger.info("twoDaysAgo = " + twoDaysAgo);
        logger.info("threeDaysAgo = " + threeDaysAgo);
        Password password = new Password("svcuser", "oldpassword", "password");
        PasswordHistory passwordHistory = new PasswordHistory();
        LongMap map = new LongMap();
        map.put("hashedPassword1", oneDayAgo);
        map.put("hashedPassword3", threeDaysAgo);
        map.put("hashedPassword2", twoDaysAgo);
        passwordHistory.setUserPasswordHash(map);
        password.setPasswordHistory(passwordHistory);

        long latestChangedTime = password.getLatestChangedTime();
        logger.info("latestChangedTime = " + latestChangedTime);
        Assert.assertEquals(latestChangedTime, oneDayAgo);

        List<String> passwords = password.getPreviousPasswords(3);
        logger.info("password sorted:");
        for (String p : passwords) {
            logger.info(p);
        }
        Assert.assertTrue(passwords.get(0).equals("hashedPassword1"));
        Assert.assertTrue(passwords.get(1).equals("hashedPassword2"));
        Assert.assertTrue(passwords.get(2).equals("hashedPassword3"));
    }

    @Test
    public void testGetDaysAfterEpoch() {
        Calendar year1971 = Calendar.getInstance();
        year1971.set(1971, 0, 1);
        logger.info("Time: " + year1971.getTime() + ", " + year1971.getTimeInMillis());

        int days = PasswordUtils.getDaysAfterEpoch(year1971);
        logger.info("days after epoch for year 1971: " + String.valueOf(days));
        Assert.assertEquals(days, 365);

        // test now + 60 days
        Calendar now = Calendar.getInstance();
        logger.info("Time: " + now.getTime() + ", " + now.getTimeInMillis());
        days = PasswordUtils.getDaysAfterEpoch(now);
        logger.info("days after epoch for now: " + String.valueOf(days));

        now.add(Calendar.DATE, 60);
        logger.info("Time: " + now.getTime() + ", " + now.getTimeInMillis());
        int newdays = PasswordUtils.getDaysAfterEpoch(now);
        logger.info("days after epoch for now + 60 days: " + String.valueOf(newdays));
        Assert.assertEquals(days + 60, newdays);
    }

    @Test
    public void testDayToMilliSeconds() {
        Calendar gracePoint = Calendar.getInstance();
        gracePoint.add(Calendar.DATE, Constants.GRACE_DAYS);
        logger.info("grace point + " + gracePoint.getTime() + ", " + gracePoint.getTimeInMillis());

        long lastChangeTime = 1414018435954L;
        Calendar temp = Calendar.getInstance();
        temp.setTimeInMillis(lastChangeTime);
        logger.info("lastChangeTime + " + temp.getTime() + ", " + temp.getTimeInMillis());

        long days30InMillis = PasswordUtils.dayToMilliSeconds(30);
        logger.info("Days30InMillis: " + days30InMillis);
        long longNewExpireTime = lastChangeTime + days30InMillis;
        logger.info("longNewExpireTime: " + longNewExpireTime);
        Calendar newExpireTime = Calendar.getInstance();
        newExpireTime.setTimeInMillis(longNewExpireTime);
        logger.info("newExpireTime: " + newExpireTime.getTime() + ", " + newExpireTime.getTimeInMillis());

    }
}
