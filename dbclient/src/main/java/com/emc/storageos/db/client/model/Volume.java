/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlTransient;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockSnapshot.TechnologyType;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Block volume data object
 */
@Cf("Volume")
public class Volume extends BlockObject implements ProjectResource {
    // project this volume is associated with
    private NamedURI _project;
    // total capacity in bytes
    private Long _capacity;
    // thinVolumePreAllocate size in bytes
    private Long _thinVolumePreAllocationSize;
    // thin or thick volume type
    Boolean _thinlyProvisioned = false;
    // class of service for this volume
    private URI _virtualPool;
    // Setting pool so that the it is available for the delete method
    private URI _pool;
    // Tenant who owns this volume
    private NamedURI _tenant;
    // Logical size of a storage volume on array which is volume.ConsumableBlocks *
    // volume.BlockSize.
    private Long _provisionedCapacity;
    // Total amount of storage space consumed within the StoragePool which is SpaceConsumed of
    // CIM_AllocatedFromStoragePool.
    private Long _allocatedCapacity;
    // Associated volumes. In the case that this volume is a virtual volume, this
    // member captures the backend volume(s) that provide the actual storage.
    private StringSet _associatedVolumes;
    // In case when the volume is a meta volume, metaVolumeMembers have list of meta volume members (meta volume head is not included).
    private StringSet _metaVolumeMembers;
    // Basic volume personality type (source, target, metadata) [determined internally]
    private String _personality;
    // Protection URI
    private NamedURI _protectionSet;
    // volume-unique RP property
    private String _rSetName;
    // volume-unique RP property
    private String _rpCopyName;
    // corresponding RP journal volume
    private URI rpJournalVolume;
    // The secondary RP journal volume in the case of MetroPoint.
    private URI secondaryRpJournalVolume;
    // volume-unique RP property
    private String _internalSiteName;
    // Is composite volume (meta)
    private Boolean _isComposite = false;
    // Meta volume type. Used for create meta volume and expand regular volume as meta.
    private String _compositionType;
    // Size of meta meta members (the same for all members)
    private Long _metaMemberSize;
    // Count of meta meta members (including meta head)
    private Integer _metaMemberCount;
    // Target BlockMirror volume IDs that act as a mirror for this Volume
    private StringSet _mirrors;
    // Target RP volume IDs where this volume is a source
    private StringSet _rpTargets;
    // Target SRDF volume IDs that are a remote copy for this volume
    private StringSet _srdfTargets;
    // Parent source volume for SRDF targets
    private NamedURI _srdfParent;
    // SRDF RDF Group
    private URI _srdfGroup;
    // SRDF copy policy
    private String _srdfCopyMode;
    // SRDF/RP link Status
    private String _linkStatus;
    // volume access state from array; can be overridden by protection (RP)
    private String _accessState;

    // The value alignments 0-4 correspond to SMIS values. Other storage types must map to these values.
    public static enum VolumeAccessState {
        UNKNOWN("0"),
        READABLE("1"),
        WRITEABLE("2"),
        READWRITE("3"),
        WRITEONCE("4"),
        NOT_READY("5"); // Not part of SMIS "Access" field, gathered from StatusDescriptions

        private final String state;

        VolumeAccessState(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }

        private static final VolumeAccessState[] copyOfValues = values();

        public static String getVolumeAccessStateDisplayName(String state) {
            for (VolumeAccessState stateValue : copyOfValues) {
                if (stateValue.getState().contains(state)) {
                    return stateValue.name();
                }
            }
            return VolumeAccessState.UNKNOWN.name();
        }
    }

    public enum LinkStatus {
        FAILED_OVER("6014"),
        IN_SYNC("6002 6015"),
        SUSPENDED("6013"),
        CONSISTENT("6111"),
        SPLIT(""),
        SWAPPED(""),
        DETACHED(""),
        OTHER("");
        private final String status;

