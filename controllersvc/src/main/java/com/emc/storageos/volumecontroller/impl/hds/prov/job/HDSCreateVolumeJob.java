/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NameGenerator;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;

/**
 * A HDS Volume Create job
 */
public class HDSCreateVolumeJob extends HDSAbstractCreateVolumeJob {

    private static final Logger log = LoggerFactory.getLogger(HDSCreateVolumeJob.class);
    // These atomic references are for use in the volume rename step in processVolume
    private static final AtomicReference<NameGenerator> _nameGeneratorRef = new AtomicReference<NameGenerator>();

    public HDSCreateVolumeJob(String hdsJob,
            URI storageSystem,
            URI storagePool,
            TaskCompleter taskCompleter) {
        super(hdsJob, storageSystem, storagePool, taskCompleter, "CreateSingleVolume");
        // Keep a reference to these singletons
        _nameGeneratorRef.compareAndSet(null,
                (NameGenerator) ControllerServiceImpl.getBean("defaultNameGenerator"));
    }

    public HDSCreateVolumeJob(String hdsJob,
            URI storageSystem,
            URI storagePool,
            TaskCompleter taskCompleter,
            String name) {
        super(hdsJob, storageSystem, storagePool, taskCompleter, name);
    }

    /**
     * This simply updates the deviceLabel name for the single volume that was created.
     * 
     * @param dbClient [in] - Client for reading/writing from/to database.
     * @param client [in] - HDSAPI Client for accessing HiCommand DM data
     * @param volume [in] - Reference to Bourne's Volume object
     */
    @Override
    void specificProcessing(DbClient dbClient, HDSApiClient client, Volume volume) {
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
                    volume.getId().toString(), '-', HDSConstants.MAX_VOLUME_NAME_LENGTH);
            changeVolumeName(dbClient, client, volume, generatedName);
        } catch (DatabaseException e) {
            log.error("Encountered an error while trying to set the volume name", e);
        } catch (Exception e) {
            log.error("Encountered an error while trying to set the volume name", e);
        }
    }

}