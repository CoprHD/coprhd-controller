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

    private String autoTierPolicyName;
    private StorageSystem storage;

    StorageGroupPolicyLimitsParam() {

    }

    public StorageGroupPolicyLimitsParam(VolumeURIHLU volumeURIHlu, StorageSystem storage) {
        this(volumeURIHlu.getAutoTierPolicyName(), volumeURIHlu.getHostIOLimitBandwidth(), volumeURIHlu.getHostIOLimitIOPs(), storage);
    }

    public StorageGroupPolicyLimitsParam(VolumeURIHLU volumeURIHlu, StorageSystem storage, SmisCommandHelper helper) {
        if (storage.checkIfVmax3()) {
            setAutoTierPolicyName(helper.getVMAX3FastSettingForVolume(volumeURIHlu.getVolumeURI(), volumeURIHlu.getAutoTierPolicyName()));
            setHostIOLimitBandwidth(volumeURIHlu.getHostIOLimitBandwidth());
            setHostIOLimitIOPs(volumeURIHlu.getHostIOLimitIOPs());
            setStorage(storage);
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