        LinkStatus(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }

        private static final LinkStatus[] copyOfValues = values();

        public static String getLinkStatusDisplayName(String status) {
            for (LinkStatus statusValue : copyOfValues) {
                if (statusValue.getStatus().contains(status)) {
                    return statusValue.name();
                }
            }
            return LinkStatus.OTHER.name();
        }
    }

    public static enum CompositionType {
        STRIPED,
        CONCATENATED
    }

    // Total capacity in bytes of all meta members
    private Long _totalMetaMemberCapacity = 0L;
    // Auto Tiering policy Uri, this volume is associated with.
    private URI _autoTieringPolicyUri;

    public static enum PersonalityTypes {
        SOURCE, // Source Volume, direct result of provision request
        TARGET, // Target Volume, side-effect of getting protection
        METADATA // Metadata Volume, Protection "hidden" volume not as important to end-user, like
                 // an RP Journal volume
    }

    /*
     * When the volume is the target of a full copy operation, this flag is used to determine if the
     * synchronization has been activated or not. Activation means that the source and target
     * synchronization has been initialized (Data may not have been written to the target, however).
     */
    private Boolean isSyncActive;
    // Contains a list of URI's for any full copies that were created from this volume.
    private StringSet fullCopies;
    // When a volume is created as a full copy of another source volume, the source volume URI is
    // set here.
    private URI associatedSourceVolume;

    /*
     * when this is a full copy, this specifies the current relationship state with its source volume.
     */
    private String replicaState;

    @Name("isSyncActive")
    public Boolean getSyncActive() {
        return (isSyncActive != null) ? isSyncActive : Boolean.FALSE;
    }

    public void setSyncActive(Boolean syncActive) {
        isSyncActive = syncActive;
        setChanged("isSyncActive");
    }

    @Name("fullCopies")
    public StringSet getFullCopies() {
        return fullCopies;
    }

    public void setFullCopies(StringSet fullCopies) {
        this.fullCopies = fullCopies;
        setChanged("fullCopies");
    }

    @RelationIndex(cf = "associatedSourceVolumeIndex", type = Volume.class)
    @Name("associatedSourceVolume")
    public URI getAssociatedSourceVolume() {
        return associatedSourceVolume;
    }

    public void setAssociatedSourceVolume(URI associatedSourceVolume) {
        this.associatedSourceVolume = associatedSourceVolume;
        setChanged("associatedSourceVolume");
    }

    @Override
    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @NamedRelationIndex(cf = "NamedRelation", type = ProtectionSet.class)
    @Name("protectionSet")
    public NamedURI getProtectionSet() {
        return _protectionSet;
    }

    public void setProtectionSet(NamedURI protectionSet) {
        _protectionSet = protectionSet;
        setChanged("protectionSet");
    }

    @Override
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

    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    @Name("capacity")
    public Long getCapacity() {
        return (null == _capacity) ? 0L : _capacity;
    }

    public void setCapacity(Long capacity) {
        if (_capacity == null || !_capacity.equals(capacity)) {
            _capacity = capacity;
            setChanged("capacity");
        }
    }

    @Name("thinVolumePreAllocationSize")
    public Long getThinVolumePreAllocationSize() {
        return (null == _thinVolumePreAllocationSize) ? 0L : _thinVolumePreAllocationSize;
    }

    public void setThinVolumePreAllocationSize(Long thinVolumePreAllocationSize) {
        _thinVolumePreAllocationSize = thinVolumePreAllocationSize;
        setChanged("thinVolumePreAllocationSize");
    }

    @Name("thinlyProvisioned")
    public Boolean getThinlyProvisioned() {
        return _thinlyProvisioned;
    }

