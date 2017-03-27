/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.metering.plugins.vnxfile;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.metering.plugins.smis.Cassandraforplugin;
import com.emc.storageos.volumecontroller.impl.plugins.VNXFileCommunicationInterface;

/**
 * Unit test cases to test the VNX File plug-in.
 * 
 */
public class VNXFileCommunicationInterfaceTest {
    private static Logger _logger = LoggerFactory
            .getLogger(VNXFileCommunicationInterfaceTest.class);
    private ApplicationContext _context = null;
    private DbClient _dbClient = null;
    private String serialNumber = EnvConfig.get("sanity", "vnx.serial");

    public VNXFileCommunicationInterfaceTest() {
    }

    @Before
    public void setup() {
        _context = new ClassPathXmlApplicationContext("metering-vnxfile-context.xml");
        try {
            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(
                    "/dbutils-conf.xml");
            _dbClient = (DbClientImpl) ctx.getBean("dbclient");
            _dbClient = Cassandraforplugin.returnDBClient();
            final TenantOrg tenantorg = new TenantOrg();
            tenantorg.setId(URIUtil.createId(TenantOrg.class));
            tenantorg.setLabel("some org");
            tenantorg.setParentTenant(new NamedURI(URIUtil.createId(TenantOrg.class),
                    tenantorg.getLabel()));
            _logger.info("TenantOrg :" + tenantorg.getId());
            _dbClient.persistObject(tenantorg);
            final Project proj = new Project();
            proj.setId(URIUtil.createId(Project.class));
            proj.setLabel("some name");
            proj.setTenantOrg(new NamedURI(tenantorg.getId(), tenantorg.getLabel()));
            _logger.info("Project :" + proj.getId());
            _logger.info("TenantOrg-Proj :" + proj.getTenantOrg());
            _dbClient.persistObject(proj);
            final FileShare fileShare = new FileShare();
            fileShare.setId(URIUtil.createId(FileShare.class));
            fileShare.setLabel("some fileshare");
            fileShare.setNativeGuid("CELERRA+" + serialNumber);
            fileShare.setVirtualPool(URIUtil.createId(VirtualPool.class));
            fileShare.setProject(new NamedURI(proj.getId(), proj.getLabel()));
            fileShare.setCapacity(12500L);
            _dbClient.persistObject(fileShare);
        } catch (final Exception ioEx) {
            _logger.error("Exception occurred while persisting objects in db {}",
                    ioEx.getMessage());
            _logger.error(ioEx.getMessage(), ioEx);
        }
    }

    /**
     * Test case to collect statistics for VNX File.
     */
    @Test
    public void testVNXFileDataCollection() {
        try {
            _logger.debug("Executing testVNXFileDataCollection() test case.");
            final VNXFileCommunicationInterface plugin = (VNXFileCommunicationInterface) _context
                    .getBean("vnxfile");
            plugin.injectCache(new ConcurrentHashMap<String, Object>());
            plugin.injectDBClient(_dbClient);
            plugin.collectStatisticsInformation(createAccessProfile());
            long latchcount = Cassandraforplugin.query(_dbClient);
            Assert.assertTrue("Processed 0 record", latchcount == 0);
            plugin.cleanup();
        } catch (Exception e) {
            _logger.error(e.getMessage(), e);
        }
        _logger.debug("Executing testVNXFileDataCollection() test case completed.");
    }

    /**
     * Create a VNX File Access Credentials.
     * 
     * @return : AccessProfile.
     */
    private AccessProfile createAccessProfile() {
        final AccessProfile profile = new AccessProfile();
        String host = EnvConfig.get("sanity", "vnx.host");
        String userName = EnvConfig.get("sanity", "vnx.username");
        String password = EnvConfig.get("sanity", "vnx.password");
        String portNumber = EnvConfig.get("sanity", "vnx.port");
        profile.setIpAddress(host);
        profile.setUserName(userName);
        profile.setPassword(password);
        profile.setPortNumber(Integer.parseInt(portNumber));
        profile.setSystemType("vnxfile");
        profile.setProfileName("VNXFileProfile");
        Map<String, String> props = new HashMap<String, String>();
        props.put("metering-dump", "true");
        props.put("metering-dump-location", "/tmp");
        profile.setProps(props);
        // need to set Array serial ID;
        profile.setserialID(serialNumber);
        return profile;
    }
}
