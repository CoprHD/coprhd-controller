/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3.smc.symmetrix.resource.volume.model;

import com.emc.storageos.driver.vmax3.smc.basetype.DefaultResponse;

/**
 * @author fengs5
 *
 */
public class RdfGroupIdType extends DefaultResponse {

    private long rdf_group_number;
    private String label;

    /**
     * @return the rdf_group_number
     */
    public long getRdf_group_number() {
        return rdf_group_number;
    }

    /**
     * @param rdf_group_number the rdf_group_number to set
     */
    public void setRdf_group_number(long rdf_group_number) {
        this.rdf_group_number = rdf_group_number;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "RdfGroupIdType [rdf_group_number=" + rdf_group_number + ", label=" + label + "]";
    }

}
