/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.ArrayList;
import java.util.List;

import com.emc.storageos.db.client.model.ObjectNamespace;

public class ECSNamespaceRepGroup {
    private String namespaceName;
    private List<String> replicationGroups = new ArrayList<String>();
    private ObjectNamespace.ECS_RepGroup_Type rgType;
    
    public String getNamespaceName() {
        return namespaceName;
    }
    
    public void setNamespaceName(String namespaceName) {
        this.namespaceName = namespaceName;
    }
    
    public ObjectNamespace.ECS_RepGroup_Type getRgType() {
        return rgType;
    }
    
    public void setRgType(ObjectNamespace.ECS_RepGroup_Type rgType) {
        this.rgType = rgType;
    }

    public List<String> getReplicationGroups() {
        return replicationGroups;
    }
    
    public void setReplicationGroups(String replicationGroups) {
        this.replicationGroups.add(replicationGroups);
    }
}