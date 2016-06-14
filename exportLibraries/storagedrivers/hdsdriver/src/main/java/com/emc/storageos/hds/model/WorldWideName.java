/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class WorldWideName {

    private String wwn;

    public WorldWideName(String wwn) {
        this.wwn = wwn;
    }

    public WorldWideName() {
        // TODO Auto-generated constructor stub
    }

    /**
     * @return the wwn
     */
    public String getWwn() {
        return wwn;
    }

    /**
     * @param wwn the wwn to set
     */
    public void setWwn(String wwn) {
        this.wwn = wwn;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();

        if (null != this.wwn) {
            xmlString.append(HDSConstants.SPACE_STR).append("wwn=")
                    .append(HDSConstants.QUOTATION_STR).append(this.wwn)
                    .append(HDSConstants.QUOTATION_STR);
        }

        return xmlString.toString();
    }
}
