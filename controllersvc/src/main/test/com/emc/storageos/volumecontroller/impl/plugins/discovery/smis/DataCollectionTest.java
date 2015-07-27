/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Resource;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJob.JobOrigin;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;

/**
 * SMI scan and discovery test
 * 
 * Configuration
 * 
 * 1. VM arguments 
 *  -Dproduct.home=/opt/storageos
 * 
 * 2. Classpath
 *  Folders 
 *      cimadapter/src/main/resources
 *      controllersvc/src/main/test/resources 
 *      discoveryplugins/src/main/resources
 *      dbutils/src/conf
 * 
 *  Project
 *      dbutils (project only, no exported entries and required projects)
 * 
 * Log file will be in controllersvc/logs dir. Configure
 * cimadapter/src/main/resources/log4j.properties as necessary
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath*:**CimAdapterSpringContext-test.xml" })
public class DataCollectionTest {
    private static final Logger _logger = LoggerFactory
            .getLogger(DataCollectionTest.class);
    
    @Autowired
    private DbClient _dbClient = null;
    @Autowired
    private CoordinatorClientImpl _coordinator = null;
    @Autowired
    private ConnectionManager _connectionManager = null;
    @Resource(name = "configinfo")
    private Map<String, String> _configInfo;
    @Autowired(required = false)
    private StorageProvider _provider = null;
    private URI _providerURI = null;
    
    private DataCollectionJobUtil _jobUtil = null;
    private DataCollectionJobConsumer _jobConsumer = null;

    private JobOrigin _jobOrigin = JobOrigin.SCHEDULER; // change it to USER_API to run scan only in testScan()
    private boolean isTestNewProvider = false;

    @Before
    public void setup() {
        try {
            for(Lock lock : Lock.values()) {
                Method method = lock.getClass().getDeclaredMethod("setLock", InterProcessLock.class);
                method.setAccessible(true);
                Object[] parameters = new Object[1];
                parameters[0] = _coordinator.getLock(lock.name());
                method.invoke(lock, parameters);
            }

            _dbClient.start();
            
            if (_provider != null) {
                String providerKey = _provider.getIPAddress() + "-" + _provider.getPortNumber();
                List<StorageProvider> providers = CustomQueryUtility.getActiveStorageProvidersByProviderId(_dbClient, providerKey);
                if (providers != null && !providers.isEmpty()) {
                    _providerURI = providers.get(0).getId();
                   _logger.warn("Provider has already been in db.");
                }
                else if (isTestNewProvider) {     
                    _providerURI = URIUtil.createId(StorageProvider.class);
                    _provider.setId(_providerURI);
                    _dbClient.createObject(_provider);
                }
            }
            
            CIMConnectionFactory cimConnectionFactory = new CIMConnectionFactory();
            cimConnectionFactory.setDbClient(_dbClient);
            cimConnectionFactory.setConnectionManager(_connectionManager);

            DataCollectionJobScheduler scheduler = new DataCollectionJobScheduler();
            scheduler.setConfigInfo(_configInfo);
            scheduler.setConnectionFactory(cimConnectionFactory);
            scheduler.setCoordinator(_coordinator);
            scheduler.setDbClient(_dbClient);

            _jobUtil = new DataCollectionJobUtil();
            _jobUtil.setDbClient(_dbClient);
            _jobUtil.setConfigInfo(_configInfo);

            _jobConsumer = new TestDataCollectionJobConsumer();
            _jobConsumer.setConfigInfo(_configInfo);
            _jobConsumer.setConnectionFactory(cimConnectionFactory);
            _jobConsumer.setCoordinator(_coordinator);
            _jobConsumer.setDbClient(_dbClient);
            _jobConsumer.setUtil(_jobUtil);
            _jobConsumer.setJobScheduler(scheduler);

            VersionChecker versionChecker = new VersionChecker();
            versionChecker.setCoordinator(_coordinator);
        } catch (Exception e) {
            _logger.error("Failed to run setup. Exception - " + e.getMessage());
            _logger.error(e.getMessage(), e);
        }
    }

    @After
    public void cleanup() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    /*
     * Clean up DB by removing providers, arrays, pools and ports   
     * 
     * note - all StorageProvider, StorageSystem, StoragePool and StoragePort objects will be removed 
     */
    @Ignore // so that won't be run accidently
    @Test
    public void cleanDB() {
        List<Class<? extends DataObject>> types = new ArrayList<Class<? extends DataObject>>();
        types.add(StorageProvider.class);
        types.add(StorageSystem.class);
        types.add(StoragePool.class);
        types.add(StoragePort.class);
        
        for (Class<? extends DataObject> clazz : types) {
            cleanDBObjects(clazz);
        }
    }

    /**
     * Load StorageProviders from DB and do scanning Provider(s) need to be
     * added outside this class, or run after createProvider()
     * 
     * @throws Exception
     */
    @Test
    public void testScan() throws Exception {
        _logger.info("Started Loading Storage Providers from DB");
        Iterator<URI> providerURIsIter = _dbClient.queryByType(
                StorageProvider.class, true).iterator();
        DataCollectionScanJob scanJob = new DataCollectionScanJob(_jobOrigin);
        while (providerURIsIter.hasNext()) {
            URI providerURI = providerURIsIter.next();
            if (isTestNewProvider && !providerURI.equals(_providerURI)) {
                continue;    
            }
            
            StorageProvider provider = _dbClient.queryObject(
                    StorageProvider.class, providerURI);

            provider.setLastScanTime(0L);
            provider.setManufacturer(NullColumnValueGetter.getNullStr());
            _dbClient.persistObject(provider);

            String taskId = UUID.randomUUID().toString();
            scanJob.addCompleter(new ScanTaskCompleter(StorageProvider.class,
                    providerURI, taskId));
        }

        List<URI> providers = scanJob.getProviders();
        if (providers != null && !providers.isEmpty()) {
            _logger.info("Start to process scan job");
            _jobConsumer.consumeItem(scanJob, null);
        } else {
            _logger.error("No scan job to be processed");
        }
    }

    /**
     * Load StorageSystems from DB, and run discovery Provider(s) need to be
     * added outside this class, or run after testScan()
     * 
     * @throws Exception
     */
    @Test
    public void testDiscovery() throws Exception {
        _logger.info("Started Loading Systems from DB for discovery jobs");
        Iterator<URI> systemURIsItr = _dbClient.queryByType(
                StorageSystem.class, true).iterator();
        while (systemURIsItr.hasNext()) {
            URI systemURI = systemURIsItr.next();
            StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                    systemURI);
            system.setLastDiscoveryRunTime(0L);
            _dbClient.persistObject(system);

            URI providerURI = system.getActiveProviderURI();
            if (providerURI == null
                    || providerURI.equals(NullColumnValueGetter.getNullURI())) {
                _logger.error(
                        "Skipping Discovery Job: StorageSystem {} does not have an active provider",
                        systemURI);
                continue;
            }

            StorageProvider provider = _dbClient.queryObject(
                    StorageProvider.class, providerURI);
            if (provider == null || provider.getInactive()) {
                _logger.info(
                        "Skipping Discovery Job: StorageSystem {} does not have a valid active provider",
                        systemURI);
                continue;
            }

            String taskId = UUID.randomUUID().toString();
            DiscoverTaskCompleter completer = new DiscoverTaskCompleter(
                    StorageSystem.class, systemURI, taskId, ControllerServiceImpl.DISCOVERY);
            DataCollectionJob job = new DataCollectionDiscoverJob(completer, _jobOrigin,
                    Discovery_Namespaces.ALL.name());
            _jobConsumer.consumeItem(job, null);
        }
    }

    private <T extends DataObject> void cleanDBObjects(Class<T> clazz) {
        List<URI> storageProviderURIs = _dbClient.queryByType(clazz, true);
        Iterator<T> storageProvidersIter = _dbClient.queryIterativeObjects(
                clazz, storageProviderURIs);
        while (storageProvidersIter.hasNext()) {
            _dbClient.removeObject(storageProvidersIter.next());
        }
    }
}
