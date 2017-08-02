/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vcentercontroller.exceptions;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface VcenterControllerExceptions {
    @DeclareServiceCode(ServiceCode.VCENTER_CONTROLLER_ERROR)
    public VcenterControllerException clusterException(final String details, final Throwable e);

    @DeclareServiceCode(ServiceCode.VCENTER_CONTROLLER_ERROR)
    public VcenterControllerException hostException(final String details, final Throwable e);

    @DeclareServiceCode(ServiceCode.VCENTER_CONTROLLER_ERROR)
    public VcenterControllerException unexpectedException(final String opName, final Throwable e);

    @DeclareServiceCode(ServiceCode.VCENTER_CONTROLLER_OBJECT_NOT_FOUND)
    public VcenterControllerException objectNotFoundException(final String opName, final Throwable e);

    @DeclareServiceCode(ServiceCode.VCENTER_CONTROLLER_ERROR)
    public VcenterControllerException objectConnectionException(final String opName, final Throwable e);

    @DeclareServiceCode(ServiceCode.VCENTER_CONTROLLER_ERROR)
    public VcenterControllerException serverConnectionException(final String opName, final Throwable e);
}