    public void setThinlyProvisioned(Boolean thinlyProvisioned) {
        _thinlyProvisioned = thinlyProvisioned;
        setChanged("thinlyProvisioned");
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

    @RelationIndex(cf = "RelationIndex", type = StoragePool.class)
    @Name("pool")
    public URI getPool() {
        return _pool;
    }

    public void setPool(URI pool) {
        _pool = pool;
        setChanged("pool");
    }

    @AggregatedIndex(cf = "AggregatedIndex", groupBy = "virtualPool,project", classGlobal = true)
    @Name("provisionedCapacity")
    public Long getProvisionedCapacity() {
        return (null == _provisionedCapacity) ? 0L : _provisionedCapacity;
    }

    public void setProvisionedCapacity(Long provisionedCapacity) {
        if (_provisionedCapacity == null || !_provisionedCapacity.equals(provisionedCapacity)) {
            _provisionedCapacity = provisionedCapacity;
            setChanged("provisionedCapacity");
        }
    }

    @AggregatedIndex(cf = "AggregatedIndex", classGlobal = true)
    @Name("allocatedCapacity")
    public Long getAllocatedCapacity() {
        return (null == _allocatedCapacity) ? 0L : _allocatedCapacity;
    }

    public void setAllocatedCapacity(Long allocatedCapacity) {
        if (_allocatedCapacity == null || !_allocatedCapacity.equals(allocatedCapacity)) {
            _allocatedCapacity = allocatedCapacity;
            setChanged("allocatedCapacity");
        }
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
     * Setter for the ids of the backend volumes that provide the actual storage for a virtual
     * volume.
     *
     * @param volumes
     *            The ids of the backend volumes that provide the actual storage for a virtual
     *            volume.
     */
    public void setAssociatedVolumes(StringSet volumes) {
        _associatedVolumes = volumes;
        setChanged("associatedVolumes");
    }

    /**
     * Getter for the device ids of the meta volume members volumes.
     *
     * @return The set of device ids of the meta volume member volumes.
     */
    @Name("metaVolumeMembers")
    public StringSet getMetaVolumeMembers() {
        return _metaVolumeMembers;
    }

    /**
     * Setter for the ids of the meta volume members volumes.
     *
     * @param volumes
     */
    public void setMetaVolumeMembers(StringSet volumes) {
        _metaVolumeMembers = volumes;
        setChanged("metaVolumeMembers");
    }

    /**
     * Getter for the ids of the BlockMirror volumes that act as a mirror for this volume.
     *
     * @return The set of ids for the BlockMirror objects
     */
    @Name("mirrors")
    public StringSet getMirrors() {
        return _mirrors;
    }

    /**
     * Setter for the ids of the BlockMirror volumes that act as a mirror for this volume.
     *
     * @param mirrors
     *            The set of ids for the BlockMirror objects
     */
    public void setMirrors(StringSet mirrors) {
        _mirrors = mirrors;
        setChanged("mirrors");
    }

    @Name("personality")
    @AlternateId("AltIdIndex")
    public String getPersonality() {
        return _personality;
    }

    public void setPersonality(String personality) {
        this._personality = personality;
        setChanged("personality");
    }

    @Name("replication_set")
    public String getRSetName() {
        return _rSetName;
    }

    public void setRSetName(String _rSetName) {
        this._rSetName = _rSetName;
        setChanged("replication_set");
    }

    @Name("isComposite")
    public Boolean getIsComposite() {
        return _isComposite;
    }

    public void setIsComposite(Boolean isComposite) {
        this._isComposite = isComposite;
        setChanged("isComposite");
    }

    @Name("metaMemberSize")
    public Long getMetaMemberSize() {
        return _metaMemberSize;
    }

    public void setMetaMemberSize(Long metaMemberSize) {
        this._metaMemberSize = metaMemberSize;
        setChanged("metaMemberSize");
    }

    @Name("metaMemberCount")
    public Integer getMetaMemberCount() {
        return _metaMemberCount;
    }

    public void setMetaMemberCount(Integer metaMemberCount) {
        this._metaMemberCount = metaMemberCount;
        setChanged("metaMemberCount");
    }

    @Name("compositionType")
    public String getCompositionType() {
        return _compositionType;
    }

    public void setCompositionType(String compositionType) {
        this._compositionType = compositionType;
        setChanged("compositionType");
    }

    @Name("totalMetaMemberCapacity")
    public Long getTotalMetaMemberCapacity() {
        return _totalMetaMemberCapacity;
    }

    public void setTotalMetaMemberCapacity(Long totalMetaMemberCapacity) {
        this._totalMetaMemberCapacity = totalMetaMemberCapacity;
        setChanged("totalMetaMemberCapacity");
    }

    @Name("autoTieringPolicyUri")
    public URI getAutoTieringPolicyUri() {
        return _autoTieringPolicyUri;
    }

    public void setAutoTieringPolicyUri(URI autoTieringPolicyUri) {
        _autoTieringPolicyUri = autoTieringPolicyUri;
        setChanged("autoTieringPolicyUri");
    }

    @Name("internalSiteName")
    public String getInternalSiteName() {
        return _internalSiteName;
    }

    public void setInternalSiteName(String internalSiteName) {
        this._internalSiteName = internalSiteName;
        setChanged("internalSiteName");
    }

    @Name("rpTargets")
    @AlternateId("RpTargetsAltIdIndex")
    public StringSet getRpTargets() {
        return _rpTargets;
    }

    public void setRpTargets(StringSet rpTargets) {
        this._rpTargets = rpTargets;
        setChanged("rpTargets");
    }

    @Name("rpCopyName")
    public String getRpCopyName() {
        return _rpCopyName;
    }

    public void setRpCopyName(String rpCopyName) {
        this._rpCopyName = rpCopyName;
        setChanged("rpCopyName");
    }

    @Name("srdfTargets")
    public StringSet getSrdfTargets() {
        return _srdfTargets;
    }

    public void setSrdfTargets(StringSet srdfTargets) {
        this._srdfTargets = srdfTargets;
        setChanged("srdfTargets");
    }

    @Name("srdfParent")
    public NamedURI getSrdfParent() {
        return _srdfParent;
    }

    public void setSrdfParent(NamedURI srdfParent) {
        _srdfParent = srdfParent;
        setChanged("srdfParent");
    }

    @Name("srdfGroup")
    public URI getSrdfGroup() {
        return _srdfGroup;
    }

    public void setSrdfGroup(URI srdfGroup) {
        this._srdfGroup = srdfGroup;
        setChanged("srdfGroup");
    }

    @Name("srdfCopyMode")
    public String getSrdfCopyMode() {
        return _srdfCopyMode;
    }

    public void setSrdfCopyMode(String srdfCopyMode) {
        this._srdfCopyMode = srdfCopyMode;
        setChanged("srdfCopyMode");
    }

    /**
     * Uses a field in the volume to determine if the volume is an SRDF volume. Best to use a field
     * that is set during placement/scheduling of the volume, during ViPR (cassandra) volume
     * creation.
     *
     * @return true if the volume is used by SRDF
     */
    public static boolean checkForSRDF(DbClient dbClient, URI blockURI) {
        if (URIUtil.isType(blockURI, Volume.class)) {
            Volume volume = dbClient.queryObject(Volume.class, blockURI);
            if (volume != null) {
                return volume.checkForSRDF();
            }
        }
        return false;
    }

    /**
     * Uses a field in the volume to determine if the volume is an SRDF volume. Best to use a field
     * that is set during placement/scheduling of the volume, during ViPR (cassandra) volume
     * creation.
     *
     * @return true if the volume is used by SRDF
     */
    public boolean checkForSRDF() {
        if (getSrdfTargets() != null && !getSrdfTargets().isEmpty()) {
            return true;
        }
        // If the SRDF parent is set, this is an SRDF device
        return getSrdfParent() != null;
    }

    /**
     * Checks whether the volume is a SRDF source volume or not
     *
     * @return true if the volume is a SRDF source volume
     */
    public boolean isSRDFSource() {
        return (getSrdfTargets() != null && !getSrdfTargets().isEmpty());
    }

    /**
     * Get all of the volumes in this SRDF set; the source and all of its targets. For a
     * multi-volume SRDF, it only returns the targets (and source) associated with this one volume.
     *
     * @param dbClient db object to read from database
     * @param volumeURI volume object
     * @return list of volume URIs
     */
    public static List<URI> fetchSRDFVolumes(DbClient dbClient, URI volumeURI) {
        List<URI> volumeIDs = new ArrayList<URI>();
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        if (volume.getSrdfTargets() != null && !volume.getSrdfTargets().isEmpty()) {
            volumeIDs.add(volume.getId());
            for (String volumeID : volume.getSrdfTargets()) {
                volumeIDs.add(URI.create(volumeID));
            }
        }
        // If the SRDF parent is set, this is an SRDF device
        if (volume.getSrdfParent() != null) {
            Volume parentVol = dbClient.queryObject(Volume.class, volume.getSrdfParent().getURI());
            if (parentVol != null) {
                volumeIDs.addAll(Volume.fetchSRDFVolumes(dbClient, parentVol.getId()));
            }
        }
        return volumeIDs;
    }

    /**
     * Uses a field in the volume to determine if the volume is an RP volume. Best to use a field
     * that is set during placement/scheduling of the volume, during ViPR (cassandra) volume
     * creation.
     *
     * @return true if the volume is used by RP
     */
    public boolean checkForRp() {
        return NullColumnValueGetter.isNotNullValue(getRpCopyName());
    }

    /**
     * Utility function that retrieves the block object associated with the URI that is passed in.
     * If the block object URI is volume, then that is returned. If the block object URI is an RP snapshot
     * then the parent volume object of the snapshot is returned. If the block object URI is a regular snapshot,
     * then the snapshot object is returned.
     *
     * This utility function is called from various places in the controller code when it is necessary to determine
     * if the operation needs to be performed on the actual block object or its parent. In the case of RP snapshots,
     * operations such as export/unexport of RP type snapshots needs to be performed on the parent of the snapshot rather
     * than the snapshot object itself.
     *
     * @param dbClient
     *            [in] - DbClient object to read from database
     * @param blockURI
     *            [in] - URI of BlockObject
     * @return Volume associated with the blockURI for RP, otherwise the object associated with the ID
     */
    public static BlockObject fetchExportMaskBlockObject(DbClient dbClient, URI blockURI) {
        BlockObject bo = null;
        if (URIUtil.isType(blockURI, Volume.class)) {
            bo = dbClient.queryObject(Volume.class, blockURI);
        } else if (URIUtil.isType(blockURI, BlockSnapshot.class)) {
            BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, blockURI);
            if (snapshot != null && snapshot.getParent() != null &&
                    NullColumnValueGetter.isNotNullValue(snapshot.getTechnologyType()) &&
                    snapshot.getTechnologyType().equals(TechnologyType.RP.name())) {
                // Process RP snapshots. At this point, if we don't find the target RP volume,
                // we need to return null.
                Volume parent = dbClient.queryObject(Volume.class, snapshot.getParent().getURI());
                if (parent.getRpTargets() != null) {
                    for (String targetIdStr : parent.getRpTargets()) {
                        Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetIdStr));
                        if (targetVolume != null && targetVolume.getVirtualArray().equals(snapshot.getVirtualArray())) {
                            // Always return the BlockSnapshot here. ExportMasks reference the RP BlockSnapshot, not
                            // Volume, when a RP BlockSnapshot export is performed.
                            return snapshot;
                        }
                    }
                }
                // The snapshot is an RP snapshot, and the RP target couldn't be found. Return null.
                return null;
            }
            // It's a snapshot that can be exported directly.
            // Local array snapshots of RP or RP+VPlex target volumes also apply to this case.
            bo = snapshot;
        } else if (URIUtil.isType(blockURI, BlockMirror.class)) {
            BlockMirror mirror = dbClient.queryObject(BlockMirror.class, blockURI);
            bo = mirror;
        }
        return bo;
    }

    @Name("rpJournalVolume")
    @RelationIndex(cf = "JournalVolumeIndex", type = Volume.class)
    public URI getRpJournalVolume() {
        return rpJournalVolume;
    }

    public void setRpJournalVolume(URI rpJournalVolume) {
        this.rpJournalVolume = rpJournalVolume;
        setChanged("rpJournalVolume");
    }

    /**
     * Getter for the secondary RecoverPoint journal volume. This
     * will only ever be used in the case of MetroPoint.
     *
     * @return The secondary RP journal volume URI.
     */
    @Name("secondaryRpJournalVolume")
    @RelationIndex(cf = "SecondaryJournalVolumeIndex", type = Volume.class)
    public URI getSecondaryRpJournalVolume() {
        return secondaryRpJournalVolume;
    }

    /**
     * Setter for the secondary RecoverPoint journal volume.
     *
     * @param secondaryRpJournalVolumes
     *            The secondary journal volume.
     */
    public void setSecondaryRpJournalVolume(URI secondaryRpJournalVolume) {
        this.secondaryRpJournalVolume = secondaryRpJournalVolume;
        setChanged("secondaryRpJournalVolume");
    }

    @Name("srdfLinkStatus")
    public String getLinkStatus() {
        return _linkStatus;
    }

    public void setLinkStatus(String linkStatus) {
        this._linkStatus = linkStatus;
        setChanged("srdfLinkStatus");
    }

    @Name("accessState")
    public String getAccessState() {
        return _accessState;
    }

    public void setAccessState(String accessState) {
        this._accessState = accessState;
        setChanged("accessState");
    }

    @Name("replicaState")
    public String getReplicaState() {
        return replicaState;
    }

    public void setReplicaState(String state) {
        this.replicaState = state;
        setChanged("replicaState");
    }

    /**
     * Returns true if the passed volume is in an export group, false otherwise.
     *
     * @param dbClient A reference to a DbClient.
     *
     * @return true if the passed volume is in an export group, false otherwise.
     */
    public boolean isVolumeExported(DbClient dbClient) {
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(getId()), exportGroupURIs);
        return exportGroupURIs.iterator().hasNext();
    }
    
    /**
     * Returns true if the passed volume is in an export group, false otherwise.
     * 
     * @param dbClient A reference to a DbClient.
     * @param ignoreRPExports If true, ignore if this volume has been exported to RP
     * @return true if the passed volume is in an export group, false otherwise.
     */
    public boolean isVolumeExported(DbClient dbClient, boolean ignoreRPExports, boolean ignoreVPlexExports) {
        boolean isExported = false;
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(getId()), exportGroupURIs);
        if (ignoreRPExports) {
            
            while (exportGroupURIs.iterator().hasNext()) {
                URI exportGroupURI = exportGroupURIs.iterator().next();
                if (exportGroupURI != null) {
                    ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
                    if (!exportGroup.checkInternalFlags(Flag.RECOVERPOINT)) {
                        isExported = true;
                        break;
                    }
                }
            }            
        } else {
            isExported = exportGroupURIs.iterator().hasNext();
        }
        return isExported;
    }

    /**
     * Returns true if the passed volume is in an export group that isn't associated with RP.
     * 
     * @param dbClient A reference to a DbClient
     * 
     * @return true if the passed volume is in an export group that isn't associated with RP, false otherwise
     */
    public boolean isVolumeExportedNonRP(DbClient dbClient) {
        URIQueryResultList exportGroupURIs = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getBlockObjectExportGroupConstraint(getId()), exportGroupURIs);
        Iterator<URI> exportGroupURIIter = exportGroupURIs.iterator();
        while (exportGroupURIIter.hasNext()) {
            URI exportGroupURI = exportGroupURIIter.next();
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
            if (!exportGroup.checkInternalFlags(Flag.RECOVERPOINT)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return whether or not a volume in ViPR was created outside
     * of ViPR and ingested.
     *
     * @param volume A reference to a volume.
     *
     * @return true if the volume was ingested, else false.
     */
    public boolean isIngestedVolume(DbClient dbClient) {
        URI systemURI = getStorageController();
        if (systemURI != null) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class, systemURI);
            if (system != null) {
                if (system.getSystemType().equals(DiscoveredSystemObject.Type.vplex.name())) {
                    StringSet associatedVolumeIds = getAssociatedVolumes();
                    if ((associatedVolumeIds == null) || (associatedVolumeIds.isEmpty())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Utility function that tells if the passed in volume is a back-end volume of a VPLEX virtual volume.
     *
     * @param dbClient
     * @param volume
     * @return
     */
    public static boolean checkForVplexBackEndVolume(DbClient dbClient, Volume volume) {
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeByAssociatedVolumesConstraint(volume.getId().toString()),
                queryResults);

        if (queryResults.iterator().hasNext()) {
            return true;
        }

        return false;
    }

    /**
     * Given a volume, this is an utility method that returns the VPLEX virtual volume that this volume is associated with.
     *
     * @param dbClient
     * @param volume
     * @return
     */
    public static Volume fetchVplexVolume(DbClient dbClient, Volume volume) {
        Volume vplexVolume = null;
        URIQueryResultList queryResults = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeByAssociatedVolumesConstraint(volume.getId().toString()),
                queryResults);

        if (queryResults.iterator().hasNext()) {
            while (queryResults.iterator().hasNext()) {
                URI vplexVolumeURI = queryResults.iterator().next();
                if (vplexVolumeURI != null) {
                    vplexVolume = dbClient.queryObject(Volume.class, vplexVolumeURI);
                    break;
                }
            }
        }

        return vplexVolume;
    }

    public static boolean isSRDFProtectedTargetVolume(Volume volume) {
        return (!NullColumnValueGetter.isNullNamedURI(volume.getSrdfParent()) || null != volume.getSrdfTargets());
    }

    /**
     * Utility function that tells if the passed in volume is a back-end volume of a protected VPLEX virtual volume.
     * For now the only supported protection type for VPLEX virtual volumes is RecoverPoint, if additional protection
     * types are added in the future we can add checks for them as they are introduced.
     *
     * @param dbClient
     * @param volume
     * @return
     */
    public static boolean checkForProtectedVplexBackendVolume(DbClient dbClient, Volume volume) {
        if (checkForVplexBackEndVolume(dbClient, volume)) {
            Volume vplexVolume = fetchVplexVolume(dbClient, volume);
            if (vplexVolume != null && vplexVolume.checkForRp()) {
                return true;
            }
        }
        return false;
    }

    public static enum ReplicationState {
        UNKNOWN(0), SYNCHRONIZED(1), CREATED(2), RESYNCED(3), INACTIVE(4), DETACHED(5), RESTORED(6);

        private static final Map<String, ReplicationState> states = new HashMap<String, ReplicationState>();

        static {
            for (ReplicationState state : EnumSet.allOf(ReplicationState.class)) {
                states.put(state.name(), state);
            }
        }
        private final int value;

        ReplicationState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static ReplicationState getEnumValue(String state) {
            return states.get(state);
        }

    }

    /**
     * Uses a field in the volume to determine if the volume is part of a CG.
     *
     * @return true if the volume is part of a CG
     */
    public boolean isInCG() {
        return !NullColumnValueGetter.isNullURI(getConsistencyGroup());
    }

}
