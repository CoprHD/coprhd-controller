/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resources that may be explicitly assigned to a VirtualArray, but also have
 * explicit associations to a VirtualArray through the VirtualArray's network
 * connectivity.
 */
public class VirtualArrayTaggedResource extends DiscoveredDataObject {
    
    // Logger reference
    protected static final Logger s_logger = LoggerFactory.getLogger(VirtualArrayTaggedResource.class);

    // A set containing the URIs of the virtual arrays to which the resource
    // has been assigned explicitly by the user.
    private StringSet _assignedVirtualArrays;

    // A set containing the URIs of the virtual arrays to which the resource
    // is implicitly connected due to the network connectivity of the virtual
    // array. For example, a storage port can be implicitly connected to a
    // virtual array because the storage port is in a network assigned to the
    // virtual array. Similarly, the storage pools defined on the storage
    // system for that storage port would be implicitly connected to the
    // virtual array.
    private StringSet _connectedVirtualArrays;

    // The set of virtual arrays associated with the resource for the
    // purpose of searching for the list of resource associated with
    // a virtual array. The tagged virtual arrays are those that are explicitly
    // assigned to the resource unless there are none, in which case, the
    // tagged virtual arrays are those that are implicitly connected.
    private StringSet _taggedVirtualArrays;
    

    /**
     * Gets the URIs of the virtual arrays assigned to the storage port or pool by the
     * user. The assigned virtual arrays override the connected virtual arrays when 
     * present.
     *
     * @return A set of the URIs of the virtual arrays assigned to the resource
     *         by the user.
     */
    @Name("assignedVirtualArrays")
    @AlternateId("AssignedAltIdIndex")
    public StringSet getAssignedVirtualArrays() {
        return _assignedVirtualArrays;
    }

    /**
     * Sets the URIs of the assigned VirtualArrays for the resource to the
     * VirtualArray URIs in the passed set.
     * 
     * NOTE: Don't call directly as tagged virtual arrays will
     * not be updated. Use the add/remove APIs instead.
     *
     * @param virtualArrayURIs A set of the URIs of the VirtualArrays to be
     *        assigned to the resource.
     */
    public void setAssignedVirtualArrays(StringSet virtualArrayURIs) {
        _assignedVirtualArrays = virtualArrayURIs;
        setChanged("assignedVirtualArrays");
    }

    /**
     * Adds the passed VirtualArray URI to the set of VirtualArrays assigned to
     * the resource by the user.
     *
     * @param virtualArrayURI The URI of the VirtualArray to be assigned to the
     *        resource.
     */
    public void addAssignedVirtualArray(String virtualArrayURI) {
        if (virtualArrayURI != null) {
            if (_assignedVirtualArrays == null) {
                setAssignedVirtualArrays(new StringSet());
                _assignedVirtualArrays.add(virtualArrayURI);
            } else {
                _assignedVirtualArrays.add(virtualArrayURI);
            }
            updateVirtualArrayTags();
        }
    }

