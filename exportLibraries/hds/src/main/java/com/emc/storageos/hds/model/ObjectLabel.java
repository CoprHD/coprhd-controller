/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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

public class ObjectLabel {
    
    private String targetID;
    
    private String label;
    
    public ObjectLabel(String targetID, String label) {
        this.targetID = targetID;
        this.label = label;
    }

    public ObjectLabel() {}
    
    /**
     * @return the targetID
     */
    public String getTargetID() {
        return targetID;
    }

    /**
     * @param targetID the targetID to set
     */
    public void setTargetID(String targetID) {
        this.targetID = targetID;
    }

    /**
     * @return the label
     */
    public String getLabel() {
        return label;
    }

    /**
     * @param label the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }
    /**
     * Returns this object in XMLString format.
     * @return
     */
    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.targetID) {
            xmlString.append(HDSConstants.SPACE_STR).append("targetID=\"")
                    .append(this.targetID).append("\" ");
        }
        if (null != this.label) {
            xmlString.append(HDSConstants.SPACE_STR).append("label=\"")
                    .append(this.label).append("\" ");
        }
        return xmlString.toString();
    }
    
}
