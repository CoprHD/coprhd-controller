/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.simulators.db;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientInetAddressMap;
import com.emc.storageos.coordinator.client.service.impl.DualInetAddress;
import com.emc.storageos.coordinator.common.impl.ServiceImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.server.impl.DbServiceImpl;
import com.emc.storageos.db.server.impl.SchemaUtil;
import com.emc.storageos.simulators.db.impl.SimulatorDbClient;
import com.emc.storageos.simulators.db.model.Directory;
import com.emc.storageos.simulators.impl.ObjectStoreImplDb;

import java.io.File;
import java.net.URI;

/**
 * Db service
 */
public class DbSvcBase {
    protected DbServiceImpl _dbsvc;
    private CoordinatorClient _coordinator;
    private SimulatorDbClient _dbClient;
    private DataObjectScanner _scanner;

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setDbClient(SimulatorDbClient dbClient) {
        _dbClient = dbClient;
    }

    public SimulatorDbClient getDbClient() {
        return _dbClient;
    }

    /**
     * Start embedded DB
     */
    public void startDb() throws Exception {
        CoordinatorClientInetAddressMap coordinatorMap = new CoordinatorClientInetAddressMap();
        coordinatorMap.setNodeName("localhost");
        coordinatorMap.setDualInetAddress(DualInetAddress.fromAddress("127.0.0.1"));
        coordinatorMap.setCoordinatorClient(_coordinator);
        _coordinator.setInetAddessLookupMap(coordinatorMap);
        _coordinator.start();

        ServiceImpl service = new ServiceImpl();
        service.setName("dbsvc");
        service.setVersion("1");
        service.setEndpoint(URI.create("thrift://localhost:9960"));
        service.setId("foobar");
        StubBeaconImpl beacon = new StubBeaconImpl(service);

        _scanner = new DataObjectScanner();
        _scanner.setPackages("com.emc.storageos.db.client.model");
        _scanner.init();

        SchemaUtil util = new SchemaUtil();
        util.setKeyspaceName("Simulators");
        util.setClusterName("Simulators");
        util.setDataObjectScanner(_scanner);
        util.setService(service);

        _dbsvc = new DbServiceImpl();
        _dbsvc.setConfig("db-simulator.yaml");
        _dbsvc.setSchemaUtil(util);
        _dbsvc.setCoordinator(_coordinator);
        _dbsvc.setService(service);
        _dbsvc.start();

        _dbClient.start();
        beacon.start();
        initDb();
    }

    /**
     * Initialize db, create root folder
     */
    public void initDb() throws Exception {
        URI root;
        Directory directory = new Directory();
        directory.setId(root = URI.create(String.format("urn:dir:ifs")));
        directory.setParent(null);
        directory.setQuota(ObjectStoreImplDb._emptyURI);
        try {
            _dbClient.persistObject(directory);
        } catch (Exception e) {
            throw new Exception("cannot create root folder info to db.");
        }
    }
}
