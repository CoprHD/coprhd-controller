/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import static com.emc.storageos.db.client.util.CommonTransformerFunctions.collectionString;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.valid.EnumType;

/**
 * Export Group
 */
@Cf("ExportGroup")
public class ExportGroup extends DataObject implements ProjectResource {
    private NamedURI _project;
    private URI _virtualArray;
    private StringMap _volumes;             // volume/snapshot uri -> lun mapping
    // This mapping is what the user requested.
    // The exportMasks will contain the mappings
    // of volume to LUNs that gets pushed to
    // the device.
    private StringSet _snapshots;           // Keep track of snapshots used in the exports
    // The main reason for having this seemingly
    // redundant container is that we need some way
    // for the DependencyTracker to know that
    // BlockSnapshots can belong to ExportGroups
    private StringSet _initiators;
    private StringSet _hosts;
    private StringSet _clusters;
    private NamedURI _tenant;
    private StringSet _exportMasks;         // export-mask uris for all exports that
    // are associated with an ExportGroup. The
    // ExportGroup is a Bourne-level
    // abstraction, the ExportMask represents a
    // lower-level array masking component.
    private String _generatedName;
    private Integer _numPaths;			    // number of paths for each initiator
    private String _exportGroupType;        // instance of #ExportGroupType
    private Boolean _zoneAllInitiators = Boolean.FALSE;     // if true all initiators are zoned.

    private StringMap _altVirtualArrays;    // alternate virtual arrays in this ExportGroup (VPlex)
    // map from BlockObject id to ExportPathParam id for over-ridden path parameters
    private StringMap _pathParameters;

    public static final int LUN_UNASSIGNED = -1;
    public static final String LUN_UNASSIGNED_STR = Integer.toHexString(LUN_UNASSIGNED);
    public static final String LUN_UNASSIGNED_DECIMAL_STR = Integer.toString(LUN_UNASSIGNED);

    @NamedRelationIndex(cf = "NamedRelation", type = Project.class)
    @Name("project")
    public NamedURI getProject() {
        return _project;
    }

    public void setProject(NamedURI project) {
        _project = project;
        setChanged("project");
    }

    @Name("varray")
    @AlternateId("AltIdIndex")
    public URI getVirtualArray() {
        return _virtualArray;
    }

    public void setVirtualArray(URI virtualArray) {
        _virtualArray = virtualArray;
        setChanged("varray");
    }

    @RelationIndex(cf = "RelationIndex", type = Volume.class)
    @IndexByKey
    @Name("volumes")
    public StringMap getVolumes() {
        return _volumes;
    }

    public void setVolumes(StringMap volumes) {
        _volumes = volumes;
    }

    public void addVolume(URI volume, Integer lun) {
        if (getVolumes() == null) {
            setVolumes(new StringMap());
        }
        if (lun == null) {
            lun = LUN_UNASSIGNED;
        }

        // Set volume's LUN only if it doesn't already exist
        // or if currentLUN is unassigned
        String lunString = lun.toString();
        String currentLUN = getVolumes().get(volume.toString());
        if (currentLUN == null || currentLUN.equals(LUN_UNASSIGNED_DECIMAL_STR)) {
            getVolumes().put(volume.toString(), lunString);
        }

        if (URIUtil.isType(volume, BlockSnapshot.class)) {
            if (_snapshots == null) {
                _snapshots = new StringSet();
            }
            _snapshots.add(volume.toString());
        }
    }

    public void removeVolume(URI volume) {
        if (_volumes != null) {
            getVolumes().remove(volume.toString());
            if (URIUtil.isType(volume, BlockSnapshot.class)) {
                if (_snapshots != null) {
                    _snapshots.remove(volume.toString());
                }
            }
        }
    }

    public void removeVolumes(List<URI> volumes) {
        if (_volumes != null) {
            for (URI uri : volumes) {
                _volumes.remove(uri.toString());
            }
        }
    }

    public void removeVolumes(Set<String> volumeURIStrings) {
        if (_volumes != null) {

            for (String uriString : volumeURIStrings) {
                _volumes.remove(uriString);
            }
        }
    }

    @RelationIndex(cf = "RelationIndex", type = BlockSnapshot.class)
    @IndexByKey
    @Name("snapshots")
    public StringSet getSnapshots() {
        return _snapshots;
    }

    public void setSnapshots(StringSet set) {
        _snapshots = set;
    }

    @Name("initiators")
    @AlternateId("ExportGroupInitiators")
    public StringSet getInitiators() {
        return _initiators;
    }

    public void setInitiators(StringSet initiators) {
        _initiators = initiators;
    }

    public boolean hasInitiators() {
        return _initiators != null && !_initiators.isEmpty();
    }

    public void addInitiators(List<URI> initiators) {
        if (getInitiators() == null) {
            setInitiators(new StringSet());
        }
        getInitiators().addAll(StringSetUtil.uriListToStringSet(initiators));
    }

    public void addHosts(List<URI> hosts) {
        if (getHosts() == null) {
            setHosts(new StringSet());
        }
        getHosts().addAll(StringSetUtil.uriListToStringSet(hosts));
    }

