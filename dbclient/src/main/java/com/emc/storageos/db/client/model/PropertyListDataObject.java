/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

/**
 * Class to store arbitrary auxiliary resources in the database
 * It stores all data as string values in the StringMap
 */
@Cf("PropertyListDataObject")
public class PropertyListDataObject extends DataObject {

    private StringMap _resourceData;
    private String _type;

    @Name("resourceData")
    public StringMap getResourceData() {
        if (_resourceData == null) {
            _resourceData = new StringMap();
        }
        return _resourceData;
    }

    public void setResourceData(StringMap data) {
        _resourceData = data;
    }

    @Name("resourceType")
    @AlternateId("AltIdIndex")
    public String getResourceType() {
        return _type;
    }

    public void setResourceType(String type) {
        _type = type;
        setChanged("resourceType");
    }

}
