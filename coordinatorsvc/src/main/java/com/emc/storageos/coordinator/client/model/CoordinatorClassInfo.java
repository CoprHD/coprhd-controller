/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.coordinator.client.model;

/**
 * This class is used to create necessary coordinator related invariants
 * when publishing an object to coordinator. e.g. RepositoryInfo, PropertyInfoExt etc.
 *
 * 1. If an object needs to be published as a shared object among all nodes,
 *    id, kind must be present, kind must be initialized with an unique value.
 * 2. If an object needs to be published as a node level object,
 *    attribute must be initialized with an unique value.
 */
public final class CoordinatorClassInfo {
    public final String id;
    public final String kind;
    public final String attribute;

    public CoordinatorClassInfo(String id, String kind, String attribute) {
        this.id = id;
        this.kind = kind;
        this.attribute = attribute;
    }
}
