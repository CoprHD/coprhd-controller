/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DataCollectionJobStatus;

public class DiscoveryStatusUtils {

    /**
     * Marks the target as processing.
     * 
     * @param target
     *            the target object.
     */
    public static void markAsProcessing(ModelClient modelClient, DataObject target) {
        if (target instanceof DiscoveredSystemObject) {
            DiscoveredSystemObject obj = (DiscoveredSystemObject) target;
            obj.setDiscoveryStatus(DataCollectionJobStatus.IN_PROGRESS.name());
            modelClient.save(obj);
        }
    }

    /**
     * Marks the target as succeeded.
     * 
     * @param target
     *            the target object.
     */
    public static void markAsSucceeded(ModelClient modelClient, DataObject target) {
        if (target instanceof DiscoveredSystemObject) {
            DiscoveredSystemObject obj = (DiscoveredSystemObject) target;
            obj.setDiscoveryStatus(DataCollectionJobStatus.COMPLETE.name());
            obj.setLastDiscoveryStatusMessage("");
            obj.setLastDiscoveryRunTime(System.currentTimeMillis());
            obj.setSuccessDiscoveryTime(System.currentTimeMillis());
            modelClient.save(obj);
        }
    }

    /**
     * Marks the target as incompatible.
     * 
     * @param target
     *            the target object.
     */
    public static void markAsIncompatible(ModelClient modelClient, DataObject target, String message) {
        if (target instanceof DiscoveredSystemObject) {
            DiscoveredSystemObject obj = (DiscoveredSystemObject) target;
            obj.setDiscoveryStatus(DataCollectionJobStatus.COMPLETE.name());
            obj.setCompatibilityStatus(CompatibilityStatus.INCOMPATIBLE.name());
            obj.setLastDiscoveryStatusMessage(message);
            obj.setLastDiscoveryRunTime(System.currentTimeMillis());
            modelClient.save(obj);
        }
    }

    /**
     * Marks the target as failed.
     * 
     * @param target
     *            the target object.
     * @param message
     *            the error message
     * @param e
     *            the error that caused the failure.
     */
    public static void markAsFailed(ModelClient modelClient, DataObject target, String message, Exception e) {
        if (target instanceof DiscoveredSystemObject) {
            DiscoveredSystemObject obj = (DiscoveredSystemObject) target;
            obj.setDiscoveryStatus(DataCollectionJobStatus.ERROR.name());
            obj.setLastDiscoveryStatusMessage(message);
            obj.setLastDiscoveryRunTime(System.currentTimeMillis());
            modelClient.save(obj);
        }
    }

    /**
     * Marks the target as ignored.
     * 
     * @param target
     *            the target object.
     */
    public static void markAsIgnored(ModelClient modelClient, DataObject target) {
        if (target instanceof DiscoveredSystemObject) {
            DiscoveredSystemObject obj = (DiscoveredSystemObject) target;
            obj.setCompatibilityStatus(CompatibilityStatus.UNKNOWN.name());
            obj.setDiscoveryStatus(DataCollectionJobStatus.COMPLETE.name());
            obj.setLastDiscoveryStatusMessage("");
            obj.setLastDiscoveryRunTime(System.currentTimeMillis());
            modelClient.save(obj);
        }
    }
}
