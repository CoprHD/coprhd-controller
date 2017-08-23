/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.univmax.rest.type.sloprovisioning;


public class EditHostActionParamType {

    private RenameHostParamType renameHostParam;
    private AddInitiatorParamType addInitiatorParam;
    private RemoveInitiatorParamType removeInitiatorParam;
    private SetHostFlagsParamType setHostFlagsParam;

    /**
     * @return the renameHostParam
     */
    public RenameHostParamType getRenameHostParam() {
        return renameHostParam;
    }

    /**
     * @param renameHostParam the renameHostParam to set
     */
    public void setRenameHostParam(RenameHostParamType renameHostParam) {
        this.renameHostParam = renameHostParam;
    }

    /**
     * @return the addInitiatorParam
     */
    public AddInitiatorParamType getAddInitiatorParam() {
        return addInitiatorParam;
    }

    /**
     * @param addInitiatorParam the addInitiatorParam to set
     */
    public void setAddInitiatorParam(AddInitiatorParamType addInitiatorParam) {
        this.addInitiatorParam = addInitiatorParam;
    }

    /**
     * @return the removeInitiatorParam
     */
    public RemoveInitiatorParamType getRemoveInitiatorParam() {
        return removeInitiatorParam;
    }

    /**
     * @param removeInitiatorParam the removeInitiatorParam to set
     */
    public void setRemoveInitiatorParam(RemoveInitiatorParamType removeInitiatorParam) {
        this.removeInitiatorParam = removeInitiatorParam;
    }

    /**
     * @return the setHostFlagsParam
     */
    public SetHostFlagsParamType getSetHostFlagsParam() {
        return setHostFlagsParam;
    }

    /**
     * @param setHostFlagsParam the setHostFlagsParam to set
     */
    public void setSetHostFlagsParam(SetHostFlagsParamType setHostFlagsParam) {
        this.setHostFlagsParam = setHostFlagsParam;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "EditHostActionParamType [renameHostParam=" + renameHostParam + ", addInitiatorParam=" + addInitiatorParam
                + ", removeInitiatorParam=" + removeInitiatorParam + ", setHostFlagsParam=" + setHostFlagsParam + "]";
    }

}
