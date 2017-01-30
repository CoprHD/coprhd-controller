package com.emc.sa.service.vipr.oe;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Created by sonalisahu on 1/23/17.
 */
public class CustomServiceException extends InternalException {

    private static final long serialVersionUID = 8508132363498513928L;
    public static final CustomServiceExceptions exceptions = ExceptionMessagesProxy
            .create(CustomServiceExceptions.class);

    public CustomServiceException(boolean retryable, ServiceCode code, Throwable cause, String detailBase, String detailKey, Object[] detailParams) {
        super(retryable, code, cause, detailBase, detailKey, detailParams);
    }

    public CustomServiceException(ServiceCode code, Throwable cause, String pattern, Object[] params) {
        super(code, cause, pattern, params);
    }
}
