/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import com.emc.storageos.model.property.PropertiesMetadata;
import com.emc.storageos.model.property.PropertyMetadata;
import com.emc.storageos.systemservices.impl.iso.CreateISO;

/**
 * Test creating an iso image.  The test creates the file but does not read it.  
 * You can mount it on linux and check the contents.
 *
 */
public class CreateISOTest {

	@Test
	public void testGetBytes() throws IOException {
		String userHome = System.getProperty("user.home");
		PropertiesMetadata propertiesMetadata = new PropertiesMetadata();
		propertiesMetadata.setMetadata(getPropsMetaData());
		Map<String,String> ovfProps = new HashMap<String,String>();
		Map<String,String> overrideProps = new HashMap<String,String>();
		
		ovfProps.put("network_1_ipaddr", "0.0.0.1");
		ovfProps.put("network_2_ipaddr", "0.0.0.2");
		ovfProps.put("network_3_ipaddr", "0.0.0.3");
		ovfProps.put("network_gateway", "10.247.96.1");
		
		overrideProps.put("network_dns_servers", "10.254.66.23,10.254.66.24");
		overrideProps.put("network_ntp_servers", "10.254.140.21,10.254.140.22");
		overrideProps.put("system_datanode_ipadddrs", "0.0.0.4,0.0.0.5");
		overrideProps.put("system_datanode_ipadddrs", "0.0.0.4,0.0.0.5");
		overrideProps.put("system_upgrade_repo", "http://test.domain.com/ovf/Bourne");
		byte[] isoBytes = CreateISO.getBytes(ovfProps, overrideProps);
		FileOutputStream f = new FileOutputStream(new File(userHome+"\\test-config.iso"));
		f.write(isoBytes);
		f.close();
	}
	
	public Map<String, PropertyMetadata> getPropsMetaData() {
    	Map<String, PropertyMetadata> metadata = new TreeMap<String, PropertyMetadata>();
    	PropertyMetadata networkMetadata = setPropMetaData("server ip address", "ipaddress",
    			"ipaddr", null, "Network", true, false, true, null, null, "", false);
    	PropertyMetadata controlNodeOnlyMetadata = setPropMetaData("server ip address", "ipaddress",
    			"ipaddr", null, "Network", true, false, true, null, null, "", true);
    	PropertyMetadata iplistMetadata = setPropMetaData("server ip address", "ipaddress",
    			"ipaddrlist", null, "Network", true, false, true, null, null, "", false);
    	metadata.put("network_1_ipaddr", networkMetadata);
    	metadata.put("network_2_ipaddr", networkMetadata);
    	metadata.put("network_3_ipaddr", networkMetadata);
    	metadata.put("network_dns_servers", iplistMetadata);
    	metadata.put("network_ntp_servers", iplistMetadata);
    	metadata.put("system_datanode_ipaddrs", iplistMetadata);
    	
    	metadata.put("network_gateway", controlNodeOnlyMetadata);
    	metadata.put("system_upgrade_repo", controlNodeOnlyMetadata);
    	
        return metadata;
    }
	public PropertyMetadata setPropMetaData(String label, String description, String type, Integer maxLen, String tag, Boolean advanced, 
			Boolean userMutable, Boolean userConfigurable, Boolean reconfigRequired, Boolean rebootRequired,
			String value, Boolean controlNodeOnly) {
        
    	PropertyMetadata metaData = new PropertyMetadata();

    	metaData.setLabel(label);
    	metaData.setDescription(description);
    	metaData.setType(type);
    	metaData.setMaxLen(maxLen);
    	metaData.setTag(tag);
    	metaData.setAdvanced(advanced);
    	metaData.setUserMutable(userMutable);
    	metaData.setUserConfigurable(userConfigurable);
    	metaData.setReconfigRequired(reconfigRequired);
    	metaData.setRebootRequired(rebootRequired);
    	metaData.setValue(value);
    	metaData.setControlNodeOnly(controlNodeOnly);

    	return metaData;
    }
}
