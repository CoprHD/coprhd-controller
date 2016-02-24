package com.emc.storageos.ceph;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public class CephException extends InternalException {

    private static final long serialVersionUID = 1997830806105943736L;

    public static final CephExceptions exceptions = ExceptionMessagesProxy.create(CephExceptions.class);

    public static final CephErrors errors = ExceptionMessagesProxy.create(CephErrors.class);

    protected CephException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

}
