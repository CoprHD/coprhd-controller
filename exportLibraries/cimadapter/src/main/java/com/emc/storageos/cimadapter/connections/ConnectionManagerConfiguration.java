/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.cimadapter.connections;

// StorageOS imports
import com.emc.storageos.cimadapter.connections.celerra.CelerraMessageSpecList;
import com.emc.storageos.cimadapter.connections.cim.CimFilterMap;
import com.emc.storageos.cimadapter.connections.cim.CimListenerInfo;
import com.emc.storageos.cimadapter.consumers.CimIndicationConsumerList;

/**
 * A Spring bean capturing the connection manager configuration.
 */
public class ConnectionManagerConfiguration {

    // A reference to the CIM listener configuration information.
    private CimListenerInfo _listenerInfo;

    // A reference to the indications filter map.
    private CimFilterMap _indicationFilterMap;

    // A reference to the list of indication consumers
    private CimIndicationConsumerList _indicationConsumers;

    // A reference to the list of Celerra message specifications.
    private CelerraMessageSpecList _celerraMessageSpecs;

    // The key that is used in the filter and handler names for the subscriptions
    // created when a connection is made to an CIM provider.
    private String _subscriptionsIdentifier = null;

    // Flag indicates whether or not the connection manager should attempt
    // to delete stale subscriptions on a CIM provider when a new connection
    // is made to that provider.
    private boolean _deleteStaleSubscriptionsOnConnect = false;

    /**
     * Default Constructor.
     */
    public ConnectionManagerConfiguration() {
    }

    /**
     * Getter for the listener configuration information.
     * 
     * @return The listener configuration information.
     */
    public CimListenerInfo getListenerInfo() {
        return _listenerInfo;
    }

    /**
     * Setter for the listener configuration information.
     * 
     * @param listenerInfo The listener configuration information.
     */
    public void setListenerInfo(CimListenerInfo listenerInfo) {
        _listenerInfo = listenerInfo;
    }

    /**
     * Getter for the indication filter map.
     * 
     * @return The indication filter map.
     */
    public CimFilterMap getIndicationFilterMap() {
        return _indicationFilterMap;
    }

    /**
     * Setter for the indication filter map.
     * 
     * @param indicationFilterMap The indication filter map.
     */
    public void setIndicationFilterMap(CimFilterMap indicationFilterMap) {
        _indicationFilterMap = indicationFilterMap;
    }

    /**
     * Getter for the list of indication consumers.
     * 
     * @return The list of indication consumers.
     */
    public CimIndicationConsumerList getIndicationConsumers() {
        return _indicationConsumers;
    }

    /**
     * Setter for the list of indication consumers.
     * 
     * @param indicationConsumers The list of indication consumers.
     */
    public void setIndicationConsumers(CimIndicationConsumerList indicationConsumers) {
        _indicationConsumers = indicationConsumers;
    }

    /**
     * Getter for the list of Celerra message specifications.
     * 
     * @return The list of Celerra message specifications.
     */
    public CelerraMessageSpecList getCelerraMessageSpecs() {
        return _celerraMessageSpecs;
    }

    /**
     * Setter for the list of Celerra message specifications.
     * 
     * @param celerraMessageSpecs The list of Celerra message specifications.
     */
    public void setCelerraMessageSpecs(CelerraMessageSpecList celerraMessageSpecs) {
        _celerraMessageSpecs = celerraMessageSpecs;
    }

    /**
     * Getter for the subscriptions identifier.
     * 
     * @return The subscriptions identifier.
     */
    public String getSubscriptionsIdentifier() {
        return _subscriptionsIdentifier;
    }

    /**
     * Setter for the subscriptions identifier.
     * 
     * @param subscriptionsIdentifier The subscriptions identifier.
     */
    public void setSubscriptionsIdentifier(String subscriptionsIdentifier) {
        _subscriptionsIdentifier = subscriptionsIdentifier;
    }

    /**
     * Getter for the delete stale subscriptions on connect flag.
     * 
     * @return The value of the delete stale subscriptions on connect flag.
     */
    public boolean getDeleteStaleSubscriptionsOnConnect() {
        return _deleteStaleSubscriptionsOnConnect;
    }

    /**
     * Setter for the delete stale subscriptions on connect flag.
     * 
     * @param deleteStaleSubscriptionsOnConnect The value for the delete stale
     *            subscriptions on connect flag.
     */
    public void setDeleteStaleSubscriptionsOnConnect(boolean deleteStaleSubscriptionsOnConnect) {
        _deleteStaleSubscriptionsOnConnect = deleteStaleSubscriptionsOnConnect;
    }
}
