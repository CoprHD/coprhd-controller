/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.metering.plugins.smis;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.DataCollectionJobUtil;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;

/**
 * To-Do : Mock Interface needs to be done To-Do :Add extra unit tests To-Do :
 * Add proper assertion cases
 * 
 * 
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath*:**/standalone-plugin-context.xml" })
public class SMICommunicationInterfaceTest {
    private static final Logger _LOGGER = LoggerFactory
            .getLogger(SMICommunicationInterfaceTest.class);
    private ApplicationContext _context = null;
    private DbClient _dbClient = null;

    @Autowired
    private CoordinatorClientImpl coordinator = null;

    @Autowired
    private SMICommunicationInterface xiv = null;
    @Autowired
    private ConnectionManager connectionManager = null;
    @Resource(name = "configinfo")
    private Map<String, String> configinfo;

    private CIMConnectionFactory cimConnectionFactory = null;
    private static final String UNIT_TEST_CONFIG_FILE = "sanity";

    private static final String providerIP = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.ipaddress");
    private static final String providerPortStr = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.port");
    private static final int providerPort = Integer.parseInt(providerPortStr);
    private static final String providerUser = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.username");
    private static final String providerPassword = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.password");
    private static final String providerUseSsl = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.usessl");
    private static final String providerNamespace = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.namespace");
    private static final String providerArraySerial = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.array.serial");
    private static final String providerManfacturer = EnvConfig.get(UNIT_TEST_CONFIG_FILE, "smis.host.manfacturer");
    private static boolean isProviderSslEnabled = Boolean.parseBoolean(providerUseSsl);

    @Before
    public void setup() {
        // _context = new ClassPathXmlApplicationContext("/plugin-context.xml");
        try {
            _dbClient = Cassandraforplugin.returnDBClient();
            cimConnectionFactory = new CIMConnectionFactory();
            cimConnectionFactory.setDbClient(_dbClient);
            cimConnectionFactory.setConnectionManager(connectionManager);

            cleanDB();

            URI tenantURI = URIUtil.createId(TenantOrg.class);
            URI projURI = URIUtil.createId(Project.class);

            TenantOrg tenantorg = new TenantOrg();
            tenantorg.setId(URIUtil.createId(TenantOrg.class));
            tenantorg.setLabel("some tenant");
            tenantorg.setParentTenant(new NamedURI(URIUtil.createId(TenantOrg.class),
                    tenantorg.getLabel()));
            _LOGGER.info("TenantOrg :" + tenantorg.getId());
            _dbClient.createObject(tenantorg);

            Project proj = new Project();
            proj.setId(URIUtil.createId(Project.class));
            proj.setLabel("some name");
            proj.setTenantOrg(new NamedURI(tenantorg.getId(), tenantorg.getLabel()));
            _LOGGER.info("Project :" + proj.getId());
            _LOGGER.info("TenantOrg-Proj :" + proj.getTenantOrg());
            _dbClient.createObject(proj);

            Volume vol = new Volume();
            vol.setId(URIUtil.createId(Volume.class)); // 02751
            vol.setLabel("some volume");
            vol.setNativeGuid("SYMMETRIX+000187910031+VOLUME+00000");
            vol.setVirtualPool(URIUtil.createId(VirtualPool.class));
            vol.setProject(new NamedURI(proj.getId(), vol.getLabel()));
            _dbClient.createObject(vol);

            // StorageProvider provider = new StorageProvider();
            // provider.setId(URIUtil.createId(StorageProvider.class));
            // provider.setIPAddress(providerIP);
            // provider.setPassword(providerPassword);
            // provider.setUserName(providerUser);
            // provider.setPortNumber(providerPort);
            // provider.setUseSSL(isProviderSslEnabled);
            // provider.setInterfaceType(StorageProvider.InterfaceType.smis.name());
            // _dbClient.createObject(provider);
        } catch (Exception e) {
            _LOGGER.error(e.getMessage(), e);
        }
        // to be used for Mock
        // System.setProperty("wbeminterface",
        // "com.emc.srm.base.discovery.plugins.smi.MockWBEMClient");
    }

