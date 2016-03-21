/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.api;

import java.util.concurrent.atomic.AtomicReference;

public class ScaleIOConstants {
    static final String REGEX_CAPACITY = "\\s+\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    static final String REGEX_CAPACITY_NO_SPACE_IN_FRONT = "\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    static final String REGEX_BYTES_CAPACITY = "\\s+(\\d+)\\s+Bytes\\s+";

    public static final String API_LOGIN = "/api/login";
    public static final String ERROR_CODE = "httpStatusCode";
    public static final String GET_SYSTEMS_URI = "/api/types/System/instances";
    public static final String GET_SDS_URI = "/api/types/Sds/instances";
    public static final String GET_PROTECTION_DOMAIN_URI = "/api/types/ProtectionDomain/instances";
    public static final String GET_SDC_URI = "/api/types/Sdc/instances";
    public static final String GET_SCSI_INITIATOR_URI = "/api/types/ScsiInitiator/instances";
    public static final String VOLUMES_URI = "/api/types/Volume/instances";
    public static final String GET_VOLUMES_BYIDS_URI = "/api/types/Volume/instances/action/queryBySelectedIds";
    public static final String NAME = "Name";
    public static final String POOL_ID = "PoolId";
    public static final String THIN_PROVISIONED = "ThinProvisioned";
    public static final String THICK_PROVISIONED = "ThickProvisioned";

    public static final String SCALEIO_VERSION = "Version";
    public static final String SCALEIO_CUSTOMER_ID = "CustomerID";
    public static final String SCALEIO_INSTALLATION_ID = "InstallationID";
    public static final String SCALEIO_TOTAL_CAPACITY = "TotalCapacity";
    public static final String SCALEIO_FREE_CAPACITY = "FreeCapacity";
    public static final String SCALEIO_IN_USE_CAPACITY = "InUseCapacity";
    public static final String SCALEIO_PROTECTED_CAPACITY = "ProtectedCapacity";
    public static final String SCALEIO_SNAPSHOT_CAPACITY = "SnapshotCapacity";
    public static final String IP_ADDRESS="IpAddress";
    public static final String PORT_NUMBER="PortNumber";
    public static final String USER_NAME="UserName";
    public static final String PASSWORD="Password";
    public static final String DRIVER_NAME="ScaleIO";
    public static final Double MINIMUM_SUPPORTED_VERSION=1.32;
    public static final int START_POS=0;
    public static final int END_POS=4;
    public static final Boolean INCOMPATIBLE=false;
    public static final Boolean COMPATIBLE=true;
    public static final String OPERATIONAL_STATUS_CONNECTED="Normal";
    public static final Long maxVolumeSize = 1048576L;
    public static final Long minVolumeSize = 1L;
    public static final Long GB_BYTE = 1073741824L;
    public enum TaskType{
        DISCOVER_STORAGE_SYSTEM,
        DISCOVER_STORAGE_POOLS,
        DISCOVER_STORAGE_PORTS,
        DISCOVER_STORAGE_HOSTS,

        VOLUME_CREATE,
        VOLUME_EXPAND,
        VOLUME_DELETE,

        SNAPSHOT_CREATE,
        SNAPSHOT_DELETE,

        CLONE_CREATE,
        CLONE_DETACH,
        CLONE_DELETE,

        GET_ITL,
        EXPORT,
        UNEXPORT,

        CG_SNAP_CREATE,
        CG_SNAP_DELETE,
        CG_CLONE_CREATE,
        CG_CLONE_DELETE,
        CG_CLONE_DETACH,

        /*Not Supported Operations in ScaleIO*/
        SNAPSHOT_RESTORE,
        MIRROR_OPERATIONS,
        CLONE_RESTORE,
        CG_CREATE,
        CG_DELETE


    }

    enum PoolCapacityMultiplier {

        BYTES("Bytes", 1),
        KB_BYTES("KB", 1024),
        MB_BYTES("MB", 1048576),
        GB_BYTES("GB", 1073741824),
        TB_BYTES("TB", 1099511627776L),
        PB_BYTES("PB", 1125899906842624L);

        private String postFix;
        private long multiplier;

        PoolCapacityMultiplier(String name, long multipler) {
            this.postFix = name;
            this.multiplier = multipler;
        }

        static AtomicReference<PoolCapacityMultiplier[]> cachedValues =
                new AtomicReference<PoolCapacityMultiplier[]>();

        static PoolCapacityMultiplier matches(String name) {
            if (cachedValues.get() == null) {
                cachedValues.compareAndSet(null, values());
            }
            for (PoolCapacityMultiplier thisMultiplier : cachedValues.get()) {
                if (name.endsWith(thisMultiplier.postFix)) {
                    return thisMultiplier;
                }
            }
            return null;
        }

        String getPostFix() {
            return postFix;
        }

        public long getMultiplier() {
            return multiplier;
        }

    }

    public static String getAPIBaseURI(String ipAddress, int port) {
        return String.format("https://%1$s:%2$d", ipAddress, port);
    }

    public static String getProtectionDomainStoragePoolURI(String protectionDomainId) {
        return String.format("/api/instances/ProtectionDomain::%1$s/relationships/StoragePool", protectionDomainId);
    }

    public static String getStoragePoolStatsURI(String poolId) {
        return String.format("/api/instances/StoragePool::%1$s/relationships/Statistics", poolId);
    }

    public static String getVolumeURI(String volId) {
        return String.format("/api/instances/Volume::%1$s", volId);
    }

    public static String getRemoveVolumeURI(String volId) {
        return String.format("/api/instances/Volume::%1$s/action/removeVolume", volId);
    }

    public static String getModifyVolumeSizeURI(String volId) {
        return String.format("/api/instances/Volume::%1$s/action/setVolumeSize", volId);
    }

    public static String getMapVolumeToSDCURI(String volId) {
        return String.format("/api/instances/Volume::%1$s/action/addMappedSdc", volId);
    }

    public static String getUnmapVolumeToSDCURI(String volId) {
        return String.format("/api/instances/Volume::%1$s/action/removeMappedSdc", volId);
    }

    public static String getMapVolumeToScsiInitiatorURI(String volId) {
        return String.format("/api/instances/Volume::%1$s/action/addMappedScsiInitiator", volId);
    }

    public static String getUnmapVolumeToScsiInitiatorURI(String volId) {
        return String.format("/api/instances/Volume::%1$s/action/removeMappedScsiInitiator", volId);
    }

    public static String getSnapshotVolumesURI(String systemId) {
        return String.format("/api/instances/System::%1$s/action/snapshotVolumes", systemId);
    }

    public static String getRemoveConsistencyGroupSnapshotsURI(String systemId) {
        return String.format("/api/instances/System::%1$s/action/removeConsistencyGroupSnapshots", systemId);
    }

}
