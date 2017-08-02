/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import java.util.List;

import com.emc.storageos.driver.univmax.rest.type.common.GenericResultImplType;

/**
 * @author fengs5
 *
 */
public class GetMaskingViewResultType extends GenericResultImplType {

    List<MaskingViewType> maskingView;

    /**
     * @return the maskingView
     */
    public List<MaskingViewType> getMaskingView() {
        return maskingView;
    }

    /**
     * @param maskingView the maskingView to set
     */
    public void setMaskingView(List<MaskingViewType> maskingView) {
        this.maskingView = maskingView;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "GetMaskingViewResultType [maskingView=" + maskingView + ", getSuccess()=" + getSuccess() + ", getHttpCode()="
                + getHttpCode() + ", getMessage()=" + getMessage() + ", isSuccessfulStatus()=" + isSuccessfulStatus() + "]";
    }

}
