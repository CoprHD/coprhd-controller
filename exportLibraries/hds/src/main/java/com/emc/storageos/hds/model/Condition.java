/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class Condition {
    private String poolFunction;

    public Condition(String poolFunction) {
        this.poolFunction = poolFunction;
    }

    public String getPoolFunction() {
        return poolFunction;
    }

    public void setPoolFunction(String poolFunction) {
        this.poolFunction = poolFunction;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.poolFunction) {
            xmlString.append(HDSConstants.SPACE_STR).append("poolFunction=")
                    .append(HDSConstants.QUOTATION_STR).append(this.poolFunction)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }
}
