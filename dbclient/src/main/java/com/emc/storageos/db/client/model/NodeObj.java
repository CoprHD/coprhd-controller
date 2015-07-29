/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

/**
 */
@Cf("NodeObj")
public class NodeObj extends DataObject {
    private String _description;

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
        setChanged("description");
    }

}
