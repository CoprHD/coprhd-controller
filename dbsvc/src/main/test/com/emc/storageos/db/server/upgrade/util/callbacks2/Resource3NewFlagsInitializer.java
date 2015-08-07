/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.server.upgrade.util.callbacks2;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.server.upgrade.util.BaseTestCustomMigrationCallback;
import com.emc.storageos.db.server.upgrade.util.models.updated2.Resource3;
import com.emc.storageos.db.server.upgrade.util.models.updated2.Resource6;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.netflix.astyanax.connectionpool.exceptions.OperationException;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;

/**
 * initialize flags on Resource 3 and all its subclasses
 */
public class Resource3NewFlagsInitializer extends BaseTestCustomMigrationCallback {
    public static boolean injectFault = false; // set true to inject a retryable exception
    public static boolean injectFatalFault = false;  // set true to inject fatal exception
    public static boolean faultInjected = false; // will be set to true after we inject the false

    @Override
    public void process() {

        DbClient dbClient = getDbClient();

        // check if we need to inject
        if (injectFault) {
            faultInjected = true;
            throw DatabaseException.retryables.operationFailed(new OperationException("Custom callback execuction error. Injected fault"));
        }

        // check if we need to inject
        if (injectFatalFault) {
            faultInjected = true;
            throw DatabaseException.fatals.failedDuringUpgrade("Injected fatal exception during upgrade", null);
        }

        // Check Resource3
        List<URI> res3Keys = dbClient.queryByType(Resource3.class, false);
        Iterator<Resource3> res3Objs =
                dbClient.queryIterativeObjects(Resource3.class, res3Keys);
        while (res3Objs.hasNext()) {
            Resource3 res3 = res3Objs.next();

            // Resource3FlagsInitializer should be executed first so extraFlags has value
            Long extraFlags = res3.getExtraFlags();
            if (extraFlags == null) {
                throw new IllegalStateException(
                        "Custom callback order error. Resource3FlagsInitializer should be executed first for Resource3.");
            }

            // Current value for new flag - should be zero always
            // Custom callback should be executed only once even referenced by many fields
            Long currentValue = res3.getNewFlags();
            if (currentValue != null) {
                throw new IllegalStateException("Custom callback order error. Resource3NewFlagsInitializer should not be executed twice.");
            }

            res3.setNewFlags(extraFlags);
            dbClient.persistObject(res3);
        }

        // Check Resource6
        List<URI> res6Keys = dbClient.queryByType(Resource6.class, false);
        Iterator<Resource6> res6Objs =
                dbClient.queryIterativeObjects(Resource6.class, res6Keys);
        while (res6Objs.hasNext()) {
            Resource6 res6 = res6Objs.next();

            // Resource3FlagsInitializer should be executed first so extraFlags has value
            Long extraFlags = res6.getExtraFlags();
            if (extraFlags == null) {
                throw new IllegalStateException(
                        "Custom callback order error. Resource3FlagsInitializer should be executed first for Resource6.");
            }

            // Current value for new flag - should be zero always
            // Custom callback should be executed only once even referenced by many fields
            Long currentValue = res6.getNewFlags();
            if (currentValue != null) {
                throw new IllegalStateException("Custom callback order error. Resource3NewFlagsInitializer should not be executed twice.");
            }

            res6.setNewFlags(extraFlags);
            res6.setDupTestFlags(extraFlags);
            dbClient.persistObject(res6);
        }
    }

    @Override
    public void verify() {

        // Check Resource3
        List<URI> res3Keys = dbClient.queryByType(Resource3.class, false);
        Iterator<Resource3> res3Objs =
                dbClient.queryIterativeObjects(Resource3.class, res3Keys);
        Assert.assertTrue(res3Objs.hasNext());
        while (res3Objs.hasNext()) {
            Resource3 res3 = res3Objs.next();
            Assert.assertEquals(res3.getExtraFlags(), res3.getNewFlags());
        }

        // Check Resource6
        List<URI> res6Keys = dbClient.queryByType(Resource6.class, false);
        Iterator<Resource6> res6Objs =
                dbClient.queryIterativeObjects(Resource6.class, res6Keys);
        while (res6Objs.hasNext()) {
            Resource6 res6 = res6Objs.next();
            Assert.assertEquals(res6.getExtraFlags(), res6.getNewFlags());
            Assert.assertEquals(res6.getExtraFlags(), res6.getDupTestFlags());
        }

    }
}
