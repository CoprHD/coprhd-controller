package com.emc.storageos.ecs.api;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public interface ECSExceptions {

    @DeclareServiceCode(ServiceCode.ECS_CONNECTION_ERROR)
    public ECSException unableToConnect(final URI baseUrl, final int status);
    
    @DeclareServiceCode(ServiceCode.ECS_RETURN_PARAM_ERROR)
    public ECSException invalidReturnParameters(final URI baseUrl);
    
    @DeclareServiceCode(ServiceCode.ECS_LOGINVALIDATE_ERROR)
    public ECSException isSystemAdminFailed(final URI baseUrl, final int status);
    
    @DeclareServiceCode(ServiceCode.ECS_STORAGEPOOL_ERROR)
    public ECSException getStoragePoolsAccessFailed(final URI baseUrl, final int status);
    
    @DeclareServiceCode(ServiceCode.ECS_STATS_ERROR)
    public ECSException getStoragePoolsFailed(final String response, final Throwable cause);

}
