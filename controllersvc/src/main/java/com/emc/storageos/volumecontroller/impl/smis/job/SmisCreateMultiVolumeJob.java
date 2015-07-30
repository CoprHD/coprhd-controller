/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.WBEMException;
import javax.wbem.client.WBEMClient;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DistributedSemaphore;

import org.apache.curator.framework.recipes.locks.Lease;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;

/**
 * SmisJob implementation that handles the case of multi-volume create
 */
public class SmisCreateMultiVolumeJob extends SmisAbstractCreateVolumeJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisCreateMultiVolumeJob.class);
    // These atomic references are for use in the volume rename step in processVolume
    private static final AtomicReference<NameGenerator> _nameGeneratorRef = new AtomicReference<NameGenerator>();
    private static final AtomicReference<CIMPropertyFactory> _propertyFactoryRef =
            new AtomicReference<CIMPropertyFactory>();
    // Executor used for short-lived task to update
    // the volume names in the background
    private static final Executor _executor = Executors.newCachedThreadPool();
    private static final AtomicReference<DistributedSemaphore> _distributedLock =
            new AtomicReference<DistributedSemaphore>();
    private static final AtomicReference<CoordinatorClient> _coordinator =
            new AtomicReference<CoordinatorClient>();
    private static final int MAX_PERMITS = 10;

    public SmisCreateMultiVolumeJob(CIMObjectPath cimJob,
            URI storageSystem, URI storagePool, int count,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, storagePool, taskCompleter, String.format("Create%dVolumes", count));

        // Keep a reference to these singletons
        _propertyFactoryRef.compareAndSet(null,
                (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory"));
        _nameGeneratorRef.compareAndSet(null,
                (NameGenerator) ControllerServiceImpl.getBean("defaultNameGenerator"));
        _coordinator.compareAndSet(null,
                (CoordinatorClient) ControllerServiceImpl.getBean("coordinator"));
        _distributedLock.compareAndSet(null, _coordinator.get().
                getSemaphore(this.getClass().getSimpleName(), MAX_PERMITS));
    }

    /**
     * Execute operations to rename the volume name, which will run in the background.
     * 
     * @param dbClient [in] - Client for reading/writing from/to database.
     * @param client [in] - WBEMClient for accessing SMI-S provider data
     * @param volume [in] - Reference to Bourne's Volume object
     * @param volumePath [in] - Name reference to the SMI-S side volume object
     */
    @Override
    void specificProcessing(final DbClient dbClient, final WBEMClient client, final Volume volume,
            CIMInstance volumeInstance, final CIMObjectPath volumePath) {
        _executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    // Get the tenant name from the volume
                    TenantOrg tenant = dbClient.queryObject(TenantOrg.class, volume.getTenant().getURI());
                    String tenantName = tenant.getLabel();
                    // Generate the name, then modify the volume instance
                    // that was successfully created
                    if (_nameGeneratorRef.get() == null) {
                        _nameGeneratorRef.compareAndSet(null,
                                (NameGenerator) ControllerServiceImpl.getBean("defaultNameGenerator"));
                    }
                    String generatedName = _nameGeneratorRef.get().generate(tenantName, volume.getLabel(),
                            volume.getId().toString(), '-', SmisConstants.MAX_VOLUME_NAME_LENGTH);
                    changeVolumeName(dbClient, client, volumePath, volume, generatedName);
                } catch (DatabaseException e) {
                    _log.error("Encountered an error while trying to set the volume name", e);
                } catch (Exception e) {
                    _log.error("Encountered an error while trying to set the volume name", e);
                }
            }
        });
    }

    /**
     * Method will modify the name of a given volume to a generate name.
     * 
     * @param dbClient [in] - Client instance for reading/writing from/to DB
     * @param client [in] - WBEMClient used for reading/writing from/to SMI-S
     * @param volumePath [in] - CIMObjectPath referencing the volume
     * @param volume [in] - Volume object
     */
    private void changeVolumeName(DbClient dbClient, WBEMClient client, CIMObjectPath volumePath,
            Volume volume, String name) {
        Lease lease = null;
        try {
            _log.info(String.format("Attempting to modify volume %s to %s", volumePath.toString(), name));
            if (_propertyFactoryRef.get() == null) {
                _propertyFactoryRef.compareAndSet(null,
                        (CIMPropertyFactory) ControllerServiceImpl.getBean("CIMPropertyFactory"));
            }
            CIMInstance toUpdate = new CIMInstance(volumePath,
                    new CIMProperty[] {
                            _propertyFactoryRef.get().string(SmisConstants.CP_ELEMENT_NAME, name)
                    }
                    );
            if (_distributedLock.get() == null) {
                if (_coordinator.get() == null) {
                    _coordinator.compareAndSet(null,
                            (CoordinatorClient) ControllerServiceImpl.getBean("coordinator"));
                }
                _distributedLock.compareAndSet(null, _coordinator.get().
                        getSemaphore(this.getClass().getSimpleName(), MAX_PERMITS));
            }
            lease = _distributedLock.get().acquireLease();
            client.modifyInstance(toUpdate, SmisConstants.PS_ELEMENT_NAME);
            _distributedLock.get().returnLease(lease);
            lease = null;
            volume.setDeviceLabel(name);
            dbClient.persistObject(volume);
            _log.info(String.format("Volume name has been modified to %s", name));
        } catch (WBEMException e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        } catch (DatabaseException e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        } catch (Exception e) {
            _log.error("Encountered an error while trying to set the volume name", e);
        } finally {
            if (lease != null) {
                try {
                    _distributedLock.get().returnLease(lease);
                } catch (Exception e) {
                    _log.error("Exception when trying to return lease", e);
                }
            }
        }
    }

}
