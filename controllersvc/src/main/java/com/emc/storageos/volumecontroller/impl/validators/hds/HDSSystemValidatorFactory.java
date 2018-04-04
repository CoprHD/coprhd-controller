/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.validators.hds;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.volumecontroller.impl.validators.StorageSystemValidatorFactory;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.Validator;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorConfig;
import com.emc.storageos.volumecontroller.impl.validators.contexts.ExportMaskValidationContext;

public class HDSSystemValidatorFactory implements StorageSystemValidatorFactory {

    private static final Logger log = LoggerFactory.getLogger(HDSSystemValidatorFactory.class);

    private DbClient dbClient;
    private HDSApiFactory clientFactory;
    private ValidatorConfig config;


    @Override
    public Validator exportMaskDelete(ExportMaskValidationContext ctx) {
        HDSExportMaskValidator validator = new HDSExportMaskValidator(ctx.getStorage(), ctx.getExportMask(), this, ctx);
        return validator;
    }

    @Override
    public Validator removeVolumes(ExportMaskValidationContext ctx) {
        HDSExportMaskVolumesValidator validator = new HDSExportMaskVolumesValidator(ctx.getStorage(), ctx.getExportMask(), this, ctx);
        return validator;
    }

    @Override
    public Validator addVolumes(StorageSystem storage, URI exportMaskURI, Collection<Initiator> initiators) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator removeInitiators(ExportMaskValidationContext ctx) {
        HDSExportMaskInitiatorsValidator validator = new HDSExportMaskInitiatorsValidator(ctx.getStorage(), ctx.getExportMask(), this, ctx);
        return validator;
    }

    @Override
    public Validator addInitiators(StorageSystem storage, ExportMask exportMask, Collection<URI> volumeURIList) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator deleteVolumes(StorageSystem storage, Collection<Volume> volumes) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Volume> volumes(StorageSystem storageSystem, List<Volume> volumes, boolean delete, boolean remediate, ValCk[] checks) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator expandVolumes(StorageSystem storageSystem, Volume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Validator createSnapshot(StorageSystem storage, BlockSnapshot snapshot, Volume volume) {
        // TODO Auto-generated method stub
        return null;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public ValidatorConfig getConfig() {
        return config;
    }

    public void setConfig(ValidatorConfig config) {
        this.config = config;
    }

    public HDSApiFactory getClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(HDSApiFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    public Validator changePortGroupAddPaths(ExportMaskValidationContext ctx) {
        return null;
    }

    @Override
    public Validator exportPathAdjustment(ExportMaskValidationContext ctx) {
        return null;
    }
}
