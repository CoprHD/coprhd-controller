package com.emc.storageos.vplexcontroller;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorFactory;
import com.emc.storageos.vplex.api.VPlexApiFactory;

public class VPlexOperations {

    protected DbClient _dbClient;
    protected VPlexApiFactory _vplexApiFactory;
    protected VPlexApiLockManager _vplexApiLockManager;
    protected ValidatorFactory validator;

    @Autowired
    protected DataSourceFactory dataSourceFactory;
    @Autowired
    protected CustomConfigHandler customConfigHandler;
    @Autowired
    protected NetworkDeviceController _networkDeviceController;

    public void setDbClient(DbClient dbClient) {
        this._dbClient = dbClient;
    }

    public void setVplexApiLockManager(VPlexApiLockManager lockManager) {
        this._vplexApiLockManager = lockManager;
    }

    public void setValidator(ValidatorFactory validator) {
        this.validator = validator;
    }

    public void setVplexApiFactory(VPlexApiFactory _vplexApiFactory) {
        this._vplexApiFactory = _vplexApiFactory;
    }

}
