/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.util;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.util.StringSetUtil;

/**
 * NetworkLite -- this is a read-only copy of a Network.
 * The purpose is to avoid the memory overhead of holding a Network with its endpoint set
 * in certain critical paths like provisioning export groups. Using a separate structure
 * that parallels the Network minimizes impact to existing code structure and emphasizes
 * what fields are available (or not) and the fact that this structure cannot be re-persisted.
 * 
 * NetworkLite contains just the structures needed for provisioning without instantiating
 * the entire Network with its entire endpoint set. The fields included are listed in
 * the constructor.
 * 
 * To obtain a NetworkLite, use the code in NetworkUtil.getNetworkLite, which
 * calls the dbClient to instantiate only certain fields in a Network structure,
 * and then calls the constructor below to create a Network lite.
 * 
 * To support additional fields in the NetworkLite:
 * 1. Add them to the copy constructor from Network (there should only be that single
 * constructor).
 * 2. Add the Cassandra column name to getColumnNames() below.
 * 
 * Take care that this structure remains read-only. No settrs should be introduced.
 * 
 * @author watson
 */
public class NetworkLite {

    // Debug facility for disabling routing; should be false in the tree
    // at all times for production.
    static public boolean disableRouting = false;

    protected URI _id;

    protected Boolean _inactive;

    private String _label;

    private String _transportType;

    private String _registrationStatus;

    private Set<String> _assignedVirtualArrays = new HashSet<String>();

    private Set<String> _connectedVirtualArrays = new HashSet<String>();

    private Set<String> _routedNetworks = new HashSet<String>();

    private Set<String> _networkSystems = new HashSet<String>();

    private String _nativeId;

    private String _nativeGuid;

    public NetworkLite(Network network) {
        _id = network.getId();
        _inactive = network.getInactive();
        _label = network.getLabel();
        _transportType = network.getTransportType();
        _registrationStatus = network.getRegistrationStatus();
        _nativeId = network.getNativeId();
        _nativeGuid = network.getNativeGuid();
        if (network.getAssignedVirtualArrays() != null) {
            _assignedVirtualArrays.addAll(network.getAssignedVirtualArrays());
        }
        if (network.getConnectedVirtualArrays() != null) {
            _connectedVirtualArrays.addAll(network.getConnectedVirtualArrays());
        }
        if (network.getRoutedNetworks() != null) {
            _routedNetworks.addAll(network.getRoutedNetworks());
        }
        if (network.getNetworkSystems() != null) {
            _networkSystems.addAll(network.getNetworkSystems());
        }
    }

    /**
     * For testing use only.
     * 
     * @param label
     */
    public NetworkLite(String label) {
        _label = label;
    }

    /**
     * For testing use only.
     * 
     * @param id
     * @param label
     */
    public NetworkLite(URI id, String label) {
        _id = id;
        _label = label;
    }

    private static Set<String> _columnNames = null;

    /**
     * These have to correspond to the column names in Network.
     * 
     * @return Set<String> of field names to be retrieved
     */
    public synchronized static Set<String> getColumnNames() {
        if (_columnNames == null) {
            _columnNames = new HashSet<String>();
            _columnNames.add("inactive");
            _columnNames.add("label");
            _columnNames.add("transportType");
            _columnNames.add("nativeId");
            _columnNames.add("nativeGuid");
            _columnNames.add("registrationStatus");
            _columnNames.add("assignedVirtualArrays");
            _columnNames.add("connectedVirtualArrays");
            _columnNames.add("routedNetworks");
            _columnNames.add("networkSystems");
        }
        return _columnNames;
    }

    public URI getId() {
        return _id;
    }

    public String getLabel() {
        return _label;
    }

    public String getTransportType() {
        return _transportType;
    }

    public String getRegistrationStatus() {
        return _registrationStatus;
    }

    public Set<String> getAssignedVirtualArrays() {
        return _assignedVirtualArrays;
    }

    public Set<String> getConnectedVirtualArrays() {
        return _connectedVirtualArrays;
    }

    public Set<String> getRoutedNetworks() {
        if (disableRouting) {
            return new HashSet<String>();
        }
        return _routedNetworks;
    }

    /**
     * Returns true if the networkUri belongs to a network that is routed
     * to this network.
     * 
     * @param networkUri the URI of the network being checked
     * @return true if the networkUri belongs to a network that is routed
     *         to this network.
     */
    public boolean hasRoutedNetworks(URI networkUri) {
        return _routedNetworks != null && !disableRouting &&
                _routedNetworks.contains(networkUri.toString());
    }

    public Set<String> fetchAllVirtualArrays() {
        Set<String> allVirtualArrays = new HashSet<String>();
        if (_assignedVirtualArrays != null) {
            allVirtualArrays.addAll(_assignedVirtualArrays);
        }
        if (_connectedVirtualArrays != null) {
            allVirtualArrays.addAll(_connectedVirtualArrays);
        }
        return allVirtualArrays;
    }

    public Boolean getInactive() {
        return (_inactive != null) && _inactive;
    }

    /**
     * Returns true if Network is registered.
     * 
     * @return
     */
    public boolean registered() {
        return getRegistrationStatus().equals(RegistrationStatus.REGISTERED.name());
    }

    /**
     * Returns true if this network is connected to another network
     * 
     * @return
     */
    public boolean connectedToNetwork(URI networkUri) {
        if (networkUri != null) {
            if (networkUri.equals(_id)) {
                return true;
            }
            if (_routedNetworks != null) {
                return _routedNetworks.contains(networkUri.toString());
            }
        }
        return false;
    }

    /**
     * Returns true if this network is connected to another network
     * 
     * @return
     */
    public boolean connectedToAtLeastOneNetwork(Collection<URI> networkUris) {
        if (networkUris != null) {
            if (networkUris.contains(_id)) {
                return true;
            }
            if (_routedNetworks != null) {
                List<URI> routedNetowrks = StringSetUtil.stringSetToUriList(getRoutedNetworks());
                return !Collections.disjoint(networkUris, routedNetowrks);
            }
        }
        return false;
    }

    public String getNativeId() {
        return _nativeId;
    }

    public String getNativeGuid() {
        return _nativeGuid;
    }

    public Set<String> getNetworkSystems() {
        return _networkSystems;
    }

    @Override
    public int hashCode() {
        return _id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj != null && obj instanceof NetworkLite) {
            return ((NetworkLite) obj).getId().equals(_id);
        }
        return super.equals(obj);
    }

}
