/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration;

import java.net.URI;

import com.emc.storageos.svcs.errorhandling.annotations.DeclareServiceCode;
import com.emc.storageos.svcs.errorhandling.annotations.MessageBundle;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;

/**
 * Created by bonduj on 8/2/2016.
 */
public interface IngestionFileExceptions {
    @DeclareServiceCode(ServiceCode.UNMANAGED_VOLUME_INGESTION_EXCEPTION)
    public IngestionFileException generalException(String message);
}
