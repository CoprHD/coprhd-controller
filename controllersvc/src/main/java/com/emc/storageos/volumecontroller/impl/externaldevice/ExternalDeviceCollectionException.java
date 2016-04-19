/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Class for discovery and metering exceptions for SB SDK baded drivers.
 */
public class ExternalDeviceCollectionException extends BaseCollectionException {

    public ExternalDeviceCollectionException(boolean retryable, ServiceCode code,
                                    Throwable cause, String detailBase, String detailKey,
                                    Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.plugins.BaseCollectionException#getErrorCode()
     */
    @Override
    public int getErrorCode() {
        return -1;
    }


}
