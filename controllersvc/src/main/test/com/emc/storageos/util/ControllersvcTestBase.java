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
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocatorTest;
import com.emc.storageos.workflow.WorkflowService;

/**
 * This is intended as a base class for controllersvc Junit tests that want to run the controlllersvc within the Junit execution.
 * That is, the controllersvc will be started as part of the Junit setup(), and will be terminated when the Junit exits.
 * 
 * Prequisites:
 * 1. The following services must be running:  coordinator, db, and geodb.
 * 2. Controllersvc cannot already be running.
 * 3. Any other services are optional, and have may not been tested in this environment.
 * 4. Any test using this base class should use the following as the working directory:
 * /opt/storageos/conf
 * This is set in Eclipse under "Debug Configurations" "Arguments" "Working Directory".
 * 5. Any test using this base class should set the following VM environment variables 
 * (taken from /opt/storageos/bin/controllersvc):
      -ea
      -server
      -d64
      -Xmx2048m
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/opt/storageos/logs/controllersvc-$$.hprof
      -XX:+PrintGCDateStamps
      -XX:+PrintGCDetails
      -XX:MaxPermSize=192m
 * This is set in  Eclipse under "Debug Configurations" "Arguments" "VM Arguments".
 * 
 * The startControllersvc() method below can be called from a setup() method in the Junit. It basically sets up
 * the parameters the same way as /opt/storageos/bin/controllersvc does and then invokes Main in a similar fashion.
 * Afterwards, it gets the ApplicationContext from the AttributeMatcherFramework (who happened to export it)
 * and then sets up bean references to the coordinator, dbClient, dispatcher, and workflowService.
 * (Others could be added).
 * 
 * Note this is a full copy of the controllersvc. Depending on what is in the existing database, things like
 * discovery and collection of port metrics may proceed in the threads that are started. 
 * The logging output goes to the usual controllersvc.log.
 * 
 * The junit test is free to call any of the beans defined here, and could for instance place requests on
 * the dispatcher queue directly, or create new workflows. For an example Junit, see WorkflowTest.
 *
 */
abstract public class ControllersvcTestBase {
    protected static final Logger log = LoggerFactory.getLogger(ControllersvcTestBase.class);
    /*
     * The following context beans are prepopulated from the application context for your convenience.
     * Feel free to add others as required.
     */
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
    private static boolean started = false;
    
    /**
     * Starts the controllersvc. Works by simulating a call to 
     * com.emc.storageos.volumecontroller.impl.Main main() just as
     * if the controllersvc script had done so.
     */
    protected void startControllersvc() {
        if (!started) {
            started = true;
            Properties sysProps = System.getProperties();
            sysProps.put("buildType", "emc");
            sysProps.put("java.library.path", "/opt/storageos/lib");
            sysProps.put("sblim.wbem.configURL", "file:/opt/storageos/conf/cimom.properties");
            sysProps.put("log4j.configuration", "controllersvc-log4j.properties");
            sysProps.put("product.home", "/opt/storageos" );
            PropertyConfigurator.configure("controllersvc-log4j.properties");
            log.info("Beginning logging");
            Main.main(args);
        }
        applicationContext = AttributeMatcherFramework.getApplicationContext();
        coordinator = (CoordinatorClient) applicationContext.getBean("coordinator");
        dbClient = (DbClient) applicationContext.getBean("dbclient");
        dispatcher = (Dispatcher) applicationContext.getBean("dispatcher");
        workflowService = (WorkflowService) applicationContext.getBean("workflowService");
    }

}
