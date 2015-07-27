/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block;

import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.volumecontroller.impl.HostIOLimitsParam;

/**
 * Transient policy object to track fast policies and whether the mask has 
 * cascading storage groups or not.
 */
public class ExportMaskPolicy extends HostIOLimitsParam{
	@Override
	public String toString() {
		return "ExportMaskPolicy [localTierPolicy=" + localTierPolicy
				+ ", tierPolicies=" + tierPolicies + ", simpleMask="
				+ simpleMask + ", sgName=" + sgName + ", export-type=" + exportType + "]";
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
        if (null == exportType)
            return EXPORT_TYPE.REGULAR.name();
        return exportType;
    }

    public void setExportType(String exportType) {
        this.exportType = exportType;
    }
    public String getIgType() {
        if (null == igType) return IG_TYPE.SIMPLE.name();
        return igType;
    }
    public void setIgType(String igType) {
        this.igType = igType;
    }
    
    public boolean isCascadedIG() {
        return IG_TYPE.CASCADED.name().equalsIgnoreCase(getIgType());
    }
    
    
}
