/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class Add {

    private String target;

    private int numOfLus;

    private boolean bulk;

    private String formatType;

    private boolean force;

    private boolean overwrite;

    private String option;

    public Add(String target, int numOfLus, String formatType) {
        this.target = target;
        this.numOfLus = numOfLus;
        this.formatType = formatType;
    }

    public Add(String target, boolean force) {
        this.target = target;
        this.force = force;
    }

    public Add(String target) {
        this.target = target;
    }

    /**
     * @return the target
     */
    public String getTarget() {
        return target;
    }

    /**
     * @param target the target to set
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * @return the numOfLus
     */
    public int getNumOfLus() {
        return numOfLus;
    }

    /**
     * @param numOfLus the numOfLus to set
     */
    public void setNumOfLus(int numOfLus) {
        this.numOfLus = numOfLus;
    }

    /**
     * @return the formatType
     */
    public String getFormatType() {
        return formatType;
    }

    /**
     * @param formatType the formatType to set
     */
    public void setFormatType(String formatType) {
        this.formatType = formatType;
    }

    /**
     * @return the force
     */
    public boolean isForce() {
        return force;
    }

    /**
     * @param force the force to set
     */
    public void setForce(boolean force) {
        this.force = force;
    }

    /**
     * @return the overwrite
     */
    public boolean getOverwrite() {
        return overwrite;
    }

    /**
     * @param overwrite the overwrite to set
     */
    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    /**
     * @return the bulk
     */
    public boolean isBulk() {
        return bulk;
    }

    /**
     * @param bulk the bulk to set
     */
    public void setBulk(boolean bulk) {
        this.bulk = bulk;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.target) {
            xmlString.append(HDSConstants.SPACE_STR).append("target=\"")
                    .append(this.target).append("\" ");
        }
        if (1 < this.numOfLus && null != formatType && this.bulk) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"bulk;")
                    .append(formatType).append("\"").append(" ")
                    .append("option2=\"numOfLUs:").append(this.numOfLus).append("\"");
        } else if (1 < this.numOfLus && null != formatType) {
            xmlString.append(HDSConstants.SPACE_STR).append(formatType).append("\"")
                    .append(" ").append("option2=\"numOfLUs:").append(this.numOfLus)
                    .append("\"");
        } else if (1 < this.numOfLus && this.bulk) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"bulk\"")
                    .append(" ").append("option2=\"numOfLUs:").append(this.numOfLus)
                    .append("\"");
        } else if (1 < this.numOfLus) {
            xmlString.append(HDSConstants.SPACE_STR).append("option2=\"numOfLUs:")
                    .append(this.numOfLus).append("\"");
        } else if (null != formatType) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"")
                    .append(this.formatType).append("\" ");
        } else if (force) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"").append("force")
                    .append("\" ");
        } else if (this.overwrite) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"")
                    .append("overwrite").append("\" ");
        }

        if (null != this.option) {
            xmlString.append(HDSConstants.SPACE_STR)
                    .append("option=").append(HDSConstants.QUOTATION_STR)
                    .append(this.option).append(HDSConstants.QUOTATION_STR);
        }
        return xmlString.toString();
    }

}
