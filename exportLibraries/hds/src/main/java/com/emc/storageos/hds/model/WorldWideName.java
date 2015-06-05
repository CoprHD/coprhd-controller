/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
