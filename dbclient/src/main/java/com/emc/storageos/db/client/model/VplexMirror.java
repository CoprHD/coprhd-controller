/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2008-2011 EMC Corporation All Rights Reserved This software contains the
 * intellectual property of EMC Corporation or is licensed to EMC Corporation from third parties.
 * Use of this software and the intellectual property contained therein is expressly limited to the
 * terms and conditions of the License Agreement under which it is provided by or on behalf of EMC.
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;

/**
 * Vplex Mirror data object
 * @author tahals
 */
@Cf("VplexMirror")
public class VplexMirror extends DataObject implements ProjectResource {
    // Reference to the volume representing the SystemElement
    private NamedURI _source;
    // storage controller where this vplex mirror is located
    private URI _storageController;
    // project this vplex mirror is associated with
    private NamedURI _project;
    // total capacity in bytes
    private Long _capacity;
    // virtual pool for this vplex mirror
    private URI _virtualPool;
    // Tenant who owns this vplex mirror
    private NamedURI _tenant;
    // Logical size of a storage volume on array which is volume.ConsumableBlocks *
    // volume.BlockSize.
    private Long _provisionedCapacity;
    // Total amount of storage space consumed within the StoragePool which is SpaceConsumed of
    // CIM_AllocatedFromStoragePool.
    private Long _allocatedCapacity;
    // Associated volumes. 
    // This captures the backend volume(s) that provide the actual storage.
    private StringSet _associatedVolumes;
    // virtual array where this vplex mirror exists
    private URI _virtualArray;
    // device label for this vplex mirror
    private String _deviceLabel;
    // device native ID for this vplex mirror
    private String _nativeId;
    // thinPreAllocate size in bytes
    private Long _thinPreAllocationSize;
    // thin or thick mirror type
    Boolean _thinlyProvisioned = false;
                 
    @NamedRelationIndex(cf = "NamedRelation", type = Volume.class)
    @Name("source")
    public NamedURI getSource() {
        return _source;
    }

    public void setSource(NamedURI source) {
        _source = source;
        setChanged("source");
    }
    
    @RelationIndex(cf = "RelationIndex", type = StorageSystem.class)
    @Name("storageDevice")
    public URI getStorageController() {
        return _storageController;
    }

    public void setStorageController(URI storageController) {
        _storageController = storageController;
        setChanged("storageDevice");
    }
    
    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }
    
    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }
       
    @XmlTransient
    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return _tenant;
    }
    
    public void setTenant(NamedURI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }
    
    @Name("capacity")
    public Long getCapacity() {
        return (null == _capacity) ? 0L : _capacity;
    }
    
    public void setCapacity(Long capacity) {
        _capacity = capacity;
        setChanged("capacity");
    }
           
    @RelationIndex(cf = "RelationIndex", type = VirtualPool.class)
    @Name("virtualPool")
    public URI getVirtualPool() {
        return _virtualPool;
    }
    
    public void setVirtualPool(URI virtualPool) {
        _virtualPool = virtualPool;
        setChanged("virtualPool");
    }
        
    @Name("provisionedCapacity")
    public Long getProvisionedCapacity() {
        return (null == _provisionedCapacity) ? 0L : _provisionedCapacity;
    }
    
    public void setProvisionedCapacity(Long provisionedCapacity) {
        _provisionedCapacity = provisionedCapacity;
        setChanged("provisionedCapacity");
    }
    
    @Name("allocatedCapacity")
    public Long getAllocatedCapacity() {
        return (null == _allocatedCapacity) ? 0L : _allocatedCapacity;
    }
    
    public void setAllocatedCapacity(Long allocatedCapacity) {
        _allocatedCapacity = allocatedCapacity;
        setChanged("allocatedCapacity");
    }
    
    /**
     * Getter for the ids of the backend volumes that provide the actual storage for a virtual
     * volume.
     * 
     * @return The set of ids of the backend volumes that provide the actual storage for a virtual
     *         volume.
     */
    @Name("associatedVolumes")
    @AlternateId("AssocVolumes")
    public StringSet getAssociatedVolumes() {
        return _associatedVolumes;
    }
    
    /**
     * Setter for the ids of the backend volumes that provide the actual storage for a vplex mirror.
     * 
     * @param volumes
     *            The ids of the backend volumes that provide the actual storage for a vplex mirror.
     */
    public void setAssociatedVolumes(StringSet volumes) {
        _associatedVolumes = volumes;
        setChanged("associatedVolumes");
    }
    
    @Name("varray")
    @RelationIndex(cf = "RelationIndex", type = VirtualArray.class)
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }
    
    @Name("deviceLabel")
    public String getDeviceLabel() {
        return _deviceLabel;
    }

    public void setDeviceLabel(String deviceLabel) {
        _deviceLabel = deviceLabel;
        setChanged("deviceLabel");
    }
    
    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        _nativeId = nativeId;
        setChanged("nativeId");
    }
            
    @Name("thinPreAllocationSize")
    public Long getThinPreAllocationSize() {
        return (null == _thinPreAllocationSize) ? 0L : _thinPreAllocationSize;
    }
    
    public void setThinPreAllocationSize(Long thinPreAllocationSize) {
        _thinPreAllocationSize = thinPreAllocationSize;
        setChanged("thinPreAllocationSize");
    }
    
    @Name("thinlyProvisioned")
    public Boolean getThinlyProvisioned() {
        return _thinlyProvisioned;
    }
    
    public void setThinlyProvisioned(Boolean thinlyProvisioned) {
        _thinlyProvisioned = thinlyProvisioned;
        setChanged("thinlyProvisioned");
    }
}
