/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.scaleio.api;

import java.util.concurrent.atomic.AtomicReference;

public class ScaleIOContants {
    static final String REGEX_CAPACITY = "\\s+\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    static final String REGEX_CAPACITY_NO_SPACE_IN_FRONT = "\\d+(?:\\.\\d+)?\\s+\\w{2}\\s+\\((.*?)\\)";
    static final String REGEX_BYTES_CAPACITY = "\\s+(\\d+)\\s+Bytes\\s+";
    
    public static final String API_LOGIN="/api/login";
    public static final String ERROR_CODE = "httpStatusCode";
    public static final String GET_SYSTEMS_URI="/api/types/System/instances";
    public static final String GET_SDS_URI= "/api/types/Sds/instances";
    public static final String GET_PROTECTION_DOMAIN_URI="/api/types/ProtectionDomain/instances";
    public static final String GET_SDC_URI="/api/types/Sdc/instances";
    public static final String GET_SCSI_INITIATOR_URI="/api/types/ScsiInitiator/instances";
    public static final String VOLUMES_URI="/api/types/Volume/instances";
    public static final String GET_VOLUMES_BYIDS_URI="/api/types/Volume/instances/action/queryBySelectedIds";
    public static final String NAME="Name";
    public static final String POOL_ID="PoolId";
    public static final String THIN_PROVISIONED = "ThinProvisioned";
    public static final String THICK_PROVISIONED = "ThickProvisioned";

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
        return String.format("https://%1$s:%2$d", ipAddress,port);
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
