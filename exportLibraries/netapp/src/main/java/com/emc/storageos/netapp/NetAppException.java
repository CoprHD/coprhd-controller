/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.netapp;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class NetAppException extends InternalException {

    private static final long serialVersionUID = 1L;

    /** Holds the methods used to create NetApp related exceptions */
    public static final NetAppExceptions exceptions = ExceptionMessagesProxy.create(NetAppExceptions.class);

    /** Holds the methods used to create NetApp related error conditions */
    public static final NetAppErrors errors = ExceptionMessagesProxy.create(NetAppErrors.class);

    private NetAppException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

    @Deprecated
    public NetAppException(String s) {
        super(ServiceCode.NETAPP_ERROR, null, s, null);
    }

    @Deprecated
    public NetAppException(String s, Throwable throwable) {
        super(ServiceCode.NETAPP_ERROR, throwable, s, null);
    }
}
