/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;

/**
 * @author burckb
 * 
 */
public abstract class BlockObject extends DataObject {

    // storage controller where this volume is located
    private URI _storageController;

    // storage controller where this volume is located
    private URI _protectionController;

    // device native ID for this volume
    private String _nativeId;

    // native device ID to be indexed - this field is not exposed to client
    private String _nativeGuid;

    // these will include things like
    // thinProvisioned->Y/N, ALU->1,2,3, and raidLevel->RAID-1,RAID-6+2
    // may include volumeGroup->name for mapping multiple volumes
    private StringMap _extensions;

    // Tag for grouping volumes that need to have consistent snapshots
    private URI _consistencyGroupId;

    // Tag for grouping volumes that need to have consistent snapshots
    @Deprecated
    private StringSet consistencyGroups;

    // storage protocols supported by this volume
    private StringSet _protocols;

    // virtual array where this volume exists
    private URI _virtualArray;

    private String _wwn;

    private String _deviceLabel;

    // This is an alternate name for the block snapshot. It's useful in the case of VNX
    // SnapView (RAID GROUP based) snapshots, which usually have a Name like UID+<WWN>.
    private String _alternateName;

    // This value is an indicator that a the snapshot information is out-of-sync on the
    // provider side and an EMCRefresh will be required.
    private Boolean _emcRefreshRequired;

    // Name reference of replication group that the object belong to.
    private String _replicationGroupInstance;

    @AlternateId("AltIdIndex")
    @Name("wwn")
    public String getWWN() {
        return _wwn;
    }

    public void setWWN(String wwn) {
        _wwn = BlockObject.normalizeWWN(wwn);
        setChanged("wwn");
    }

