/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.ecs.api;

import java.net.URI;

/**
 * 
 * ECS specific storage pool
 *
 */
public class ECSStoragePool {
    private String name;
    private String id;
    private Long TotalCapacity;
    private Long FreeCapacity;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getTotalCapacity() {
        return TotalCapacity;
    }

    public void setTotalCapacity(Long TotalCapacity) {
        this.TotalCapacity = TotalCapacity;
    }

    public Long getFreeCapacity() {
        return FreeCapacity;
    }

    public void setFreeCapacity(Long FreeCapacity) {
        this.FreeCapacity = FreeCapacity;
    }

}