    private void sleeptest(final SMICommunicationInterface smi) {
        ExecutorService service = Executors.newFixedThreadPool(3);
        service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    smi.collectStatisticsInformation(createAccessProfile());
                } catch (BaseCollectionException e) {
                    _LOGGER.error(e.getMessage(), e);
                }
            }
        });
        service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    smi.scan(createAccessProfile());
                } catch (BaseCollectionException e) {
                    _LOGGER.error(e.getMessage(), e);
                }
            }
        });
        service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(300000);
                } catch (InterruptedException e) {
                    _LOGGER.error(e.getMessage(), e);
                }
                _LOGGER.info("Started Querying");
                long latchcount = Cassandraforplugin.query(_dbClient);
                Assert.assertTrue("Processed 1 record", latchcount == 1);
                smi.cleanup();
            }
        });
        service.shutdown();
        try {
            service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            _LOGGER.error(e.getMessage(), e);
        }
    }

    private void scanTest(final SMICommunicationInterface smi) {
        ExecutorService service = Executors.newFixedThreadPool(2);
        service.submit(new Runnable() {
            @Override
            public void run() {
                try {

                    smi.scan(createXIVAccessProfile());
                } catch (BaseCollectionException e) {
                    _LOGGER.error(e.getMessage(), e);
                }
            }
        });

        service.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(300000);
                } catch (InterruptedException e) {
                    _LOGGER.error(e.getMessage(), e);
                }
                _LOGGER.info("Started Querying");
                long latchcount = Cassandraforplugin.query(_dbClient);
                Assert.assertTrue("Processed 1 record", latchcount == 1);
                smi.cleanup();
            }
        });

        service.shutdown();
        try {
            service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            _LOGGER.error(e.getMessage(), e);
        }
    }

    // Need to start coordinator & DB service, before running this test
    @Test
    public void testVNXBlockPlugin() {
        try {
            final SMICommunicationInterface _smiplugin = (SMICommunicationInterface) _context
                    .getBean("block");
            Map<String, Object> map1 = new ConcurrentHashMap<String, Object>();
            Set<String> idset = new HashSet<String>();
            idset.add("SYMMETRIX+000194900404+VOLUME+00000");
            map1.put("000194900404-block-Volumes", idset);
            _smiplugin.injectCache(map1);
            _smiplugin.injectDBClient(new DbClientImpl());
            _smiplugin.scan(createAccessProfile());
            // sleeptest(_smiplugin);
        } catch (Exception e) {
            _LOGGER.error(e.getMessage(), e);
        }
    }

    // SYMMETRIX+000187910031
    private AccessProfile createAccessProfile() {
        AccessProfile _profile = new AccessProfile();
        _profile.setInteropNamespace("interop");
        _profile.setIpAddress(providerIP);
        _profile.setPassword(providerPassword);
        _profile.setProviderPort(providerPortStr);
        _profile.setnamespace("Performance");
        _profile.setelementType("Array");
        _profile.setUserName(providerUser);
        _profile.setSslEnable(providerUseSsl); // need to set Array serial ID;
        _profile.setserialID(providerArraySerial);
        return _profile;
    }

    // Need to start coordinator & DB service, before running this test
    @Test
    public void testXIVBlockPlugin() {
        StorageProvider provider = createXIVProvider();

        try {
            Map<String, Object> cache = new ConcurrentHashMap<String, Object>();
            Set<String> idset = new HashSet<String>();
            idset.add("SYMMETRIX+000194900404+VOLUME+00000");
            cache.put("000194900404-block-Volumes", idset);
            xiv.injectCache(cache);
            xiv.injectDBClient(_dbClient);
            xiv.injectCoordinatorClient(coordinator);
            // scanTest(_smiplugin);

            AccessProfile profile = populateSMISAccessProfile(provider);
            verifyDB(profile);

            Map<String, StorageSystemViewObject> storageSystemsCache = Collections
                    .synchronizedMap(new HashMap<String, StorageSystemViewObject>());
            profile.setCache(storageSystemsCache);

            xiv.scan(profile);

            DataCollectionJobUtil util = new DataCollectionJobUtil();
            util.setDbClient(_dbClient);
            util.setConfigInfo(configinfo);
            List<URI> providerList = new ArrayList<URI>();
            providerList.add(provider.getId());

            util.performBookKeeping(storageSystemsCache, providerList);

            // provider.getStorageSystems();
            // profile.setSystemId(provider.getSystemId());
            profile.setserialID(profile.getserialID());

            xiv.discover(profile);

        } catch (Exception e) {
            _LOGGER.error(e.getMessage(), e);
        }
    }

    private StorageProvider createXIVProvider() {
        StorageProvider provider = new StorageProvider();
        provider.setId(URIUtil.createId(StorageProvider.class));
        provider.setIPAddress(providerIP);
        provider.setPassword(providerPassword);
        provider.setUserName(providerUser);
        provider.setPortNumber(providerPort);
        provider.setUseSSL(isProviderSslEnabled);
        provider.setInterfaceType(StorageProvider.InterfaceType.smis.name());
        provider.setManufacturer(providerManfacturer);
        _dbClient.createObject(provider);

        return provider;
    }

    private AccessProfile createXIVAccessProfile() {
        AccessProfile _profile = new AccessProfile();
        _profile.setInteropNamespace(providerNamespace);
        _profile.setIpAddress(providerIP);
        _profile.setUserName(providerUser);
        _profile.setPassword(providerPassword);
        _profile.setProviderPort(String.valueOf(providerPort));
        // _profile.setnamespace("Performance");
        _profile.setelementType("Array");
        _profile.setSslEnable(String.valueOf(isProviderSslEnabled));
        // _profile.setserialID(providerArraySerial);

        return _profile;
    }

    private AccessProfile populateSMISAccessProfile(StorageProvider provider) {
        AccessProfile _profile = new AccessProfile();
        _profile.setSystemId(provider.getId());
        _profile.setSystemClazz(provider.getClass());
        _profile.setIpAddress(provider.getIPAddress());
        _profile.setUserName(provider.getUserName());
        _profile.setPassword(provider.getPassword());
        _profile.setSystemType(Type.ibmxiv.name());
        _profile.setProviderPort(String.valueOf(provider.getPortNumber()));
        _profile.setSslEnable(String.valueOf(provider.getUseSSL()));
        _profile.setInteropNamespace(Constants.INTEROP);
        _profile.setProps(configinfo);
        _profile.setCimConnectionFactory(cimConnectionFactory);

        return _profile;
    }

    private void cleanDB() {
        List<URI> storageProviderURIs = _dbClient.queryByType(StorageProvider.class,
                false);
        Iterator<StorageProvider> storageProvidersIter = _dbClient.queryIterativeObjects(StorageProvider.class,
                storageProviderURIs);
        List<StorageProvider> storageProviders = new ArrayList<StorageProvider>();
        while (storageProvidersIter.hasNext()) {
            // add to list to be deleted
            storageProviders.add(storageProvidersIter.next());
        }

        // delete all objects in the list
        _dbClient.removeObject(storageProviders
                .toArray(new StorageProvider[storageProviders.size()]));
    }

    private void verifyDB(AccessProfile profile) {
        URI providerURI = null;
        StorageProvider provider = null;
        String detailedStatusMessage = "Unknown Status";
        // try {
        _LOGGER.info("Access Profile Details :" + profile.toString());
        providerURI = profile.getSystemId();
        provider = _dbClient.queryObject(StorageProvider.class, providerURI);
        Assert.assertNotNull(provider);
        // }
    }
}
