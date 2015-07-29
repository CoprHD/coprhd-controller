/*
 * Copyright (c) 2011-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.cimadapter.connections.celerra;

// StorageOS imports
import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.cimadapter.connections.cim.CimConnectionInfo;
import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimFilterMap;
import com.emc.storageos.cimadapter.connections.cim.CimListener;
import com.emc.storageos.cimadapter.processors.CelerraIndicationProcessor;
import com.emc.storageos.cimadapter.processors.CimIndicationProcessor;

/**
 * Represents an ECOM connection to a CIM Provider. Essentially a CIM connection
 * but with special processing for ECOM indications.
 */
public class CelerraConnection extends CimConnection {

    // The list of message specifications for Celerra connections.
    private static volatile CelerraMessageSpecList s_messageSpecs;

    /**
     * Constructs a Celerra connection.
     * 
     * @param connectionInfo The bean containing the connection information.
     * @param listener The CIM indication listener for this connection.
     * @param filterMap The indication filters to be subscribed for this
     *            connection.
     * @param messageSpecs The list of Celerra message specifications from the
     *            connection manager configuration.
     */
    public CelerraConnection(CimConnectionInfo connectionInfo,
            CimListener listener, CimFilterMap filterMap, CelerraMessageSpecList messageSpecs)
            throws Exception {
        super(connectionInfo, listener, filterMap);
        s_messageSpecs = messageSpecs;
    }

    /**
     * Getter for the Celera message specifications for Celerra connections.
     * 
     * @return The Celera message specifications for Celerra connections.
     */
    public CelerraMessageSpecList getMessageSpecs() {
        return s_messageSpecs;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    public String getConnectionType() {
        return CimConstants.ECOM_FILE_CONNECTION_TYPE;
    }

    @Override
    /**
     * {@inheritDoc}
     */
    protected CimIndicationProcessor getDefaultIndicationProcessor() {
        if (_dfltIndicationProcessor == null) {
            _dfltIndicationProcessor = new CelerraIndicationProcessor(this);
        }

        return _dfltIndicationProcessor;
    }
}