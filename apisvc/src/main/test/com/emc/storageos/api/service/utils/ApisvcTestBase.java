package com.emc.storageos.api.service.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import com.emc.storageos.api.service.impl.Main;
import com.emc.storageos.api.service.impl.resource.BlockService;
import com.emc.storageos.api.service.impl.resource.StorageApplication;
import com.emc.storageos.api.service.impl.resource.TaskService;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.volumecontroller.impl.utils.AttributeMatcherFramework;
import com.emc.vipr.client.AuthClient;
import com.emc.vipr.client.ClientConfig;
import com.emc.vipr.client.ViPRCoreClient;

/**
 * This is intended as a base class for apisvc Junit tests that want to run the controlllersvc within the Junit execution.
 * That is, the apisvc will be started as part of the Junit setup(), and will be terminated when the Junit exits.
 *
 * Prequisites:
 * 1. All services except apisvc should be running.
 * 2. apisvc cannot already be running.
 * 3. Any test using this base class should use the following as the working directory:
 * /opt/storageos/conf
 * This is set in Eclipse under "Debug Configurations" "Arguments" "Working Directory".
 * 5. Any test using this base class should set the following VM environment variables
 * (taken from /opt/storageos/bin/apisvc):
      -ea
      -server
      -d64
      -Xmx512m
      -XX:+HeapDumpOnOutOfMemoryError
      -XX:HeapDumpPath=/opt/storageos/logs/apisvc-$$.hprof
      -XX:+PrintGCDateStamps
      -XX:+PrintGCDetails
 * This is set in  Eclipse under "Debug Configurations" "Arguments" "VM Arguments".
 *
 * The startapisvc() method below can be called from a setup() method in the Junit. It basically sets up
 * the parameters the same way as /opt/storageos/bin/apisvc does and then invokes Main in a similar fashion.
 * Afterwards, it gets the ApplicationContext from the AttributeMatcherFramework (who happened to export it)
 * and then sets up bean references to the coordinator, dbClient, dispatcher, and workflowService.
 * (Others could be added).
 *
 * Note this is a full copy of the apisvc. 
 *
 * The junit test is free to call any of the beans defined here, and could for instance place requests on
 * the dispatcher queue directly, or create new workflows. For an example Junit, see WorkflowTest.
 *
 */
abstract public class ApisvcTestBase {
	 	protected static final Logger log = LoggerFactory.getLogger(ApisvcTestBase.class);
	    /*
	     * The following context beans are prepopulated from the application context for your convenience.
	     * Feel free to add others as required.
	     */
	    protected DbClient dbClient;
	    protected CoordinatorClient coordinator;
	    protected ApplicationContext applicationContext;
	    protected StorageApplication storageApplication;
	    protected ViPRCoreClient viprCoreClient;

	    private static final String args[] = {
	        "file:/opt/storageos/conf/api-conf.xml",
	        "file:/opt/storageos/conf/api-emc-conf.xml",
	        "file:/opt/storageos/conf/api-oss-conf.xml"
	    };
	    private static boolean started = false;

	    /**
	     * Starts the apisvc. Works by simulating a call to
	     * com.emc.storageos.volumecontroller.impl.Main main() just as
	     * if the apisvc script had done so.
	     */
	    protected void startApisvc() {
	        if (!started) {
	            started = true;
	            Properties sysProps = System.getProperties();
	            sysProps.put("buildType", "emc");
	            sysProps.put("java.library.path", "/opt/storageos/lib");
	            sysProps.put("entyExpansionLimit", "-1");
	            sysProps.put("ssun.rmi.transport.connectionTimeout", "5000");
	            sysProps.put("sun.rmi.transport.tcp.handshakeTimeout", "5000");
	            sysProps.put("log4j.configuration", "apisvc-log4j.properties");
	            sysProps.put("product.home", "/opt/storageos" );
	            PropertyConfigurator.configure("apisvc-log4j.properties");
	            log.info("Beginning logging");
	            Main.main(args);
	            try {
	            	Thread.sleep(10000);	 // 10 second wait for apisvc to get up
	            } catch (InterruptedException ex) {
	            	log.info("Interrupted");
	            }
	        }
	        applicationContext = AttributeMatcherFramework.getApplicationContext();
	        coordinator = (CoordinatorClient) applicationContext.getBean("coordinator");
	        dbClient = (DbClient) applicationContext.getBean("dbclient");
	    }
	    
	    /**
	     * Get a logged in ViPR client instance.
	     * @param viprIP -- IP address (or FQDN) of Vipr server or devkit.
	     * @param userName -- String user name.
	     * @param password -- String password (unobfuscated).
	     * @return ViPRCoreClient that has been authenticated
	     */
	    public ViPRCoreClient getViprClient(String viprIP,  String userName, String password) {
			ClientConfig clientConfig = new ClientConfig().withHost(viprIP)
					.withRequestLoggingDisabled().withMaxRetries(10).withMediaType("application/json")
					.withIgnoringCertificates(true);
			viprCoreClient = new ViPRCoreClient(clientConfig);
			AuthClient auth = viprCoreClient.auth();
			String token = auth.login(userName, password);
			viprCoreClient.setAuthToken(token);
			log.info("Auth token is: " + token);
			return viprCoreClient;
		}
}
