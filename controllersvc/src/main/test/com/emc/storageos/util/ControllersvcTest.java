package com.emc.storageos.util;

import static org.junit.Assert.*;

import java.net.URI;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Resource;

import org.apache.log4j.PropertyConfigurator;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import com.emc.storageos.cimadapter.connections.ConnectionManager;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.Main;
import com.emc.storageos.volumecontroller.impl.metering.plugins.smis.Cassandraforplugin;
import com.emc.storageos.volumecontroller.impl.metering.plugins.smis.SMICommunicationInterfaceTest;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;

public class ControllersvcTest {
    
    private static final Logger log = LoggerFactory
            .getLogger(ControllersvcTest.class);
    
    protected DbClient dbClient;
    protected CoordinatorClient coordinator;
    protected ApplicationContext applicationContext;
    
    private static final String args[] = {
        "file:/opt/storageos/conf/controller-conf.xml",
        "file:/opt/storageos/conf/controller-emc-conf.xml",
        "file:/opt/storageos/conf/controller-oss-conf.xml"
    };
    
    @Before
    public void setup() {
        PropertyConfigurator.configure("log4j.properties");
        log.info("Beginning logging");
        Properties sysProps = System.getProperties();
        sysProps.put("buildType", "emc");
        sysProps.put("java.library.path", "/opt/storageos/lib");
        sysProps.put("sblim.wbem.configURL", "file:/opt/storageos/conf/cimom.properties");
        sysProps.put("log4j.configuration", "controllersvc-log4j.properties");
        Main.main(args);
        applicationContext = AttributeMatcherFramework.getApplicationContext();
        coordinator = (CoordinatorClient) applicationContext.getBean("coordinator");
        dbClient = (DbClient) applicationContext.getBean("dbclient");
        
    }

    @Test
    public void test() {
        log.info("Started...");
    }
    
 

}