    public void removeHosts(List<URI> hostsToRemove) {
        StringSet hosts = getHosts();
        if (hosts != null) {
            hosts.removeAll(StringSetUtil.uriListToStringSet(hostsToRemove));
        }
    }

    public void addClusters(List<URI> clusters) {
        if (getClusters() == null) {
            setClusters(new StringSet());
        }
        getClusters().addAll(StringSetUtil.uriListToStringSet(clusters));
    }

    public void removeClusters(List<URI> clustersToRemove) {
        StringSet clusters = getClusters();
        if (clusters != null) {
            clusters.removeAll(StringSetUtil.uriListToStringSet(clustersToRemove));
        }
    }

    public void addVolumes(Map<URI, Integer> volumes) {
        if (volumes != null) {
            for (Map.Entry<URI, Integer> entry : volumes.entrySet()) {
                addVolume(entry.getKey(), entry.getValue());
            }
        }
    }

    public void removeVolumes(Map<URI, Integer> volumesToRemove) {
        if (volumesToRemove != null) {
            for (Map.Entry<URI, Integer> entry : volumesToRemove.entrySet()) {
                removeVolume(entry.getKey());
            }
        }
    }

    public void addInitiator(Initiator initiator) {
        if (getInitiators() == null) {
            setInitiators(new StringSet());
        }
        getInitiators().add(initiator.getId().toString());
    }

    public void removeInitiator(Initiator initiator) {
        StringSet initiators = getInitiators();
        if (initiators != null) {
            initiators.remove(initiator.getId().toString());
        }
    }

    public void removeInitiators(List<URI> initiatorsToRemove) {
        StringSet initiators = getInitiators();
        if (initiators != null) {
            initiators.removeAll(StringSetUtil.uriListToStringSet(initiatorsToRemove));
        }
    }

    public void removeInitiator(URI id) {
        StringSet initiators = getInitiators();
        if (initiators != null) {
            initiators.remove(id.toString());
        }
    }

    @Name("hosts")
    @AlternateId("ExportGroupHosts")
    public StringSet getHosts() {
        return _hosts;
    }

    public void setHosts(StringSet hosts) {
        _hosts = hosts;
    }

    public void addHost(Host host) {
        if (getHosts() == null) {
            setHosts(new StringSet());
        }
        getHosts().add(host.getId().toString());
    }

    public void removeHost(Host host) {
        StringSet hosts = getHosts();
        if (hosts != null) {
            hosts.remove(host.getId().toString());
        }
    }

    @Name("clusters")
    @AlternateId("ExportGroupClusters")
    public StringSet getClusters() {
        return _clusters;
    }

    public void setClusters(StringSet clusters) {
        _clusters = clusters;
    }

    public void addCluster(Cluster cluster) {
        if (getClusters() == null) {
            setClusters(new StringSet());
        }
        getClusters().add(cluster.getId().toString());
    }

    public void removeCluster(Cluster cluster) {
        StringSet clusters = getClusters();
        if (clusters != null) {
            clusters.remove(cluster.getId().toString());
        }
    }

    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI getTenant() {
        return _tenant;
    }

    public void setTenant(NamedURI tenant) {
        _tenant = tenant;
        setChanged("tenant");
    }

    public boolean hasMask(URI id) {
        return _exportMasks != null && _exportMasks.contains(id.toString());
    }

    @RelationIndex(cf = "RelationIndex", type = ExportMask.class)
    @IndexByKey
    @Name("exportMasks")
    public StringSet getExportMasks() {
        return _exportMasks;
    }

    public void setExportMasks(StringSet exportMasks) {
        _exportMasks = exportMasks;
    }

    public void addExportMask(URI maskUri) {
        addExportMask(maskUri.toString());
    }

    public void addExportMask(String maskUriStr) {
        if (_exportMasks == null) {
            _exportMasks = new StringSet();
        }
        if (!_exportMasks.contains(maskUriStr)) {
            _exportMasks.add(maskUriStr);
        }
    }

    public void removeExportMask(URI maskUri) {
        removeExportMask(maskUri.toString());
    }

    public void removeExportMask(String maskUriStr) {
        if (_exportMasks != null) {
            _exportMasks.remove(maskUriStr);
        }
    }

    @Name("generatedName")
    public String getGeneratedName() {
        return (_generatedName != null) ? _generatedName : "";
    }

    public void setGeneratedName(String generatedName) {
        _generatedName = generatedName;
        setChanged("generatedName");
    }

    @Name("numPaths")
    public Integer getNumPaths() {
        if (_numPaths == null) {
            return 0;
        }
        return _numPaths;
    }

    /**
     * Use this value ONLY to over-ride the automatic calculation of NumPaths
     * in the BlockStorageScheduler which is based on finding the maximum num_path
     * value in the VPools associated with each volume in one particular Export Mask.
     * One place it is used is to over-ride is for VPLEX to back-end volumes,
     * where we want the number of paths to be set up once for all volumes between
     * a VPLEX and one particular back-end array.
     * 
     * @param numPaths
     */
    public void setNumPaths(Integer numPaths) {
        this._numPaths = numPaths;
        setChanged("numPaths");
    }

