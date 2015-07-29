/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
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
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;

/**
 * HDSJob implementation that handles the case of multi-volume create
 */
public class HDSCreateMultiVolumeJob extends HDSAbstractCreateVolumeJob {
    private static final Logger _log = LoggerFactory.getLogger(HDSCreateMultiVolumeJob.class);

    // These atomic references are for use in the volume rename step in processVolume
    private static final AtomicReference<NameGenerator> _nameGeneratorRef = new AtomicReference<NameGenerator>();
    private static final AtomicReference<CIMPropertyFactory> _propertyFactoryRef =
            new AtomicReference<CIMPropertyFactory>();
    // Executor used for short-lived task to update
    // the volume names in the background
    private static final Executor _executor = Executors.newCachedThreadPool();

    public HDSCreateMultiVolumeJob(String hdsJob,
            URI storageSystem, URI storagePool, int count,
            TaskCompleter taskCompleter) {
        super(hdsJob, storageSystem, storagePool, taskCompleter, String.format("Create%dVolumes", count));

        // Keep a reference to these singletons
        _nameGeneratorRef.compareAndSet(null,
                (NameGenerator) ControllerServiceImpl.getBean("defaultNameGenerator"));
    }

    /**
     * Execute operations to rename the volume name, which will run in the background.
     * 
     * @param dbClient [in] - Client for reading/writing from/to database.
     * @param client [in] - HDSApiClient for accessing Hitachi HiCommand DM data
     * @param volume [in] - Reference to Bourne's Volume object
     */
    @Override
    void specificProcessing(final DbClient dbClient, final HDSApiClient client, final Volume volume) {
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
                            volume.getId().toString(), '-', HDSConstants.MAX_VOLUME_NAME_LENGTH);
                    changeVolumeName(dbClient, client, volume, generatedName);
                } catch (DatabaseException e) {
                    _log.error("Encountered an error while trying to set the volume name", e);
                } catch (Exception e) {
                    _log.error("Encountered an error while trying to set the volume name", e);
                }
            }
        });
    }

}