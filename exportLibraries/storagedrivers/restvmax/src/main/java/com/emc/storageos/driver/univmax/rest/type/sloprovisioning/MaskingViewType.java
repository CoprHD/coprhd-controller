/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultType;

public class MaskingViewType extends GenericResultType {

    private String maskingViewId;
    private String hostId;
    private String hostGroupId;
    private String portGroupId;
    private String storageGroupId;

    /**
     * @return the maskingViewId
     */
    public String getMaskingViewId() {
        return maskingViewId;
    }

    /**
     * @param maskingViewId the maskingViewId to set
     */
    public void setMaskingViewId(String maskingViewId) {
        this.maskingViewId = maskingViewId;
    }

    /**
     * @return the hostId
     */
    public String getHostId() {
        return hostId;
    }

    /**
     * @param hostId the hostId to set
     */
    public void setHostId(String hostId) {
        this.hostId = hostId;
    }

    /**
     * @return the hostGroupId
     */
    public String getHostGroupId() {
        return hostGroupId;
    }

    /**
     * @param hostGroupId the hostGroupId to set
     */
    public void setHostGroupId(String hostGroupId) {
        this.hostGroupId = hostGroupId;
    }

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
     * @return the storageGroupId
     */
    public String getStorageGroupId() {
        return storageGroupId;
    }

    /**
     * @param storageGroupId the storageGroupId to set
     */
    public void setStorageGroupId(String storageGroupId) {
        this.storageGroupId = storageGroupId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "MaskingViewType [maskingViewId=" + maskingViewId + ", hostId=" + hostId + ", hostGroupId=" + hostGroupId + ", portGroupId="
                + portGroupId + ", storageGroupId=" + storageGroupId + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()="
                + isSuccessfulStatus() + "]";
    }

}
