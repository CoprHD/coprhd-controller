package com.emc.storageos.db.client.upgrade.callbacks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ComputeImageServer;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.model.property.PropertyInfo;


public class ComputeImageServerMigration extends BaseCustomMigrationCallback {
	private static final Logger log = LoggerFactory.getLogger(ComputeImageServerMigration.class);

	@Override
	public void process() {
		// TODO Auto-generated method stub
		//Retrieve data from zk db using coordinator client
		PropertyInfo p = coordinatorClient.getPropertyInfo();
		if (p.getProperty("Image_server_address")!=null){
			
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
			//TODO  Delete imageserverConf data from ZK db.
			
			
		}else{
			log.info("No image server configuration found in Zookeeper db");
		}
        

	}

}
