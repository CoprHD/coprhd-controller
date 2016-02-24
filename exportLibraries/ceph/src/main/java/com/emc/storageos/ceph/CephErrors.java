package com.emc.storageos.ceph;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

@MessageBundle
public interface CephErrors {

    @DeclareServiceCode(ServiceCode.CEPH_OPERATION_EXCEPTION)
    public ServiceError operationIsUnsupported(String op);

    @DeclareServiceCode(ServiceCode.CEPH_OPERATION_EXCEPTION)
    public ServiceError operationFailed(final String methodName, final String cause);
}
