package com.emc.storageos.migrationcontroller;

import com.emc.storageos.svcs.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@SuppressWarnings("serial")
public class MigrationControllerException extends InternalException {

    /** Holds the methods used to create Migration related exceptions */
    public static final MigrationControllerExceptions exceptions = ExceptionMessagesProxy.create(MigrationControllerExceptions.class);

    /** Holds the methods used to create Migration related error conditions */
    public static final MigrationErrors errors = ExceptionMessagesProxy.create(MigrationErrors.class);

    private MigrationControllerException(final ServiceCode code, final Throwable cause,
            final String detailBase, final String detailKey, final Object[] detailParams) {
        super(false, code, cause, detailBase, detailKey, detailParams);
    }

}
