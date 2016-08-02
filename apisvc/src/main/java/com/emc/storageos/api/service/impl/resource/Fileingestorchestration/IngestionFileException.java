/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration;

import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;


import javax.ws.rs.core.Response;

/**
 * Created by bonduj on 8/2/2016.
 */
public class IngestionFileException extends APIException {
    public static final IngestionFileExceptions exceptions = ExceptionMessagesProxy
            .create(IngestionFileExceptions.class);
    protected IngestionFileException(Response.StatusType status, ServiceCode code, Throwable cause, String detailBase, String detailKey, Object[] detailParams) {
        super(Response.Status.INTERNAL_SERVER_ERROR, code, cause, detailBase, detailKey, detailParams);
    }
}
