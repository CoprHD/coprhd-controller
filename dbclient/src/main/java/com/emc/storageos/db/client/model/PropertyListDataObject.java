/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import com.emc.storageos.model.valid.EnumType;
import java.util.Calendar;

/**
 * Class to store arbitrary auxiliary resources in the database
 * It stores all data as string values in the StringMap
 */
@Cf("PropertyListDataObject")
public class PropertyListDataObject extends DataObject {

    private StringMap  _resourceData;
    private String     _type;

    @Name("resourceData")
    public StringMap getResourceData() {
        if (_resourceData == null)  {
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
