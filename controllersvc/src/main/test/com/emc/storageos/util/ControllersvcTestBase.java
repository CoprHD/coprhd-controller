package com.emc.storageos.util;

import java.util.Properties;

import org.apache.log4j.PropertyConfigurator;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import com.emc.storageos.volumecontroller.impl.Main;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.storageos.workflow.WorkflowService;

public class ControllersvcTestBase {
    protected static final Logger log = LoggerFactory
            .getLogger(ControllersvcTestBase.class);
    
    protected DbClient dbClient;
    protected CoordinatorClient coordinator;
    protected ApplicationContext applicationContext;
    protected Dispatcher dispatcher;
    protected WorkflowService workflowService;
    
    
    private static final String args[] = {
        "file:/opt/storageos/conf/controller-conf.xml",
        "file:/opt/storageos/conf/controller-emc-conf.xml",
        "file:/opt/storageos/conf/controller-oss-conf.xml"
    };
    
    protected void startControllersvc() {
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
        dispatcher = (Dispatcher) applicationContext.getBean("dispatcher");
        workflowService = (WorkflowService) applicationContext.getBean("workflowService");
    }

}
