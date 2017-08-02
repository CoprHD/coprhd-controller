/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;
import com.emc.storageos.driver.univmax.rest.type.common.SymmetrixPortKeyType;

public class PortGroupType extends GenericResultImplType {

    String portGroupId;
    List<SymmetrixPortKeyType> symmetrixPortKey;
    int num_of_ports;
    long num_of_masking_views;
    String type;
    List<String> maskingview;

    /**
     * @return the portGroupId
     */
    public String getPortGroupId() {
        return portGroupId;
    }

    /**
     * @param portGroupId the portGroupId to set
     */
    public void setPortGroupId(String portGroupId) {
        this.portGroupId = portGroupId;
    }

    /**
     * @return the symmetrixPortKey
     */
    public List<SymmetrixPortKeyType> getSymmetrixPortKey() {
        return symmetrixPortKey;
    }

    /**
     * @param symmetrixPortKey the symmetrixPortKey to set
     */
    public void setSymmetrixPortKey(List<SymmetrixPortKeyType> symmetrixPortKey) {
        this.symmetrixPortKey = symmetrixPortKey;
    }

    /**
     * @return the num_of_ports
     */
    public int getNum_of_ports() {
        return num_of_ports;
    }

    /**
     * @param num_of_ports the num_of_ports to set
     */
    public void setNum_of_ports(int num_of_ports) {
        this.num_of_ports = num_of_ports;
    }

    /**
     * @return the num_of_masking_views
     */
    public long getNum_of_masking_views() {
        return num_of_masking_views;
    }

    /**
     * @param num_of_masking_views the num_of_masking_views to set
     */
    public void setNum_of_masking_views(long num_of_masking_views) {
        this.num_of_masking_views = num_of_masking_views;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the maskingview
     */
    public List<String> getMaskingview() {
        return maskingview;
    }

    /**
     * @param maskingview the maskingview to set
     */
    public void setMaskingview(List<String> maskingview) {
        this.maskingview = maskingview;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "PortGroupType [portGroupId=" + portGroupId + ", symmetrixPortKey=" + symmetrixPortKey + ", num_of_ports=" + num_of_ports
                + ", num_of_masking_views=" + num_of_masking_views + ", type=" + type + ", maskingview=" + maskingview + ", getSuccess()="
                + getSuccess() + ", getHttpCode()=" + getHttpCode() + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()="
                + isSuccessfulStatus() + "]";
    }

}
