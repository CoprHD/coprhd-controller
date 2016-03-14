/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Info for a VPlex virtual volume.
 */
public class VPlexVirtualVolumeInfo extends VPlexResourceInfo {

    // Values for rebuild completion status
    public enum WaitOnRebuildResult {
        SUCCESS,
        FAILED,
        TIMED_OUT,
        INVALID_REQUEST
    }

    // Values for expansion status
    public enum ExpansionStatus {
        INPROGRESS("in-progress");

        // The VPlex expansion status value.
        private String _status;

        /**
         * Constructor.
         * 
         * @param status The VPlex expansion status value
         */
        ExpansionStatus(String status) {
            _status = status;
        }

        /**
         * Getter for the VPlex expansion status value.
         * 
         * @return The VPlex expansion status value.
         */
        public String getStatus() {
            return _status;
        }
    }

    // Values for service status
    public enum ServiceStatus {
        unexported;
    }

    // Values for service status
    public enum Locality {
        local,
        distributed;
    }

    // Enumerates the virtual volume attributes we are interested in and
    // parse from the VPlex virtual volume response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum VirtualVolumeAttribute {
        BLOCK_COUNT("block-count"),
        BLOCK_SIZE("block-size"),
        EXPANSION_STATUS("expansion-status"),
        SUPPORTING_DEVICE("supporting-device"),
        SERVICE_STATUS("service-status"),
        LOCALITY("locality"),
        VPD_ID("vpd-id");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        VirtualVolumeAttribute(String name) {
            _name = name;
        }

        /**
         * Getter for the VPlex name for the attribute.
         * 
         * @return The VPlex name for the attribute.
         */
        public String getAttributeName() {
            return _name;
        }

