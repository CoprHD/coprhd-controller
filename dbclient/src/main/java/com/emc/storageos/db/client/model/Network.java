/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.valid.EnumType;

/**
 * Network object
 */
@Cf("Network")
public class Network extends DiscoveredDataObject {

    // device native ID
    private String _nativeId;

    // Varray this network is part of
    private URI _virtualArray;

    // Virtual arrays to which this network is explicitly assigned.
    private StringSet _assignedVArrays;

    // Virtual arrays the network is associated with either by assignment ,
    // of the network to the virtual array, by assignment of ports from
    // the network to the virtual array, or by being routed to a network
    // that is associated with the virtual array.
    private StringSet _connectedVArrays;

    // type of varray
    private String _transportType;

    // Map of end value (key) (indexed) to "true" indicating it was discovered
    // or "false" indicating it was not.
    private StringMap _endpointsMap = new StringMap();

    // Holds all the networks that are routed to this network if any
    private StringSet _routedNetworks;

    // Indicates whether the TransportZone was automatically discovered by Bourne vs. manually created via an external API.
    private Boolean _discovered;

    // Holds all the network systems that can manage this network
    private StringSet _networkSystems;

    private String _registrationStatus = RegistrationStatus.REGISTERED.toString();

    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    @Name("varray")
    @Deprecated
    public URI getVirtualArray() {
        return _virtualArray;
    }

    @Deprecated
    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @Name("transportType")
    public String getTransportType() {
        return _transportType;
    }

    public void setTransportType(String transportType) {
        _transportType = transportType;
        setChanged("transportType");
    }

    public StringSet retrieveEndpoints() {
        return new StringSet(_endpointsMap.keySet());
    }

    @Name("endpoints")
    @AlternateId("EndpointIndex")
    @IndexByKey
    public StringMap getEndpointsMap() {
        return _endpointsMap;
    }

    public void setEndpointsMap(StringMap _endpointsMap) {
        this._endpointsMap = _endpointsMap;
        setChanged("endpoints");
    }

    @XmlElement
    @Name("discovered")
    public Boolean getDiscovered() {
        return (_discovered != null) && _discovered;
    }

    public void setDiscovered(Boolean discovered) {
        this._discovered = discovered;
        setChanged("discovered");
    }

    @RelationIndex(cf = "RelationIndex", type = NetworkSystem.class, deactivateIfEmpty = true)
    @IndexByKey
    @Name("networkSystems")
    public StringSet getNetworkSystems() {
        return _networkSystems;
    }

    public void setNetworkSystems(StringSet _networkSystems) {
        this._networkSystems = _networkSystems;
        setChanged("networkSystems");
    }

    public void addNetworkSystems(List<String> networkSystems) {
        if (_networkSystems == null) {
            _networkSystems = new StringSet();
        }
        _networkSystems.addAll(networkSystems);
        setChanged("networkSystems");
    }

    public boolean removeNetworkSystems(List<String> networkSystems) {
        boolean removed = _networkSystems != null &&
                _networkSystems.removeAll(networkSystems);
        if (removed) {
            setChanged("networkSystems");
        }
        return removed;
    }

    @RelationIndex(cf = "SecondaryRelationIndex", type = VirtualArray.class, deactivateIfEmpty = false)
    @IndexByKey
    @Name("assignedVirtualArrays")
    public StringSet getAssignedVirtualArrays() {
        return _assignedVArrays;
    }

    public void setAssignedVirtualArrays(StringSet varrays) {
        _assignedVArrays = varrays;
        setChanged("assignedVirtualArrays");
    }

    public void addAssignedVirtualArrays(Collection<String> varrays) {
        if (_assignedVArrays == null) {
            _assignedVArrays = new StringSet();
        }
        _assignedVArrays.addAll(varrays);
        setChanged("assignedVirtualArrays");
    }

    public boolean removeAssignedVirtualArrays(Collection<String> varrays) {
        boolean removed = _assignedVArrays != null
                && _assignedVArrays.removeAll(new HashSet<String>(varrays));
        if (removed) {
            setChanged("assignedVirtualArrays");
        }

        return removed;
    }

    /**
     * Returns the virtual arrays to which this network is connected. A network can be
     * connected to a virtual array when one of three conditions exist:
     * <ol>
     * <li>The network is assigned to the virtual array</li>
     * <li>The network has one or more storage ports assigned to the virtual array</li>
     * <li>The network is routed to a network that is connected to the virtual array</li>
     * </ol>
     * Connected virtual arrays are mostly used to find the virtual arrays the network's
     * hosts can be used in.
     * 
     * @return a list of virtual arrays
     */
    @RelationIndex(cf = "TertiaryRelationIndex", type = VirtualArray.class, deactivateIfEmpty = false)
    @IndexByKey
    @Name("connectedVirtualArrays")
    public StringSet getConnectedVirtualArrays() {
        return _connectedVArrays;
    }

