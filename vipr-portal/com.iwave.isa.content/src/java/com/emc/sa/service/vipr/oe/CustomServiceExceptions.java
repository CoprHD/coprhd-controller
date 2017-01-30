package com.emc.sa.service.vipr.oe;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Created by sonalisahu on 1/23/17.
 */
@MessageBundle
public interface CustomServiceExceptions {

    @DeclareServiceCode(ServiceCode.CUSTOM_SERVICE_EXCEPTION)
    public CustomServiceException customServiceException(String msg);
}
