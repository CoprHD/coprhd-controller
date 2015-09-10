/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hds.model;

import com.emc.storageos.hds.HDSConstants;

public class Delete {

    private String target;

    private boolean force;

    private String option;

    public Delete(String target) {
        this.target = target;
    }

    public Delete(String target, boolean force) {
        this.target = target;
        this.force = force;
    }

    public Delete(String target, String option) {
        this.target = target;
        this.option = option;
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

    public String getOption() {
        return option;
    }

    public void setOption(String option) {
        this.option = option;
    }

    public String toXMLString() {
        StringBuilder xmlString = new StringBuilder();
        if (null != this.target) {
            xmlString.append(HDSConstants.SPACE_STR).append("target=\"")
                    .append(this.target).append("\" ");
        }
        if (this.force) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"").append("force")
                    .append("\" ");
        }
        if (null != this.option) {
            xmlString.append(HDSConstants.SPACE_STR).append("option=\"").append(this.option)
                    .append("\" ");
        }
        return xmlString.toString();
    }

}
