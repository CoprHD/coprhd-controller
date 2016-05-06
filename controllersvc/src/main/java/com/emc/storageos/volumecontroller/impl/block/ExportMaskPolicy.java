/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.volumecontroller.impl.HostIOLimitsParam;

/**
 * Transient policy object to track fast policies and whether the mask has
 * cascading storage groups or not.
 */
public class ExportMaskPolicy extends HostIOLimitsParam {
    @Override
    public String toString() {
        return String.format("ExportMaskPolicy [localTierPolicy=%s, tierPolicies=%s, simpleMask=%s, sgName=%s, export-type=%s, igType=%s]",
                localTierPolicy, tierPolicies, simpleMask, sgName, exportType, igType);
    }

    public String localTierPolicy;

    public String getLocalTierPolicy() {
        return localTierPolicy;
    }

    public void setLocalTierPolicy(String localTierPolicy) {
        this.localTierPolicy = localTierPolicy;
    }

    public StringSet tierPolicies;
    public boolean simpleMask;
    public String sgName;
    public String igType;
    public int maxVolumesAllowed;
    public String exportType;

    public static enum EXPORT_TYPE {
        PHANTOM, REGULAR
    }

    public static enum IG_TYPE {
        SIMPLE, CASCADED
    }

    public StringSet getTierPolicies() {
        return tierPolicies;
    }

    public void setTierPolicies(StringSet tierPolicies) {
        this.tierPolicies = tierPolicies;
    }

    public boolean isSimpleMask() {
        return simpleMask;
    }

    public void setSimpleMask(boolean simpleMask) {
        this.simpleMask = simpleMask;
    }

    public String getSgName() {
        return sgName;
    }

    public void setSgName(String sgName) {
        this.sgName = sgName;
    }

    public String getExportType() {
        if (null == exportType) {
            return EXPORT_TYPE.REGULAR.name();
        }
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }

    public String getIgType() {
        if (null == igType) {
            return IG_TYPE.SIMPLE.name();
        }
        return igType;
    }

    public void setIgType(String igType) {
        this.igType = igType;
    }

    public boolean isCascadedIG() {
        return IG_TYPE.CASCADED.name().equalsIgnoreCase(getIgType());
    }

    public int getMaxVolumesAllowed() {
        return maxVolumesAllowed;
    }

    public void setMaxVolumesAllowed(int maxVolumesAllowed) {
        this.maxVolumesAllowed = maxVolumesAllowed;
    }
}
