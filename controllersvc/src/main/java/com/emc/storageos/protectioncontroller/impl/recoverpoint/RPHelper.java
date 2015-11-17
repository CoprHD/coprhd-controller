/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller.impl.recoverpoint;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getRpSourceVolumeByTarget;
import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getRpJournalVolumeParent;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getSecondaryRpJournalVolumeParent;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.utils.RecoverPointClientFactory;
import com.emc.storageos.recoverpoint.utils.RecoverPointUtils;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.MetaVolumeUtils;
import com.google.common.base.Joiner;

/**
 * RecoverPoint specific helper bean
 */
public class RPHelper {

    /**
     *
     */
    private static final String VOL_DELIMITER = "-";
    private static final double RP_DEFAULT_JOURNAL_POLICY = 0.25;
    public static final String REMOTE = "remote";
    public static final String LOCAL = "local";
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String JOURNAL = "journal";
    public static final Long DEFAULT_RP_JOURNAL_SIZE_IN_BYTES = 10737418240L; // default minimum journal size is 10GB (in bytes)

    private DbClient _dbClient;
    private static final Logger _log = LoggerFactory.getLogger(RPHelper.class);

    private static final String HTTPS = "https";
    private static final String WSDL = "wsdl";
    private static final String RP_ENDPOINT = "/fapi/version4_1";
    
    private static final String LOG_MSG_OPERATION_TYPE_DELETE = "delete";
    private static final String LOG_MSG_OPERATION_TYPE_REMOVE_PROTECTION = "remove protection from";    
    private static final String LOG_MSG_VOLUME_TYPE_RP = "RP_SOURCE";
    private static final String LOG_MSG_VOLUME_TYPE_RPVPLEX = "RP_VPLEX_VIRT_SOURCE";
    
    public static final String REMOVE_PROTECTION = "REMOVE_PROTECTION";

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    /**
     * Get all of the volumes in this replication set; the source and all of its targets.
     * For a multi-CG protection, it only returns the targets (and source) associated with this one volume.
     *
     * @param volume volume object
     * @return list of volume URIs
     * @throws DeviceControllerException
     */
    public List<URI> getReplicationSetVolumes(Volume volume) throws DeviceControllerException {

        if (volume == null) {
            throw DeviceControllerException.exceptions.invalidObjectNull();
        }

        List<URI> volumeIDs = new ArrayList<URI>();
        for (Volume vol : getVolumesInRSet(volume)) {
            volumeIDs.add(vol.getId());
        }

        return volumeIDs;
    }

    /**
     * Helper Method: The caller wants to get the protection settings associated with a specific virtual array
     * and virtual pool. Handle the exceptions appropriately.
     *
     * @param vpool VirtualPool to look for
     * @param varray VirtualArray to protect to
     * @return the stored protection settings object
     * @throws InternalException
     */
    public VpoolProtectionVarraySettings getProtectionSettings(VirtualPool vpool, VirtualArray varray) throws InternalException {
        if (vpool.getProtectionVarraySettings() != null) {
            String settingsID = vpool.getProtectionVarraySettings().get(varray.getId().toString());
            try {
                return (_dbClient.queryObject(VpoolProtectionVarraySettings.class, URI.create(settingsID)));
            } catch (IllegalArgumentException e) {
                throw DeviceControllerException.exceptions.invalidURI(e);
            }
        }
        throw DeviceControllerException.exceptions.objectNotFound(varray.getId());
    }

    /**
     * Gets the virtual pool of the target copy.
     *
     * @param tgtVarray
     * @param srcVpool the base virtual pool
     * @return
     */
    public VirtualPool getTargetVirtualPool(VirtualArray tgtVarray, VirtualPool srcVpool) {
        VpoolProtectionVarraySettings settings = getProtectionSettings(srcVpool, tgtVarray);
        // If there was no vpool specified use the source vpool for this varray.
        VirtualPool tgtVpool = srcVpool;
        if (settings.getVirtualPool() != null) {
            tgtVpool = _dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
        }
        return tgtVpool;
    }

    /**
     * given one volume in an rset (either source or any target) return all source and target volumes in that rset
     *
     * @param vol
     * @return
     */
    private List<Volume> getVolumesInRSet(Volume volume) {
        List<Volume> allVolumesInRSet = new ArrayList<Volume>();

        Volume sourceVol = null;
        if (volume.getRpTargets() != null && !volume.getRpTargets().isEmpty()) {
            sourceVol = volume;
        } else {
            sourceVol = getRPSourceVolumeFromTarget(_dbClient, volume);
        }
        if (sourceVol != null) {
            allVolumesInRSet.add(sourceVol);
        }

        for (String tgtVolId : sourceVol.getRpTargets()) {
            if (tgtVolId.equals(volume.getId().toString())) {
                allVolumesInRSet.add(volume);
            } else {
                Volume tgt = _dbClient.queryObject(Volume.class, URI.create(tgtVolId));
                if (tgt != null && !tgt.getInactive()) {
                    allVolumesInRSet.add(tgt);
                }

                // if this target was previously the Metropoint active source, go out and get the standby copy
                if (tgt != null && isMetroPointVolume(tgt)) {
                    allVolumesInRSet.addAll(getMetropointStandbyCopies(tgt));
                }
            }
        }

        return allVolumesInRSet;
    }

    /**
     * Gets a volume's associated target volumes.
     *
     * @param volume the volume whose targets we want to find.
     * @return the list of associated target volumes.
     */
    public List<Volume> getTargetVolumes(Volume volume) {
        List<Volume> targets = new ArrayList<Volume>();

        if (volume != null && PersonalityTypes.SOURCE.name().equals(volume.getPersonality())) {
            List<Volume> rsetVolumes = getVolumesInRSet(volume);

            for (Volume rsetVolume : rsetVolumes) {
                if (PersonalityTypes.TARGET.name().equals(rsetVolume.getPersonality())) {
                    targets.add(rsetVolume);
                }
            }
        }

        return targets;
    }

    /**
     * This method will return all volumes that should be deleted based on the entire list of volumes to be deleted.
     * If this is the last source volume in the CG, this method will return all journal volumes as well.
     *
     * @param reqDeleteVolumes all volumes in the delete request
     * @return list of volumes to unexport and delete
     * @throws InternalException
     * @throws URISyntaxException
     */
    public Set<URI> getVolumesToDelete(Collection<URI> reqDeleteVolumes) throws InternalException {
        _log.info(String.format("Getting all RP volumes to delete for requested list: %s", reqDeleteVolumes));

        Set<URI> volumeIDs = new HashSet<URI>();
        Set<URI> protectionSetIds = new HashSet<URI>();

        Iterator<Volume> volumes = _dbClient.queryIterativeObjects(Volume.class, reqDeleteVolumes, true);

        // Divide the RP volumes by BlockConsistencyGroup so we can determine if all volumes in the
        // RP consistency group are being removed.
        Map<URI, Set<URI>> cgsToVolumesForDelete = new HashMap<URI, Set<URI>>();

        // for each volume requested to be deleted, add that volume plus any source or target related
        // to that volume to the list of volumes to be deleted
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            // get the list of all source and target volumes in the same replication set as the
            // volume passed in
            List<Volume> allVolsInRSet = getVolumesInRSet(volume);
            List<URI> allVolsInRSetURI = new ArrayList<URI>();
            URI cgURI = null;

            // Loop through the replication set volumes to:
            // 1. Determine the consistency group.
            // 2. Keep track of the protection set if one is being referenced. This will be used
            // later to perform a cleanup operation.
            for (Volume vol : allVolsInRSet) {
                allVolsInRSetURI.add(vol.getId());

                if (!NullColumnValueGetter.isNullURI(vol.getConsistencyGroup())) {
                    cgURI = vol.getConsistencyGroup();
                }

                if (!NullColumnValueGetter.isNullNamedURI(vol.getProtectionSet())) {
                    // Keep track of the protection sets for a cleanup operation later in case we
                    // find any stale volume references
                    protectionSetIds.add(vol.getProtectionSet().getURI());
                }
            }

            // Add the replication set volume IDs to the list of volumes to be deleted
            _log.info(String.format("Adding volume %s to the list of volumes to be deleted", allVolsInRSetURI.toString()));
            volumeIDs.addAll(allVolsInRSetURI);

            // Add a mapping of consistency groups to volumes to determine if we are deleting
            // the entire CG which would indicate journals are also being deleted.
            if (cgURI != null) {
                if (cgsToVolumesForDelete.get(cgURI) == null) {
                    cgsToVolumesForDelete.put(cgURI, new HashSet<URI>());
                }
                cgsToVolumesForDelete.get(cgURI).addAll(allVolsInRSetURI);
            } else {
                _log.warn(String
                        .format("Unable to find a valid CG for replication set volumes %s. Unable to determine if the entire CG is being deleted as part of this request.",
                                allVolsInRSetURI.toString()));
            }
        }

