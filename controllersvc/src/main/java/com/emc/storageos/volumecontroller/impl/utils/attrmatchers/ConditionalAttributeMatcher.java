/*
 * Copyright (c) 2014-2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.utils.attrmatchers;

import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.SupportedDriveTypes;
import com.emc.storageos.volumecontroller.AttributeMatcher;


/**
 * Add conditions, which can decide whether to run a matcher.
 * Ex: If VMAX FAST Policy is chosen, then we should skip both Raid level and DriveType Matchers.   
 * 
 */
public abstract class ConditionalAttributeMatcher extends AttributeMatcher {
      
    protected boolean isAutoTieringPolicyOn(Map<String, Object> attributeMap) {
         
        if(attributeMap.containsKey(Attributes.auto_tiering_policy_name.toString())) {
            StringSet deviceTypes = (StringSet) attributeMap.get(Attributes.system_type.toString());
            //systemType can't be null , and this check is valid only for vmax
            return deviceTypes.contains(VirtualPool.SystemType.vmax.toString());
        }
        return false;
    }
    
    protected boolean isRaidLevelOrDriveTypeConfigured(Map<String, Object> attributeMap) {
        if (attributeMap != null) {
            if (attributeMap.get(Attributes.raid_levels.name()) != null) {
                @SuppressWarnings("unchecked")
                Set<String> raidLevels = (Set<String>) attributeMap.get(Attributes.raid_levels.name());
                return !raidLevels.isEmpty();
                
            } 
            return attributeMap.containsKey(Attributes.drive_type.toString())
                        && !SupportedDriveTypes.NONE.toString().equals(attributeMap.get(
                               Attributes.drive_type.toString()).toString());
        }
        return false;
    }
}
