/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;

import com.emc.storageos.driver.univmax.rest.type.common.ParamType;

public class PortGroupSelectionType extends ParamType {

    private UseExistingPortGroupParamType useExistingPortGroupParam;
    private CreatePortGroupParamType createPortGroupParam;

    /**
     * @return the useExistingPortGroupParam
     */
    public UseExistingPortGroupParamType getUseExistingPortGroupParam() {
        return useExistingPortGroupParam;
    }

    /**
     * @param useExistingPortGroupParam the useExistingPortGroupParam to set
     */
    public void setUseExistingPortGroupParam(UseExistingPortGroupParamType useExistingPortGroupParam) {
        this.useExistingPortGroupParam = useExistingPortGroupParam;
    }

    /**
     * @return the createPortGroupParam
     */
    public CreatePortGroupParamType getCreatePortGroupParam() {
        return createPortGroupParam;
    }

    /**
     * @param createPortGroupParam the createPortGroupParam to set
     */
    public void setCreatePortGroupParam(CreatePortGroupParamType createPortGroupParam) {
        this.createPortGroupParam = createPortGroupParam;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "PortGroupSelectionType [useExistingPortGroupParam=" + useExistingPortGroupParam + ", createPortGroupParam="
                + createPortGroupParam + "]";
    }

}