        // if we're deleting all of the volumes in this consistency group, we can add the journal volumes
        for (Map.Entry<URI, Set<URI>> cgToVolumesForDelete : cgsToVolumesForDelete.entrySet()) {
            List<Volume> cgVolumes = getCgVolumes(cgToVolumesForDelete.getKey(), _dbClient);

            // determine if all of the source and target volumes in the consistency group are on the list
            // of volumes to delete; if so, we will add the journal volumes to the list.
            // also create a list of stale volumes to be removed from the protection set
            Set<URI> journalVols = new HashSet<URI>();
            boolean wholeCG = true;
            if (cgVolumes != null) {
                for (Volume cgVol : cgVolumes) {
                    Set<URI> cgVolsToDelete = cgToVolumesForDelete.getValue();

                    // If the CG volume is not in the list of volumes to delete for this CG, we must
                    // determine if it's a journal or another source/target not being deleted.
                    if (!cgVolsToDelete.contains(cgVol.getId())) {
                        // Do not consider VPlex backing volumes or inactive volumes
                        if (!cgVol.getInactive() && NullColumnValueGetter.isNotNullValue(cgVol.getPersonality())) {
                            if (!Volume.PersonalityTypes.METADATA.toString().equals(cgVol.getPersonality())) {
                                // the volume is either a source or target; this means there are other volumes in the rset
                                wholeCG = false;
                                break;
                            }
                        }
                    }
                }
            }

            if (wholeCG) {
                // Determine all the journals in the CG based on the source/target volume journal
                // references.
                Set<URI> cgVolsToDelete = cgToVolumesForDelete.getValue();
                for (URI volToDeleteUri : cgVolsToDelete) {
                    Volume volToDelete = _dbClient.queryObject(Volume.class, volToDeleteUri);
                    if (!NullColumnValueGetter.isNullURI(volToDelete.getRpJournalVolume())) {
                        journalVols.add(volToDelete.getRpJournalVolume());
                    }
                }

                _log.info(String
                        .format("Determined that this is a request to delete consistency group %s.  Adding journal volumes to the list of volumes to delete: %s",
                                cgToVolumesForDelete.getKey(), journalVols.toString()));
                volumeIDs.addAll(journalVols);
            } else {
                _log.info(String.format(
                        "Consistency group %s will not be removed.  Only a subset of the replication sets are being removed.",
                        cgToVolumesForDelete.getKey()));
            }
        }

        // Clean-up stale ProtectionSet volume references. This is just a cautionary operation to prevent
        // "bad things" from happening.
        for (URI protSetId : protectionSetIds) {
            List<String> staleVolumes = new ArrayList<String>();
            ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, protSetId);

            if (protectionSet.getVolumes() != null) {
                for (String protSetVol : protectionSet.getVolumes()) {
                    URI protSetVolUri = URI.create(protSetVol);
                    if (!volumeIDs.contains(protSetVolUri)) {
                        Volume vol = _dbClient.queryObject(Volume.class, protSetVolUri);
                        if (vol == null || vol.getInactive()) {
                            // The ProtectionSet references a stale volume that no longer exists in the DB.
                            _log.info("ProtectionSet " + protectionSet.getLabel() + " references volume " + protSetVol
                                    + " that no longer exists in the DB.  Removing this volume reference.");
                            staleVolumes.add(protSetVol);
                        }
                    }
                }
            }

