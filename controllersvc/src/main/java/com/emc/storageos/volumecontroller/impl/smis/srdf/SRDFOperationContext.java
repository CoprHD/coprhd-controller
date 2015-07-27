/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.srdf;

import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.impl.providerfinders.FindProviderStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorResultFilter;
import com.emc.storageos.volumecontroller.impl.smis.srdf.collectors.CollectorStrategy;
import com.emc.storageos.volumecontroller.impl.smis.srdf.executors.ExecutorStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by bibbyi1 on 3/24/2015.
 */
public class SRDFOperationContext {
    private static final Logger log = LoggerFactory.getLogger(SRDFOperationContext.class);

    private FindProviderStrategy providerFinder;
    private CollectorStrategy collector;
    private List<CollectorResultFilter> filters = new LinkedList<>();
    private ExecutorStrategy executor;
    private Volume target;

    public FindProviderStrategy getProviderFinder() {
        return providerFinder;
    }

    public void setProviderFinder(FindProviderStrategy providerFinder) {
        this.providerFinder = providerFinder;
    }

    public CollectorStrategy getCollector() {
        return collector;
    }

    public void setCollector(CollectorStrategy collector) {
        this.collector = collector;
    }

    public ExecutorStrategy getExecutor() {
        return executor;
    }

    public void setExecutor(ExecutorStrategy executor) {
        this.executor = executor;
    }

    public Volume getTarget() {
        return target;
    }

    public void setTarget(Volume target) {
        this.target = target;
    }

    public void appendFilters(CollectorResultFilter... filters) {
        for (CollectorResultFilter filter : filters) {
            this.filters.add(filter);
        }
    }

    public void perform() throws Exception {
        // Find the provider to make SMI-S calls to.
        StorageSystem provider = providerFinder.find();
        if (provider == null) {
            log.error("Both source and target providers are not reachable");
            throw new IllegalStateException("Both source and target providers are not reachable");
        }

        // Collect object paths for a GroupSync or one or more StorageSyncs.
        Collection<CIMObjectPath> objectPaths = collector.collect(provider, target);

        for (CollectorResultFilter<CIMObjectPath> filter : filters) {
            objectPaths = filter.filter(objectPaths, provider);
        }

        if (!objectPaths.isEmpty()) {
            // Execution entails building the arguments and calling the appropriate SMI-S method.
            executor.execute(objectPaths, provider);
        } else {
            log.info("Skipped execution because no object paths were found");
        }
    }
}
