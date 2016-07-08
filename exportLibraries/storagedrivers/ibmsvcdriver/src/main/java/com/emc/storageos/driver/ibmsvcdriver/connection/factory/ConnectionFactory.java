/**
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 * 
 */
package com.emc.storageos.driver.ibmsvcdriver.connection.factory;

import com.emc.storageos.driver.ibmsvcdriver.connection.Connection;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionInfo;

public interface ConnectionFactory {

    Connection getConnection(ConnectionInfo connectionInfo);

  }