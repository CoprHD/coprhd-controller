/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.machinetags;

import java.net.URI;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.BlockObjectRestRep;

public class KnownMachineTags {
    public static String ISA_NAMESPACE = "vipr";

    private static String MOUNTPOINT = fqnName(ISA_NAMESPACE, "mountPoint");
    private static String VMFS_DATASTORE = fqnName(ISA_NAMESPACE, "vmfsDatastore");
    private static String BOOT_VOLUME = fqnName(ISA_NAMESPACE, "bootVolume");
    private static String ORDER_ID = fqnName(ISA_NAMESPACE, "orderId");
    private static String ORDER_NUMBER = fqnName(ISA_NAMESPACE, "orderNumber");

    public static String getHostMountPointTagName(URI hostId) {
        return MOUNTPOINT + "-" + hostId;
    }

    public static String getHostMountPointTagName() {
        return MOUNTPOINT;
    }

    public static String getVMFSDatastoreTagName(URI hostId) {
        return VMFS_DATASTORE + "-" + hostId;
    }

    public static String getVmfsDatastoreTagName() {
        return VMFS_DATASTORE;
    }

    public static String getBootVolumeTagName() {
        return BOOT_VOLUME;
    }

    public static String getOrderIdTagName() {
        return ORDER_ID;
    }

    public static String getOrderNumberTagName() {
        return ORDER_NUMBER;
    }

    public static String getBlockVolumeMountPoint(URI hostId, BlockObjectRestRep blockObject) {
        if (hostId == null) {
            return null;
        }
        return MachineTagUtils.getBlockVolumeTag(blockObject, getHostMountPointTagName(hostId));
    }

    public static String getBlockVolumeVMFSDatastore(URI hostId, BlockObjectRestRep blockObject) {
        return MachineTagUtils.getBlockVolumeTag(blockObject, getVMFSDatastoreTagName(hostId));
    }

    public static String getBlockVolumeBootVolume(BlockObjectRestRep blockObject) {
        return MachineTagUtils.getBlockVolumeTag(blockObject, getBootVolumeTagName());
    }

    public static String getTaskOrderId(TaskResourceRep taskResourceRep) {
        return MachineTagUtils.getTaskTag(taskResourceRep, getOrderIdTagName());
    }

    public static String getTaskOrderNumber(TaskResourceRep taskResourceRep) {
        return MachineTagUtils.getTaskTag(taskResourceRep, getOrderNumberTagName());
    }

    private static String fqnName(String namespace, String name) {
        return namespace + ":" + name;
    }
}
