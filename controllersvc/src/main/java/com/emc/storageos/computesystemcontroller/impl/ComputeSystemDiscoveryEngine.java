/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computesystemcontroller.impl;

import java.net.URI;
import java.util.List;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.emc.storageos.computesystemcontroller.exceptions.CompatibilityException;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.ModelClient;
import com.emc.storageos.db.client.impl.ModelClientImpl;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import org.apache.curator.framework.recipes.locks.InterProcessLock;

/**
 * Discovery engine that will discover Hosts and vCenters
 */
@Component
public class ComputeSystemDiscoveryEngine {
    private static final Logger LOG = LoggerFactory.getLogger(ComputeSystemDiscoveryEngine.class);

    @Autowired
    private List<ComputeSystemDiscoveryAdapter> discoveryAdapters;

    private CoordinatorClient coordinatorClient;

    private DbClient dbClient;

    private ModelClient modelClient;

    public void setCoordinatorClient(CoordinatorClient client) {
        this.coordinatorClient = client;
        this.shareCoordinatorClient();
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
        this.shareDbClient();
    }

    @PostConstruct
    public void init() throws Exception {
    }

    /**
     * Gets the target object for
     * 
     * @param id
     * @return
     */
    protected DiscoveredSystemObject getTarget(String id) throws ComputeSystemControllerException {
        try {
            DiscoveredSystemObject target = modelClient.findById(URI.create(id));
            return target;
        } catch (Exception e) {
            LOG.error("Could not get discovery target: " + id);
            throw ComputeSystemControllerException.exceptions.targetNotFound(id);
        }
    }

    /**
     * Performs discovery of a given target. An exclusive lock is obtained for the target so that only a single node can
     * be performing discovery for any given object at a time.
     * 
     * @param targetId
     *            the ID of the target to discover.
     * 
     * @throws Exception
     *             if an error occurs obtaining a lock.
     */
    public void discover(String targetId) throws Exception {
        InterProcessLock lock = coordinatorClient.getLock(targetId);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Acquiring lock: " + targetId);
        }
        lock.acquire();
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Acquired lock: " + targetId);
            }
            discoverInLock(targetId);
        } finally {
            lock.release();
            if (LOG.isDebugEnabled()) {
                LOG.debug("Lock Released: " + targetId);
            }
        }
    }

    /**
     * Performs the discovery, within a lock.
     * 
     * @param targetId
     *            the ID of the target to discover.
     */
    protected void discoverInLock(String targetId) {
        DiscoveredSystemObject target = modelClient.findById(URI.create(targetId));
        if (target == null) {
            LOG.error("Could not find: " + targetId);
            throw ComputeSystemControllerException.exceptions.targetNotFound(targetId);
        }

        ComputeSystemDiscoveryAdapter adapter = getDiscoveryAdapter(targetId);
        if (adapter != null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Discovering target " + target.getLabel() + " [" + targetId + "]");
            }
            try {
                adapter.discoverTarget(targetId);
                if (LOG.isInfoEnabled()) {
                    LOG.info("Discovery completed for " + target.getLabel() + " [" + targetId + "]");
                }
            } catch (CompatibilityException e) {
                String errorMessage = adapter.getErrorMessage(e);
                LOG.error("Device is incompatible: " + target.getLabel() + " [" + targetId + "]: " + errorMessage);
                adapter.discoveryFailure(target, DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name(), errorMessage);
                throw e;
            } catch (RuntimeException e) {
                String errorMessage = adapter.getErrorMessage(e);
                LOG.error("Discovery failed for " + target.getLabel() + " [" + targetId + "]: " + errorMessage, e);
                adapter.discoveryFailure(target, DiscoveredDataObject.CompatibilityStatus.UNKNOWN.name(), errorMessage);
                throw ComputeSystemControllerException.exceptions.discoverFailed(targetId, e);
            }
        }
        else {
            LOG.warn("No discovery adapter for target " + target.getLabel() + " [" + targetId + "]");
            target.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.UNKNOWN.name());
            dbClient.persistObject(target);
            throw ComputeSystemControllerException.exceptions.discoveryAdapterNotFound(target.getLabel(), targetId);
        }
    }

    /**
     * Gets the discovery adapter to use for the given target.
     * 
     * @param targetId
     *            the ID of the target to discover.
     * @return the discovery adapter, or null if the target cannot be discovered.
     */
    protected ComputeSystemDiscoveryAdapter getDiscoveryAdapter(String targetId) {
        for (ComputeSystemDiscoveryAdapter adapter : discoveryAdapters) {
            if (adapter.isSupportedTarget(targetId)) {
                return adapter;
            }
        }
        return null;
    }

    private void shareDbClient() {
        this.modelClient = new ModelClientImpl(dbClient);

        for (ComputeSystemDiscoveryAdapter discoveryAdapter : discoveryAdapters) {
            discoveryAdapter.setModelClient(this.modelClient);
            discoveryAdapter.setDbClient(this.dbClient);
        }
    }

    private void shareCoordinatorClient() {
        for (ComputeSystemDiscoveryAdapter discoveryAdapter : discoveryAdapters) {
            discoveryAdapter.getVersionValidator().setCoordinatorClient(this.coordinatorClient);
            discoveryAdapter.setCoordinator(this.coordinatorClient);
        }
    }

}
