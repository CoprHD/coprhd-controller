/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller;

import com.emc.storageos.cinder.api.CinderApiFactory;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.ibm.xiv.XIVSmisCommandHelper;
import com.emc.storageos.vplex.api.VPlexApiFactory;

/**
 * JobContext for jobs in the job queue
 */
public class JobContext
{
    private DbClient _dbClient;
    private CIMConnectionFactory _cimConnectionFactory;
    private VPlexApiFactory _vplexApiFactory;
    private HDSApiFactory hdsApiFactory;
    private SmisCommandHelper _helper;
    private XIVSmisCommandHelper _xivHelper;
    private CinderApiFactory cinderApiFactory = null;
    private IsilonApiFactory _isilonApiFactory = null;
    

    private VNXeApiClientFactory _vnxeApiClientFactory;

    public JobContext(DbClient dbClient, CIMConnectionFactory cimConnectionFactory,
            VPlexApiFactory vplexApiFactory, HDSApiFactory hdsApiFactory, CinderApiFactory cinderApiFactory,
            VNXeApiClientFactory vnxeApiClientFactory, IsilonApiFactory isilonApiFactory, SmisCommandHelper helper, 
            XIVSmisCommandHelper xivHelper) {
        _dbClient = dbClient;
        _cimConnectionFactory = cimConnectionFactory;
        _vplexApiFactory = vplexApiFactory;
        this.hdsApiFactory = hdsApiFactory;
        this.cinderApiFactory = cinderApiFactory;
        _vnxeApiClientFactory = vnxeApiClientFactory;
        _helper = helper;
        this._isilonApiFactory = isilonApiFactory;
        _xivHelper = xivHelper;
    }
    
    public JobContext(DbClient dbClient, CIMConnectionFactory cimConnectionFactory,
            VPlexApiFactory vplexApiFactory, HDSApiFactory hdsApiFactory, CinderApiFactory cinderApiFactory,
            VNXeApiClientFactory vnxeApiClientFactory, SmisCommandHelper helper) {
        _dbClient = dbClient;
        _cimConnectionFactory = cimConnectionFactory;
        _vplexApiFactory = vplexApiFactory;
        this.hdsApiFactory = hdsApiFactory;
        this.cinderApiFactory = cinderApiFactory;
        _vnxeApiClientFactory = vnxeApiClientFactory;
        _helper = helper;
        
    }

    public JobContext(DbClient dbClient, CIMConnectionFactory cimConnectionFactory,
            VPlexApiFactory vplexApiFactory, HDSApiFactory hdsApiFactory, CinderApiFactory cinderApiFactory,
            VNXeApiClientFactory vnxeApiClientFactory,SmisCommandHelper helper,
            XIVSmisCommandHelper xivHelper) {
        this(dbClient, cimConnectionFactory, vplexApiFactory, hdsApiFactory,
                cinderApiFactory, vnxeApiClientFactory, helper);

        _xivHelper = xivHelper;
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    public SmisCommandHelper getSmisCommandHelper() {
        return _helper;
    }

    public XIVSmisCommandHelper getXIVSmisCommandHelper() {
        return _xivHelper;
    }

    public CIMConnectionFactory getCimConnectionFactory() {
        return _cimConnectionFactory;
    }

    public VPlexApiFactory getVPlexApiFactory() {
        return _vplexApiFactory;
    }

    public HDSApiFactory getHdsApiFactory() {
        return hdsApiFactory;
    }

    public CinderApiFactory getCinderApiFactory() {
        return cinderApiFactory;
    }

    public VNXeApiClientFactory getVNXeApiClientFactory() {
        return _vnxeApiClientFactory;
    }
    
    public IsilonApiFactory getIsilonApiFactory() {
        return _isilonApiFactory;
    }
}