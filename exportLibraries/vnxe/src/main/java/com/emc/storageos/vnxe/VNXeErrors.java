/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create {@link ServiceError}s
 * related to Vnxe Devices
 * <p/>
 * Remember to add the English message associated to the method in VnxeErrors.properties and use the annotation {@link DeclareServiceCode}
 * to set the service code associated to this error condition. You may need to create a new service code if there is no an existing one
 * suitable for your error condition.
 * <p/>
 * For more information or to see an example, check the Developers Guide section in the Error Handling Wiki page:
 * http://confluence.lab.voyence.com/display/OS/Error+Handling+Framework+and+Exceptions+in+ViPR
 */
@MessageBundle
public interface VNXeErrors {

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public ServiceError jobFailed(final String operationName);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public ServiceError jobFailed(final String operationName, final String reason);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public ServiceError unableToCreateFileSystem(final String reason);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public ServiceError unableToDeleteFileSystem(final String reason);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public ServiceError cannotRestoreAttachedSnapshot(final String snapshotId);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public ServiceError operationNotSupported(final String operation, final String deviceType);

}
