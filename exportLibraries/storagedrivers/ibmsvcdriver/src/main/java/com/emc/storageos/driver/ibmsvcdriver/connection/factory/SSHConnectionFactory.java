/**
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 * 
 */
package com.emc.storageos.driver.ibmsvcdriver.connection.factory;

import com.emc.storageos.driver.ibmsvcdriver.connection.Connection;
import com.emc.storageos.driver.ibmsvcdriver.connection.ConnectionInfo;
import com.emc.storageos.driver.ibmsvcdriver.connection.SSHConnection;

public class SSHConnectionFactory implements ConnectionFactory {

    @Override
    public Connection getConnection(ConnectionInfo connectionInfo) {
        return new SSHConnection(connectionInfo);
    }

}
