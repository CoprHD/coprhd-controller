/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices;

import com.emc.storageos.coordinator.client.model.SoftwareVersion;
import com.emc.storageos.coordinator.client.model.ProductName;
import com.emc.storageos.coordinator.client.model.RepositoryInfo;

import com.emc.storageos.systemservices.impl.upgrade.SyncInfo;
import com.emc.storageos.systemservices.impl.upgrade.SyncInfoBuilder;

import org.junit.Assert;
import org.junit.Test;
import java.util.*;

/**
 * Tests SyncInfoBuilder class
 */
public class SyncInfoTest {
    
    private List<SoftwareVersion> arrayToList(String[] versions) throws Exception {
        List<SoftwareVersion> listVersions = new ArrayList<SoftwareVersion>();
        for (String version: versions) {
            listVersions.add(new SoftwareVersion(version));
        }
        return listVersions;
    }

    private void verifyEqual(SyncInfo result, String toInstall, String[] toRemove) throws Exception {
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue("toInstall " + result.getToInstall() + " not same as expected",
                (!toInstall.isEmpty()) ? (result.getToInstall().get(0).equals(new SoftwareVersion(toInstall))):
                        (result.getToInstall() == null || result.getToInstall().size() == 0));
        Assert.assertTrue("toRemove not same", result.getToRemove().size() == toRemove.length);
        for (String version: toRemove) {
            Assert.assertTrue("toRemove not same", result.getToRemove().contains(new SoftwareVersion(version)));
        }
    }

    private void verifyEqual(SyncInfo result, String[] toInstall, String[] toRemove) throws Exception {
        Assert.assertFalse(result.isEmpty());
        Assert.assertTrue("toInstall not same", result.getToInstall().size() == toInstall.length);
        for (String version: toInstall) {
            Assert.assertTrue("toRemove not same", result.getToInstall().contains(new SoftwareVersion(version)));
        }
        Assert.assertTrue("toRemove not same", result.getToRemove().size() == toRemove.length);
        for (String version: toRemove) {
            Assert.assertTrue("toRemove not same", result.getToRemove().contains(new SoftwareVersion(version)));
        }
    }

    @Test
    public void testLocal() throws Exception {
        new TestProductName();
        // tests getLeaderSyncInfo
        List<SoftwareVersion> localVersions = arrayToList(new String[] {
                "storageos-1.0.0.0.r500", "storageos-1.0.0.0.r555", "storageos-1.0.0.1.r500" });
        List<SoftwareVersion> remoteVersions = arrayToList(new String[] {
                "storageos-1.0.0.2.r500", "storageos-1.0.0.0.r555", "storageos-1.0.0.1.r500" });
        
        // invalid args
        RepositoryInfo localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.1.r500"),
                remoteVersions);
        RepositoryInfo remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.1.r500"),
                remoteVersions);
        Assert.assertTrue(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState).isEmpty());

        // test -1   -  add 1, nothing to remove
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r555"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r500"),
                localVersions);
        SyncInfo syncInfo = SyncInfoBuilder.getTargetSyncInfo(localState, remoteState);
        Assert.assertTrue(syncInfo.getToRemove().isEmpty());
        Assert.assertEquals(arrayToList(new String[]{"storageos-1.0.0.2.r500"}),syncInfo.getToInstall());
        
        // test 2  -  sync 1, remove 1
        localVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r500", "storageos-1.0.0.0.r555", "storageos-1.0.0.1.r500" });
        remoteVersions = arrayToList(new String[] {
            "storageos-1.0.0.1.r555", "storageos-1.0.0.0.r555", "storageos-1.0.0.1.r500" });
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.1.r500"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r555"),
                localVersions);
        verifyEqual(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState), "storageos-1.0.0.1.r555",
                new String[]{});

        // test 3 -  sync to remote current
        // phase - 1
        localVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r500", "storageos-1.0.0.0.r555", "storageos-1.0.0.1.r500" });
        remoteVersions = arrayToList(new String[] {
            "storageos-1.0.0.2.r555", "storageos-1.0.0.1.r500", "storageos-1.0.0.2.r500" });
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.2.r555"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.1.r500"),
                localVersions);
        verifyEqual(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState), "storageos-1.0.0.2.r500",
                new String[] {});

        // phase 2
        localVersions = arrayToList(new String[] {
            "storageos-1.0.0.2.r500", "storageos-1.0.0.0.r555", "storageos-1.0.0.1.r500"});
        remoteVersions = arrayToList(new String[] {
            "storageos-1.0.0.2.r555", "storageos-1.0.0.1.r500", "storageos-1.0.0.2.r500"});
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.2.r555"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.1.r500"),
                localVersions);
        verifyEqual(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState), "storageos-1.0.0.2.r555",
                new String[] {});

        // test 4 - complete disjoint , upgradable
        // phase 1
        localVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r500", "storageos-1.0.0.0.r555", "storageos-1.0.0.0.r600"});
        remoteVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r700", "storageos-1.0.0.0.r750", "storageos-1.0.0.0.r800"});
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r800"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r500"),
                localVersions);
        verifyEqual(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState), "storageos-1.0.0.0.r700",
                new String[] {});
        // phase 2
        localVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r500", "storageos-1.0.0.0.r700", "storageos-1.0.0.0.r600"});
        remoteVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r700", "storageos-1.0.0.0.r750", "storageos-1.0.0.0.r800"});
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r800"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r500"),
                localVersions);
        verifyEqual(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState), "storageos-1.0.0.0.r750",
                new String[] {});

        // phase 3 - nothing to do - no-deletable
        localVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r500", "storageos-1.0.0.0.r700", "storageos-1.0.0.0.r750"});
        remoteVersions = arrayToList(new String[] {
            "storageos-1.0.0.0.r700", "storageos-1.0.0.0.r750", "storageos-1.0.0.0.r800"});
        remoteState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r800"),
                remoteVersions);
        localState = new RepositoryInfo(new SoftwareVersion("storageos-1.0.0.0.r500"),
                localVersions);
        verifyEqual(SyncInfoBuilder.getTargetSyncInfo(localState, remoteState), "storageos-1.0.0.0.r800",
                new String[] {});
    }

}