    /**
     * 
     */
    public BlockObject() {
        super();
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

    @RelationIndex(cf = "RelationIndex", type = ProtectionSystem.class)
    @Name("protectionDevice")
    public URI getProtectionController() {
        return _protectionController;
    }

    public void setProtectionController(URI protectionController) {
        _protectionController = protectionController;
        setChanged("protectionDevice");
    }

    @AlternateId("AltIdIndex")
    @Name("nativeId")
    public String getNativeId() {
        return _nativeId;
    }

    public void setNativeId(String nativeId) {
        _nativeId = nativeId;
        setChanged("nativeId");
    }

    @AlternateId("AltIdIndex")
    @Name("nativeGuid")
    public String getNativeGuid() {
        return _nativeGuid;
    }

    public void setNativeGuid(String nativeGuid) {
        _nativeGuid = nativeGuid;
        setChanged("nativeGuid");
    }

    @Name("extensions")
    public StringMap getExtensions() {
        return _extensions;
    }

    /**
     * Set extensions map - overwrites existing one
     * 
     * @param map StringMap of extensions to set
     */
    public void setExtensions(StringMap map) {
        _extensions = map;
        setChanged("extensions");
    }

    @RelationIndex(cf = "RelationIndex", type = BlockConsistencyGroup.class)
    @Name("consistencyGroup")
    public URI getConsistencyGroup() {
        return _consistencyGroupId;
    }

    public void setConsistencyGroup(URI consistencyGroup) {
        _consistencyGroupId = consistencyGroup;
        setChanged("consistencyGroup");
    }

    public boolean hasConsistencyGroup() {
        return _consistencyGroupId != null;
    }

    @Name("consistencyGroups")
    @AlternateId("CgAltIdIndex")
    @Deprecated
    public StringSet getConsistencyGroups() {
        return consistencyGroups;
    }

    @Deprecated
    public void setConsistencyGroups(StringSet consistencyGroups) {
        this.consistencyGroups = consistencyGroups;
        setChanged("consistencyGroups");
    }

    @Name("protocols")
    public StringSet getProtocol() {
        return _protocols;
    }

    public void setProtocol(StringSet protocols) {
        _protocols = protocols;
        setChanged("protocols");
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

    public void setAlternateName(String alternateName) {
        _alternateName = alternateName;
        setChanged("alternateName");
    }

    @Name("alternateName")
    public String getAlternateName() {
        return _alternateName;
    }

    @Name("refreshRequired")
    public Boolean getRefreshRequired() {
        return (_emcRefreshRequired != null) ? _emcRefreshRequired : Boolean.FALSE;
    }

    public void setRefreshRequired(Boolean required) {
        _emcRefreshRequired = required;
        setChanged("refreshRequired");
    }

    @AlternateId("AltIdIndex")
    @Name("replicationGroupInstance")
    public String getReplicationGroupInstance() {
        return _replicationGroupInstance;
    }

    public void setReplicationGroupInstance(String replicaGroupInstance) {
        _replicationGroupInstance = replicaGroupInstance;
        setChanged("replicationGroupInstance");
    }

    /**
     * Utility function that would allow you to retrieve any derived BlockObject
     * based on its URI (e.g, Volume or BlockSnapshot).
     * 
     * @param dbClient [in] - DbClient object to read from database
     * @param blockURI [in] - URI of BlockObject
     * @return BlockObject instance that has id 'blockURI'
     */
    public static BlockObject fetch(DbClient dbClient, URI blockURI) {
        BlockObject block = null;

        if (URIUtil.isType(blockURI, Volume.class)) {
            block = dbClient.queryObject(Volume.class, blockURI);
        } else if (URIUtil.isType(blockURI, BlockSnapshot.class)) {
            block = dbClient.queryObject(BlockSnapshot.class, blockURI);
        } else if (URIUtil.isType(blockURI, BlockMirror.class)) {
            block = dbClient.queryObject(BlockMirror.class, blockURI);
        }

        return block;
    }

    /**
     * Utility function to retrieve derived BlockObjects based on their URIs (e.g, Volume or BlockSnapshot).
     * The block objects need to be of same type
     *
     * @param dbClient [in] - DbClient object to read from database
     * @param blockURIs [in] - URIs of BlockObjects
     * @return BlockObject instances
     */
    public static List <? extends BlockObject> fetch(DbClient dbClient, List<URI> blockURIs) {
        List<? extends BlockObject> blockObjects = null;

        if (URIUtil.isType(blockURIs.get(0), Volume.class)) {
            blockObjects = dbClient.queryObject(Volume.class, blockURIs);
        } else if (URIUtil.isType(blockURIs.get(0), BlockSnapshot.class)) {
            blockObjects = dbClient.queryObject(BlockSnapshot.class, blockURIs);
        } else if (URIUtil.isType(blockURIs.get(0), BlockMirror.class)) {
            blockObjects = dbClient.queryObject(BlockMirror.class, blockURIs);
        }

        return blockObjects;
    }

    /**
     * Utility function that normalize the volume wwn, so that it only contains upper case hex numbers
     * 
     * @param wwn the wwn to be normalized
     * @return normalized wwn
     */
    static public String normalizeWWN(String wwn) {
        String result = wwn;
        if (wwn != null && !wwn.isEmpty()) {
            result = wwn.replaceAll("[^A-Fa-f0-9]", "");
            result = result.toUpperCase();
        }
        return result;
    }

    /**
     * Utility function that allows you to check for RP regardless of the
     * object type.
     * 
     * @param dbClient [in] - DbClient object to read from database
     * @param blockURI [in] - URI of BlockObject
     * @return true if the object is RP
     */
    public static boolean checkForRP(DbClient dbClient, URI blockURI) {
        if (URIUtil.isType(blockURI, BlockSnapshot.class)) {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, blockURI);
            return snapshot.getProtectionController() != null;
        } else if (URIUtil.isType(blockURI, Volume.class)) {
            Volume volume = dbClient.queryObject(Volume.class, blockURI);
            return volume.checkForRp();
        }
        return false;
    }

    /**
     * Deprecated - Needed only for 2.1 migration callback.
     * 
     * Convenience method to get a BlockConsistencyGroup by type.
     * 
     * @param dbClient
     * @param type
     * @return
     */
    @Deprecated
    public BlockConsistencyGroup fetchConsistencyGroupByType(DbClient dbClient, BlockConsistencyGroup.Types type) {
        BlockConsistencyGroup cg = null;

        if (getConsistencyGroups() != null && !getConsistencyGroups().isEmpty()) {
            // If we only have a single CG, ignore the type and try and return the CG.
            // It has to be the one we're looking for. The only use for type right now is for
            // RP+VPLEX which would have multiple CGs.
            if (getConsistencyGroups().size() == 1) {
                cg = dbClient.queryObject(BlockConsistencyGroup.class, URI.create(getConsistencyGroups().iterator().next()));
            }
            else {
                // Multiple CGs, try to find the correct one with the type passed in.
                for (String cgUriStr : getConsistencyGroups()) {
                    cg = dbClient.queryObject(BlockConsistencyGroup.class, URI.create(cgUriStr));
                    if (cg != null && cg.getType() != null) {
                        if (cg.getType().equalsIgnoreCase(type.toString())) {
                            return cg;
                        }
                    }
                }
            }
        }

        return cg;
    }

    /**
     * Deprecated - Needed only for 2.1 migration callback.
     * 
     * Convenience method to get a consistency group URI by type.
     * 
     * @param dbClient
     * @param type
     * @return
     */
    @Deprecated
    public URI fetchConsistencyGroupUriByType(DbClient dbClient, BlockConsistencyGroup.Types type) {
        BlockConsistencyGroup cg = fetchConsistencyGroupByType(dbClient, type);
        if (cg != null) {
            return cg.getId();
        }

        return null;
    }

    /**
     * Deprecated - Needed only for 2.1 migration callback.
     * 
     * Convenience method to add a consistency group.
     * 
     * @param cgUri
     */
    @Deprecated
    public void addConsistencyGroup(String cgUri) {
        if (getConsistencyGroups() == null) {
            setConsistencyGroups(new StringSet());
        }
        getConsistencyGroups().add(cgUri);
    }
}
