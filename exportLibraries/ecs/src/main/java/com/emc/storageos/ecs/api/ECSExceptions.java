package com.emc.storageos.ecs.api;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

public interface ECSExceptions {

    @DeclareServiceCode(ServiceCode.ECS_CONNECTION_ERROR)
    public ECSException unableToConnect(final URI baseUrl);
}
