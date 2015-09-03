package com.emc.storageos.db.client.upgrade.callbacks;

import java.net.URI;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.model.PropertyInfoExt;
import com.emc.storageos.coordinator.common.Configuration;
import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.model.property.PropertyInfo;


public class ComputeImageServerMigration extends BaseCustomMigrationCallback {
	private static final Logger log = LoggerFactory.getLogger(ComputeImageServerMigration.class);

	@Override
	public void process() {
		try{
			//Retrieve data from zk db using coordinator client
			Configuration config1 = coordinatorClient.queryConfiguration(PropertyInfoExt.TARGET_PROPERTY, PropertyInfoExt.TARGET_PROPERTY_ID);
			String imageServerIP = config1.getConfig("image_server_address");
			log.info("imageServerIP:"+imageServerIP);
			PropertyInfo p = coordinatorClient.getPropertyInfo();
			if (p.getProperty("image_server_address")!=null){
				
				ComputeImageServer imageServer = new ComputeImageServer();
				imageServer.setImageServerIp(p.getProperty("image_server_address"));
				imageServer.setImageServerUser(p.getProperty("image_server_username"));
				imageServer.setTftpbootDir(p.getProperty("image_server_tftpboot_directory"));
				imageServer.setImageServerSecondIp(p.getProperty("image_server_os_network_ip"));
				imageServer.setImageServerHttpPort(p.getProperty("image_server_http_port"));
				imageServer.setImageDir(p.getProperty("image_server_image_directory"));
				imageServer.setImageServerPassword(p.getProperty("image_server_encpassword"));
				dbClient.createObject(imageServer);
				log.info("Saved imageServer info into cassandra db");
				
				// Associate all existing Compute Systems to this image server
				List<URI> computeSystemURIs = dbClient.queryByType(ComputeSystem.class, true);
				Iterator<ComputeSystem> computeSystemListIterator = dbClient.queryIterativeObjects(ComputeSystem.class, computeSystemURIs);
				while (computeSystemListIterator.hasNext()){
					ComputeSystem computeSystem = computeSystemListIterator.next();
					computeSystem.setComputeImageServer(imageServer.getId());
					dbClient.persistObject(computeSystem);
					
				}
				// Delete imageserverConf data from ZK db.
				Configuration config = coordinatorClient.queryConfiguration(PropertyInfoExt.TARGET_PROPERTY, PropertyInfoExt.TARGET_PROPERTY_ID);
				config.removeConfig("image_server_address");
				config.removeConfig("image_server_username");
				config.removeConfig("image_server_encpassword");
				config.removeConfig("image_server_tftpbootdir");
				config.removeConfig("image_server_http_port");
				config.removeConfig("image_server_os_network_ip");
				config.removeConfig("image_server_image_directory");
				coordinatorClient.persistServiceConfiguration(config);
						
				
						
				
			}else {
				log.info("No image server configuration found in Zookeeper db");
			}
			
		} catch (Exception e) {
            log.error("Exception occured while migrating compute image server information");
            log.error(e.getMessage(), e);
        }
        

	}

}