        /**
         * Returns the enum whose name matches the passed name, else null when
         * not found.
         * 
         * @param name The name to match.
         * 
         * @return The enum whose name matches the passed name, else null when
         *         not found.
         */
        public static VirtualVolumeAttribute valueOfAttribute(String name) {
            VirtualVolumeAttribute[] volumeAtts = values();
            for (int i = 0; i < volumeAtts.length; i++) {
                if (volumeAtts[i].getAttributeName().equals(name)) {
                    return volumeAtts[i];
                }
            }
            return null;
        }
    };

    // The block count.
    private String blockCount;

    // The block size in Bytes.
    private String blockSize;

    // The expansion status
    private String expansionStatus;

    // The name of the local or distributed device supporting
    // this virtual volume.
    private String supportingDevice;

    // A reference to the supporting device info, which could be
    // a VPlexDeviceInfo or VPlexDistributedDeviceInfo depending
    // upon the locality of the volume.
    private VPlexResourceInfo supportingDeviceInfo;

    // The service status
    private String serviceStatus;

    // The locality of the virtual volume.
    private String locality;

    // The clusters for the virtual volume.
    private List<String> clusters = new ArrayList<String>();

    // The volume id containing the wwn
    private String vpdId;

    /**
     * Getter for the volume block count.
     * 
     * @return The volume block count.
     */
    public String getBlockCount() {
        return blockCount;
    }

    /**
     * Setter for the volume block count.
     * 
     * @param strVal The volume block count.
     */
    public void setBlockCount(String strVal) {
        blockCount = strVal;
    }

    /**
     * Getter for the volume block size.
     * 
     * @return The volume block size.
     */
    public String getBlockSize() {
        return blockSize;
    }

    /**
     * Setter for the volume block size.
     * 
     * @param strVal The volume block size.
     */
    public void setBlockSize(String strVal) {
        blockSize = strVal;
    }

    /**
     * Getter for the volume expansion status.
     * 
     * @return The volume expansion status.
     */
    public String getExpansionStatus() {
        return expansionStatus;
    }

    /**
     * Setter for the volume expansion status.
     * 
     * @param strVal The volume expansion status.
     */
    public void setExpansionStatus(String strVal) {
        expansionStatus = strVal;
    }

    /**
     * Getter for the supporting device name.
     * 
     * @return The supporting device name.
     */
    public String getSupportingDevice() {
        return supportingDevice;
    }

    /**
     * Setter for the supporting device name.
     * 
     * @param strVal The supporting device name.
     */
    public void setSupportingDevice(String strVal) {
        supportingDevice = strVal;
    }

    /**
     * Getter for the supporting device information.
     * 
     * @return The supporting device information.
     */
    public VPlexResourceInfo getSupportingDeviceInfo() {
        return supportingDeviceInfo;
    }

    /**
     * Setter for the supporting device information.
     * 
     * @param resourceInfo The supporting device information.
     */
    public void setSupportingDeviceInfo(VPlexResourceInfo resourceInfo) {
        supportingDeviceInfo = resourceInfo;
    }

    /**
     * Getter for the volume service status.
     * 
     * @return The volume service status.
     */
    public String getServiceStatus() {
        return serviceStatus;
    }

    /**
     * Setter for the volume service status.
     * 
     * @param strVal The volume service status.
     */
    public void setServiceStatus(String strVal) {
        serviceStatus = strVal;
    }

    /**
     * Getter for the volume locality.
     * 
     * @return The volume locality.
     */
    public String getLocality() {
        return locality;
    }

    /**
     * Setter for the volume locality.
     * 
     * @param strVal The volume locality.
     */
    public void setLocality(String strVal) {
        locality = strVal;
    }

    /**
     * Getter for the volume clusters.
     * 
     * @return The volume clusters.
     */
    public List<String> getClusters() {
        return clusters;
    }

    /**
     * Adds the passed cluster to the list of clusters for the volume.
     * 
     * @param clusterId The clusterId.
     */
    public void addCluster(String clusterId) {
        if (!clusters.contains(clusterId)) {
            clusters.add(clusterId);
        }
    }
    
    /**
     * Getter for the volume vpd-id.
     * 
     * @return The volume vpd-id.
     */
    public String getVpdId() {
        return vpdId;
    }
    
    /**
     * Setter for the volume vpd-id.
     * 
     * @param strVal The volume vpd-id.
     */
    public void setVpdId(String strVal) {
        vpdId = strVal;
    }
    
    /**
     * Getter for the volume WWN, parsed
     * from the vpd-id value.
     * 
     * @return the volume's WWN or null if none
     */
    public String getWwn() {
        if (null != vpdId) {
            if (vpdId.startsWith(VPlexApiConstants.VOLUME_WWN_PREFIX)) {
                return vpdId.substring(VPlexApiConstants.VOLUME_WWN_PREFIX.length());
            }
        }
        
        return null;
    }
    
    /**
     * Return the virtual volume capacity in bytes.
     * 
     * @return The virtual volume capacity in bytes.
     * 
     * @throws VPlexApiException For an invalid formatted capacity.
     */
    public Long getCapacityBytes() throws VPlexApiException {
        if ((blockCount == null) || (VPlexApiConstants.NULL_ATT_VAL.equals(blockCount)) ||
                (blockSize == null) || (VPlexApiConstants.NULL_ATT_VAL.equals(blockSize))) {
            return null;
        }

        // Note block size is assumed to be in Bytes, which is what the
        // VPlex returns.
        Pattern p = Pattern.compile("(\\d+)");
        Matcher m = p.matcher(blockSize);
        if (m.find()) {
            Long blockSizeBytes = Long.valueOf(m.group(1));
            m = p.matcher(blockCount);
            if (m.find()) {
                return Long.valueOf(m.group(1)) * blockSizeBytes;
            } else {
                throw VPlexApiException.exceptions.unexpectedBlockCountFormat(blockCount);
            }
        } else {
            throw VPlexApiException.exceptions.unexpectedBlockSizeFormat(blockSize);
        }
    }

    /**
     * Update the virtual volume name after path of the virtual volume
     * when a migration associated with the virtual volume is committed.
     * 
     * @param updatedName The new name.
     */
    public void updateNameOnMigrationCommit(String updatedName) {
        // When a migration for the virtual volume is committed, we
        // update the name and path to reflect the new underlying
        // volume, which is the migration target.
        String currentPath = getPath();
        setPath(currentPath.replace(getName(), updatedName));
        setName(updatedName);
    }

    /*
     * Returns whether or not the volume is exported.
     * 
     * @return true if the volume is exported, false otherwise.
     */
    public boolean isExported() {
        return (!ServiceStatus.unexported.name().equals(serviceStatus));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters() {
        List<String> attFilters = new ArrayList<String>();
        for (VirtualVolumeAttribute att : VirtualVolumeAttribute.values()) {
            attFilters.add(att.getAttributeName());
        }
        return attFilters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("VirtualVolumeInfo ( ");
        str.append(super.toString());
        str.append(", blockCount: ").append(blockCount);
        str.append(", blockSize: ").append(blockSize);
        str.append(", expansionStatus: ").append(expansionStatus);
        str.append(", supportingDevice: ").append(supportingDevice);
        if (supportingDeviceInfo != null) {
            str.append(", supportingDeviceInfo: ").append(supportingDeviceInfo.toString());
        }
        str.append(", serviceStatus: ").append(serviceStatus);
        str.append(", locality: ").append(locality);
        str.append(", clusters: ").append(clusters);
        str.append(", vpdId: ").append(vpdId);
        str.append(" )");
        return str.toString();
    }
}
