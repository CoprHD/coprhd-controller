/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

import java.util.ArrayList;

public class NamespaceCommandResult {
    private ArrayList<NamespaceBase> namespace;
    
    ArrayList<NamespaceBase> getNamespace() {
        return namespace;
    }
    
    void setNamespace (ArrayList<NamespaceBase> namespace) {
        this.namespace = namespace;
    }
}