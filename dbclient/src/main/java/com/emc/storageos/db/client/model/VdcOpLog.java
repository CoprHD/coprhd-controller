/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;

/**
 * Representation for the detail of each vdc management operation
 */
@SuppressWarnings("serial")
@Cf("VdcOpLog")
public class VdcOpLog extends DataObject {

    /**
     * Vdc management operation type
     */
    private String operationType;

    /**
     * Vdc to be operated, like vdc to be connected, vdc to be removed
     */
    private URI operatedVdc;

    /**
     * Detail parameters of the vdc management operation
     */
    private byte[] operationParam;

    /**
     * Current stable vdc config info, used when recovery
     */
    private byte[] vdcConfigInfo;

    @Name("type")
    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
        setChanged("type");
    }

    @Name("opVdcId")
    public URI getOperatedVdc() {
        return operatedVdc;
    }

    public void setOperatedVdc(URI operatedVdc) {
        this.operatedVdc = operatedVdc;
        setChanged("opVdcId");
    }

    @Name("opParam")
    public byte[] getOperationParam() {
        return operationParam.clone();
    }

    public void setOperationParam(byte[] operationParam) {
        this.operationParam = operationParam.clone();
        setChanged("opParam");
    }

    @Name("vdcConfig")
    public byte[] getVdcConfigInfo() {
        return vdcConfigInfo.clone();
    }

    public void setVdcConfigInfo(byte[] vdcConfigInfo) {
        this.vdcConfigInfo = vdcConfigInfo.clone();
        setChanged("vdcConfig");
    }

}
