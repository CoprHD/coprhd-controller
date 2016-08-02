/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.Fileingestorchestration.context;

import com.emc.storageos.api.service.impl.resource.Fileingestorchestration.IngestFileStrategyFactory;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by bonduj on 8/1/2016.
 */
public interface IngestionFileRequestContext extends Iterable<UnManagedFileSystem> {

}
