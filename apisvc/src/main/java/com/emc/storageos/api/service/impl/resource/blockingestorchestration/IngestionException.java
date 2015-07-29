/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import javax.ws.rs.core.Response.Status;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class IngestionException extends APIException {

    private static final long serialVersionUID = 228652339079671167L;

    public static final IngestionExceptions exceptions = ExceptionMessagesProxy
            .create(IngestionExceptions.class);

    private IngestionException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(Status.INTERNAL_SERVER_ERROR, code, cause, detailBase, detailKey, detailParams);
    }
}
