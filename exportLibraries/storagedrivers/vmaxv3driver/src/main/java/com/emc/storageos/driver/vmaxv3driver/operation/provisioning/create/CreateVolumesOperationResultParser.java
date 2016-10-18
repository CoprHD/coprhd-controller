/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver.operation.provisioning.create;

import com.emc.storageos.driver.vmaxv3driver.base.OperationResultParser;
import com.emc.storageos.driver.vmaxv3driver.rest.response.Volume;
import com.emc.storageos.driver.vmaxv3driver.util.CapUnit;
import com.emc.storageos.driver.vmaxv3driver.util.DriverUtil;
import com.emc.storageos.storagedriver.model.StorageVolume;

import java.util.List;

/**
 * For the "createVolumes" operation, the operation result is
 * a list of objects of the "Volume" response type. However, the type needed
 * by the SDK method call is the passed-in "List<StorageVolume>" type. The
 * interface here is used to do such parsing.
 *
 * Created by gang on 9/28/16.
 */
public class CreateVolumesOperationResultParser implements OperationResultParser {

    /**
     * The operation result.
     */
    protected List<Volume> operationResult;

    /**
     * The passed-in-out argument.
     */
    protected List<StorageVolume> passedInOut;

    public CreateVolumesOperationResultParser(List<Volume> operationResult, List<StorageVolume> passedInOut) {
        this.operationResult = operationResult;
        this.passedInOut = passedInOut;
    }

    /**
     * Parse "operationResult" and update the information into "passedInOut".
     *
     * @return
     */
    @Override
    public Object parse() {
        for (int i = 0; i < this.operationResult.size(); i++) {
            Volume source = this.operationResult.get(i);
            StorageVolume target = this.passedInOut.get(i);
            target.setNativeId(source.getVolumeId());
            target.setWwn(source.getWwn());
            target.setDisplayName(source.getVolumeId());
            target.setDeviceLabel(source.getVolumeId());
            target.setProvisionedCapacity(DriverUtil.convert2Bytes(CapUnit.GB, source.getCap_gb()));
        }
        return this.passedInOut;
    }
}
