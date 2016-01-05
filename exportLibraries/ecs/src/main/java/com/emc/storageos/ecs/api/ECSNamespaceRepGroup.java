/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.ECSNamespace.ECS_RepGroup_Type;

public class ECSNamespaceRepGroup {
    private List<String> replicationGroups = new ArrayList<String>();
    private ECS_RepGroup_Type rgType;
    
    public ECS_RepGroup_Type getRgType() {
        return rgType;
    }
    
    public void setRgType(ECS_RepGroup_Type rgType) {
        this.rgType = rgType;
    }

    public List<String> getReplicationGroups() {
        return replicationGroups;
    }
    
    public void setReplicationGroups(String replicationGroups) {
        this.replicationGroups.add(replicationGroups);
    }
}