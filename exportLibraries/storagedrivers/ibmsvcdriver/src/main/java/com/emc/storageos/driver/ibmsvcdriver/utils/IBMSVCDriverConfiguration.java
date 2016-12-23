/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.driver.ibmsvcdriver.utils;

public class IBMSVCDriverConfiguration {

    private static IBMSVCDriverConfiguration instance;

    private static final Object syncObject = new Object();

    /**
     * Get static instance of this object
     * @return
     */
    public static IBMSVCDriverConfiguration getInstance(){
        synchronized (syncObject) {
            if (instance == null) {
                instance = new IBMSVCDriverConfiguration();
            }
        }

        return instance;
    }

    /**
     * Set Static instance of this object
     * @param tempInstance
     * @return
     */
    public static void setInstance(IBMSVCDriverConfiguration tempInstance){

        synchronized (syncObject) {
            instance = tempInstance;
        }

    }

    private String defaultVolumeGrainSize = "256";
    private String defaultVolumeRealSize = "2%";
    private String defaultVolumeWarning = "80%";
    private String defaultVolumeMirrorSyncRate = "50%";
    private String defaultVolumeIOgroup = "io_grp0";
    private String defaultVolumeVirtualizationType = "striped";
    private String defaultVolumeCacheSetting = "readwrite";

    private String defaultStoragePoolMinThickVolSizeGB = "1.00";
    private String defaultStoragePoolMaxThickVolSizeGB = "100.00";
    private String defaultStoragePoolMinThinVolSizeGB = "1.00";
    private String defaultStoragePoolMaxThinVolSizeGB = "100.00";
    private boolean enforceCGForSnapshots = false;

    public boolean isEnforceCGForSnapshots() {
        return enforceCGForSnapshots;
    }

    public void setEnforceCGForSnapshots(boolean enforceCGForSnapshots) {
        this.enforceCGForSnapshots = enforceCGForSnapshots;
    }

    public String getDefaultStoragePoolMinThickVolSizeGB() {
        return String.format("%sGB", defaultStoragePoolMinThickVolSizeGB);
    }

    public void setDefaultStoragePoolMinThickVolSizeGB(String defaultStoragePoolMinThickVolSizeGB) {
        this.defaultStoragePoolMinThickVolSizeGB = defaultStoragePoolMinThickVolSizeGB;
    }

    public String getDefaultStoragePoolMaxThickVolSizeGB() {
        return String.format("%sGB", defaultStoragePoolMaxThickVolSizeGB);
    }

    public void setDefaultStoragePoolMaxThickVolSizeGB(String defaultStoragePoolMaxThickVolSizeGB) {
        this.defaultStoragePoolMaxThickVolSizeGB = defaultStoragePoolMaxThickVolSizeGB;
    }

    public String getDefaultStoragePoolMinThinVolSizeGB() {
        return String.format("%sGB", defaultStoragePoolMinThinVolSizeGB);
    }

    public void setDefaultStoragePoolMinThinVolSizeGB(String defaultStoragePoolMinThinVolSizeGB) {
        this.defaultStoragePoolMinThinVolSizeGB = defaultStoragePoolMinThinVolSizeGB;
    }

    public String getDefaultStoragePoolMaxThinVolSizeGB() {
        return String.format("%sGB", defaultStoragePoolMaxThinVolSizeGB);
    }

    public void setDefaultStoragePoolMaxThinVolSizeGB(String defaultStoragePoolMaxThinVolSizeGB) {
        this.defaultStoragePoolMaxThinVolSizeGB = defaultStoragePoolMaxThinVolSizeGB;
    }

    public String getDefaultVolumeCacheSetting() {
        return defaultVolumeCacheSetting;
    }

    public void setDefaultVolumeCacheSetting(String defaultVolumeCacheSetting) {
        this.defaultVolumeCacheSetting = defaultVolumeCacheSetting;
    }

    public String getDefaultVolumeIOgroup() {
        return defaultVolumeIOgroup;
    }

    public void setDefaultVolumeIOgroup(String defaultVolumeIOgroup) {
        this.defaultVolumeIOgroup = defaultVolumeIOgroup;
    }

    public String getDefaultVolumeVirtualizationType() {
        return defaultVolumeVirtualizationType;
    }

    public void setDefaultVolumeVirtualizationType(String defaultVolumeVirtualizationType) {
        this.defaultVolumeVirtualizationType = defaultVolumeVirtualizationType;
    }

    public String getDefaultVolumeGrainSize() {
        return defaultVolumeGrainSize;
    }

    public void setDefaultVolumeGrainSize(String defaultVolumeGrainSize) {
        this.defaultVolumeGrainSize = defaultVolumeGrainSize;
    }

    public String getDefaultVolumeRealSize() {
        return defaultVolumeRealSize;
    }

    public void setDefaultVolumeRealSize(String defaultVolumeRealSize) {
        this.defaultVolumeRealSize = defaultVolumeRealSize;
    }

    public String getDefaultVolumeWarning() {
        return defaultVolumeWarning;
    }

    public void setDefaultVolumeWarning(String defaultVolumeWarning) {
        this.defaultVolumeWarning = defaultVolumeWarning;
    }

    public String getDefaultVolumeMirrorSyncRate() {
        return defaultVolumeMirrorSyncRate;
    }

    public void setDefaultVolumeMirrorSyncRate(String defaultVolumeMirrorSyncRate) {
        this.defaultVolumeMirrorSyncRate = defaultVolumeMirrorSyncRate;
    }

}