    public void setConnectedVirtualArrays(StringSet varrays) {
        _connectedVArrays = varrays;
        setChanged("connectedVirtualArrays");
    }

    public void addConnectedVirtualArrays(Collection<String> varrays) {
        if (_connectedVArrays == null) {
            _connectedVArrays = new StringSet();
        }
        _connectedVArrays.addAll(varrays);
        setChanged("connectedVirtualArrays");
    }

    public boolean removeConnectedVirtualArrays(Collection<String> varrays) {
        boolean removed = _connectedVArrays != null
                && _connectedVArrays.removeAll(new HashSet<String>(varrays));
        if (removed) {
            setChanged("connectedVirtualArrays");
        }

        return removed;
    }

    public void replaceConnectedVirtualArrays(Set<String> varrays) {
        if (_connectedVArrays == null) {
            _connectedVArrays = new StringSet();
        }
        _connectedVArrays.replace(varrays);
        setChanged("connectedVirtualArrays");
    }

    /**
     * Add an endpoint to the transport zone.
     * 
     * @param endpoints
     */
    private void addEndpoints(HashMap<String, String> endpoints) {
        if (getEndpointsMap() == null) {
            setEndpointsMap(new StringMap());
        }
        getEndpointsMap().putAll(endpoints);
        setChanged("endpoints");
    }

    /**
     * Add an endpoint to the transport zone.
     * 
     * @param endpoints String (required)
     * @param isDiscovered boolean (required)
     */
    public void addEndpoints(List<String> endpoints, boolean isDiscovered) {
        HashMap<String, String> endpointMap = makeEndpointMap(EndpointUtility.changeCase(endpoints), isDiscovered);
        addEndpoints(endpointMap);
    }

    public boolean hasEndpoint(String endpoint) {
        return _endpointsMap != null && _endpointsMap.containsKey(EndpointUtility.changeCase(endpoint));
    }

    /**
     * Remove an endpoint from the transport zone.
     * 
     * @param endpoints
     * @return
     */
    public Collection<String> removeEndpoints(List<String> endpoints) {
        List<String> removed = new ArrayList<String>();
        if (getEndpointsMap() == null) {
            return removed;
        }
        for (String key : EndpointUtility.changeCase(endpoints)) {
            if (getEndpointsMap().containsKey(key)) {
                getEndpointsMap().remove(key);
                removed.add(key);
            }
        }
        if (!removed.isEmpty()) {
            setChanged("endpoints");
        }
        return removed;
    }

    /**
     * Converts a list of endpoints to a StringMap with the value being the isDiscovered flag
     * 
     * @param paramEndpoints
     * @param isDiscovered
     * @return
     */
    private StringMap makeEndpointMap(List<String> paramEndpoints, boolean isDiscovered) {
        StringMap map = new StringMap();
        for (String ep : paramEndpoints) {
            map.put(ep, new Boolean(isDiscovered).toString());
        }
        return map;
    }

    /**
     * Returns true if the endpoint specified was discovered
     * 
     * @param key endpoint value in String, e.g. WWN
     * @return true if discovered, false if no entry or not discovered
     */
    public boolean endpointIsDiscovered(String key) {
        String value = getEndpointsMap().get(key.toUpperCase());
        if (value == null) {
            return false;
        }
        return new Boolean(value);
    }

    @AlternateId("AltIdIndex")
    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        this._nativeId = nativeId;
        setChanged("nativeId");
    }

    public void setRegistrationStatus(String registrationStatus) {
        _registrationStatus = registrationStatus;
        setChanged("registrationStatus");
    }

    @EnumType(RegistrationStatus.class)
    @Name("registrationStatus")
    public String getRegistrationStatus() {
        return _registrationStatus;
    }

    public boolean inAssignedVarray(URI varrayURI) {
        return getAssignedVirtualArrays() != null && getAssignedVirtualArrays().contains(varrayURI.toString());
    }

    public boolean assignedToVarray() {
        return getAssignedVirtualArrays() != null && !getAssignedVirtualArrays().isEmpty();
    }

    public boolean registered() {
        return RegistrationStatus.REGISTERED.name().equals(_registrationStatus);
    }

    @RelationIndex(cf = "RelationIndex", type = Network.class, deactivateIfEmpty = false)
    @IndexByKey
    @Name("routedNetworks")
    public StringSet getRoutedNetworks() {
        return _routedNetworks;
    }

    public void setRoutedNetworks(StringSet routedNetworks) {
        if (this._routedNetworks == null) {
            this._routedNetworks = new StringSet();
        }
        this._routedNetworks.replace(routedNetworks);
        setChanged("routedNetworks");
    }
}