    /**
     * Adds the passed virtual array URIs to the set of virtual array URIs assigned to
     * the resources by the user.
     *
     * @param virtualArrayURIs The URIs of the VirtualArrays to be assigned to
     *        the resource.
     */
    public void addAssignedVirtualArrays(Set<String> virtualArrayURIs) {
        if ((virtualArrayURIs != null) && (!virtualArrayURIs.isEmpty())) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // addAll method is invoked, else base class method
            // is invoked.
            HashSet<String> addVirtualArrays = new HashSet<String>();
            addVirtualArrays.addAll(virtualArrayURIs);
            if (_assignedVirtualArrays == null) {
                setAssignedVirtualArrays(new StringSet());
                _assignedVirtualArrays.addAll(addVirtualArrays);
            } else {
                _assignedVirtualArrays.addAll(addVirtualArrays);
            }
            updateVirtualArrayTags();
        }
    }

    /**
     * Removes the passed VirtualArray URI from the set of VirtualArrays
     * assigned to the resource by the user
     *
     * @param virtualArrayURI The URI of the VirtualArray to be removed.
     */
    public void removeAssignedVirtualArray(String virtualArrayURI) {
        if ((virtualArrayURI != null) && (_assignedVirtualArrays != null)) {
            _assignedVirtualArrays.remove(virtualArrayURI);
            updateVirtualArrayTags();
        }
    }

    /**
     * Removes the passed VirtualArray URIs from the set of VirtualArrays
     * assigned to the resource by the user.
     *
     * @param virtualArrayURIs The URIs of the VirtualArrays to be removed from
     *        the resource.
     */
    public void removeAssignedVirtualArrays(Set<String> virtualArrayURIs) {
        if ((virtualArrayURIs != null) && (!virtualArrayURIs.isEmpty())
                && (_assignedVirtualArrays != null)) {
            // Must be a HashSet to ensure AbstractChangeTrackingSet
            // removeAll method is invoked, else base class method
            // is invoked.
            HashSet<String> removeVirtualArrays = new HashSet<String>();
            removeVirtualArrays.addAll(virtualArrayURIs);
            _assignedVirtualArrays.removeAll(removeVirtualArrays);
            updateVirtualArrayTags();
        }
    }

    /**
     * Gets the URIs of the VirtualArrays to which the storage port or pool is
     * implicitly connected. Storage ports are implicitly connected to virtual 
     * arrays to which the port's network is assigned, if any. Storage pools are
     * implicitly connected to virtual arrays when their storage array has one 
     * or more ports in the virtual array. In other words, a pool's connected  
     * virtual arrays are the union of all the tagged virtual arrays of the 
     * storage array's ports.
     *
     * @return A set of the URIs of the virtual arrays to which the resource
     *         is implicitly connected.
     */
    @Name("connectedVirtualArrays")
    @AlternateId("ConnAltIdIndex")
    public StringSet getConnectedVirtualArrays() {
        return _connectedVirtualArrays;
    }

    /**
     * Sets the URIs of the VirtualArrays to which the resource is
     * implicitly connected to the VirtualArray URIs in the passed set.
     * 
     * NOTE: Don't call directly as tagged virtual arrays will
     * not be updated. Use the add/remove/clear/replace APIs instead.
     *
     * @param virtualArrayURIs The set of the URIs to which the resource is
     *        implicitly connected.
     */
    public void setConnectedVirtualArrays(StringSet virtualArrayURIs) {
        _connectedVirtualArrays = virtualArrayURIs;
        setChanged("connectedVirtualArrays");
    }

    /**
     * Adds the passed VirtualArray URI to the set of VirtualArrays to which the
     * resource is implicitly connected.
     *
     * @param virtualArrayURI The URI of a VirtualArray to which the resource
     * is implicitly connected.
     */
    public void addConnectedVirtualArray(String virtualArrayURI) {
        if (virtualArrayURI != null) {
            if (_connectedVirtualArrays == null) {
                setConnectedVirtualArrays(new StringSet());
                _connectedVirtualArrays.add(virtualArrayURI);
            } else {
                _connectedVirtualArrays.add(virtualArrayURI);
            }
            // TODO - check if this is really needed
            setChanged("connectedVirtualArrays");
            updateVirtualArrayTags();
        }
    }

    /**
     * Removes the passed VirtualArray URI from the set of VirtualArrays to
     * which the resource is implicitly connected.
     *
     * @param virtualArrayURI The URI of the VirtualArray to be removed.
     */
    public void removeConnectedVirtualArray(String virtualArrayURI) {
        if ((virtualArrayURI != null) && (_connectedVirtualArrays != null)
                && (_connectedVirtualArrays.contains(virtualArrayURI))) {
            _connectedVirtualArrays.remove(virtualArrayURI);
            // TODO - check if this is really needed
            setChanged("connectedVirtualArrays");
            updateVirtualArrayTags();
        }
    }

    /**
     * Clears the connected virtual arrays and update the tagged arrays.
     *
     */
    public void clearConnectedVirtualArray() {
        if (_connectedVirtualArrays != null) {
            _connectedVirtualArrays.clear();
            setChanged("connectedVirtualArrays");
            updateVirtualArrayTags();
        }
    }

    /**
     * Clears the connected virtual arrays and update the tagged arrays.
     *
     */
    public void replaceConnectedVirtualArray(Set<String> set) {
        if (_connectedVirtualArrays != null) {
            _connectedVirtualArrays.replace(set);
        } else {
            _connectedVirtualArrays = new StringSet();
            _connectedVirtualArrays.replace(set);
        }
        setChanged("connectedVirtualArrays");
        updateVirtualArrayTags();
    }

    /**
     * Getter for the tagged VirtualArrays for the port or pool. Tagged virtual
     * arrays are the effective virtual arrays for the port or pool. They
     * are set to assigned virtual arrays when this set is not
     * empty, otherwise they are set to connected virtual arrays.
     *
     * @return The URIs of the tagged VirtualArrays.
     */
    @Name("taggedVirtualArrays")
    @AlternateId("TagAltIdIndex")
    public StringSet getTaggedVirtualArrays() {
        return _taggedVirtualArrays;
    }

    /**
     * Setter for the tagged VirtualArrays for the resource.
     *
     * @param virtualArrayURIs The URIs of the tagged VirtualArrays.
     */
    public void setTaggedVirtualArrays(StringSet virtualArrayURIs) {
        if (virtualArrayURIs != null) {
            _taggedVirtualArrays = virtualArrayURIs;
            setChanged("taggedVirtualArrays");
        }
    }

    /**
     * Called to update the tagged VirtualArrays when VirtualArrays are
     * explicitly assigned/unassigned to/from the resource and also when the
     * resource is implicitly connected/disconnected to/from a VirtualArray.
     * Note that the tagged VirtualArrays will always be the URNs of the 
     * explicitly assigned VirtualArrays unless there are none, in which case,
     * the search tags are the URNs of the implicitly connected VirtualArrays.
     */
    public void updateVirtualArrayTags() {

        if (_taggedVirtualArrays == null) {
            _taggedVirtualArrays = new StringSet();
        }
        s_logger.info("entering updateVirtualArrayTags: assigned: {}, " +
                " connected: {} , tagged: {}", new Object[] {
                _assignedVirtualArrays == null ? "[]" :_assignedVirtualArrays.toArray(), 
                _connectedVirtualArrays == null ? "[]" : _connectedVirtualArrays.toArray(), 
                _taggedVirtualArrays == null ? "[]" : _taggedVirtualArrays.toArray()});

        if ((_assignedVirtualArrays != null) && (_assignedVirtualArrays.size() != 0)) {
            s_logger.debug("updateVirtualArrayTags: replacing with assigned {}", _assignedVirtualArrays.size());
            _taggedVirtualArrays.replace(_assignedVirtualArrays);
            s_logger.debug("updateVirtualArrayTags is taking assigned varrays: added {} removed {}",
                (_taggedVirtualArrays.getAddedSet() != null)?_taggedVirtualArrays.getAddedSet().size():"0",
                (_taggedVirtualArrays.getRemovedSet() != null)?_taggedVirtualArrays.getRemovedSet().size():"0");
        } else if ((_connectedVirtualArrays != null)
                && (_connectedVirtualArrays.size() != 0)) {
            s_logger.debug("updateVirtualArrayTags: replacing with connected {}", _connectedVirtualArrays.size());
            _taggedVirtualArrays.replace(_connectedVirtualArrays);
           s_logger.debug("updateVirtualArrayTags is taking connected varrays: added {} removed {}",
               (_taggedVirtualArrays.getAddedSet() != null)?_taggedVirtualArrays.getAddedSet().size():"0",
               (_taggedVirtualArrays.getRemovedSet() != null)?_taggedVirtualArrays.getRemovedSet().size():"0");
        } else {
            _taggedVirtualArrays.clear();
        }

        s_logger.info("leaving updateVirtualArrayTags: tagged: {}",
                   _taggedVirtualArrays == null ? "[]" : _taggedVirtualArrays.toArray());
    }
}
