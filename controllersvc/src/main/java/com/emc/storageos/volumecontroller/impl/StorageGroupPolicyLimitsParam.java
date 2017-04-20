/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;

/**
 * Auxiliary class to hold a storage group policy and host quota attributes
 */
public class StorageGroupPolicyLimitsParam extends HostIOLimitsParam {
    public static final String NON_FAST_POLICY = "NonFast";
    public static final String BANDWIDTH = "bw";
    public static final String IOPS = "iops";
    public static final String COMP = "Comp";

    private String autoTierPolicyName;
    private StorageSystem storage;
    private boolean compression = false;

    StorageGroupPolicyLimitsParam() {

    }

    public StorageGroupPolicyLimitsParam(VolumeURIHLU volumeURIHlu, StorageSystem storage) {
        this(volumeURIHlu.getAutoTierPolicyName(), volumeURIHlu.getHostIOLimitBandwidth(), volumeURIHlu.getHostIOLimitIOPs(), storage);
    }

    public StorageGroupPolicyLimitsParam(VolumeURIHLU volumeURIHlu, StorageSystem storage, SmisCommandHelper helper) {
        if (storage.checkIfVmax3()) {
            String policyName = helper.getVMAX3FastSettingForVolume(volumeURIHlu.getVolumeURI(), volumeURIHlu.getAutoTierPolicyName());
            setAutoTierPolicyName(policyName);
            setHostIOLimitBandwidth(volumeURIHlu.getHostIOLimitBandwidth());
            setHostIOLimitIOPs(volumeURIHlu.getHostIOLimitIOPs());
            setStorage(storage);
            setCompression(Constants.NONE.equalsIgnoreCase(policyName) ? false :
                    helper.isVMAX3VolumeCompressionEnabled(volumeURIHlu.getVolumeURI()));   // Compression cannot be enabled on non FAST SG
        }
    }

    public StorageGroupPolicyLimitsParam(String policyName) {
        this(policyName, (Integer) null, (Integer) null, null);
    }

    public StorageGroupPolicyLimitsParam(String autoTierPolicyName, Integer hostIOLimitBandwidth, Integer hostIOLimitIOPs,
            StorageSystem storage) {
        setAutoTierPolicyName(autoTierPolicyName);
        setHostIOLimitBandwidth(hostIOLimitBandwidth);
        setHostIOLimitIOPs(hostIOLimitIOPs);
        setStorage(storage);
    }

    public StorageGroupPolicyLimitsParam(String autoTierPolicyName, Integer hostIOLimitBandwidth, Integer hostIOLimitIOPs,
            boolean compression, StorageSystem storage) {
        this(autoTierPolicyName, hostIOLimitBandwidth, hostIOLimitIOPs, storage);
        setCompression(compression);
    }

    public StorageGroupPolicyLimitsParam(String autoTierPolicyName, String hostIOLimitBandwidth, String hostIOLimitIOPs,
            StorageSystem storage) {
        this(autoTierPolicyName, (Integer) null, (Integer) null, storage);
        try {
            setHostIOLimitBandwidth(StringUtils.isEmpty(hostIOLimitBandwidth) ? null : Integer.parseInt(hostIOLimitBandwidth));
            setHostIOLimitIOPs(StringUtils.isEmpty(hostIOLimitIOPs) ? null : Integer.parseInt(hostIOLimitIOPs));
        } catch (Exception e) {
            // ignore number format exception
        }
    }

    public String getAutoTierPolicyName() {
        return autoTierPolicyName;
    }

    public void setAutoTierPolicyName(String autoTierPolicyName) {
        this.autoTierPolicyName = autoTierPolicyName;
    }

    public StorageSystem getStorage() {
        return storage;
    }

    public void setStorage(StorageSystem storage) {
        this.storage = storage;
    }

    public void setCompression(final boolean compression) {
        this.compression = compression;
    }

    public boolean getCompression() {
        return compression;
    }

    /**
     * Construct a storage group key string based on given FAST policy name, limit bandwidth, and limit IO
     * 
     * @return
     */
    public String toString() {
        String policyName = StringUtils.equalsIgnoreCase(autoTierPolicyName, Constants.NONE) ? NON_FAST_POLICY : getAutoTierPolicyName();
        if (isHostIOLimitBandwidthSet()) {
            policyName += "_bw" + getHostIOLimitBandwidth();
        }

        if (isHostIOLimitIOPsSet()) {
            policyName += "_iops" + getHostIOLimitIOPs();
        }

        if (getCompression()) {
            policyName += "_" + COMP;
        }

        return policyName;
    }

    public String getString() {
        if (storage != null && storage.checkIfVmax3()) {
            autoTierPolicyName = autoTierPolicyName.replaceAll(Constants.SMIS_PLUS_REGEX, Constants.UNDERSCORE_DELIMITER);
        }
        String policyName = StringUtils.equalsIgnoreCase(autoTierPolicyName, Constants.NONE) ? NON_FAST_POLICY : getAutoTierPolicyName();
        if (isHostIOLimitBandwidthSet()) {
            policyName += "_" + BANDWIDTH + getHostIOLimitBandwidth();
        }

        if (isHostIOLimitIOPsSet()) {
            policyName += "_" + IOPS + getHostIOLimitIOPs();
        }

        if (getCompression()) {
            policyName += "_" + COMP;
        }

        return policyName;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        return StringUtils.equalsIgnoreCase(toString(), obj.toString());
    }
}
