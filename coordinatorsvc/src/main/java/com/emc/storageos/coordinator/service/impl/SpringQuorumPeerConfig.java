/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.coordinator.service.impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

import org.apache.zookeeper.server.quorum.QuorumPeerConfig;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;

public class SpringQuorumPeerConfig extends QuorumPeerConfig {
   private static final String SERVER_ID_FILE = "myid";

   private int _id;
   private Properties _properties;

   public void setMachineId(int id) {
      _id = id;
   }
   
   public void setProperties(Properties properties) {
      _properties = properties;
   }

   public void init() throws ConfigException, IOException {
      dataDir = (String)_properties.get("dataDir");

      // emit server id file
      File serverIdDir = new File(dataDir);
      if (!serverIdDir.exists()) {
         if (!serverIdDir.mkdirs()) {
                throw CoordinatorException.fatals
                        .unableToCreateServerIDDirectories(serverIdDir.getAbsolutePath());
         }
      }

      File serverId = new File(dataDir, SERVER_ID_FILE);
      if (!serverId.exists()) {
         FileWriter writer = new FileWriter(serverId);
         writer.write(Integer.toString(_id));
         writer.close();
      }

      parseProperties(_properties);
   }
}