    @EnumType(ExportGroupType.class)
    @Name("type")
    public String getType() {
        return _exportGroupType;
    }

    public void setType(String exportGroupType) {
        _exportGroupType = exportGroupType;
        setChanged("type");
    }

    @Name("zoneAllInitiators")
    public Boolean getZoneAllInitiators() {
        return _zoneAllInitiators;
    }

    /**
     * Use this value ONLY when sure that a full zone map is needed.
     * Normally it is not. Currently only needed by RecoverPoint.
     * 
     * @param zoneAllInitiators
     */
    public void setZoneAllInitiators(Boolean zoneAllInitiators) {
        _zoneAllInitiators = zoneAllInitiators;
        setChanged("zoneAllInitiators");
    }

    public boolean hasBlockObject(URI blockObjectId) {
        if ((getSnapshots() != null && getSnapshots().contains(blockObjectId.toString()) || (getVolumes() != null && getVolumes()
                .containsKey(blockObjectId.toString())))) {
            return true;
        }
        return false;
    }

    /**
     * The type of export is used to decide how masking views and initiator groups
     * should be created for the export group and what updates are allowable. Types
     * can be used as follow:
     * <ol>
     * <li>For creating an initiator type export group where only one host can have access to the volumes use
     * {@link ExportGroupType#Initiator}. In this mode it is expected that the user wants to use specific initiators.</li>
     * <li>For creating shared-access to same volumes by independent hosts use {@link ExportGroupType#Host}. All the host's initiators that
     * are connected to the volumes will be used including those used for initiator type export groups.</li>
     * <li>For creating shared-access to same volumes and at the same time manage the hosts as cluster where all hosts would have identical
     * exports use {@link ExportGroupType#Cluster}</li>
     * </ol>
     * 
     */
    public enum ExportGroupType {
        Initiator, Host, Cluster, Exclusive
    }

    /**
     * If true, it means the ExportGroup.Type == ExportGroupType.Cluster
     * 
     * @return
     */
    public boolean forCluster() {
        if (_exportGroupType != null) {
            return _exportGroupType.equals(ExportGroupType.Cluster.name());
        }
        return false;
    }

    public boolean forHost() {
        return _exportGroupType != null &&
                _exportGroupType.equals(ExportGroupType.Host.name());
    }

    public boolean forInitiator() {
        return _exportGroupType != null &&
                _exportGroupType.equals(ExportGroupType.Initiator.name());
    }

    @Name("altVirtualArrays")
    public StringMap getAltVirtualArrays() {
        return _altVirtualArrays;
    }

    public void setAltVirtualArrays(StringMap altVirtualArrays) {
        _altVirtualArrays = altVirtualArrays;
        setChanged("altVirtualArrays");
    }

    public boolean hasAltVirtualArray(String storageId) {
        return (_altVirtualArrays != null && _altVirtualArrays.containsKey(storageId));
    }

    public void putAltVirtualArray(String storageId, String altVirtualArray) {
        if (_altVirtualArrays == null) {
            _altVirtualArrays = new StringMap();
        }
        _altVirtualArrays.put(storageId, altVirtualArray);
        setChanged("altVirtualArrays");
    }

    public void removeAltVirtualArray(String storageId) {
        if (_altVirtualArrays != null && _altVirtualArrays.containsKey(storageId)) {
            _altVirtualArrays.remove(storageId);
            setChanged("altVirtualArrays");
        }
    }

    public boolean hasHost(URI id) {
        return _hosts != null && _hosts.contains(id.toString());
    }

    public boolean hasCluster(URI id) {
        return _clusters != null && _clusters.contains(id.toString());
    }

    public boolean hasInitiator(Initiator initiator) {
        return (_initiators != null && _initiators.contains(initiator.getId().toString()));
    }

    @Override
    public String toString() {
        return String.format("ExportGroup %s (%s)\n" +
                "\tInactive    : %s\n" +
                "\tType        : %s\n" +
                "\tVolumes     : %s\n" +
                "\tClusters    : %s\n" +
                "\tHosts       : %s\n" +
                "\tInitiators  : %s\n" +
                "\tExportMasks : %s\n",
                getLabel(), getId(),
                getInactive(), getType(),
                collectionString(getVolumes()),
                collectionString(getClusters()),
                collectionString(getHosts()),
                collectionString(getInitiators()),
                collectionString(getExportMasks()));
    }

    @Name("pathParam")
    public StringMap getPathParameters() {
        if (_pathParameters == null) {
            return new StringMap();
        }
        return _pathParameters;
    }

    public void setPathParameters(StringMap pathParameters) {
        this._pathParameters = pathParameters;
        setChanged("pathParam");
    }
    
    public void addToPathParameters(URI key, URI value) {
        if (_pathParameters == null) {
            setPathParameters(new StringMap());
        }
        getPathParameters().put(key.toString(), value.toString());
    }
    
    public void removeFromPathParameters(URI key) {
        getPathParameters().remove(key.toString());
    }
    
}
