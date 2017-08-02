/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class CreateMaskingViewParamType extends ParamType {

    private String maskingViewId;
    private HostOrHostGroupSelectionType hostOrHostGroupSelection;
    private PortGroupSelectionType portGroupSelection;
    private StorageGroupSelectionType storageGroupSelection;

    /**
     * @param maskingViewId
     */
    public CreateMaskingViewParamType(String maskingViewId) {
        super();
        this.maskingViewId = maskingViewId;
    }

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
     * @return the hostOrHostGroupSelection
     */
    public HostOrHostGroupSelectionType getHostOrHostGroupSelection() {
        return hostOrHostGroupSelection;
    }

    /**
     * @param hostOrHostGroupSelection the hostOrHostGroupSelection to set
     */
    public void setHostOrHostGroupSelection(HostOrHostGroupSelectionType hostOrHostGroupSelection) {
        this.hostOrHostGroupSelection = hostOrHostGroupSelection;
    }

    /**
     * @return the portGroupSelection
     */
    public PortGroupSelectionType getPortGroupSelection() {
        return portGroupSelection;
    }

    /**
     * @param portGroupSelection the portGroupSelection to set
     */
    public void setPortGroupSelection(PortGroupSelectionType portGroupSelection) {
        this.portGroupSelection = portGroupSelection;
    }

    /**
     * @return the storageGroupSelection
     */
    public StorageGroupSelectionType getStorageGroupSelection() {
        return storageGroupSelection;
    }

    /**
     * @param storageGroupSelection the storageGroupSelection to set
     */
    public void setStorageGroupSelection(StorageGroupSelectionType storageGroupSelection) {
        this.storageGroupSelection = storageGroupSelection;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "CreateMaskingViewParamType [maskingViewId=" + maskingViewId + ", hostOrHostGroupSelection=" + hostOrHostGroupSelection
                + ", portGroupSelection=" + portGroupSelection + ", storageGroupSelection=" + storageGroupSelection + "]";
    }

}