            // remove stale entries from protection set
            if (!staleVolumes.isEmpty()) {
                for (String vol : staleVolumes) {
                    protectionSet.getVolumes().remove(vol);
                }
                _dbClient.updateObject(protectionSet);
            }
        }

        return volumeIDs;
    }

    /**
     * Gets volume descriptors for volumes in an RP protection to be deleted
     * handles vplex andnon-vplex as well as mixed storage configurations
     * (e.g. vplex source and non-vplex targets)
     * 
     * @param systemURI System that the delete request belongs to
     * @param volumeURIs All volumes to be deleted
     * @param deletionType The type of deletion
     * @param newVpool Only used when removing protection, the new vpool to move the volume to
     * @return All descriptors needed to clean up volumes
     */
    public List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
            List<URI> volumeURIs, String deletionType, VirtualPool newVpool) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        try {
            Set<URI> allVolumeIds = getVolumesToDelete(volumeURIs);

            for (URI volumeURI : allVolumeIds) {                
                Volume volume = _dbClient.queryObject(Volume.class, volumeURI);                
                VolumeDescriptor descriptor = null;
                boolean isSourceVolume = false;
                
                // if RP source, add a descriptor for the RP source
                if (volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                    isSourceVolume = true;
                    String volumeType = LOG_MSG_VOLUME_TYPE_RP;
                    String operationType = LOG_MSG_OPERATION_TYPE_DELETE;
                    if (volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty()) {
                        volumeType = LOG_MSG_VOLUME_TYPE_RPVPLEX;
                        descriptor = new VolumeDescriptor(VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE, 
                                volume.getStorageController(), volume.getId(), null, null);
                    } else {                        
                        descriptor = new VolumeDescriptor(VolumeDescriptor.Type.RP_SOURCE, 
                                volume.getStorageController(), volume.getId(), null, null);
                    }
                    
                    if (REMOVE_PROTECTION.equals(deletionType)) {
                        operationType = LOG_MSG_OPERATION_TYPE_REMOVE_PROTECTION;
                        Map<String, Object> volumeParams = new HashMap<String, Object>();
                        volumeParams.put(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME, Boolean.TRUE); 
                        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID, newVpool.getId()); 
                        descriptor.setParameters(volumeParams);
                    }
                                                        
                    _log.info(String.format("Adding %s descriptor to %s%s volume [%s] (%s)", 
                            volumeType, operationType, 
                            (volumeType.equals(LOG_MSG_VOLUME_TYPE_RP) ? "" : "virtual "), 
                            volume.getLabel(), volume.getId()));
                    volumeDescriptors.add(descriptor);                                        
                }
                
                // If this is a virtual volume, add a descriptor for the virtual volume
                if (RPHelper.isVPlexVolume(volume)) {
                    // VPLEX virtual volume                                      
                    descriptor = new VolumeDescriptor(VolumeDescriptor.Type.VPLEX_VIRT_VOLUME, volume.getStorageController(),
                            volume.getId(), null, null);
                    String operationType = LOG_MSG_OPERATION_TYPE_DELETE;
                    // Add a flag to not delete this virtual volume if this is a Source volume and
                    // the deletion type is Remove Protection
                    if (isSourceVolume && REMOVE_PROTECTION.equals(deletionType)) {
                        operationType = LOG_MSG_OPERATION_TYPE_REMOVE_PROTECTION;
                        Map<String, Object> volumeParams = new HashMap<String, Object>();
                        volumeParams.put(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME, Boolean.TRUE);
                        descriptor.setParameters(volumeParams);
                    }
                    
                    _log.info(String.format("Adding VPLEX_VIRT_VOLUME descriptor to %s virtual volume [%s] (%s)", 
                            operationType, volume.getLabel(), volume.getId()));                    
                    volumeDescriptors.add(descriptor);                    
                    
                    // Next, add all the BLOCK volume descriptors for the VPLEX back-end volumes
                    for (String associatedVolumeId : volume.getAssociatedVolumes()) {
                        operationType = LOG_MSG_OPERATION_TYPE_DELETE;
                        Volume associatedVolume = _dbClient.queryObject(Volume.class, URI.create(associatedVolumeId));
                        // a previous failed delete may have already removed associated volumes
                        if (associatedVolume != null && !associatedVolume.getInactive()) {                            
                            descriptor = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA, associatedVolume.getStorageController(),
                                    associatedVolume.getId(), null, null);
                            // Add a flag to not delete these backing volumes if this is a Source volume and
                            // the deletion type is Remove Protection
                            if (isSourceVolume && REMOVE_PROTECTION.equals(deletionType)) {
                                operationType = LOG_MSG_OPERATION_TYPE_REMOVE_PROTECTION;
                                Map<String, Object> volumeParams = new HashMap<String, Object>();
                                volumeParams.put(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME, Boolean.TRUE);                    
                                descriptor.setParameters(volumeParams);
                            }
                            _log.info(String.format("Adding BLOCK_DATA descriptor to %s virtual volume backing volume [%s] (%s)",
                                    operationType, associatedVolume.getLabel(), associatedVolume.getId()));
                            volumeDescriptors.add(descriptor);
                        }
                    }
                } else {                    
                    String operationType = LOG_MSG_OPERATION_TYPE_DELETE;
                    descriptor = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA, volume.getStorageController(), volume.getId(),
                            null, null);
                    // Add a flag to not delete this volume if this is a Source volume and
                    // the deletion type is Remove Protection
                    if (isSourceVolume && REMOVE_PROTECTION.equals(deletionType)) {
                        operationType = LOG_MSG_OPERATION_TYPE_REMOVE_PROTECTION;
                        Map<String, Object> volumeParams = new HashMap<String, Object>();
                        volumeParams.put(VolumeDescriptor.PARAM_DO_NOT_DELETE_VOLUME, Boolean.TRUE); 
                        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_VPOOL_ID, newVpool.getId());
                        descriptor.setParameters(volumeParams);
                    }
                    _log.info(String.format("Adding BLOCK_DATA descriptor to %s volume [%s] (%s)", 
                            operationType, volume.getLabel(), volume.getId()));
                    volumeDescriptors.add(descriptor);
                }
            }
        } catch (Exception e) {
            throw RecoverPointException.exceptions.deletingRPVolume(e);
        }

        return volumeDescriptors;
    }

    private int getJournalRsetCount(List<URI> protectionSetVolumes, URI journalVolume) {
        int rSetCount = 0;

        Iterator<URI> iter = protectionSetVolumes.iterator();
        while (iter.hasNext()) {
            URI protectedVolumeID = iter.next();
            Volume protectionVolume = _dbClient.queryObject(Volume.class, protectedVolumeID);
            if (!protectionVolume.getInactive() &&
                    !protectionVolume.getPersonality().equals(Volume.PersonalityTypes.METADATA.toString())
                    && protectionVolume.getRpJournalVolume().equals(journalVolume)) {
                rSetCount++;
            }
        }

        return rSetCount;
    }

    /**
     * Determine if the protection set's source volumes are represented in the volumeIDs list.
     * Used to figure out if we can perform full CG operations or just partial CG operations.
     *
     * @param dbClient db client
     * @param protectionSet protection set
     * @param volumeIDs volume IDs
     * @return true if volumeIDs contains all of the source volumes in the protection set
     */
    public static boolean containsAllRPSourceVolumes(DbClient dbClient, ProtectionSet protectionSet, Collection<URI> volumeIDs) {

        // find all source volumes.
        List<URI> sourceVolumeIDs = new ArrayList<URI>();
        _log.info("Inspecting protection set: " + protectionSet.getLabel() + " to see if request contains all source volumes");
        for (String volumeIDStr : protectionSet.getVolumes()) {
            Volume volume = dbClient.queryObject(Volume.class, URI.create(volumeIDStr));
            if (volume != null) {
                _log.debug("Looking at volume: " + volume.getLabel());
                if (!volume.getInactive() && volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())) {
                    _log.debug("Adding volume: " + volume.getLabel());
                    sourceVolumeIDs.add(volume.getId());
                }
            }
        }

        // go through all volumes sent in, remove any volumes you find in the source list.
        sourceVolumeIDs.removeAll(volumeIDs);

        if (!sourceVolumeIDs.isEmpty()) {
            _log.info("Found that the volumes requested do not contain all source volumes in the protection set, namely: " +
                    Joiner.on(',').join(sourceVolumeIDs));
            return false;
        }

        _log.info("Found that all of the source volumes in the protection set are in the request.");
        return true;
    }

    /**
     * Determine if the consistency group's source volumes are represented in the volumeIDs list.
     * Used to figure out if we can perform full CG operations or just partial CG operations.
     *
     * @param dbClient db client
     * @param consistencyGroupUri the BlockConsistencyGroup ID
     * @param volumeIDs volume IDs
     * @return true if volumeIDs contains all of the source volumes in the protection set
     */
    public static boolean cgSourceVolumesContainsAll(DbClient dbClient, URI consistencyGroupUri, Collection<URI> volumeIDs) {
        boolean cgSourceVolumesContainsAll = false;

        if (consistencyGroupUri != null) {
            // find all source volumes.
            List<URI> sourceVolumeIDs = new ArrayList<URI>();
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroupUri);
            _log.info("Inspecting consisency group: " + cg.getLabel() + " to see if request contains all source volumes");

            List<Volume> sourceVolumes = getCgSourceVolumes(consistencyGroupUri, dbClient);

            if (sourceVolumes != null) {
                for (Volume srcVolume : sourceVolumes) {
                    sourceVolumeIDs.add(srcVolume.getId());
                }
            }

            // go through all volumes sent in, remove any volumes you find in the source list.
            sourceVolumeIDs.removeAll(volumeIDs);

            if (!sourceVolumeIDs.isEmpty()) {
                _log.info("Found that the volumes requested do not contain all source volumes in the consistency group, namely: " +
                        Joiner.on(',').join(sourceVolumeIDs));
            } else {
                _log.info("Found that all of the source volumes in the consistency group are in the request.");
                cgSourceVolumesContainsAll = true;
            }
        }

        return cgSourceVolumesContainsAll;
    }

    /**
     * Determines if a journal volume is shared by multiple replication sets.
     *
     * @param protectionSetVolumes volumes from a protection set
     * @param journalVolume journal volume
     * @return true if journal is shared between more than one volume in a protection set
     */
    public boolean isJournalShared(List<URI> protectionSetVolumes, URI journalVolume) {
        if (getJournalRsetCount(protectionSetVolumes, journalVolume) > 1) {
            return true;
        }

        return false;
    }

    /**
     * Determines if a journal volume is active in a list of volumes.
     *
     * @param protectionSetVolumes volumes from a protection set
     * @param journalVolume journal volume
     * @return true if journal is active with any active volume in a protection set
     */
    public boolean isJournalActive(List<URI> protectionSetVolumes, URI journalVolume) {
        if (getJournalRsetCount(protectionSetVolumes, journalVolume) > 0) {
            return true;
        }

        return false;
    }

    /**
     * Given an RP source volume and a protection virtual array, give me the corresponding target volume.
     *
     * @param id source volume id
     * @param virtualArray virtual array protected to
     * @return Volume of the target
     */
    public static Volume getRPTargetVolumeFromSource(DbClient dbClient, Volume srcVolume, URI virtualArray) {
        if (srcVolume.getRpTargets() == null || srcVolume.getRpTargets().isEmpty()) {
            return null;
        }

        for (String targetId : srcVolume.getRpTargets()) {
            Volume target = dbClient.queryObject(Volume.class, URI.create(targetId));

            if (target.getVirtualArray().equals(virtualArray)) {
                return target;
            }
        }

        return null;
    }

    /**
     * Given a RP target volume, this method gets the corresponding source volume.
     *
     * @param dbClient the database client.
     * @param id target volume id.
     */
    public static Volume getRPSourceVolumeFromTarget(DbClient dbClient, Volume tgtVolume) {
        Volume sourceVolume = null;

        if (tgtVolume == null) {
            return sourceVolume;
        }

        final List<Volume> sourceVolumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        getRpSourceVolumeByTarget(tgtVolume.getId().toString()));

        if (sourceVolumes != null && !sourceVolumes.isEmpty()) {
            // A RP target volume will only be associated to 1 source volume so return
            // the first entry.
            sourceVolume = sourceVolumes.get(0);
        }

        return sourceVolume;
    }

    /**
     * Given a RP journal volume, this method gets the corresponding parent volume. The
     * parent will either be a source or target volume.
     *
     * @param dbClient the database client.
     * @param id target volume id.
     */
    public static Volume getRPJournalParentVolume(DbClient dbClient, Volume journalVolume) {
        // Source or target parent volume.
        Volume parentVolume = null;

        if (journalVolume == null) {
            return parentVolume;
        }

        List<Volume> parentVolumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        getRpJournalVolumeParent(journalVolume.getId()));

        // If we haven't found a primary journal volume parent then this volume might be
        // a secondary journal volume. So try to find a secondary journal volume parent.
        if (parentVolumes == null || parentVolumes.isEmpty()) {
            parentVolumes = CustomQueryUtility
                    .queryActiveResourcesByConstraint(dbClient, Volume.class,
                            getSecondaryRpJournalVolumeParent(journalVolume.getId()));
        }

        if (parentVolumes != null && !parentVolumes.isEmpty()) {
            // A RP journal volume will only be associated to 1 source or target volume so return
            // the first entry.
            parentVolume = parentVolumes.get(0);
        }

        return parentVolume;
    }

    /**
     * Gets the associated source volume given any type of RP volume. If a source volume
     * is given, that volume is returned. For a source journal volume, the associated source
     * volume is found and returned. For a target journal volume, the associated target
     * volume is found and then its source volume is found and returned. For a target volume,
     * the associated source volume is found and returned.
     *
     * @param dbClient the database client.
     * @param volume the volume for which we find the associated source volume.
     * @return the associated source volume.
     */
    public static Volume getRPSourceVolume(DbClient dbClient, Volume volume) {
        Volume sourceVolume = null;

        if (volume == null) {
            return sourceVolume;
        }

        if (NullColumnValueGetter.isNotNullValue(volume.getPersonality())) {
            if (volume.getPersonality().equals(PersonalityTypes.SOURCE.name())) {
                _log.info("Attempting to find RP source volume corresponding to source volume " + volume.getId());
                sourceVolume = volume;
            } else if (volume.getPersonality().equals(PersonalityTypes.TARGET.name())) {
                _log.info("Attempting to find RP source volume corresponding to target volume " + volume.getId());
                sourceVolume = getRPSourceVolumeFromTarget(dbClient, volume);
            } else if (volume.getPersonality().equals(PersonalityTypes.METADATA.name())) {
                _log.info("Attempting to find RP source volume corresponding to journal volume" + volume.getId());
                Volume journalParent = getRPJournalParentVolume(dbClient, volume);
                // The journal's parent might be a target volume. In this case we want
                // to get the associated source.
                if (journalParent.getPersonality().equals(PersonalityTypes.TARGET.name())) {
                    sourceVolume = getRPSourceVolumeFromTarget(dbClient, journalParent);
                } else {
                    // The journal's parent is in fact the source volume.
                    sourceVolume = journalParent;
                }
            } else {
                _log.warn("Attempting to find RP source volume corresponding to an unknown RP volume type, for volume " + volume.getId());
            }
        }

        if (sourceVolume == null) {
            _log.warn("Unable to find RP source volume corresponding to volume " + volume.getId());
        } else {
            _log.info("Found RP source volume " + sourceVolume.getId() + ", corresponding to volume " + volume.getId());
        }

        return sourceVolume;
    }

    /**
     * Convenience method that determines if the passed network is connected to the
     * passed varray.
     *
     * Check the assigned varrays list if it exist, if not check against the connect varrays.
     *
     * @param network
     * @param virtualArray
     * @return
     */
    public boolean isNetworkConnectedToVarray(NetworkLite network, VirtualArray virtualArray) {
        if (network != null && network.getConnectedVirtualArrays() != null
                && network.getConnectedVirtualArrays().contains(String.valueOf(virtualArray.getId()))) {
            return true;
        }
        return false;
    }

    /**
     * Check if initiator being added to export-group is good.
     *
     * @param exportGroup
     * @param initiator
     * @throws InternalException
     */
    public boolean isInitiatorInVarray(VirtualArray varray, String wwn) throws InternalException {
        // Get the networks assigned to the virtual array.
        List<Network> networks = CustomQueryUtility.queryActiveResourcesByRelation(
                _dbClient, varray.getId(), Network.class, "connectedVirtualArrays");

        for (Network network : networks) {
            if (network == null || network.getInactive() == true) {
                continue;
            }

            StringMap endpointMap = network.getEndpointsMap();
            for (String endpointKey : endpointMap.keySet()) {
                String endpointValue = endpointMap.get(endpointKey);
                if (wwn.equals(endpointValue) ||
                        wwn.equals(endpointKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Check if any of the networks containing the RP site initiators contains storage
     * ports that are explicitly assigned or implicitly connected to the passed virtual
     * array.
     *
     * @param storageSystemURI The storage system who's connected networks we want to find.
     * @param protectionSystemURI The protection system used to find the site initiators.
     * @param siteId The side id for which we need to lookup associated initiators.
     * @param varrayURI The virtual array being used to check for network connectivity
     * @throws InternalException
     */
    public boolean rpInitiatorsInStorageConnectedNework(URI storageSystemURI, URI protectionSystemURI, String siteId, URI varrayURI)
            throws InternalException {
        // Determine what network the StorageSystem is part of and verify that the RP site initiators
        // are part of that network.
        // Then get the front end ports on the Storage array.
        Map<URI, List<StoragePort>> arrayTargetMap = ConnectivityUtil.getStoragePortsOfType(_dbClient,
                storageSystemURI, StoragePort.PortType.frontend);
        Set<URI> arrayTargetNetworks = new HashSet<URI>();
        arrayTargetNetworks.addAll(arrayTargetMap.keySet());

        ProtectionSystem protectionSystem =
                _dbClient.queryObject(ProtectionSystem.class, protectionSystemURI);
        StringSet siteInitiators =
                protectionSystem.getSiteInitiators().get(siteId);

        // Build a List of RP site initiator networks
        Set<URI> rpSiteInitiatorNetworks = new HashSet<URI>();
        for (String wwn : siteInitiators) {
            NetworkLite rpSiteInitiatorNetwork = NetworkUtil.getEndpointNetworkLite(wwn, _dbClient);
            if (rpSiteInitiatorNetwork != null) {
                rpSiteInitiatorNetworks.add(rpSiteInitiatorNetwork.getId());
            }
        }

        // Eliminate any storage ports that are not explicitly assigned
        // or implicitly connected to the passed varray.
        Iterator<URI> arrayTargetNetworksIter = arrayTargetNetworks.iterator();
        while (arrayTargetNetworksIter.hasNext()) {
            URI networkURI = arrayTargetNetworksIter.next();
            Iterator<StoragePort> targetStoragePortsIter = arrayTargetMap.get(networkURI).iterator();
            while (targetStoragePortsIter.hasNext()) {
                StoragePort targetStoragePort = targetStoragePortsIter.next();
                StringSet taggedVArraysForPort = targetStoragePort.getTaggedVirtualArrays();
                if ((taggedVArraysForPort == null) || (!taggedVArraysForPort.contains(varrayURI.toString()))) {
                    targetStoragePortsIter.remove();
                }
            }

            // Eliminate any storage array connected networks who's storage ports aren't
            // explicitly assigned or implicitly connected to the passed varray.
            if (arrayTargetMap.get(networkURI).isEmpty()) {
                arrayTargetMap.remove(networkURI);
            }
        }

        List<URI> initiators = new ArrayList<URI>();
        Iterator<URI> rpSiteInitiatorsNetworksItr = rpSiteInitiatorNetworks.iterator();

        while (rpSiteInitiatorsNetworksItr.hasNext()) {
            URI initiatorURI = rpSiteInitiatorsNetworksItr.next();
            if (arrayTargetMap.keySet().contains(initiatorURI)) {
                initiators.add(initiatorURI);
            }
        }

        if (initiators.isEmpty()) {
            return false;
        }

        return true;
    }

    /**
     * Determines if the given storage system has any active RecoverPoint protected
     * volumes under management.
     *
     * @param id the storage system id
     * @return true if the storage system has active RP volumes under management. false otherwise.
     */
    public boolean containsActiveRpVolumes(URI id) {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(id), result);
        Iterator<URI> volumeUriItr = result.iterator();

        while (volumeUriItr.hasNext()) {
            Volume volume = _dbClient.queryObject(Volume.class, volumeUriItr.next());
            // Is this an active RP volume?
            if (volume != null && !volume.getInactive()
                    && volume.getRpCopyName() != null && !volume.getRpCopyName().isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Helper method that determines what the potential provisioned capacity is of a VMAX volume.
     * The size returned may or may not be what the eventual provisioned capacity will turn out to be, but its pretty accurate estimate.
     *
     * @param requestedSize Size of the volume requested
     * @param volume volume
     * @param storageSystem storagesystem of the volume
     * @return potential provisioned capacity
     */
    public Long computeVmaxVolumeProvisionedCapacity(long requestedSize,
            Volume volume, StorageSystem storageSystem) {
        Long vmaxPotentialProvisionedCapacity = 0L;
        StoragePool expandVolumePool = _dbClient.queryObject(StoragePool.class, volume.getPool());
        long metaMemberSize = volume.getIsComposite() ? volume.getMetaMemberSize() : volume.getCapacity();
        long metaCapacity = volume.getIsComposite() ? volume.getTotalMetaMemberCapacity() : volume.getCapacity();
        MetaVolumeRecommendation metaRecommendation = MetaVolumeUtils.getExpandRecommendation(storageSystem, expandVolumePool,
                metaCapacity, requestedSize, metaMemberSize, volume.getThinlyProvisioned(),
                _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool()).getFastExpansion());

        if (metaRecommendation.isCreateMetaVolumes()) {
            long metaMemberCount = volume.getIsComposite() ? metaRecommendation.getMetaMemberCount() + volume.getMetaMemberCount() :
                    metaRecommendation.getMetaMemberCount() + 1;
            vmaxPotentialProvisionedCapacity = metaMemberCount * metaRecommendation.getMetaMemberSize();
        } else {
            vmaxPotentialProvisionedCapacity = requestedSize;
        }
        return vmaxPotentialProvisionedCapacity;
    }

    /**
     * Get the FAPI RecoverPoint Client using the ProtectionSystem
     *
     * @param ps ProtectionSystem object
     * @return RecoverPointClient object
     * @throws RecoverPointException
     */
    public static RecoverPointClient getRecoverPointClient(ProtectionSystem protectionSystem) throws RecoverPointException {
        RecoverPointClient recoverPointClient = null;
        if (protectionSystem.getUsername() != null && !protectionSystem.getUsername().isEmpty()) {
            try {
                List<URI> endpoints = new ArrayList<URI>();
                // Main endpoint that was registered by the user
                endpoints.add(new URI(HTTPS, null, protectionSystem.getIpAddress(), protectionSystem.getPortNumber(), RP_ENDPOINT, WSDL,
                        null));
                // Add any other endpoints for cluster management ips we have
                for (String clusterManagementIp : protectionSystem.getClusterManagementIPs()) {
                    endpoints.add(new URI(HTTPS, null, clusterManagementIp, protectionSystem.getPortNumber(), RP_ENDPOINT, WSDL, null));
                }
                recoverPointClient = RecoverPointClientFactory.getClient(protectionSystem.getId(), endpoints,
                        protectionSystem.getUsername(), protectionSystem.getPassword());
            } catch (URISyntaxException ex) {
                throw DeviceControllerExceptions.recoverpoint.errorCreatingServerURL(protectionSystem.getIpAddress(),
                        protectionSystem.getPortNumber(), ex);
            }
        } else {
            throw DeviceControllerExceptions.recoverpoint.noUsernamePasswordSpecified(protectionSystem
                    .getIpAddress());
        }

        return recoverPointClient;
    }

    /**
     * Determines if the given volume descriptor applies to an RP source volume.
     *
     * @param volumeDescriptor the volume descriptor.
     * @return true if the descriptor applies to an RP source volume, false otherwise.
     */
    public boolean isRPSource(VolumeDescriptor volumeDescriptor) {
        boolean isSource = false;
        if ((volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_SOURCE)) ||
                (volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_EXISTING_SOURCE)) ||
                (volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_EXISTING_PROTECTED_SOURCE)) ||
                (volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_VPLEX_VIRT_SOURCE))) {
            isSource = true;
        }

        return isSource;
    }

    /**
     * Determines if the given volume descriptor applies to an RP target volume.
     *
     * @param volumeDescriptor the volume descriptor.
     * @return true if the descriptor applies to an RP target volume, false otherwise.
     */
    public boolean isRPTarget(VolumeDescriptor volumeDescriptor) {
        boolean isTarget = false;
        if ((volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_TARGET)) ||
                (volumeDescriptor.getType().equals(VolumeDescriptor.Type.RP_VPLEX_VIRT_TARGET))) {
            isTarget = true;
        }
        return isTarget;
    }

    /**
     * Determines if a volume is part of a MetroPoint configuration.
     *
     * @param volume the volume.
     * @return true if this is a MetroPoint volume, false otherwise.
     */
    public boolean isMetroPointVolume(Volume volume) {
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
        if (VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
            _log.info("vpool specifies Metropoint RPCG requested");
            return true;
        }
        return false;
    }

    /**
     * Checks to see if the volume is a production journal. We check to see if the
     * volume's rp copy name lines up with any of the given production copies.
     *
     * @param productionCopies the production copies.
     * @param volume the volume.
     * @return true if the volume is a production journal, false otherwise.
     */
    public boolean isProductionJournal(Set<String> productionCopies, Volume volume) {
        for (String productionCopy : productionCopies) {
            if (productionCopy.equalsIgnoreCase(volume.getRpCopyName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns an existing journal volume to be used as journal for a new source volume.
     * In 2.2, the largest sized journal volume already allocated to the CG will be returned.
     *
     * @param cgSourceVolumes
     * @param isMetropointStandby true only in the case when picking journals for MetroPoint stand-by copy
     * @return
     */
    public Volume selectExistingJournalForSourceVolume(List<Volume> cgSourceVolumes, boolean isMetropointStandby) {
        Volume existingCGJournalVolume = null;
        Map<Long, List<URI>> cgJournalsBySize = new TreeMap<Long, List<URI>>(Collections.reverseOrder());
        Volume journal = null;

        for (Volume cgSourceVolume : cgSourceVolumes) {
            if (isMetropointStandby) {
                if (!NullColumnValueGetter.isNullURI(cgSourceVolume.getSecondaryRpJournalVolume())) {
                    journal = _dbClient.queryObject(Volume.class, cgSourceVolume.getSecondaryRpJournalVolume());
                }
            } else {
                if (!NullColumnValueGetter.isNullURI(cgSourceVolume.getRpJournalVolume())) {
                    journal = _dbClient.queryObject(Volume.class, cgSourceVolume.getRpJournalVolume());
                }
            }

            if (journal != null) {
                if (!cgJournalsBySize.containsKey(journal.getProvisionedCapacity())) {
                    cgJournalsBySize.put(journal.getProvisionedCapacity(), new ArrayList<URI>());
                }
                cgJournalsBySize.get(journal.getProvisionedCapacity()).add(journal.getId());
            }
        }

        // Fetch the first journal in the list with the largest capacity.
        for (Long journalSize : cgJournalsBySize.keySet()) {
            existingCGJournalVolume = _dbClient.queryObject(Volume.class, cgJournalsBySize.get(journalSize).get(0));
            break;
        }

        // We should never hit this case, but just in case we do, just return the journal volume of the first source volume in the list.
        if (null == existingCGJournalVolume) {
            URI existingJournalVolumeURI = isMetropointStandby ? cgSourceVolumes.get(0).getSecondaryRpJournalVolume() : cgSourceVolumes
                    .get(0).getRpJournalVolume();
            existingCGJournalVolume = _dbClient.queryObject(Volume.class, existingJournalVolumeURI);
        }

        return existingCGJournalVolume;
    }

    /**
     * Returns an existing journal volume to be used as journal for a new target volume.
     * In 2.2, the largest sized journal volume already allocated to the CG will be returned.
     *
     * @param cgTargetVolumes Volumes in the consistency group
     * @param varray protection varray
     * @param copyInternalSiteName RP internal site of the volume
     * @return existing Journal volume to be used/shared by volumes
     */
    public Volume selectExistingJournalForTargetVolume(List<Volume> cgTargetVolumes, URI varray, String copyInternalSiteName) {
        Volume existingCGTargetJournalVolume = null;
        List<Volume> validExistingTargetJournalVolumes = new ArrayList<Volume>();
        Map<Long, List<Volume>> cgTargetJournalsBySize = new TreeMap<Long, List<Volume>>(Collections.reverseOrder());

        for (Volume cgTargetVolume : cgTargetVolumes) {
            // Make sure we only consider existing CG target volumes from the same virtual array
            if (cgTargetVolume.getVirtualArray().equals(varray)
                    && cgTargetVolume.getInternalSiteName().equalsIgnoreCase(copyInternalSiteName)) {
                if (null != cgTargetVolume.getRpJournalVolume()) {
                    Volume targetJournal = _dbClient.queryObject(Volume.class, cgTargetVolume.getRpJournalVolume());
                    if (!cgTargetJournalsBySize.containsKey(targetJournal.getProvisionedCapacity())) {
                        cgTargetJournalsBySize.put(targetJournal.getProvisionedCapacity(), new ArrayList<Volume>());
                    }
                    cgTargetJournalsBySize.get(targetJournal.getProvisionedCapacity()).add(targetJournal);
                    validExistingTargetJournalVolumes.add(targetJournal);
                }
            }
        }

        // fetch the first journal in the list with the largest capacity.
        for (Long targetJournalSize : cgTargetJournalsBySize.keySet()) {
            existingCGTargetJournalVolume = cgTargetJournalsBySize.get(targetJournalSize).get(0);
            break;
        }
        // we should never hit this case, but just in case we do, just return the journal volume of the first source volume in the list.
        if (null == existingCGTargetJournalVolume) {
            existingCGTargetJournalVolume = validExistingTargetJournalVolumes.get(0);
        }
        return existingCGTargetJournalVolume;
    }

    /**
     * Return a list of journal volumes corresponding to the list of volumes to be deleted, that can be deleted.
     * The logic for this is simple - if a journal volume in protection set is part of only those volumes that
     * are in the delete request, then that journal can be delete. If there are other protection set volumes
     * not part of the deleted that reference this journal then this journal will not be removed.
     *
     * @param protectionSet - protection set of the volumes that are deleted
     * @param rsetSrcVolumesToDelete - given the list of volumes to delete, determine journals corresponding to those that can be deleted.
     * @return List<URI> of primary or secondary (if valid) journals that can be deleted
     * @throws URISyntaxException
     */
    public Set<URI> determineJournalsToRemove(ProtectionSet protectionSet, List<URI> rsetSrcVolumesToDelete) {
        StringSet protectionSetVolumes = protectionSet.getVolumes();
        Map<URI, HashSet<URI>> psJournalVolumeMap = new HashMap<URI, HashSet<URI>>();
        Map<URI, HashSet<URI>> volumeDeleteJournalVolumeMap = new HashMap<URI, HashSet<URI>>();
        Set<URI> journalsToDelete = new HashSet<URI>();

        // Build a map of journal volumes to protection set volumes that reference that journal volume.
        for (String psVolumeID : protectionSetVolumes) {
            Volume psVolume = _dbClient.queryObject(Volume.class, URI.create(psVolumeID));
            if (psVolume == null) {
                continue;
            }
            if (psVolume.getRpJournalVolume() != null) {
                if (psJournalVolumeMap.get(psVolume.getRpJournalVolume()) == null) {
                    psJournalVolumeMap.put(psVolume.getRpJournalVolume(), new HashSet<URI>());
                }
                psJournalVolumeMap.get(psVolume.getRpJournalVolume()).add(psVolume.getId());
            }

            if (psVolume.getSecondaryRpJournalVolume() != null) {
                if (psJournalVolumeMap.get(psVolume.getSecondaryRpJournalVolume()) == null) {
                    psJournalVolumeMap.put(psVolume.getSecondaryRpJournalVolume(), new HashSet<URI>());
                }
                psJournalVolumeMap.get(psVolume.getSecondaryRpJournalVolume()).add(psVolume.getId());
            }
        }

        // Given the source volumes, find the targets based on the rset name.
        // The source and target of a rset share the same rset name in a protection set.
        Set<URI> rsetVolumesToDelete = new HashSet<URI>();
        for (String psVolumeID : protectionSetVolumes) {
            Volume psVolume = _dbClient.queryObject(Volume.class, URI.create(psVolumeID));
            for (URI volume : rsetSrcVolumesToDelete) {
                Volume rsetVolume = _dbClient.queryObject(Volume.class, URI.create(volume.toString()));
                if (rsetVolume != null &&
                        rsetVolume.getRSetName() != null &&
                        psVolume != null &&
                        psVolume.getRSetName() != null &&
                        !psVolume.getPersonality().equalsIgnoreCase(Volume.PersonalityTypes.METADATA.toString()) &&
                        rsetVolume.getRSetName().equalsIgnoreCase(psVolume.getRSetName())) {
                    rsetVolumesToDelete.add(URI.create(psVolumeID));
                }
            }
        }

        // Build another map of all the journals that are referenced by the volumes in the delete request.
        // This map includes journals on the target side as well
        for (URI rsetVolumeToDelete : rsetVolumesToDelete) {
            Volume volume = _dbClient.queryObject(Volume.class, rsetVolumeToDelete);
            if (volume.getRpJournalVolume() != null) {
                if (volumeDeleteJournalVolumeMap.get(volume.getRpJournalVolume()) == null) {
                    volumeDeleteJournalVolumeMap.put(volume.getRpJournalVolume(), new HashSet<URI>());
                }
                volumeDeleteJournalVolumeMap.get(volume.getRpJournalVolume()).add(volume.getId());
            }

            if (volume.getSecondaryRpJournalVolume() != null) {
                if (volumeDeleteJournalVolumeMap.get(volume.getSecondaryRpJournalVolume()) == null) {
                    volumeDeleteJournalVolumeMap.put(volume.getSecondaryRpJournalVolume(), new HashSet<URI>());
                }
                volumeDeleteJournalVolumeMap.get(volume.getSecondaryRpJournalVolume()).add(volume.getId());
            }
        }

        _log.info("ProtectionSet journalMap");
        for (URI psJournalEntry : psJournalVolumeMap.keySet()) {
            _log.info(String.format("%s : %s", psJournalEntry.toString(), Joiner.on(",").join(psJournalVolumeMap.get(psJournalEntry))));
        }

        _log.info("Volume delete journalMap");
        for (URI journalVolumeEntry : volumeDeleteJournalVolumeMap.keySet()) {
            _log.info(String.format("%s : %s", journalVolumeEntry.toString(),
                    Joiner.on(",").join(volumeDeleteJournalVolumeMap.get(journalVolumeEntry))));
        }

        // Journals that are safe to remove are those journals in the volumes to delete list that are not
        // referenced by volumes in the protection set volumes.
        for (URI journalUri : volumeDeleteJournalVolumeMap.keySet()) {
            int journalReferenceCount = volumeDeleteJournalVolumeMap.get(journalUri).size();
            int psJournalReferenceCount = psJournalVolumeMap.get(journalUri).size();

            if (journalReferenceCount == psJournalReferenceCount) {
                _log.info("Deleting journal volume : " + journalUri.toString());
                journalsToDelete.add(journalUri);
            }
        }
        return journalsToDelete;
    }

    /**
     * Gets a list of RecoverPoint consistency group volumes.
     *
     * @param blockConsistencyGroupUri The CG to check
     * @param dbClient The dbClient instance
     * @return List of volumes in the CG
     */
    public static List<Volume> getCgVolumes(URI blockConsistencyGroupUri, DbClient dbClient) {
        final List<Volume> cgVolumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        getVolumesByConsistencyGroup(blockConsistencyGroupUri));

        return cgVolumes;
    }

    /**
     * Gets all the source volumes that belong in the specified RecoverPoint
     * consistency group.
     *
     * @param blockConsistencyGroupUri The CG to check
     * @param dbClient The dbClient instance
     * @return All Source volumes in the CG
     */
    public static List<Volume> getCgSourceVolumes(URI blockConsistencyGroupUri, DbClient dbClient) {
        List<Volume> cgSourceVolumes = new ArrayList<Volume>();
        List<Volume> cgVolumes = getCgVolumes(blockConsistencyGroupUri, dbClient);

        // Filter only source volumes
        if (cgVolumes != null) {
            for (Volume cgVolume : cgVolumes) {
                if (NullColumnValueGetter.isNotNullValue(cgVolume.getPersonality()) 
                        && PersonalityTypes.SOURCE.toString().equals(cgVolume.getPersonality())) {
                    cgSourceVolumes.add(cgVolume);
                }
            }
        }

        return cgSourceVolumes;
    }

    /**
     * Gets all the volumes of the specified personality type in RecoverPoint
     * consistency group.
     *
     * @param blockConsistencyGroupUri The CG to check
     * @param personality The personality of the volumes to filter with
     * @return All Source volumes in the CG
     */
    public List<Volume> getCgVolumes(URI blockConsistencyGroupUri, String personality) {
        List<Volume> cgPersonalityVolumes = new ArrayList<Volume>();
        List<Volume> cgVolumes = getCgVolumes(blockConsistencyGroupUri, _dbClient);

        // Filter volumes based on personality
        if (cgVolumes != null) {
            for (Volume cgVolume : cgVolumes) {
                if (cgVolume.getPersonality() != null &&
                        cgVolume.getPersonality().equals(personality)) {
                    cgPersonalityVolumes.add(cgVolume);
                }
            }
        }

        return cgPersonalityVolumes;
    }

    /**
     *
     * Helper method that computes if journal volumes are required to be provisioned and added to the RP CG.
     *
     * @param journalPolicy
     * @param cg
     * @param size
     * @param volumeCount
     * @param personality
     * @param copyInternalSiteName
     * @param metropointSecondary
     * @return
     */
    public boolean isAdditionalJournalRequiredForCG(String journalPolicy, BlockConsistencyGroup cg, String size, Integer volumeCount,
            String personality,
            String copyInternalSiteName) {
        boolean additionalJournalRequired = false;

        if (journalPolicy != null && (journalPolicy.endsWith("x") || journalPolicy.endsWith("X"))) {
            List<Volume> cgVolumes = getCgVolumes(cg.getId(), personality);
            Set<URI> journalVolumeURIs = new HashSet<URI>();
            Long cgJournalSize = 0L;
            Long cgJournalSizeInBytes = 0L;

            // Get all the volumes of the specified personality (source/target)
            // Since multiple RP source/target volume might reference the same journal
            // we need to get a unique list of the journal volumes and use that to calculate sizes.
            for (Volume cgVolume : cgVolumes) {
                if (personality.equalsIgnoreCase(Volume.PersonalityTypes.SOURCE.toString())) {
                    if (!NullColumnValueGetter.isNullURI(cgVolume.getRpJournalVolume())) {
                        journalVolumeURIs.add(cgVolume.getRpJournalVolume());
                    }
                } else {
                    if (copyInternalSiteName.equalsIgnoreCase(cgVolume.getInternalSiteName())) {
                        if (!NullColumnValueGetter.isNullURI(cgVolume.getRpJournalVolume())) {
                            journalVolumeURIs.add(cgVolume.getRpJournalVolume());
                        }
                    }
                }
            }

            for (URI journalVolumeURI : journalVolumeURIs) {
                Volume journalVolume = _dbClient.queryObject(Volume.class, journalVolumeURI);
                cgJournalSize += journalVolume.getProvisionedCapacity();
            }

            cgJournalSizeInBytes = SizeUtil.translateSize(String.valueOf(cgJournalSize));
            _log.info(String.format("Existing total metadata size for the CG : %s GB ",
                    SizeUtil.translateSize(cgJournalSizeInBytes, SizeUtil.SIZE_GB)));

            Long cgVolumeSize = 0L;
            Long cgVolumeSizeInBytes = 0L;
            for (Volume cgVolume : cgVolumes) {
                if (personality.equalsIgnoreCase(Volume.PersonalityTypes.TARGET.toString())) {
                    if (copyInternalSiteName.equalsIgnoreCase(cgVolume.getInternalSiteName())) {
                        cgVolumeSize += cgVolume.getProvisionedCapacity();
                    }
                } else {
                    cgVolumeSize += cgVolume.getProvisionedCapacity();
                }

            }

            cgVolumeSizeInBytes = SizeUtil.translateSize(String.valueOf(cgVolumeSize));
            _log.info(String.format("Cumulative %s copies size : %s GB", personality,
                    SizeUtil.translateSize(cgVolumeSizeInBytes, SizeUtil.SIZE_GB)));

            Long newCgVolumeSizeInBytes = cgVolumeSizeInBytes + (Long.valueOf(SizeUtil.translateSize(size)) * volumeCount);
            _log.info(String.format("New cumulative %s copies size after the operation would be : %s GB", personality,
                    SizeUtil.translateSize(newCgVolumeSizeInBytes, SizeUtil.SIZE_GB)));
            Float multiplier = Float.valueOf(journalPolicy.substring(0, journalPolicy.length() - 1)).floatValue();
            _log.info(String.format("Based on VirtualPool's journal policy, journal capacity required is : %s",
                    (SizeUtil.translateSize(newCgVolumeSizeInBytes, SizeUtil.SIZE_GB) * multiplier)));
            _log.info(String.format("Current allocated journal capacity : %s GB",
                    SizeUtil.translateSize(cgJournalSizeInBytes, SizeUtil.SIZE_GB)));

            if (cgJournalSizeInBytes < (newCgVolumeSizeInBytes * multiplier)) {
                additionalJournalRequired = true;
            }
        }

        StringBuilder msg = new StringBuilder();
        msg.append(personality + "-Journal" + " : ");

        if (additionalJournalRequired) {
            msg.append("Additional journal required");
        } else {
            msg.append("Additional journal NOT required");
        }

        _log.info(msg.toString());
        return additionalJournalRequired;
    }

    /*
     * Since there are several ways to express journal size policy, this helper method will take
     * the source size and apply the policy string to come up with a resulting size.
     *
     * @param sourceSizeStr size of the source volume
     *
     * @param journalSizePolicy the policy of the journal size. ("10gb", "min", or "3.5x" formats)
     *
     * @return journal volume size result
     */
    public static long getJournalSizeGivenPolicy(String sourceSizeStr, String journalSizePolicy, int resourceCount) {
        // first, normalize the size. user can specify as GB,MB, TB, etc
        Long sourceSizeInBytes = 0L;

        // Convert the source size into bytes, if specified in KB, MB, etc.
        if (sourceSizeStr.contains(SizeUtil.SIZE_TB) || sourceSizeStr.contains(SizeUtil.SIZE_GB)
                || sourceSizeStr.contains(SizeUtil.SIZE_MB) || sourceSizeStr.contains(SizeUtil.SIZE_B)) {
            sourceSizeInBytes = SizeUtil.translateSize(sourceSizeStr);

        } else {
            sourceSizeInBytes = Long.valueOf(sourceSizeStr);
        }

        Long totalSourceSizeInBytes = sourceSizeInBytes * resourceCount;
        _log.info(String.format("getJournalSizeGivenPolicy : totalSourceSizeInBytes %s GB ",
                SizeUtil.translateSize(totalSourceSizeInBytes, SizeUtil.SIZE_GB)));

        // First check: If the journalSizePolicy is not specified or is null, then perform the default math.
        // Default journal size is 10GB if source volume size times 0.25 is less than 10GB, else its 0.25x(source size)
        if (journalSizePolicy == null || journalSizePolicy.equals(NullColumnValueGetter.getNullStr())) {
            if (DEFAULT_RP_JOURNAL_SIZE_IN_BYTES < (totalSourceSizeInBytes * RP_DEFAULT_JOURNAL_POLICY)) {
                return (long) ((totalSourceSizeInBytes * RP_DEFAULT_JOURNAL_POLICY));
            } else {
                return DEFAULT_RP_JOURNAL_SIZE_IN_BYTES;
            }
        }

        // Second Check: if the journal policy specifies min, then return default journal size
        if (journalSizePolicy.equalsIgnoreCase("min")) {
            return DEFAULT_RP_JOURNAL_SIZE_IN_BYTES;
        }

        // Third check: If the policy is a multiplier, perform the math, respecting the minimum value
        if (journalSizePolicy.endsWith("x") || journalSizePolicy.endsWith("X")) {
            float multiplier = Float.valueOf(journalSizePolicy.substring(0, journalSizePolicy.length() - 1)).floatValue();
            long journalSize = ((long) (totalSourceSizeInBytes.longValue() * multiplier) < DEFAULT_RP_JOURNAL_SIZE_IN_BYTES) ? DEFAULT_RP_JOURNAL_SIZE_IN_BYTES
                    : (long) (totalSourceSizeInBytes.longValue() * multiplier);
            return journalSize;
        }

        // If the policy is an abbreviated value.
        // This is the only way to get a value less than minimally allowed.
        // Good in case the minimum changes or we're wrong about it per version.
        return SizeUtil.translateSize(journalSizePolicy);
    }

    /**
     * Determines if a Volume is being referenced as an associated volume by an RP+VPlex
     * volume of a specified personality type (SOURCE, TARGET, METADATA, etc.).
     *
     * @param volume the volume we are trying to find a parent RP+VPlex volume reference for.
     * @param dbClient the DB client.
     * @param types the personality types.
     * @return true if this volume is associated to an RP+VPlex journal, false otherwise.
     */
    public static boolean isAssociatedToRpVplexType(Volume volume, DbClient dbClient, PersonalityTypes... types) {
        final List<Volume> vplexVirtualVolumes = CustomQueryUtility
                .queryActiveResourcesByConstraint(dbClient, Volume.class,
                        getVolumesByAssociatedId(volume.getId().toString()));

        for (Volume vplexVirtualVolume : vplexVirtualVolumes) {
            if (NullColumnValueGetter.isNotNullValue(vplexVirtualVolume.getPersonality())) {
                // If the personality type matches any of the passed in personality
                // types, we can return true.
                for (PersonalityTypes type : types) {
                    if (vplexVirtualVolume.getPersonality().equals(type.name())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * returns the list of copies residing on the standby varray given the active production volume in a
     * Metropoint environment
     *
     * @param volume the active production volume
     * @return
     */
    public List<Volume> getMetropointStandbyCopies(Volume volume) {

        List<Volume> standbyCopies = new ArrayList<Volume>();

        if (volume.getProtectionSet() == null) {
            return standbyCopies;
        }

        ProtectionSet protectionSet = _dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());

        if (protectionSet.getVolumes() == null) {
            return standbyCopies;
        }

        // look for the standby varray in the volume's vpool
        VirtualPool vpool = _dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

        if (vpool == null) {
            return standbyCopies;
        }

        StringMap varrayVpoolMap = vpool.getHaVarrayVpoolMap();
        if (varrayVpoolMap != null && !varrayVpoolMap.isEmpty()) {
            URI standbyVarrayId = URI.create(varrayVpoolMap.keySet().iterator().next());

            // now loop through the replication set volumes and look for any copies from the standby varray
            for (String rsetVolId : protectionSet.getVolumes()) {
                Volume rsetVol = _dbClient.queryObject(Volume.class, URI.create(rsetVolId));
                if (rsetVol != null && !rsetVol.getInactive() && rsetVol.getRpTargets() != null) {
                    for (String targetVolId : rsetVol.getRpTargets()) {
                        Volume targetVol = _dbClient.queryObject(Volume.class, URI.create(targetVolId));
                        if (targetVol.getVirtualArray().equals(standbyVarrayId)) {
                            standbyCopies.add(targetVol);
                        }
                    }
                }
            }
        }
        return standbyCopies;
    }

    /**
     * Check to see if the target volume (based on varray) has already been provisioned
     *
     * @param volume Source volume to check
     * @param varrayToCheckURI URI of the varray we're looking for Targets
     * @param dbClient DBClient
     * @return The target volume found or null otherwise
     */
    public static Volume findAlreadyProvisionedTargetVolume(Volume volume, URI varrayToCheckURI, DbClient dbClient) {
        Volume alreadyProvisionedTarget = null;
        if (volume.checkForRp()
                && volume.getRpTargets() != null
                && NullColumnValueGetter.isNotNullValue(volume.getPersonality())
                && volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.name())) {
            // Loop through all the targets, check to see if any of the target volumes have
            // the same varray URI as the one passed in.
            for (String targetVolumeId : volume.getRpTargets()) {
                Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetVolumeId));
                if (targetVolume.getVirtualArray().equals(varrayToCheckURI)) {
                    alreadyProvisionedTarget = targetVolume;
                    break;
                }
            }
        }

        return alreadyProvisionedTarget;
    }

    /**
     * Helper method to retrieve all related volumes from a Source Volume
     *
     * @param sourceVolumeURI The source volume URI
     * @param dbClient DBClient
     * @param includeBackendVolumes Flag to optionally have backend volumes included (VPLEX)
     * @param includeJournalVolumes Flag to optionally have journal volumes included
     * @return All volumes related to the source volume
     */
    public static Set<Volume> getAllRelatedVolumesForSource(URI sourceVolumeURI, DbClient dbClient, boolean includeBackendVolumes,
            boolean includeJournalVolumes) {
        Set<Volume> allRelatedVolumes = new HashSet<Volume>();

        if (sourceVolumeURI != null) {
            Volume sourceVolume = dbClient.queryObject(Volume.class, sourceVolumeURI);

            if (sourceVolume != null
                    && NullColumnValueGetter.isNotNullValue(sourceVolume.getPersonality())
                    && sourceVolume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.name())) {
                allRelatedVolumes.add(sourceVolume);

                if (includeJournalVolumes) {
                    Volume primaryJournalVolume = dbClient.queryObject(Volume.class, sourceVolume.getRpJournalVolume());
                    allRelatedVolumes.add(primaryJournalVolume);

                    if (!NullColumnValueGetter.isNullURI(sourceVolume.getSecondaryRpJournalVolume())) {
                        Volume secondaryJournalVolume = dbClient.queryObject(Volume.class, sourceVolume.getSecondaryRpJournalVolume());
                        allRelatedVolumes.add(secondaryJournalVolume);
                    }
                }

                if (sourceVolume.getRpTargets() != null) {
                    for (String targetVolumeId : sourceVolume.getRpTargets()) {
                        Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetVolumeId));
                        allRelatedVolumes.add(targetVolume);

                        if (includeJournalVolumes) {
                            Volume targetJournalVolume = dbClient.queryObject(Volume.class, targetVolume.getRpJournalVolume());
                            allRelatedVolumes.add(targetJournalVolume);
                        }
                    }
                }

                List<Volume> allBackendVolumes = new ArrayList<Volume>();

                if (includeBackendVolumes) {
                    for (Volume volume : allRelatedVolumes) {
                        if (volume.getAssociatedVolumes() != null
                                && !volume.getAssociatedVolumes().isEmpty()) {
                            for (String associatedVolId : volume.getAssociatedVolumes()) {
                                Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                                allBackendVolumes.add(associatedVolume);
                            }
                        }
                    }
                }

                allRelatedVolumes.addAll(allBackendVolumes);
            }
        }

        return allRelatedVolumes;
    }

    /**
     * Determines if a volume is part of a MetroPoint configuration.
     *
     * @param volume the volume.
     * @return true if this is a MetroPoint volume, false otherwise.
     */
    public static boolean isVPlexVolume(Volume volume) {
        return (volume.getAssociatedVolumes() != null && !volume.getAssociatedVolumes().isEmpty());
    }

    /**
     * Rollback protection specific fields on the existing volume. This is normally invoked if there are
     * errors during a change vpool operation. We want to return the volume back to it's un-protected state
     * or in the case of upgrade to MP then to remove any MP features from the protected volume.
     *
     * One of the biggest motivations is to ensure that the old vpool is set back on the existing volume.
     *
     * @param volume Volume to remove protection from
     * @param oldVpool The old vpool, this the original vpool of the volume before trying to add protection
     * @param dbClient DBClient object
     */
    public static void rollbackProtectionOnVolume(Volume volume, VirtualPool oldVpool, DbClient dbClient) {
        // Rollback any RP specific changes to this volume
        if (volume.checkForRp()) {
            if (!VirtualPool.vPoolSpecifiesProtection(oldVpool)) {
                _log.info(String.format("Start rollback of RP protection changes for volume [%s] (%s)...",
                        volume.getLabel(), volume.getId()));
                // List of volume IDs to clean up from the ProtectionSet
                List<String> protectionSetVolumeIdsToRemove = new ArrayList<String>();
                protectionSetVolumeIdsToRemove.add(volume.getId().toString());

                // All source volumes in this CG
                List<Volume> cgSourceVolumes = getCgSourceVolumes(volume.getConsistencyGroup(), dbClient);
                // Only rollback the Journals if there is only one volume in the CG and it's the one we're
                // trying to roll back.
                boolean lastSourceVolumeInCG = (cgSourceVolumes != null && cgSourceVolumes.size() == 1
                        && cgSourceVolumes.get(0).getId().equals(volume.getId()));

                // Potentially rollback the journal volume
                if (!NullColumnValueGetter.isNullURI(volume.getRpJournalVolume())) {
                    if (lastSourceVolumeInCG) {
                        _log.info(String.format("Rolling back RP Journal (%s)", volume.getRpJournalVolume()));
                        protectionSetVolumeIdsToRemove.add(volume.getRpJournalVolume().toString());
                        rollbackVolume(volume.getRpJournalVolume(), dbClient);
                    }
                }
                // Potentially rollback the standby journal volume
                if (!NullColumnValueGetter.isNullURI(volume.getSecondaryRpJournalVolume())) {
                    if (lastSourceVolumeInCG) {
                        _log.info(String.format("Rolling back RP Journal (%s)", volume.getSecondaryRpJournalVolume()));
                        protectionSetVolumeIdsToRemove.add(volume.getSecondaryRpJournalVolume().toString());
                        rollbackVolume(volume.getSecondaryRpJournalVolume(), dbClient);
                    }
                }

                // Set the old vpool back on the volume
                _log.info(String.format("Resetting Vpool on volume from (%s) back to it's original vpool (%s)",
                        volume.getVirtualPool(), oldVpool.getId()));
                volume.setVirtualPool(oldVpool.getId());

                // Null out any RP specific fields on the volume
                volume.setRpJournalVolume(NullColumnValueGetter.getNullURI());
                volume.setSecondaryRpJournalVolume(NullColumnValueGetter.getNullURI());
                volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                volume.setPersonality(NullColumnValueGetter.getNullStr());
                volume.setProtectionController(NullColumnValueGetter.getNullURI());
                volume.setRSetName(NullColumnValueGetter.getNullStr());
                volume.setInternalSiteName(NullColumnValueGetter.getNullStr());
                volume.setRpCopyName(NullColumnValueGetter.getNullStr());

                StringSet resetRpTargets = volume.getRpTargets();
                if (resetRpTargets != null) {
                    // Rollback any target volumes that were created
                    for (String rpTargetId : resetRpTargets) {
                        protectionSetVolumeIdsToRemove.add(rpTargetId);
                        Volume targetVol = rollbackVolume(URI.create(rpTargetId), dbClient);
                        // Rollback any target journal volumes that were created
                        if (targetVol != null && !NullColumnValueGetter.isNullURI(targetVol.getRpJournalVolume())) {
                            if (lastSourceVolumeInCG) {
                                protectionSetVolumeIdsToRemove.add(targetVol.getRpJournalVolume().toString());
                                rollbackVolume(targetVol.getRpJournalVolume(), dbClient);
                            }
                        }
                    }
                    resetRpTargets.clear();
                    volume.setRpTargets(resetRpTargets);
                }

                // Clean up the Protection Set
                if (!NullColumnValueGetter.isNullNamedURI(volume.getProtectionSet())) {
                    ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                    if (protectionSet != null) {
                        // Remove volume IDs from the Protection Set
                        protectionSet.getVolumes().removeAll(protectionSetVolumeIdsToRemove);

                        _log.info(String.format("Removing the following volumes from Protection Set [%s] (%s): %s",
                                protectionSet.getLabel(), protectionSet.getId(), Joiner.on(',').join(protectionSetVolumeIdsToRemove)));

                        // If the Protection Set is empty, we can safely set it to
                        // inactive.
                        if (lastSourceVolumeInCG) {
                            _log.info(String.format("Setting Protection Set [%s] (%s) to inactive",
                                    protectionSet.getLabel(), protectionSet.getId()));
                            protectionSet.setInactive(true);
                        }

                        dbClient.persistObject(protectionSet);
                    }
                }

                volume.setProtectionSet(NullColumnValueGetter.getNullNamedURI());
            } else {
                _log.info(String.format("Rollback changes for existing protected RP volume [%s]...", volume.getLabel()));

                _log.info("Rollback the secondary journal");
                // Rollback the secondary journal volume if it was created
                volume.setSecondaryRpJournalVolume(NullColumnValueGetter.getNullURI());
                if (!NullColumnValueGetter.isNullURI(volume.getSecondaryRpJournalVolume())) {
                    rollbackVolume(volume.getSecondaryRpJournalVolume(), dbClient);
                }
                volume.setSecondaryRpJournalVolume(NullColumnValueGetter.getNullURI());

                // Clean up the Protection Set
                if (!NullColumnValueGetter.isNullNamedURI(volume.getProtectionSet())) {
                    ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());
                    if (protectionSet != null) {
                        // Remove volume ID from the Protection Set
                        protectionSet.getVolumes().remove(volume.getSecondaryRpJournalVolume().toString());
                        dbClient.persistObject(protectionSet);
                    }
                }

                // remove consistency group from volume
                if (!NullColumnValueGetter.isNullURI(volume.getConsistencyGroup())) {
                    volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                }
            }

            _log.info(String.format("Rollback of RP protection changes for volume [%s] (%s) has completed.", volume.getLabel(),
                    volume.getId()));
            dbClient.persistObject(volume);
        }
    }

    /**
     * Cassandra level rollback of a volume. We set the volume to inactive and rename
     * the volume to indicate that rollback has occured. We do this so as to not
     * prevent subsequent use of the same volume name in the case of rollback/error.
     *
     * @param volumeURI URI of the volume to rollback
     * @param dbClient DBClient Object
     * @return The rolled back volume
     */
    public static Volume rollbackVolume(URI volumeURI, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        if (volume != null && !volume.getInactive()) {
            _log.info(String.format("Rollback volume [%s]...", volume.getLabel()));
            volume.setInactive(true);
            volume.setLabel(volume.getLabel() + "-ROLLBACK-" + Math.random());
            volume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
            dbClient.persistObject(volume);

            // Rollback any VPLEX backing volumes too
            if (volume.getAssociatedVolumes() != null
                    && !volume.getAssociatedVolumes().isEmpty()) {
                for (String associatedVolId : volume.getAssociatedVolumes()) {
                    Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                    if (associatedVolume != null && !associatedVolume.getInactive()) {
                        _log.info(String.format("Rollback volume [%s]...", associatedVolume.getLabel()));
                        associatedVolume.setInactive(true);
                        associatedVolume.setLabel(volume.getLabel() + "-ROLLBACK-" + Math.random());
                        dbClient.persistObject(associatedVolume);
                    }
                }
            }
        }

        return volume;
    }

    /**
     * returns the list of journal volumes for one site
     *
     * If this is a CDP volume, journal volumes from both the production and target copies are returned
     *
     * @param varray
     * @param consistencyGroup
     * @return
     */
    private List<Volume> getJournalVolumesForSite(VirtualArray varray, BlockConsistencyGroup consistencyGroup) {
        List<Volume> journalVols = new ArrayList<Volume>();
        List<Volume> volsInCg = getCgVolumes(consistencyGroup.getId(), _dbClient);
        if (volsInCg != null) {
            for (Volume volInCg : volsInCg) {
                if (Volume.PersonalityTypes.METADATA.toString().equals(volInCg.getPersonality())
                        && !NullColumnValueGetter.isNullURI(volInCg.getVirtualArray()) && volInCg.getVirtualArray().equals(varray.getId())) {
                    journalVols.add(volInCg);
                }
            }
        }
        return journalVols;
    }

    /**
     * returns a unique journal volume name by evaluating all journal volumes for the copy and increasing the count journal volume name is
     * in the form varrayName-cgname-journal-[count]
     *
     * @param varray
     * @param consistencyGroup
     * @return a journal name unique within the site
     */
    public String createJournalVolumeName(VirtualArray varray, BlockConsistencyGroup consistencyGroup) {
        String journalPrefix = new StringBuilder(varray.getLabel()).append(VOL_DELIMITER).append(consistencyGroup.getLabel())
                .append(VOL_DELIMITER)
                .append(JOURNAL).toString();
        List<Volume> existingJournals = getJournalVolumesForSite(varray, consistencyGroup);

        // filter out old style journal volumes
        // new style journal volumes are named with the virtual array as the first component
        List<Volume> newStyleJournals = new ArrayList<Volume>();
        for (Volume journalVol : existingJournals) {
            String volName = journalVol.getLabel();
            if (volName.substring(0, journalPrefix.length()).equals(journalPrefix)) {
                newStyleJournals.add(journalVol);
            }
        }

        // calculate the largest index
        int largest = 0;
        for (Volume journalVol : newStyleJournals) {
            String[] parts = StringUtils.split(journalVol.getLabel(), VOL_DELIMITER);
            try {
                int idx = Integer.parseInt(parts[parts.length - 1]);
                if (idx > largest) {
                    largest = idx;
                }
            } catch (NumberFormatException e) {
                // this is not an error; just means the name is not in the standard format
                continue;
            }
        }

        String journalName = new StringBuilder(journalPrefix).append(VOL_DELIMITER).append(Integer.toString(largest + 1)).toString();

        return journalName;
    }

    /**
     * Determine the wwn of the volume in the format RP is looking for. For xtremio
     * this is the 128 bit identifier. For other array types it is the deafault.
     *
     * @param volumeURI the URI of the volume the operation is being performed on
     * @param dbClient
     * @return the wwn of the volume which rp requires to perform the operation
     *         in the case of xtremio this is the 128 bit identifier
     */
    public static String getRPWWn(URI volumeURI, DbClient dbClient) {
        Volume volume = dbClient.queryObject(Volume.class, volumeURI);
        if (volume.getNativeGuid() != null && RecoverPointUtils.isXioVolume(volume.getNativeGuid())) {
            return RecoverPointUtils.getXioNativeGuid(volume.getNativeGuid());
        }
        return volume.getWWN();
    }

    /**
     * Determine if the volume being protected is provisioned on an Xtremio Storage array
     *
     * @param volume The volume being provisioned
     * @param dbClient DBClient object
     * @return boolean indicating if the volume being protected is provisioned on an Xtremio Storage array
     */
    public static boolean protectXtremioVolume(Volume volume, DbClient dbClient) {
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, volume.getStorageController());
        if (storageSystem.getSystemType() != null && storageSystem.getSystemType().equalsIgnoreCase(Type.xtremio.toString())) {
            return true;
        }
        return false;
    }

    /**
     * Returns a set of all RP ports as their related Initiator URIs.
     * 
     * @param dbClient - database client instance
     * @return a Set of Initiator URIs
     */
    public static Set<URI> getBackendPortInitiators(DbClient dbClient) {
        _log.info("Finding backend port initiators for all RP systems");
        Set<URI> initiators = new HashSet<URI>();
        
        List<URI> rpSystemUris = dbClient.queryByType(ProtectionSystem.class, true);
        List<ProtectionSystem> rpSystems = dbClient.queryObject(ProtectionSystem.class, rpSystemUris);
        for (ProtectionSystem rpSystem : rpSystems ) {
            for (Entry<String, AbstractChangeTrackingSet<String>> rpSitePorts : rpSystem.getSiteInitiators().entrySet()) {
                for (String port : rpSitePorts.getValue()) {
                    Initiator initiator = ExportUtils.getInitiator(port, dbClient);
                    if (initiator != null) {
                        // Review: OK to reduce to debug level
                        _log.info("Adding initiator " + initiator.getId() + " with port: " + port);
                        initiators.add(initiator.getId());
                    }
                }
            }
        }
        return initiators;
    }    
    
    /**
     * Fetch the RP Protected target virtual pool uris.
     * 
     * @return set of vpools that are RP target virtual pools
     */
    public static Set<URI> fetchRPTargetVirtualPools(DbClient dbClient) {
        Set<URI> rpProtectedTargetVPools = new HashSet<URI>();
        try {
            List<URI> vpoolProtectionSettingsURIs = dbClient.queryByType(VpoolProtectionVarraySettings.class,
                    true);
            Iterator<VpoolProtectionVarraySettings> vPoolProtectionSettingsItr = dbClient
                    .queryIterativeObjects(VpoolProtectionVarraySettings.class, vpoolProtectionSettingsURIs,
                            true);
            while (vPoolProtectionSettingsItr.hasNext()) {
                VpoolProtectionVarraySettings rSetting = vPoolProtectionSettingsItr.next();
                if (null != rSetting && !NullColumnValueGetter.isNullURI(rSetting.getVirtualPool())) {
                    rpProtectedTargetVPools.add(rSetting.getVirtualPool());
                }

            }
        } catch (Exception ex) {
            _log.error("Exception occurred while fetching RP enabled virtualpools", ex);
        }
        return rpProtectedTargetVPools;
    }
}
