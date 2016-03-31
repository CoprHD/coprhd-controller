/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.ecs.api;

public class NamespaceBase {
    private String name;
    private ECSLink link;
    private String id;

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    ECSLink getLink() {
        return link;
    }

    void setLink(ECSLink link) {
        this.link = link;
    }

    String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }
}