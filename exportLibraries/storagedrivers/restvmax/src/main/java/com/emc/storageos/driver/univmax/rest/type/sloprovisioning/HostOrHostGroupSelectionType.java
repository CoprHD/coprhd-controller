/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class HostOrHostGroupSelectionType extends ParamType {

    private UseExistingHostParamType useExistingHostParam;
    private CreateHostParamType createHostParam;
    private UseExistingHostGroupParamType useExistingHostGroupParam;
    private CreateHostGroupParamType createHostGroupParam;

    /**
     * @return the useExistingHostParam
     */
    public UseExistingHostParamType getUseExistingHostParam() {
        return useExistingHostParam;
    }

    /**
     * @param useExistingHostParam the useExistingHostParam to set
     */
    public void setUseExistingHostParam(UseExistingHostParamType useExistingHostParam) {
        this.useExistingHostParam = useExistingHostParam;
    }

    /**
     * @return the createHostParam
     */
    public CreateHostParamType getCreateHostParam() {
        return createHostParam;
    }

    /**
     * @param createHostParam the createHostParam to set
     */
    public void setCreateHostParam(CreateHostParamType createHostParam) {
        this.createHostParam = createHostParam;
    }

    /**
     * @return the useExistingHostGroupParam
     */
    public UseExistingHostGroupParamType getUseExistingHostGroupParam() {
        return useExistingHostGroupParam;
    }

    /**
     * @param useExistingHostGroupParam the useExistingHostGroupParam to set
     */
    public void setUseExistingHostGroupParam(UseExistingHostGroupParamType useExistingHostGroupParam) {
        this.useExistingHostGroupParam = useExistingHostGroupParam;
    }

    /**
     * @return the createHostGroupParam
     */
    public CreateHostGroupParamType getCreateHostGroupParam() {
        return createHostGroupParam;
    }

    /**
     * @param createHostGroupParam the createHostGroupParam to set
     */
    public void setCreateHostGroupParam(CreateHostGroupParamType createHostGroupParam) {
        this.createHostGroupParam = createHostGroupParam;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "HostOrHostGroupSelectionType [useExistingHostParam=" + useExistingHostParam + ", createHostParam=" + createHostParam
                + ", useExistingHostGroupParam=" + useExistingHostGroupParam + ", createHostGroupParam=" + createHostGroupParam + "]";
    }

}
