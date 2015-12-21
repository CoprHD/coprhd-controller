/*
 * Copyright (c) 2012-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices;

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.coordinator.exceptions.InvalidSoftwareVersionException;
import com.emc.storageos.coordinator.client.model.SoftwareVersion;

public class SoftwareVersionTest {

    private static String toNoPrefix(String versionStr) {
        final String[] tokens = versionStr.split("-");
        return tokens[tokens.length - 1];
    }

    private void selfTest(String versionStr) throws Exception {
        System.out.println("selfTest: " + versionStr);

        final SoftwareVersion version = new SoftwareVersion(versionStr);
        Assert.assertNotNull(version);
        Assert.assertTrue(version.equals(new SoftwareVersion(versionStr)));
        Assert.assertTrue(version.compareTo(new SoftwareVersion(versionStr)) == 0);

        final String noPrefixVersionStr = toNoPrefix(versionStr);
        final SoftwareVersion noPrefixVersion = new SoftwareVersion(noPrefixVersionStr);
        Assert.assertNotNull(noPrefixVersion);
        Assert.assertTrue(noPrefixVersion.equals(new SoftwareVersion(noPrefixVersionStr)));
        Assert.assertTrue(noPrefixVersion.compareTo(new SoftwareVersion(noPrefixVersionStr)) == 0);

        Assert.assertTrue(version.equals(noPrefixVersion));
        Assert.assertTrue(version.compareTo(noPrefixVersion) == 0);
    }

    // Call with A < B
    private void pairTest(String versionStrA, String versionStrB) throws Exception {
        System.out.println("pairTest: " + versionStrA + " : " + versionStrB);

        final SoftwareVersion versionA = new SoftwareVersion(versionStrA);
        final SoftwareVersion versionB = new SoftwareVersion(versionStrB);

        final String noPrefixVersionStrA = toNoPrefix(versionStrA);
        final SoftwareVersion noPrefixVersionA = new SoftwareVersion(noPrefixVersionStrA);
        final String noPrefixVersionStrB = toNoPrefix(versionStrB);
        final SoftwareVersion noPrefixVersionB = new SoftwareVersion(noPrefixVersionStrB);

        Assert.assertFalse(versionA.equals(versionB));
        Assert.assertFalse(versionB.equals(versionA));
        Assert.assertTrue(versionA.compareTo(versionB) < 0);
        Assert.assertTrue(versionB.compareTo(versionA) > 0);

        Assert.assertFalse(noPrefixVersionA.equals(noPrefixVersionB));
        Assert.assertFalse(noPrefixVersionB.equals(noPrefixVersionA));
        Assert.assertTrue(noPrefixVersionA.compareTo(noPrefixVersionB) < 0);
        Assert.assertTrue(noPrefixVersionB.compareTo(noPrefixVersionA) > 0);

        Assert.assertFalse(versionA.equals(noPrefixVersionB));
        Assert.assertFalse(noPrefixVersionB.equals(versionA));
        Assert.assertTrue(versionA.compareTo(noPrefixVersionB) < 0);
        Assert.assertTrue(noPrefixVersionB.compareTo(versionA) > 0);

        Assert.assertFalse(noPrefixVersionA.equals(versionB));
        Assert.assertFalse(versionB.equals(noPrefixVersionA));
        Assert.assertTrue(noPrefixVersionA.compareTo(versionB) < 0);
        Assert.assertTrue(versionB.compareTo(noPrefixVersionA) > 0);
    }

    private boolean isUp(String versionStrA, String versionStrB) throws Exception {
        System.out.println("isUp: " + versionStrA + " : " + versionStrB);
        return new SoftwareVersion(versionStrA).isSwitchableTo(new SoftwareVersion(versionStrB));
    }

    // Suppress Sonar warning that created objects are never used. The TestProductName constructor is called to set static fields. The
    // SoftwareVersion constructor is called to validate the version string.
    @SuppressWarnings("squid:S1848")
    @Test
    public void testSoftwareVersion() throws Exception {
        new TestProductName();
        // Positive tests
        selfTest("vipr-1.0.0.0.r500");
        selfTest("vipr-1.0.0.0.500");
        selfTest("vipr-1.0.0.33.r500");
        selfTest("vipr-1.0.0.33.500");
        pairTest("vipr-1.0.0.0.r500", "vipr-1.0.0.0.r501");
        pairTest("vipr-1.0.0.0.r500", "vipr-1.0.0.0.500");
        pairTest("vipr-1.0.0.0.r499", "vipr-1.0.0.0.500");
        pairTest("vipr-1.0.0.0.r501", "vipr-1.0.0.0.500");
        pairTest("vipr-1.0.0.0.500", "vipr-1.0.0.0.501");
        pairTest("vipr-1.0.0.0.500", "vipr-1.0.0.1.400");
        pairTest("vipr-1.0.0.0.500", "vipr-1.0.0.33.400");
        pairTest("vipr-1.0.0.0.500", "vipr-1.1.0.0.400");
        pairTest("vipr-1.0.0.0.500", "vipr-1.33.0.0.400");
        pairTest("vipr-1.0.0.0.5", "vipr-1.0.0.0.400");
        Assert.assertTrue(isUp("vipr-1.0.0.0.r500", "vipr-1.0.0.0.r555"));
        Assert.assertTrue(isUp("vipr-1.0.0.0.r500", "vipr-1.0.0.0.555"));
        Assert.assertTrue(isUp("vipr-1.0.0.0.r555", "vipr-1.0.0.0.r500"));
        Assert.assertTrue(isUp("vipr-1.0.0.0.555", "vipr-1.0.0.0.r500"));
        Assert.assertTrue(isUp("vipr-1.0.0.0.500", "vipr-1.0.0.0.555"));
        Assert.assertTrue(isUp("vipr-1.0.0.0.555", "vipr-1.0.0.0.500"));
        Assert.assertTrue(isUp("vipr-1.0.0.4.r500", "vipr-1.0.0.5.r555"));
        Assert.assertTrue(isUp("vipr-1.0.0.4.r500", "vipr-1.0.0.5.555"));
        Assert.assertTrue(isUp("vipr-1.0.0.4.r555", "vipr-1.0.0.5.r500"));
        Assert.assertTrue(isUp("vipr-1.0.0.4.555", "vipr-1.0.0.5.r500"));
        Assert.assertTrue(isUp("vipr-1.0.0.4.500", "vipr-1.0.0.5.555"));
        Assert.assertTrue(isUp("vipr-1.0.0.4.555", "vipr-1.0.0.5.500"));

        // Negative format tests
        try {
            new SoftwareVersion("vipr-1.00.1.3.r500");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }

        try {
            new SoftwareVersion("vipr-1.00.1.3.500");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }

        try {
            new SoftwareVersion("vipr-1.0.01.3.r500");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }

        try {
            new SoftwareVersion("vipr-1.0.01.3.500");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }

        try {
            new SoftwareVersion("1-0.0.0.r500");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }

        try {
            new SoftwareVersion("vipr-1.0.0.r500");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }

        try {
            new SoftwareVersion("vipr-1.0.0.0.");
            Assert.assertNotNull("Invalid format accepted");
        } catch (InvalidSoftwareVersionException e) {
            Assert.assertNotNull(e);
        }
    }
}
