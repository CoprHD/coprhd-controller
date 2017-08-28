/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.vnxe;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * This interface holds all the methods used to create VNXeExceptions
 */
@MessageBundle
public interface VNXeExceptions {

    @DeclareServiceCode(ServiceCode.VNXE_UNEXPECTED_DATA)
    public VNXeException unexpectedDataError(final String msg, Throwable t);

    @DeclareServiceCode(ServiceCode.VNXE_UNEXPECTED_DATA)
    public VNXeException unexpectedDataError(final String msg);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public VNXeException vnxeCommandFailed(final String msg, final Throwable cause);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public VNXeException vnxeCommandFailed(final String msg, final String code, final String message);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public VNXeException vnxeCommandFailed(final String message);

    @DeclareServiceCode(ServiceCode.VNXE_DISCOVERY_ERROR)
    public VNXeException discoveryError(final String msg, Throwable t);
    
    @DeclareServiceCode(ServiceCode.VNXE_DISCOVERY_ERROR)
    public VNXeException scanFailed(String ip, Throwable t);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public VNXeException authenticationFailure(String vnxeUri);
    
    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public VNXeException nullJobForDeleteGroupSnapshot(final String snapshotId, final String repGrpId);

    @DeclareServiceCode(ServiceCode.VNXE_COMMAND_ERROR)
    public VNXeException hluRetrievalFailed(final String msg, final Throwable t);
}
