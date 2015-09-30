/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class ArrayGroup {
    private String type;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.type) {
            xmlString.append(HDSConstants.SPACE_STR).append("type=")
                    .append(HDSConstants.QUOTATION_STR).append(this.type)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

}
