/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.connections.ecom;

// StorageOS imports
import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimFilterMap;
import com.emc.storageos.cimadapter.connections.cim.CimListener;
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;
import com.emc.storageos.cimadapter.processors.EcomIndicationProcessor;

/**
 * Represents an ECOM connection to a CIM Provider. Essentially a CIM connection
 * but with special processing for ECOM indications.
 */
public class EcomConnection extends CimConnection {

    /**
     * Constructs an ECOM connection.
     * 
     * @param connectionInfo The bean containing the connection information.
     * @param listener The CIM indication listener for this connection.
     * @param filterMap The indication filters to be subscribed for this
     *        connection.
     */
    public EcomConnection(CimConnectionInfo connectionInfo,
        CimListener listener, CimFilterMap filterMap) throws Exception {
        super(connectionInfo, listener, filterMap);
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public String getConnectionType() {
        return CimConstants.ECOM_CONNECTION_TYPE;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    protected CimIndicationProcessor getDefaultIndicationProcessor() {
        if (_dfltIndicationProcessor == null) {
            _dfltIndicationProcessor = new EcomIndicationProcessor(this);
        }

        return _dfltIndicationProcessor;
    }
}