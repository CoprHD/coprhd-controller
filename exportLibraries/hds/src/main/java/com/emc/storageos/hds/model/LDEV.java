/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class LDEV {

    private String objectID;
    private int path;
    private int composite;
    private int tierLevel;

    private ObjectLabel label;

    public LDEV() {
    }

    public LDEV(String objectID) {
        this.objectID = objectID;
    }

    /**
     * @return the objectID
     */
    public String getObjectID() {
        return objectID;
    }

    /**
     * @param objectID the objectID to set
     */
    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    /**
     * @return the path
     */
    public int getPath() {
        return path;
    }

    /**
     * @param path the path to set
     */
    public void setPath(int path) {
        this.path = path;
    }

    /**
     * @return the composite
     */
    public int getComposite() {
        return composite;
    }

    /**
     * @param composite the composite to set
     */
    public void setComposite(int composite) {
        this.composite = composite;
    }

    /**
     * @return the tierLevel
     */
    public int getTierLevel() {
        return tierLevel;
    }

    /**
     * @param tierLevel the tierLevel to set
     */
    public void setTierLevel(int tierLevel) {
        this.tierLevel = tierLevel;
    }

    public ObjectLabel getLabel() {
        return label;
    }

    public void setLabel(ObjectLabel label) {
        this.label = label;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.objectID) {
            xmlString.append(HDSConstants.SPACE_STR).append("objectID=")
                    .append(HDSConstants.QUOTATION_STR).append(this.objectID)
                    .append(HDSConstants.QUOTATION_STR)
                    .append(HDSConstants.SPACE_STR).append("tierLevel=")
                    .append(HDSConstants.QUOTATION_STR).append(this.tierLevel)
                    .append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }
}
