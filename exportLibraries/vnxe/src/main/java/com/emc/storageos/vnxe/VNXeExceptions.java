/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
}

