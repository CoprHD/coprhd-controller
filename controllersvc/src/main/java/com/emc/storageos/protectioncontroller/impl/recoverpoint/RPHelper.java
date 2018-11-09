/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller.impl.recoverpoint;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getRpSourceVolumeByTarget;
import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByAssociatedId;
import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import com.emc.storageos.blockorchestrationcontroller.VolumeDescriptor;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.Project;
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
import com.emc.storageos.db.client.model.Volume.VolumeAccessState;
import com.emc.storageos.db.client.model.VpoolProtectionVarraySettings;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.SizeUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.model.block.Copy;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.responses.GetBookmarksResponse;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse;
import com.emc.storageos.recoverpoint.responses.GetRSetResponse;
import com.emc.storageos.recoverpoint.responses.GetVolumeResponse;
import com.emc.storageos.recoverpoint.utils.RecoverPointClientFactory;
import com.emc.storageos.recoverpoint.utils.RecoverPointUtils;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.smis.MetaVolumeRecommendation;
import com.emc.storageos.volumecontroller.impl.utils.MetaVolumeUtils;
import com.google.common.base.Joiner;

/**
 * RecoverPoint specific helper bean
 */
public class RPHelper {

    private static final String VOL_DELIMITER = "-";
    private static final double RP_DEFAULT_JOURNAL_POLICY = 0.25;
    public static final String REMOTE = "remote";
    public static final String LOCAL = "local";
    public static final String SOURCE = "source";
    public static final String TARGET = "target";
    public static final String JOURNAL = "journal";
    public static final Long DEFAULT_RP_JOURNAL_SIZE_IN_BYTES = 10737418240L; // default minimum journal size is 10GB (in bytes)
   
    private static final Logger _log = LoggerFactory.getLogger(RPHelper.class);

    private static final String HTTPS = "https";
    private static final String WSDL = "wsdl";
    private static final String RP_ENDPOINT = "/fapi/version4_1";

    private static final String LOG_MSG_OPERATION_TYPE_DELETE = "delete";
    private static final String LOG_MSG_OPERATION_TYPE_REMOVE_PROTECTION = "remove protection from";
    private static final String LOG_MSG_VOLUME_TYPE_RP = "RP_SOURCE";
    private static final String LOG_MSG_VOLUME_TYPE_RPVPLEX = "RP_VPLEX_VIRT_SOURCE";

    public static final String REMOVE_PROTECTION = "REMOVE_PROTECTION";

    /**
     * Get all of the replication set volumes for a list of volumes; the sources and all of their targets.
     *
     * @param volumeIds List of volumes to find all volume for their RSets
     * @param dbClient DbClient ref
     * @return all RSet volumes
     */
    public static Set<URI> getReplicationSetVolumes(List<URI> volumeIds, DbClient dbClient) {
        Set<URI> volumeSet = new HashSet<URI>();
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeIds);
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            volumeSet.addAll(getReplicationSetVolumes(volume, dbClient));
        }
        return volumeSet;
    }

    /**
     * Get all of the volumes in this replication set; the source and all of its targets.
     * For a multi-CG protection, it only returns the targets (and source) associated with this one volume.
     *
     * @param volume volume object
     * @param dbClient DbClient ref
     * @return list of volume URIs
     * @throws DeviceControllerException
     */
    public static List<URI> getReplicationSetVolumes(Volume volume, DbClient dbClient) throws DeviceControllerException {

        if (volume == null) {
            throw DeviceControllerException.exceptions.invalidObjectNull();
        }

        List<URI> volumeIDs = new ArrayList<URI>();
        for (Volume vol : getVolumesInRSet(volume, dbClient)) {
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
     * @param dbClient DbClient ref
     * @return the stored protection settings object
     * @throws InternalException
     */
    public static VpoolProtectionVarraySettings getProtectionSettings(VirtualPool vpool, VirtualArray varray, DbClient dbClient) throws InternalException {
        if (vpool.getProtectionVarraySettings() != null) {
            String settingsID = vpool.getProtectionVarraySettings().get(varray.getId().toString());
            try {
                return (dbClient.queryObject(VpoolProtectionVarraySettings.class, URI.create(settingsID)));
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
     * @param dbClient DbClient ref
     * @param srcVpool the base virtual pool
     * @return vpool of the target copy
     */
    public static VirtualPool getTargetVirtualPool(VirtualArray tgtVarray, VirtualPool srcVpool, DbClient dbClient) {
        VpoolProtectionVarraySettings settings = getProtectionSettings(srcVpool, tgtVarray, dbClient);
        // If there was no vpool specified use the source vpool for this varray.
        VirtualPool tgtVpool = srcVpool;
        if (settings.getVirtualPool() != null) {
            tgtVpool = dbClient.queryObject(VirtualPool.class, settings.getVirtualPool());
        }
        
        return tgtVpool;
    }

    /**
     * Given one volume in an rset (either source or any target) return all source and target volumes in that rset
     *
     * @param vol A volume in the Rset
     * @param dbClient DbClient ref
     * @return All RSet volumes
     */
    private static List<Volume> getVolumesInRSet(Volume volume, DbClient dbClient) {
        List<Volume> allVolumesInRSet = new ArrayList<Volume>();

        Volume sourceVol = null;
        if (Volume.PersonalityTypes.SOURCE.name().equalsIgnoreCase(volume.getPersonality())) {
            sourceVol = volume;
        } else {
            sourceVol = getRPSourceVolumeFromTarget(dbClient, volume);
        }

        if (sourceVol != null) {
            allVolumesInRSet.add(sourceVol);

            if (sourceVol.getRpTargets() != null) {
                for (String tgtVolId : sourceVol.getRpTargets()) {
                    if (tgtVolId.equals(volume.getId().toString())) {
                        allVolumesInRSet.add(volume);
                    } else {
                        Volume tgt = dbClient.queryObject(Volume.class, URI.create(tgtVolId));
                        if (tgt != null && !tgt.getInactive()) {
                            allVolumesInRSet.add(tgt);
                        }

                        // if this target was previously the Metropoint active source, go out and get the standby copy
                        if (tgt != null && RPHelper.isMetroPointVolume(dbClient, tgt)) {
                            allVolumesInRSet.addAll(getMetropointStandbyCopies(tgt, dbClient));
                        }
                    }
                }
            }
        } else if (volume.checkInternalFlags(Flag.PARTIALLY_INGESTED)) {
            allVolumesInRSet.add(volume);
        }

        return allVolumesInRSet;
    }

    /**
     * Gets a volume's associated target volumes.
     *
     * @param volume the volume whose targets we want to find.
     * @param dbClient DbClient ref
     * @return the list of associated target volumes.
     */
    public static List<Volume> getTargetVolumes(Volume volume, DbClient dbClient) {
        List<Volume> targets = new ArrayList<Volume>();

        if (volume != null && PersonalityTypes.SOURCE.name().equals(volume.getPersonality())) {
            List<Volume> rsetVolumes = getVolumesInRSet(volume, dbClient);

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
     * @param dbClient DbClient
     * @return list of volumes to unexport and delete
     * @throws InternalException
     * @throws URISyntaxException
     */
    public static Set<URI> getVolumesToDelete(Collection<URI> reqDeleteVolumes, DbClient dbClient) throws InternalException {
        _log.info(String.format("Getting all RP volumes to delete for requested list: %s", reqDeleteVolumes));

        Set<URI> volumeIDs = new HashSet<URI>();
        Set<URI> protectionSetIds = new HashSet<URI>();

        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, reqDeleteVolumes, true);

        // Divide the RP volumes by BlockConsistencyGroup so we can determine if all volumes in the
        // RP consistency group are being removed.
        Map<URI, Set<URI>> cgsToVolumesForDelete = new HashMap<URI, Set<URI>>();

        // for each volume requested to be deleted, add that volume plus any source or target related
        // to that volume to the list of volumes to be deleted
        while (volumes.hasNext()) {
            Volume volume = volumes.next();
            // get the list of all source and target volumes in the same replication set as the
            // volume passed in
            List<Volume> allVolsInRSet = getVolumesInRSet(volume, dbClient);
            List<URI> allVolsInRSetURI = new ArrayList<URI>();
            URI cgURI = null;

            // Loop through the replication set volumes to:
            // 1. Determine the consistency group.
            // 2. Keep track of the protection set if one is being referenced. This will be used
            // later to perform a cleanup operation.
            // 3. If partially ingested volume, clean up corresponding unmanaged protection set
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
                // If this is a partially ingested RP volume, clean up the corresponding unmanaged protection set
                List<UnManagedProtectionSet> umpsets = CustomQueryUtility.getUnManagedProtectionSetByManagedVolumeId(dbClient,
                        vol.getId().toString());
                for (UnManagedProtectionSet umpset : umpsets) {
                    umpset.getManagedVolumeIds().remove(vol.getId().toString());
                    // Clean up the volume's reference, if any, in the unmanaged volumes associated with the unmanaged protection set
                    for (String umv : umpset.getUnManagedVolumeIds()) {
                        UnManagedVolume umVolume = dbClient.queryObject(UnManagedVolume.class, URI.create(umv));
                        StringSet rpManagedSourceVolumeInfo = umVolume.getVolumeInformation()
                                .get(SupportedVolumeInformation.RP_MANAGED_SOURCE_VOLUME.toString());
                        StringSet rpManagedTargetVolumeInfo = umVolume.getVolumeInformation()
                                .get(SupportedVolumeInformation.RP_MANAGED_TARGET_VOLUMES.toString());
                        if (rpManagedSourceVolumeInfo != null && !rpManagedSourceVolumeInfo.isEmpty()) {
                            rpManagedSourceVolumeInfo.remove(vol.getId().toString());
                        }

                        if (rpManagedTargetVolumeInfo != null && !rpManagedTargetVolumeInfo.isEmpty()) {
                            rpManagedTargetVolumeInfo.remove(vol.getId().toString());
                        }
                        dbClient.updateObject(umVolume);
                    }
                    dbClient.updateObject(umpset);
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

        // Determine if we're deleting all of the volumes in this consistency group
        for (Map.Entry<URI, Set<URI>> cgToVolumesForDelete : cgsToVolumesForDelete.entrySet()) {
            BlockConsistencyGroup cg = null;
            URI cgURI = cgToVolumesForDelete.getKey();
            cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            List<Volume> cgVolumes = getAllCgVolumes(cgURI, dbClient);

            // determine if all of the source and target volumes in the consistency group are on the list
            // of volumes to delete; if so, we will add the journal volumes to the list.
            // also create a list of stale volumes to be removed from the protection set
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
                // We are removing the CG, determine all the journal volumes in it and
                // add them to the list of volumes to be removed
                if (cg != null) {
                    List<Volume> allJournals = getCgVolumes(dbClient, cg.getId(), Volume.PersonalityTypes.METADATA.toString());
                    if (allJournals != null && !allJournals.isEmpty()) {
                        Set<URI> allJournalURIs = new HashSet<URI>();
                        for (Volume journalVolume : allJournals) {
                            allJournalURIs.add(journalVolume.getId());
                        }
                        _log.info(String
                                .format("Determined that this is a request to delete consistency group %s.  Adding journal volumes to the list of volumes to delete: %s",
                                        cgURI, allJournalURIs.toString()));
                        volumeIDs.addAll(allJournalURIs);
                    }
                } else {
                    _log.info(String.format(
                            "Could not determine journal volumes for consistency group %s .",
                            cgToVolumesForDelete.getKey()));
                }
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
            ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, protSetId);

            if (protectionSet.getVolumes() != null) {
                for (String protSetVol : protectionSet.getVolumes()) {
                    URI protSetVolUri = URI.create(protSetVol);
                    if (!volumeIDs.contains(protSetVolUri)) {
                        Volume vol = dbClient.queryObject(Volume.class, protSetVolUri);
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
                dbClient.updateObject(protectionSet);
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
     * @param dbClient DbClient ref
     * @return All descriptors needed to clean up volumes
     */
    public static List<VolumeDescriptor> getDescriptorsForVolumesToBeDeleted(URI systemURI,
            List<URI> volumeURIs, String deletionType, VirtualPool newVpool, DbClient dbClient) {
        List<VolumeDescriptor> volumeDescriptors = new ArrayList<VolumeDescriptor>();
        try {
            Set<URI> allVolumeIds = getVolumesToDelete(volumeURIs, dbClient);

            for (URI volumeURI : allVolumeIds) {
                Volume volume = dbClient.queryObject(Volume.class, volumeURI);
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
                        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID, newVpool.getId());
                        descriptor.setParameters(volumeParams);
                    }

                    _log.info(String.format("Adding %s descriptor to %s%s volume [%s] (%s)",
                            volumeType, operationType,
                            (volumeType.equals(LOG_MSG_VOLUME_TYPE_RP) ? "" : " virtual"),
                            volume.getLabel(), volume.getId()));
                    volumeDescriptors.add(descriptor);
                }

                // If this is a virtual volume, add a descriptor for the virtual volume
                if (RPHelper.isVPlexVolume(volume, dbClient)) {
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
                        Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolumeId));
                        // a previous failed delete may have already removed associated volumes
                        if (associatedVolume != null && !associatedVolume.getInactive()) {
                            descriptor = new VolumeDescriptor(VolumeDescriptor.Type.BLOCK_DATA, associatedVolume.getStorageController(),
                                    associatedVolume.getId(), associatedVolume.getPool(), associatedVolume.getConsistencyGroup(), null);
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
                        volumeParams.put(VolumeDescriptor.PARAM_VPOOL_CHANGE_NEW_VPOOL_ID, newVpool.getId());
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
     * Get all the target volumes in the consistency group for specified target virtual array.
     *
     * @param dbClient the database client
     * @param consistencyGroup the consistency group id
     * @param virtualArray target virtual array
     * @return Volume of the target
     */
    public static List<Volume> getTargetVolumesForVarray(DbClient dbClient, URI consistencyGroup, URI virtualArray) {
        List<Volume> targetVarrayVolumes = new ArrayList<Volume>();
        List<Volume> cgTargetVolumes = getCgVolumes(dbClient, consistencyGroup, PersonalityTypes.TARGET.name());

        if (cgTargetVolumes != null) {
            for (Volume target : cgTargetVolumes) {
                if (target.getVirtualArray().equals(virtualArray)) {
                    targetVarrayVolumes.add(target);
                }
            }
        }

        return targetVarrayVolumes;
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
     * Gets the associated source volume given any type of RP volume. If a source volume
     * is given, that volume is returned. For a target journal volume, the associated target
     * volume is found and then its source volume is found and returned.
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
                _log.info("Journal volume found, there is no associated RP source so just return null.");
                return sourceVolume;
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
     * @param network Network to check
     * @param virtualArray varray to check against
     * @return true is the network is connected to the varray, false otherwise
     */
    public static boolean isNetworkConnectedToVarray(NetworkLite network, VirtualArray virtualArray) {
        if (network != null && network.getConnectedVirtualArrays() != null
                && network.getConnectedVirtualArrays().contains(String.valueOf(virtualArray.getId()))) {
            return true;
        }
        
        return false;
    }

    /**
     * Check if initiator being added to export-group is good.
     *
     * @param exportGroup EG to check
     * @param initiator Initiator to validate
     * @param dbClient DbClient
     * @return true if the Initiator is valid, false otherwise
     * @throws InternalException
     */
    public static boolean isInitiatorInVarray(VirtualArray varray, String wwn, DbClient dbClient) throws InternalException {
        // Get the networks assigned to the virtual array.
        List<Network> networks = CustomQueryUtility.queryActiveResourcesByRelation(
                dbClient, varray.getId(), Network.class, "connectedVirtualArrays");

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
     * @param dbClient DbClient ref
     * @return true if the networks containing the RP site initiators contains valid storage ports, false othwerwise
     * @throws InternalException
     */
    public static boolean rpInitiatorsInStorageConnectedNetwork(URI storageSystemURI, URI protectionSystemURI, String siteId, URI varrayURI, DbClient dbClient)
            throws InternalException {
        // Determine what network the StorageSystem is part of and verify that the RP site initiators
        // are part of that network.
        // Then get the front end ports on the Storage array.
        Map<URI, List<StoragePort>> arrayTargetMap = ConnectivityUtil.getStoragePortsOfTypeAndVArray(dbClient,
                storageSystemURI, StoragePort.PortType.frontend, varrayURI);
        Set<URI> arrayTargetNetworks = new HashSet<URI>();
        arrayTargetNetworks.addAll(arrayTargetMap.keySet());

        ProtectionSystem protectionSystem = dbClient.queryObject(ProtectionSystem.class, protectionSystemURI);
        StringSet siteInitiators = protectionSystem.getSiteInitiators().get(siteId);

        // Build a List of RP site initiator networks
        Set<URI> rpSiteInitiatorNetworks = new HashSet<URI>();
        for (String wwn : siteInitiators) {
            NetworkLite rpSiteInitiatorNetwork = NetworkUtil.getEndpointNetworkLite(wwn, dbClient);
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
     * @param dbClient DbClient ref
     * @return true if the storage system has active RP volumes under management. false otherwise.
     */
    public static boolean containsActiveRpVolumes(URI id, DbClient dbClient) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(id), result);
        Iterator<URI> volumeUriItr = result.iterator();

        while (volumeUriItr.hasNext()) {
            Volume volume = dbClient.queryObject(Volume.class, volumeUriItr.next());
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
     * @param dbClient DbClient ref
     * @return potential provisioned capacity
     */
    public static Long computeVmaxVolumeProvisionedCapacity(long requestedSize,
            Volume volume, StorageSystem storageSystem, DbClient dbClient) {
        Long vmaxPotentialProvisionedCapacity = 0L;
        StoragePool expandVolumePool = dbClient.queryObject(StoragePool.class, volume.getPool());
        long metaMemberSize = volume.getIsComposite() ? volume.getMetaMemberSize() : volume.getCapacity();
        long metaCapacity = volume.getIsComposite() ? volume.getTotalMetaMemberCapacity() : volume.getCapacity();
        MetaVolumeRecommendation metaRecommendation = MetaVolumeUtils.getExpandRecommendation(storageSystem, expandVolumePool,
                metaCapacity, requestedSize, metaMemberSize, volume.getThinlyProvisioned(),
                dbClient.queryObject(VirtualPool.class, volume.getVirtualPool()).getFastExpansion());

        if (metaRecommendation.isCreateMetaVolumes()) {
            long metaMemberCount = volume.getIsComposite() ? metaRecommendation.getMetaMemberCount() + volume.getMetaMemberCount()
                    : metaRecommendation.getMetaMemberCount() + 1;
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
    public static boolean isRPSource(VolumeDescriptor volumeDescriptor) {
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
    public static boolean isRPTarget(VolumeDescriptor volumeDescriptor) {
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
     * @param dbClient DbClient reference
     * @param volume the volume.
     * @return true if this is a MetroPoint volume, false otherwise.
     */
    public static boolean isMetroPointVolume(DbClient dbClient, Volume volume) {
        if (volume != null) {
            VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());
            if (vpool != null && VirtualPool.vPoolSpecifiesMetroPoint(vpool)) {
                _log.info(String.format("Volume's vpool [%s](%s) specifies Metropoint", vpool.getLabel(), vpool.getId()));
                return true;
            }
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
    public static boolean isProductionJournal(Set<String> productionCopies, Volume volume) {
        for (String productionCopy : productionCopies) {
            if (productionCopy.equalsIgnoreCase(volume.getRpCopyName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets a list of RecoverPoint consistency group volumes.
     *
     * @param blockConsistencyGroupUri The CG to check
     * @param dbClient The dbClient instance
     * @return List of volumes in the CG
     */
    public static List<Volume> getAllCgVolumes(URI blockConsistencyGroupUri, DbClient dbClient) {
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
        List<Volume> cgVolumes = getAllCgVolumes(blockConsistencyGroupUri, dbClient);

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
     * Filters the list of volumes by source or target site; site is defined by a varray
     *
     * @param varrayId varray to determine site
     * @param vpoolId vpool of the volumes
     * @param volumes volumes to filter
     * @return list of filtered volumes by source or target site
     */
    public static List<Volume> getVolumesForSite(URI varrayId, URI vpoolId, Collection<Volume> volumes) {

        List<Volume> volumesForSite = new ArrayList<Volume>();

        String personality = null;
        for (Volume volume : volumes) {
            if (varrayId != null) {
                if (vpoolId != null) {
                    // for CDP volumes we need both varray and vpool to identify source or target
                    if (volume.getVirtualArray().equals(varrayId) && volume.getVirtualPool().equals(vpoolId)) {
                        volumesForSite.add(volume);
                        personality = volume.getPersonality();
                    }
                } else if (volume.getVirtualArray().equals(varrayId)) {
                    // check the first volume and include all source volumes
                    volumesForSite.add(volume);
                    personality = volume.getPersonality();
                }
            } else if (NullColumnValueGetter.isNotNullValue(volume.getPersonality())
                    && volume.getPersonality().equals(Volume.PersonalityTypes.SOURCE.name())) {
                volumesForSite.add(volume);
                personality = volume.getPersonality();
            }
        }

        // if the personality is source, include all source volumes including those not matching the passed in varray
        if (Volume.PersonalityTypes.SOURCE.toString().equals(personality)) {
            for (Volume volume : volumes) {
                if (Volume.PersonalityTypes.SOURCE.toString().equals(volume.getPersonality())
                        && !volume.getVirtualArray().equals(varrayId)) {
                    volumesForSite.add(volume);
                }
            }
        }

        return volumesForSite;
    }

    /**
     * Gets all the volumes of the specified personality type in RecoverPoint consistency group.
     *
     * @param dbClient
     *            The dbClient instance
     * @param blockConsistencyGroupUri
     *            The CG to check
     * @param personality
     *            The personality of the volumes to filter with
     * @return All Source volumes in the CG
     */
    public static List<Volume> getCgVolumes(DbClient dbClient, URI blockConsistencyGroupUri, String personality) {
        List<Volume> cgPersonalityVolumes = new ArrayList<Volume>();
        List<Volume> cgVolumes = getAllCgVolumes(blockConsistencyGroupUri, dbClient);

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
     * Queries the CG to find all the Journals for a specific RP Copy, returns the matching journals
     * sorted from largest to smallest.
     *
     * @param dbClient DbClient reference
     * @param cgURI URI of the CG to query
     * @param rpCopyName Either a valid RP copy name.
     * @return Existing matching journals for the copy or internal site sorted from largest to smallest
     */
    public static List<Volume> findExistingJournalsForCopy(DbClient dbClient, URI cgURI, String rpCopyName) {
        // Return as a list for easy consumption
        List<Volume> matchingJournals = new ArrayList<Volume>();

        // Ensure we have been passed valid arguments
        if (dbClient == null || cgURI == null || rpCopyName == null) {
            return matchingJournals;
        }

        // Get all journals for this CG
        List<Volume> cgJournalVolumes = getCgVolumes(dbClient, cgURI, Volume.PersonalityTypes.METADATA.name());

        // Filter journals based on the RP copy name.
        if (cgJournalVolumes != null && !cgJournalVolumes.isEmpty()) {
            for (Volume cgJournalVolume : cgJournalVolumes) {
                boolean copyNamesMatch = (NullColumnValueGetter.isNotNullValue(cgJournalVolume.getRpCopyName())
                        && cgJournalVolume.getRpCopyName().equals(rpCopyName));                
                if (copyNamesMatch) {
                    matchingJournals.add(cgJournalVolume);
                }
            }
        } else {
            _log.info(String.format("No journals found for RP CG [%s].", cgURI));
            return matchingJournals;
        }

        if (!matchingJournals.isEmpty()) {
            // Sort the journals by capacity
            Collections.sort(matchingJournals, new Comparator<Volume>() {
                @Override
                public int compare(Volume v1, Volume v2) {
                    Long v1Capacity = (v1.getProvisionedCapacity() > 0L ? v1.getProvisionedCapacity() : v1.getCapacity());
                    Long v2Capacity = (v2.getProvisionedCapacity() > 0L ? v2.getProvisionedCapacity() : v2.getCapacity());
                    return Long.compare(v1Capacity, v2Capacity);
                }
            });
        } else {
            _log.info(String.format("No existing journals found for RP Copy [%s].", rpCopyName));
        }

        return matchingJournals;
    }

    /**
     * Determines if an additional journal is required for this RP Copy.
     *
     * @param journalPolicy The current journal policy
     * @param cgURI The ViPR CG URI
     * @param size The size requested
     * @param volumeCount Number of volumes in the request
     * @param copyName The RP copy name
     * @param dbClient DbClient reference
     * 
     * @return a map which will contain the journal count and size if an additional journal is required, null otherwise.
     */
    public static Map<Integer, Long> additionalJournalRequiredForRPCopy(String journalPolicy, URI cgURI,
            long size, Integer volumeCount, String copyName, DbClient dbClient) {
        Map<Integer, Long> additionalJournalInfo = null;                       
        
        _log.info("Running check for additionalJournalRequiredForRPCopy()...");
        
        StringBuffer logMsg = new StringBuffer();
        logMsg.append(String.format("\nChecking if additional journal(s) required for RP Copy [%s] in RP CG [%s]\n",
                copyName, cgURI.toString()));
        logMsg.append("--------------------------------------\n");
        
        if (journalPolicy != null && (journalPolicy.endsWith("x") || journalPolicy.endsWith("X"))) {
            // Find all the journals for this RP Copy, calculate their total size in bytes, 
            // and while we're at it keep track of the minimum sized journal.
            // We use the minimum size as RecoverPoint prefers striping across all journals. 
            List<Volume> journalVolumesForCopy = RPHelper.findExistingJournalsForCopy(dbClient, cgURI, copyName);
            Long minJournalSizeForCopy = 0L;
            Long totalJournalSizeForCopy = 0L;         
            for (Volume journalVolume : journalVolumesForCopy) {
                // If the journal volume is VPLEX get its backing volume instead.
                if (RPHelper.isVPlexVolume(journalVolume, dbClient)) {                    
                    journalVolume = VPlexUtil.getVPLEXBackendVolume(journalVolume, true, dbClient);
                }
              
                // Prefer provisioned capacity, but in some cases like concurrent orders 
                // the volume may not have been provisioned just yet.
                Long journalSize = (journalVolume.getProvisionedCapacity() > 0L ? 
                        journalVolume.getProvisionedCapacity() : journalVolume.getCapacity());
                
                // Keep track of the minimum sized journal to allow for striping across all journals. 
                // However, note that best practice is to have uniform sized journals.                
                Long journalSizeInBytes = SizeUtil.translateSize(String.valueOf(journalSize));              
                if ((minJournalSizeForCopy == 0L) || (journalSizeInBytes < minJournalSizeForCopy)) {
                    minJournalSizeForCopy = journalSizeInBytes;
                }
                
                // Running total of journal capacity for this RP Copy
                totalJournalSizeForCopy += journalSize;
            }   
            
            // Never use a minimum less than the default journal size
            if (minJournalSizeForCopy < DEFAULT_RP_JOURNAL_SIZE_IN_BYTES) {
                minJournalSizeForCopy = DEFAULT_RP_JOURNAL_SIZE_IN_BYTES;
            }
            
            Long totalJournalSizeInBytesForCopy = SizeUtil.translateSize(String.valueOf(totalJournalSizeForCopy));
          
            // Find all the volumes for this RP Copy (excluding journals) and calculate their cumulative size in bytes
            List<Volume> cgVolumes = RPHelper.getAllCgVolumes(cgURI, dbClient);
            Long totalVolumeSizeForCopy = 0L;
            for (Volume cgVolume : cgVolumes) {
                if (!cgVolume.checkPersonality(Volume.PersonalityTypes.METADATA.name())
                        && copyName.equalsIgnoreCase(cgVolume.getRpCopyName())
                        && !cgVolume.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                    // If the journal volume is VPLEX get its backing volume instead.
                    if (RPHelper.isVPlexVolume(cgVolume, dbClient)) {                        
                        cgVolume = VPlexUtil.getVPLEXBackendVolume(cgVolume, true, dbClient);
                    }
                    
                    // Prefer provisioned capacity, but in some cases like concurrent orders 
                    // the volume may not have been provisioned just yet.
                    Long volumeSize = (cgVolume.getProvisionedCapacity() > 0L ? 
                            cgVolume.getProvisionedCapacity() : cgVolume.getCapacity());
                  
                    // Running total of volume capacity for this RP Copy
                    totalVolumeSizeForCopy += volumeSize;
                }
            }                        
            Long totalVolumeSizeInBytesForCopy = SizeUtil.translateSize(String.valueOf(totalVolumeSizeForCopy));
          
            // Calculate the projected cumulative size in bytes for this RP Copy after we provision the new volume(s) 
            Long newTotalVolumeSizeInBytesForCopy = totalVolumeSizeInBytesForCopy + (size * volumeCount);
          
            // Find the multiplier set in the journal policy
            Float journalMultiplier = Float.valueOf(journalPolicy.substring(0, journalPolicy.length() - 1)).floatValue();
          
            // Report on findings...            
            logMsg.append(String.format("\tCurrent allocated journal capacity for RP Copy [%s]: [%s] GB", copyName,
                    SizeUtil.translateSize(totalJournalSizeInBytesForCopy, SizeUtil.SIZE_GB)));
            logMsg.append(String.format("\n\tCurrent volume capacity for RP Copy [%s]: [%s] GB", copyName,
                    SizeUtil.translateSize(totalVolumeSizeInBytesForCopy, SizeUtil.SIZE_GB)));            
            logMsg.append(String.format("\n\tNew volume capacity for RP Copy [%s] after new volume provisioning: [%s] GB", copyName,
                    SizeUtil.translateSize(newTotalVolumeSizeInBytesForCopy, SizeUtil.SIZE_GB)));            
            logMsg.append(String.format("\n\tBased on VirtualPool's journal policy, journal capacity required is: [%s]",
                    (SizeUtil.translateSize(newTotalVolumeSizeInBytesForCopy, SizeUtil.SIZE_GB) * journalMultiplier)));
                                  
            // Now check to see if any new journals need to be provisioned for this RP Copy
            if (totalJournalSizeInBytesForCopy < (newTotalVolumeSizeInBytesForCopy * journalMultiplier)) {
                // Yes, we need to provision new journals for this RP Copy. Let's give a concrete example:
                //
                // RP Virtual Pool has a Journal Policy of: Multiplier 0.25x
                // The existing RP CG has 4 volumes at 10 GB each with one Journal at 10 GB.
                //
                // Each RP Copy would have a total of 40 GB. Using the 0.25 multiplier, that means
                // we would need (40 * 0.25) 10 GB of Journal space for proper coverage. Which we have!
                // All good so far.
                //
                // Let's add 13 volumes at 6 GB each to the existing CG.
                //
                // We'll use the RP Source Copy for the example
                //
                // Current allocated journal capacity for RP Copy [Source]: 10.0 GB
                // Current volume capacity for RP Copy [Source]: 40.0 GB
                // New volume capacity for RP Copy [Source] after new volume provisioning: 118.0 GB (40existing + (13 * 6)new volumes)
                // Based on VirtualPool's journal policy, journal capacity required is: 29.5 (118 * 0.25 journal multiplier)
                // 
                // Now that we know the capacity required, we can determine the the size and the number of new
                // journals needed to fulfill the request.
                //
                // Example to be continued below...
              
                // Calculate the new journal space required. This is based on the existing journal capacity, 
                // the new capacity of the volumes being provisioned, and using the journal policy multiplier.
                double existingJournalCapacity = SizeUtil.translateSize(totalJournalSizeInBytesForCopy, SizeUtil.SIZE_GB);
                double newCopyCapacity = SizeUtil.translateSize(newTotalVolumeSizeInBytesForCopy, SizeUtil.SIZE_GB);
                double newJournalSpaceRequiredInGB = Math.abs(existingJournalCapacity - (newCopyCapacity * journalMultiplier));
              
                // Calculate the number of new journals required for this RP Copy.
                // If we get a non-round number we need to round up to the nearest one.          
                double minJournalSizeForCopyInGB = SizeUtil.translateSize(minJournalSizeForCopy, SizeUtil.SIZE_GB);
                int numberOfNewJournals = (int)Math.ceil(newJournalSpaceRequiredInGB / minJournalSizeForCopyInGB);
                            
                // Example continued:
                // Based on the example, the new journal space required would be 19.5 GB = 10 - (118 * 0.25). 
                // We then figure out how many journals are needed: 19.5 / 10 = 1.95 = 2 (rounded up) journals at 10 GB each.
                //
                // So the below would print:
                // New journal(s) required. 2 journal(s) of size 10.0 GB will be provisioned for RP Copy [Source].
                logMsg.append(String.format("\n\t%s journal(s) of size %s GB are required and will "
                        + "be provisioned for RP Copy [%s].", 
                        numberOfNewJournals, minJournalSizeForCopyInGB, copyName));
              
                additionalJournalInfo = new HashMap<Integer, Long>(1);
                additionalJournalInfo.put(numberOfNewJournals, minJournalSizeForCopy);
            }                 
        } 
        
        if (additionalJournalInfo == null) {
            logMsg.append(String.format("\n\tSufficient journal space for RP Copy [%s]. No extra journals need to be provisioned.", copyName));
        }
        
        logMsg.append("\n--------------------------------------\n");
        _log.info(logMsg.toString());

        return additionalJournalInfo;
    }

    /**
     * Since there are several ways to express journal size policy, this helper method will take
     * the source size and apply the policy string to come up with a resulting size.
     *
     * @param sourceSizeStr size of the source volume
     * @param journalSizePolicy the policy of the journal size. ("10gb", "min", or "3.5x" formats)
     * @param resourceCount Number of resources in the request
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
            long journalSize = ((long) (totalSourceSizeInBytes.longValue() * multiplier) < DEFAULT_RP_JOURNAL_SIZE_IN_BYTES)
                    ? DEFAULT_RP_JOURNAL_SIZE_IN_BYTES
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
                    if (vplexVirtualVolume.checkPersonality(type)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Determines if a Volume is being referenced as an associated volume by an RP+VPlex
     * volume of any personality type (SOURCE, TARGET, METADATA).
     *
     * @param volume the volume we are trying to find a parent RP+VPlex volume reference for.
     * @param dbClient the DB client.
     * @param types the personality types.
     * @return true if this volume is associated to an RP+VPlex journal, false otherwise.
     */
    public static boolean isAssociatedToAnyRpVplexTypes(Volume volume, DbClient dbClient) {
        return isAssociatedToRpVplexType(volume, dbClient, PersonalityTypes.SOURCE, PersonalityTypes.TARGET, PersonalityTypes.METADATA);
    }

    /**
     * Returns the list of copies residing on the standby varray given the active production volume in a
     * Metropoint environment
     *
     * @param volume the active production volume
     * @param dbClient DbClient ref
     * @return returns the list of copies on the standby varray
     */
    public static List<Volume> getMetropointStandbyCopies(Volume volume, DbClient dbClient) {

        List<Volume> standbyCopies = new ArrayList<Volume>();

        if (volume.getProtectionSet() == null) {
            return standbyCopies;
        }

        ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, volume.getProtectionSet());

        if (protectionSet.getVolumes() == null) {
            return standbyCopies;
        }

        // look for the standby varray in the volume's vpool
        VirtualPool vpool = dbClient.queryObject(VirtualPool.class, volume.getVirtualPool());

        if (vpool == null) {
            return standbyCopies;
        }

        StringMap varrayVpoolMap = vpool.getHaVarrayVpoolMap();
        if (varrayVpoolMap != null && !varrayVpoolMap.isEmpty()) {
            URI standbyVarrayId = URI.create(varrayVpoolMap.keySet().iterator().next());

            // now loop through the replication set volumes and look for any copies from the standby varray
            for (String rsetVolId : protectionSet.getVolumes()) {
                Volume rsetVol = dbClient.queryObject(Volume.class, URI.create(rsetVolId));
                if (rsetVol != null && !rsetVol.getInactive() && rsetVol.getRpTargets() != null) {
                    for (String targetVolId : rsetVol.getRpTargets()) {
                        Volume targetVol = dbClient.queryObject(Volume.class, URI.create(targetVolId));
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
                    List<Volume> sourceJournals = RPHelper.findExistingJournalsForCopy(dbClient, sourceVolume.getConsistencyGroup(),
                            sourceVolume.getRpCopyName());

                    // Check for Stanbdy journals in the case of MetroPoint
                    String standbyCopyName = getStandbyProductionCopyName(dbClient, sourceVolume);
                    if (standbyCopyName != null) {
                        sourceJournals.addAll(RPHelper.findExistingJournalsForCopy(dbClient, sourceVolume.getConsistencyGroup(),
                                standbyCopyName));
                    }

                    allRelatedVolumes.addAll(sourceJournals);
                }

                if (sourceVolume.getRpTargets() != null) {
                    for (String targetVolumeId : sourceVolume.getRpTargets()) {
                        Volume targetVolume = dbClient.queryObject(Volume.class, URI.create(targetVolumeId));
                        allRelatedVolumes.add(targetVolume);

                        if (includeJournalVolumes) {
                            List<Volume> targetJournals = RPHelper.findExistingJournalsForCopy(dbClient,
                                    targetVolume.getConsistencyGroup(), targetVolume.getRpCopyName());
                            allRelatedVolumes.addAll(targetJournals);
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
     * MetroPoint Source volumes are represented as two copies (aka targets) in RecoverPoint.
     *
     * The VPLEX Source volume has its internal site set as do both the associated/backing volumes.
     *
     * The associated/backing volume that has the same internal site name as its VPLEX Virtual volume
     * is generally considered the "Active" copy and the other associated/backing volume's internal site name
     * would be considered the "Standby" copy.
     *
     * This method is a convenience to find the "Standby" copy name as it's currently not plainly
     * found on the VPLEX Virtual Volume.
     *
     * @param dbClient DbClient reference
     * @param sourceVolume The sourceVolume to check, we assume it's a MetroPoint volume
     * @return The standby internal site name, null otherwise.
     */
    public static String getStandbyInternalSite(DbClient dbClient, Volume sourceVolume) {
        String standbyInternalSite = null;
        if (sourceVolume != null
                && Volume.PersonalityTypes.SOURCE.name().equals(sourceVolume.getPersonality())) {
            if (isMetroPointVolume(dbClient, sourceVolume)
                    && (null != sourceVolume.getAssociatedVolumes()
                    && (!sourceVolume.getAssociatedVolumes().isEmpty()))) {
                // Check the associated volumes to find the non-matching internal site and return that one.
                for (String associatedVolId : sourceVolume.getAssociatedVolumes()) {
                    Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                    if (associatedVolume != null && !associatedVolume.getInactive()) {
                        if (NullColumnValueGetter.isNotNullValue(associatedVolume.getInternalSiteName())
                                && !associatedVolume.getInternalSiteName().equals(sourceVolume.getInternalSiteName())) {
                            // If the internal site names are different, this is the standby internal site
                            standbyInternalSite = associatedVolume.getInternalSiteName();
                            break;
                        }
                    }
                }
            }
        }

        return standbyInternalSite;
    }

    /**
     * MetroPoint Source volumes are represented as two copies (aka targets) in RecoverPoint.
     *
     * The VPLEX Source volume has its internal site set as do both the associated/backing volumes.
     *
     * The associated/backing volume that has the same internal site name as its VPLEX Virtual volume
     * is generally considered the "Active" copy and the other associated/backing volume's internal site name
     * would be considered the "Standby" copy.
     *
     * This method is a convenience to find the "Standby" production copy name as it's currently not plainly
     * found on the VPLEX Virtual Volume.
     *
     * @param dbClient DbClient reference
     * @param sourceVolume The sourceVolume to check, we assume it's a MetroPoint volume
     * @return The standby internal site name, null otherwise.
     */
    public static String getStandbyProductionCopyName(DbClient dbClient, Volume sourceVolume) {
        String standbyProductionCopyName = null;
        if (sourceVolume != null
                && Volume.PersonalityTypes.SOURCE.name().equals(sourceVolume.getPersonality())
                && sourceVolume.getAssociatedVolumes() != null
                && sourceVolume.getAssociatedVolumes().size() > 1) {
            // Check the associated volumes to find the non-matching internal site and return that one.
            for (String associatedVolId : sourceVolume.getAssociatedVolumes()) {
                Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                if (associatedVolume != null && !associatedVolume.getInactive()) {
                    if (NullColumnValueGetter.isNotNullValue(associatedVolume.getInternalSiteName())
                            && !associatedVolume.getInternalSiteName().equals(sourceVolume.getInternalSiteName())
                            && NullColumnValueGetter.isNotNullValue(associatedVolume.getRpCopyName())) {
                        // If the internal site names are different, this is the standby volume
                        standbyProductionCopyName = associatedVolume.getRpCopyName();
                        break;
                    }
                }
            }
        }

        return standbyProductionCopyName;
    }

    /**
     * Determines if a volume is a VPLEX volume.
     *
     * @param volume the volume.
     * @param dbClient the database client.
     * @return true if this is a VPLEX volume, false otherwise.
     */
    public static boolean isVPlexVolume(Volume volume, DbClient dbClient) {
        return volume.isVPlexVolume(dbClient);
    }

    /**
     * Rollback protection specific fields on the existing volume. This is normally invoked if there are
     * errors during a change vpool operation. We want to return the volume back to its un-protected state
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

                // Potentially rollback all journal volumes from the CG,
                // the main use case for this is during a brand new CG create
                // and the order needs to be rolled back so the entire CG would be
                // blown away. This will quickly clean up the journals so a new
                // order can be placed immediately.
                if (lastSourceVolumeInCG) {
                    List<Volume> journals = getCgVolumes(dbClient, volume.getConsistencyGroup(), Volume.PersonalityTypes.METADATA.name());
                    for (Volume journal : journals) {
                        _log.info(String.format("Rolling back RP Journal (%s)", journal.getLabel()));
                        protectionSetVolumeIdsToRemove.add(journal.getId().toString());
                        rollbackVolume(journal.getId(), dbClient);
                    }
                }

                // Null out any RP specific fields on the volume
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
                        rollbackVolume(URI.create(rpTargetId), dbClient);
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

                        dbClient.updateObject(protectionSet);
                    }
                }

                volume.setProtectionSet(NullColumnValueGetter.getNullNamedURI());
            } else {
                _log.info(String.format("Rollback changes for existing protected RP volume [%s]...", volume.getLabel()));
                // No specific rollback steps for existing protected volumes
            }

            // If this is a VPLEX volume, update the virtual pool references to the old vpool on
            // the backing volumes if they were set to the new vpool.
            if (RPHelper.isVPlexVolume(volume, dbClient)) {
                if (null == volume.getAssociatedVolumes()) {
                    // this is a rollback situation, so we probably don't want to
                    // throw another exception...
                    _log.warn("VPLEX volume {} has no backend volumes.",
                            volume.forDisplay());
                } else {
                    for (String associatedVolId : volume.getAssociatedVolumes()) {
                        Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                        if (associatedVolume != null && !associatedVolume.getInactive()) {
                            if (!NullColumnValueGetter.isNullURI(associatedVolume.getVirtualPool())
                                    && associatedVolume.getVirtualPool().equals(volume.getVirtualPool())) {
                                associatedVolume.setVirtualPool(oldVpool.getId());
                                _log.info(String.format("Backing volume [%s] has had its virtual pool rolled back to [%s].",
                                        associatedVolume.getLabel(),
                                        oldVpool.getLabel()));
                            }
                            associatedVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                            dbClient.updateObject(associatedVolume);
                        }
                        // If the old vpool did not specify multi volume consistency,
                        // remove the CG reference of the volume since we are rolling back
                        // in the case of VPLEX and Array rollback those steps will be fired
                        // before we get here anyway.
                        if (!oldVpool.getMultivolumeConsistency()) {
                            associatedVolume.setConsistencyGroup(NullColumnValueGetter.getNullURI());
                        }
                        dbClient.updateObject(associatedVolume);
                    }
                }
            }

            // Set the old vpool back on the volume
            _log.info(String.format("Resetting vpool on volume [%s](%s) from (%s) back to its original vpool (%s)",
                    volume.getLabel(), volume.getId(), volume.getVirtualPool(), oldVpool.getId()));
            volume.setVirtualPool(oldVpool.getId());

            dbClient.updateObject(volume);
            _log.info(String.format("Rollback of RP protection changes for volume [%s] (%s) has completed.", volume.getLabel(),
                    volume.getId()));
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
            if (volume.getProvisionedCapacity() == null
                    || volume.getProvisionedCapacity() == 0) {
                // Only set the volume to inactive if it has never
                // been provisioned. Otherwise let regular rollback
                // steps take care of cleaning it up.
                dbClient.markForDeletion(volume);
            } else {
                // Normal rollback should clean up the volume, change the label
                // to allow re-orders.
                String rollbackLabel = "-ROLLBACK-" + Math.random();
                volume.setLabel(volume.getLabel() + rollbackLabel);

                dbClient.updateObject(volume);
            }

            // Rollback any VPLEX backing volumes too
            if (RPHelper.isVPlexVolume(volume, dbClient) && (null != volume.getAssociatedVolumes())) {
                for (String associatedVolId : volume.getAssociatedVolumes()) {
                    Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(associatedVolId));
                    if (associatedVolume != null && !associatedVolume.getInactive()) {
                        _log.info(String.format("Rollback volume [%s]...", associatedVolume.getLabel()));
                        if (associatedVolume.getProvisionedCapacity() == null
                                || associatedVolume.getProvisionedCapacity() == 0) {
                            // Only set the volume to inactive if it has never
                            // been provisioned. Otherwise let regular rollback
                            // steps take care of cleaning it up.
                            dbClient.markForDeletion(associatedVolume);
                        } else {
                            // Normal rollback should clean up the volume, change the label
                            // to allow re-orders.
                            associatedVolume.setLabel(volume.getLabel() + "-ROLLBACK-" + Math.random());
                            dbClient.updateObject(associatedVolume);
                        }
                    }
                }
            }
        }

        return volume;
    }

    /**
     * Returns the list of journal volumes for one site.
     *
     * If this is a CDP volume, journal volumes from both the production and target copies are returned.
     *
     * @param varray varray to check
     * @param consistencyGroup RP CG
     * @param dbClient DbClient ref
     * @return Journal volumes for the RP site
     */
    private static List<Volume> getJournalVolumesForSite(VirtualArray varray, BlockConsistencyGroup consistencyGroup, DbClient dbClient) {
        List<Volume> journalVols = new ArrayList<Volume>();
        List<Volume> volsInCg = getAllCgVolumes(consistencyGroup.getId(), dbClient);
        if (volsInCg != null) {
            for (Volume volInCg : volsInCg) {
                if (Volume.PersonalityTypes.METADATA.toString().equals(volInCg.getPersonality())
                        && !NullColumnValueGetter.isNullURI(volInCg.getVirtualArray())
                        && volInCg.getVirtualArray().equals(varray.getId())) {
                    journalVols.add(volInCg);
                }
            }
        }
        
        return journalVols;
    }

    /**
     * Returns a unique journal volume name by evaluating all journal volumes for the copy and increasing the count journal volume name is
     * in the form varrayName-cgname-journal-[count].
     *
     * @param varray varray for the Journal
     * @param consistencyGroup RP CG
     * @return a journal name unique within the site
     */
    public static String createJournalVolumeName(VirtualArray varray, BlockConsistencyGroup consistencyGroup, DbClient dbClient) {
        String journalPrefix = new StringBuilder(consistencyGroup.getLabel()).append(VOL_DELIMITER).append(varray.getLabel())
                .append(VOL_DELIMITER)
                .append(JOURNAL).toString();
        List<Volume> existingJournals = getJournalVolumesForSite(varray, consistencyGroup, dbClient);

        // filter out old style journal volumes
        // new style journal volumes are named with the virtual array as the first component
        // some journals may be ingested and not fit either style. Avoid those too.
        List<Volume> newStyleJournals = new ArrayList<Volume>();
        for (Volume journalVol : existingJournals) {
            String volName = journalVol.getLabel();
            if (volName != null && volName.length() >= journalPrefix.length() &&
                    volName.substring(0, journalPrefix.length()).equals(journalPrefix)) {
                newStyleJournals.add(journalVol);
            }
        }

        // For some platforms volume names with blank spaces are not allowed.
        // If the varray and/or CG name has spaces they may have been removed from
        // the volume label. If this is the case, we would not have added them
        // to the new style journal list. If the list is empty, try again with
        // the blank spaces removed from the journal name prefix.
        if (newStyleJournals.isEmpty()) {
            journalPrefix = journalPrefix.replaceAll("\\s+", "");
            for (Volume journalVol : existingJournals) {
                String volName = journalVol.getLabel();
                if (volName != null && volName.length() >= journalPrefix.length() &&
                        volName.substring(0, journalPrefix.length()).equals(journalPrefix)) {
                    newStyleJournals.add(journalVol);
                }
            }
        }

        // calculate the largest index
        int largest = 0;
        for (Volume journalVol : newStyleJournals) {
            String journalVolName = journalVol.getLabel();
            // For journal volumes that are VPLEX volumes, if custom naming was enabled,
            // then the journal volume may have an unexpected name. However, the backend
            // volume is not custom named and will have the expected label, but with a
            // well-known suffix. We simply remove the suffix and compare against the backend
            // volume name. If we use the VPLEX volume name and its name was customized, then
            // we could end up with a duplicate name error when we go to prepare the backend
            // volume for a new journal volume, such as when journal capacity is added to
            // an RP protected volume. See Jira COP-24930.
            if (journalVol.isVPlexVolume(dbClient)) {
                Volume journalBackendVol = VPlexUtil.getVPLEXBackendVolume(journalVol, true, dbClient);
                if (journalBackendVol != null) {
                    journalVolName = journalBackendVol.getLabel();
                    journalVolName = journalVolName.substring(0, journalVolName.lastIndexOf("-0"));
                }
            }
            String[] parts = StringUtils.split(journalVolName, VOL_DELIMITER);
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
        for (ProtectionSystem rpSystem : rpSystems) {
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
     * Does this snapshot require any sort of protection intervention? If it's a local array-based
     * snapshot, probably not. If it's a protection-based snapshot or a remote array-based snapshot
     * that requires protection intervention to ensure consistency between the source and target, then
     * you should go to the protection controller
     *
     * @param volume source volume
     * @param snapshotType The snapshot technology type.
     *
     * @return true if this is a protection based snapshot, false otherwise.
     */
    public static boolean isProtectionBasedSnapshot(Volume volume, String snapshotType, DbClient dbClient) {
        // if volume is part of CG, and is snapshot type is not RP, then always create native Array snaps
        String rgName = volume.getReplicationGroupInstance();
        if (volume.isVPlexVolume(dbClient)) {
            Volume backendVol = VPlexUtil.getVPLEXBackendVolume(volume, true, dbClient);
            if (backendVol != null && !backendVol.getInactive()) {
                rgName = backendVol.getReplicationGroupInstance();
            }
        }
        if (NullColumnValueGetter.isNotNullValue(rgName) &&
                !snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.RP.toString())) {
            return false;
        }

        // This is a protection based snapshot request if:
        // The volume allows for bookmarking (it's under protection) and
        // - The param either asked for a bookmark, or
        // - The param didn't ask for a bookmark, but the volume is a remote volume
        if (volume.getProtectionController() != null
                && (snapshotType.equalsIgnoreCase(BlockSnapshot.TechnologyType.RP.toString()) || volume
                        .getPersonality().equals(Volume.PersonalityTypes.TARGET.toString()))) {
            return true;
        }
        return false;
    }

    /**
     * Fetch the RP Protected target virtual pool uris.
     *
     * @param dbClient db client
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

    /**
     * Creates an export group with the proper settings for RP usage
     *
     * @param exportGroupGeneratedName the generated ExportGroup name to use
     * @param virtualArray virtual array
     * @param project project
     * @param numPaths number of paths
     * @param isJournalExport flag indicating if this is an ExportGroup intended only for journal volumes
     * @return an export group
     */
    public static ExportGroup createRPExportGroup(String exportGroupGeneratedName, VirtualArray virtualArray, Project project,
            Integer numPaths, boolean isJournalExport) {
        ExportGroup exportGroup;
        exportGroup = new ExportGroup();
        exportGroup.setId(URIUtil.createId(ExportGroup.class));
        exportGroup.addInternalFlags(Flag.INTERNAL_OBJECT, Flag.SUPPORTS_FORCE, Flag.RECOVERPOINT);
        exportGroup.setProject(new NamedURI(project.getId(), project.getLabel()));
        exportGroup.setVirtualArray(virtualArray.getId());
        exportGroup.setTenant(new NamedURI(project.getTenantOrg().getURI(), project.getTenantOrg().getName()));
        exportGroup.setGeneratedName(exportGroupGeneratedName);
        // When created by CoprHD natively, it's usually the CG name.
        exportGroup.setLabel(exportGroupGeneratedName);
        exportGroup.setVolumes(new StringMap());
        exportGroup.setOpStatus(new OpStatusMap());
        // TODO: May need to use a default size or compute based on the contents of the export mask.
        exportGroup.setNumPaths(numPaths);
        exportGroup.setType(ExportGroupType.Cluster.name());
        exportGroup.setZoneAllInitiators(true);

        // If this is an exportGroup intended only for journal volumes, set the RECOVERPOINT_JOURNAL flag
        if (isJournalExport) {
            exportGroup.addInternalFlags(Flag.RECOVERPOINT_JOURNAL);
        }

        return exportGroup;
    }

    /**
     * Generates a RecoverPoint ExportGroup name based on the standard
     * ViPR RecoverPoint ExportGroup label pattern.
     *
     * @param protectionSystem the ProtectionSystem for the ExportGroup
     * @param storageSystem the StorageSystem for the ExportGroup
     * @param internalSiteName the RecoverPoint internal site name
     * @param virtualArray the VirtualArray for the ExportGroup
     * @param isJournalExport flag indicating if this is an ExportGroup intended only for journal volumes
     * @return a RecoverPoint ExportGroup name String
     */
    public static String generateExportGroupName(ProtectionSystem protectionSystem,
            StorageSystem storageSystem, String internalSiteName, VirtualArray virtualArray, boolean isJournalExport) {
        // This name generation needs to match ingestion code found in RPDeviceController until
        // we come up with better export group matching criteria.
        String protectionSiteName = protectionSystem.getRpSiteNames().get(internalSiteName);
        String exportGroupGeneratedName = protectionSystem.getNativeGuid() + "_" + storageSystem.getLabel() + "_" + protectionSiteName
                + "_"
                + virtualArray.getLabel();
        if (isJournalExport) {
            exportGroupGeneratedName = exportGroupGeneratedName + "_JOURNAL";
        }
        // Remove all non alpha-numeric characters, excluding "_".
        exportGroupGeneratedName = exportGroupGeneratedName.replaceAll("[^A-Za-z0-9_]", "");
        _log.info("ExportGroup generated name is " + exportGroupGeneratedName);
        return exportGroupGeneratedName;
    }

    /**
     * Get the name of the copy associated with the varray ID and personality of the incoming volume.
     *
     * @param dbClient db client
     * @param consistencyGroup cg
     * @param varrayId varray ID
     * @param productionCopy is this a production volume
     * @return String associated with the existing copy name
     */
    public static String getCgCopyName(DbClient dbClient, BlockConsistencyGroup consistencyGroup, URI varrayId, boolean productionCopy) {
        List<Volume> cgVolumes = RPHelper.getAllCgVolumes(consistencyGroup.getId(), dbClient);
        if (cgVolumes == null) {
            return null;
        }

        for (Volume cgVolume : cgVolumes) {
            if (cgVolume.getPersonality() == null) {
                continue;
            }

            if (RPHelper.isMetroPointVolume(dbClient, cgVolume)
                    && cgVolume.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.toString()) && productionCopy) {
                // If the volume is MetroPoint, check for varrayId in the associated volumes since their RP Copy names will be different.
                if (cgVolume.getAssociatedVolumes() != null) {
                    for (String assocVolumeIdStr : cgVolume.getAssociatedVolumes()) {
                        Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(assocVolumeIdStr));
                        if (URIUtil.identical(associatedVolume.getVirtualArray(), varrayId)) {
                            return associatedVolume.getRpCopyName();
                        }
                    }
                }
            }

            if (!URIUtil.identical(cgVolume.getVirtualArray(), varrayId)) {
                continue;
            }

            if (cgVolume.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.toString()) && productionCopy) {
                return cgVolume.getRpCopyName();
            }

            if (cgVolume.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.toString()) && !productionCopy) {
                return cgVolume.getRpCopyName();
            }
        }
        return null;
    }

    /**
     * Validate the replication set for each volume to ensure the source volume size is not greater
     * than the target volume size. This validation is required for both restore and expand because
     * we delete and re-create the replication set. The re-create step will fail if the source volume is
     * larger in size than the target. This situation would likely only arise if a swap was performed.
     *
     * @param dbClient the database client
     * @param volumes the list of volumes to validate
     */
    public static void validateRSetVolumeSizes(DbClient dbClient, List<Volume> volumes) {
        if (volumes != null) {
            for (Volume volume : volumes) {
                // We aren't sure if the volume is a source or target. We need to get a handle
                // on the source volume in order to proceed.
                Volume sourceVolume = getRPSourceVolume(dbClient, volume);

                // Validate the source volume size is not greater than the target volume size
                if (sourceVolume != null && sourceVolume.getRpTargets() != null) {
                    for (String volumeID : sourceVolume.getRpTargets()) {
                        try {
                            Volume targetVolume = dbClient.queryObject(Volume.class, new URI(volumeID));

                            if (sourceVolume.getProvisionedCapacity() > targetVolume.getProvisionedCapacity()) {
                                throw APIException.badRequests.invalidRPVolumeSizes(sourceVolume.getId());
                            }
                        } catch (URISyntaxException e) {
                            throw APIException.badRequests.invalidURI(volumeID, e);
                        }
                    }
                }
            }
        }
    }

    /**
     * For an RP configuration, get all RP bookmarks for the CGs provided
     *
     * @param system RP system
     * @param cgIDs IDs of the consistency groups to get the bookmarks
     * @return command result object, object list [0] has GetBookmarksResponse
     * @throws RecoverPointException
     */
    private static BiosCommandResult getRPBookmarks(ProtectionSystem system, Set<Integer> cgIDs) throws RecoverPointException {
        _log.info("getRPBookmarks {} - start", system.getId());
        RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
        GetBookmarksResponse bookmarkResponse = rp.getRPBookmarks(cgIDs);
        _log.info("getRPBookmarks {} - complete", system.getId());
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        List<Object> returnList = new ArrayList<Object>();
        returnList.add(bookmarkResponse);
        result.setObjectList(returnList);
        return result;
    }

    /**
     * Validate Block snapshots that correspond to RP bookmarks. Some may no longer exist in the RP system, and we
     * need to mark them as invalid.
     *
     * The strategy is as follows:
     * 1. Get all of the protection sets associated with the protection system
     * 2. Are there any Block Snapshots of type RP? (if not, don't bother cleaning up)
     * 3. Query the RP Appliance for all bookmarks for that CG (protection set)
     * 4. Find each block snapshot of type RP for each site
     * 5. If you can't find the bookmark in the RP list, move the block snapshot to inactive
     *
     * @param protectionSystem Protection System
     */
    public static void cleanupSnapshots(DbClient dbClient, ProtectionSystem protectionSystem) throws RecoverPointException {
        // 1. Get all of the protection sets associated with the protection system
        Set<URI> protectionSetIDs = new HashSet<URI>();
        Set<Integer> cgIDs = new HashSet<Integer>();
        URIQueryResultList list = new URIQueryResultList();
        Constraint constraint = ContainmentConstraint.Factory.getProtectionSystemProtectionSetConstraint(protectionSystem.getId());
        dbClient.queryByConstraint(constraint, list);
        Iterator<URI> it = list.iterator();
        while (it.hasNext()) {
            URI protectionSetId = it.next();

            // Get all snapshots that are part of this protection set.
            URIQueryResultList plist = new URIQueryResultList();
            Constraint pconstraint = ContainmentConstraint.Factory.getProtectionSetBlockSnapshotConstraint(protectionSetId);
            dbClient.queryByConstraint(pconstraint, plist);
            if (plist.iterator().hasNext()) {
                // OK, we know there are snapshots for this protection set/CG.
                // Retrieve all of the bookmarks associated with this protection set/CG later on by adding to the list now
                ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, protectionSetId);
                if (protectionSet != null && !protectionSet.getInactive()) {
                    protectionSetIDs.add(protectionSet.getId());
                    cgIDs.add(Integer.valueOf(protectionSet.getProtectionId()));
                }
            }
        }

        // 2. No reason to bother the RPAs if there are no protection sets for this protection system.
        if (protectionSetIDs.isEmpty()) {
            _log.info("Block Snapshot of RP Bookmarks cleanup not run for this protection system. No Protections or RP Block Snapshots found on protection system: "
                    + protectionSystem.getLabel());
            return;
        }

        // 3. Query the RP appliance for all of the bookmarks for these CGs in one call
        BiosCommandResult result = getRPBookmarks(protectionSystem, cgIDs);
        GetBookmarksResponse bookmarkMap = (GetBookmarksResponse) result.getObjectList().get(0);

        // 4. Go through each protection set's snapshots and see if they're there.
        it = protectionSetIDs.iterator();
        while (it.hasNext()) {
            URI protectionSetId = it.next();
            ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, protectionSetId);

            // Now find this snapshot in the returned list of snapshots
            // The map should have an entry for that CG with an empty list if it looked and couldn't find any. (a successful empty set)
            if (protectionSet.getProtectionId() != null &&
                    bookmarkMap.getCgBookmarkMap() != null &&
                    bookmarkMap.getCgBookmarkMap().containsKey(new Integer(protectionSet.getProtectionId()))) {

                // If the list of RPBookmark objects corresponding to the CG is null, lets replace it with an empty
                // list to avoid issues further down.
                if (bookmarkMap.getCgBookmarkMap().get(new Integer(protectionSet.getProtectionId())) == null) {
                    bookmarkMap.getCgBookmarkMap().put(new Integer(protectionSet.getProtectionId()), new ArrayList<RPBookmark>());
                }

                // Get all snapshots that are part of this protection set.
                URIQueryResultList plist = new URIQueryResultList();
                Constraint pconstraint = ContainmentConstraint.Factory.getProtectionSetBlockSnapshotConstraint(protectionSetId);
                dbClient.queryByConstraint(pconstraint, plist);
                Iterator<URI> snapshotIter = plist.iterator();
                while (snapshotIter.hasNext()) {
                    URI snapshotId = snapshotIter.next();
                    BlockSnapshot snapshot = dbClient.queryObject(BlockSnapshot.class, snapshotId);
                    boolean deleteSnapshot = true;

                    if (snapshot.getInactive()) {
                        // Don't bother deleting or processing if the snapshot is already on its way out.
                        deleteSnapshot = false;
                    } else if (snapshot.getEmCGGroupCopyId() == null) {
                        // If something bad happened and we weren't able to get the site information off of the snapshot
                        _log.info("Found that ViPR Snapshot corresponding to RP Bookmark is missing Site information, thus not analyzing for automated deletion. "
                                + snapshot.getId() +
                                " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":" + snapshot.getEmName());
                        deleteSnapshot = false;
                    } else if (!bookmarkMap.getCgBookmarkMap().get(Integer.valueOf(protectionSet.getProtectionId())).isEmpty()) {
                        for (RPBookmark bookmark : bookmarkMap.getCgBookmarkMap().get(Integer.valueOf(protectionSet.getProtectionId()))) {
                            // bookmark (from RP) vs. snapshot (from ViPR)
                            if (snapshot.getEmName().equalsIgnoreCase(bookmark.getBookmarkName()) &&
                                    snapshot.getEmCGGroupCopyId().equals(bookmark.getCGGroupCopyUID().getGlobalCopyUID().getCopyUID())) {
                                deleteSnapshot = false;
                                _log.info("Found that ViPR Snapshot corresponding to RP Bookmark still exists, thus saving in ViPR: "
                                        + snapshot.getId() +
                                        " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":"
                                        + snapshot.getEmCGGroupCopyId() + ":" + snapshot.getEmName());
                            }
                        }
                    } else {
                        // Just for debugging, otherwise useless
                        _log.debug("Found that ViPR Snapshot corresponding to RP Bookmark doesn't exist, thus going to delete from ViPR: "
                                + snapshot.getId() +
                                " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":"
                                + snapshot.getEmCGGroupCopyId() + ":" + snapshot.getEmName());
                    }

                    if (deleteSnapshot) {
                        // 5. We couldn't find the bookmark, and the query for it was successful, so it's time to mark it as gone
                        _log.info("Found that ViPR Snapshot corresponding to RP Bookmark no longer exists, thus deleting in ViPR: "
                                + snapshot.getId() +
                                " - " + protectionSet.getLabel() + ":" + snapshot.getEmInternalSiteName() + ":"
                                + snapshot.getEmCGGroupCopyId() + ":" + snapshot.getEmName());
                        dbClient.markForDeletion(snapshot);
                    }
                }
            } else if (protectionSet.getProtectionId() == null) {
                _log.error("Can not determine the consistency group ID of protection set: " + protectionSet.getLabel()
                        + ", can not perform any cleanup of snapshots.");
            } else {
                _log.info("No consistency groups were found associated with protection system: " + protectionSystem.getLabel()
                        + ", can not perform cleanup of snapshots.");
            }
        }
    }

    /**
     * Determines if the provided copy state is valid for creating RP bookmarks.
     *
     * @param copyState the copy state
     * @return true if the copy state if valid for creating bookmarks, false otherwise
     */
    public static boolean isValidBookmarkState(String copyState) {
        // The only invalid copy bookmark states we care about are null and DIRECT_ACCESS.
        if (copyState == null || copyState.equalsIgnoreCase(Copy.ImageAccessMode.DIRECT_ACCESS.name())) {
            return false;
        }

        return true;
    }

    /**
     * Validate the CG before performing destructive operations.
     * If additional volumes appear in the RP CG on the hardware, this method returns false
     * Clerical errors (such as missing DB entries) result in an Exception
     *
     * @param dbClient
     *            dbclient
     * @param system
     *            protection system
     * @param cgId
     *            BlockConsistencyGroup ID
     * @param volumes
     *            list of volumes
     * @return true if CG is what we expect on the hardware, false otherwise
     */
    public static boolean validateCGForDelete(DbClient dbClient, ProtectionSystem system, URI cgId, Set<URI> volumes) {
        _log.info("validateCGForDelete {} - start", system.getId());

        // Retrieve all of the RP CGs, their RSets, and their volumes
        RecoverPointClient rp = RPHelper.getRecoverPointClient(system);
        Set<GetCGsResponse> cgList = rp.getAllCGs();
        if (cgList == null || cgList.isEmpty()) {
            String errMsg = "Could not retrieve CGs from the RPA to perform validation.";
            throw DeviceControllerExceptions.recoverpoint.unableToPerformValidation(errMsg);
        }

        // Grab all of the source volumes from the CG according to ViPR
        List<Volume> srcVolumes = RPHelper.getCgVolumes(dbClient, cgId, PersonalityTypes.SOURCE.toString());
        if (srcVolumes == null || srcVolumes.isEmpty()) {
            String errMsg = "Could not retrieve volumes from the database for CG to perform validation";
            throw DeviceControllerExceptions.recoverpoint.unableToPerformValidation(errMsg);
        }

        // Get the protection set ID from the first source volume. All volumes will have the same pset ID.
        URI psetId = srcVolumes.get(0).getProtectionSet().getURI();
        if (NullColumnValueGetter.isNullURI(psetId)) {
            String errMsg = "Could not retrieve protection set ID from the database for CG to perform validation";
            throw DeviceControllerExceptions.recoverpoint.unableToPerformValidation(errMsg);
        }

        // Get the protection set, which is required to get the CG ID on the RPA
        ProtectionSet pset = dbClient.queryObject(ProtectionSet.class, psetId);
        if (pset == null) {
            String errMsg = "Could not retrieve protection set from the database for CG to perform validation";
            throw DeviceControllerExceptions.recoverpoint.unableToPerformValidation(errMsg);
        }

        // Pre-populate the wwn fields for comparisons later.
        List<String> srcVolumeWwns = new ArrayList<>();
        for (Volume srcVolume : srcVolumes) {
            srcVolumeWwns.add(srcVolume.getWWN());
        }

        // This loop finds the CG on the hardware from the list of all CGs. Ignores all CGs that don't match our ID.
        for (GetCGsResponse cgResponse : cgList) {
            // Compare the stored CG ID (unique per RP System, doesn't change even if CG name changes)
            if (Long.parseLong(pset.getProtectionId()) != cgResponse.getCgId()) {
                continue;
            }

            // Make sure we have rsets before we continue. If the CG has no RSets on the hardware, throw
            if (cgResponse.getRsets() == null || cgResponse.getRsets().isEmpty()) {
                String errMsg = "Could not retrieve replication sets from the hardware to perform validation";
                throw DeviceControllerExceptions.recoverpoint.unableToPerformValidation(errMsg);
            }

            // Find one of our volumes
            for (GetRSetResponse rsetResponse : cgResponse.getRsets()) {

                // Make sure we have volumes in the RSet before we continue
                if (rsetResponse == null || rsetResponse.getVolumes() == null || rsetResponse.getVolumes().isEmpty()) {
                    String errMsg = "Could not retrieve the volumes in the replication set from the hardware to perform validation";
                    throw DeviceControllerExceptions.recoverpoint.unableToPerformValidation(errMsg);
                }

                // Check all of the volumes in the replication set. At least ONE volume needs to match one of our source
                // volumes. An RSet contains one source (or an active and stand-by source) and multiple targets. Our
                // list of WWNs is the list of source volumes we know about.
                for (GetVolumeResponse volumeResponse : rsetResponse.getVolumes()) {
                    // This hardware volume should be represented in the list of srcVolumes
                    if (!srcVolumeWwns.contains(volumeResponse.getWwn())) {
                        _log.warn(
                                "Found at least one volume that isn't in our list of source volumes {}, therefore we can not delete the entire CG.",
                                volumeResponse.getWwn());
                        return false;
                    }
                }
            }
        }
        _log.info("validateCGForDelete {} - end", system.getId());
        return true;
    }

    /**
     * Updates the access state for a snapshot's target volume. If image access is being enabled, the
     * access state will be set to READWRITE. If image access is being disabled, the access state will
     * be set to READ_ONLY.
     *
     * @param snapshot the snapshot
     * @param volume the snapshot's parent volume
     * @param accessState the volume access state
     * @param dbClient the database client reference
     * @throws URISyntaxException
     */
    private static void updateAccessState(BlockSnapshot snapshot, Volume volume, VolumeAccessState accessState, DbClient dbClient)
            throws URISyntaxException {
        // For RP+VPLEX volumes, we need to fetch the VPLEX volume. The snapshot object references the
        // block/back-end volume as its parent. Fetch the VPLEX volume that is created with this volume
        // as the back-end volume.

        Volume vol = volume;

        if (Volume.checkForVplexBackEndVolume(dbClient, volume)) {
            vol = Volume.fetchVplexVolume(dbClient, volume);
        }

        Volume targetVolume = null;

        // If the personality is SOURCE, then the enable image access request is part of export operation.
        if (vol.checkPersonality(Volume.PersonalityTypes.SOURCE.toString())) {
            // Now determine the target volume that corresponds to the site of the snapshot
            ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, vol.getProtectionSet());
            targetVolume = ProtectionSet.getTargetVolumeFromSourceAndInternalSiteName(dbClient, protectionSet,
                    vol,
                    snapshot.getEmInternalSiteName());
        } else if (vol.checkPersonality(Volume.PersonalityTypes.TARGET.toString())) {
            targetVolume = vol;
        }

        if (targetVolume != null) {
            if (accessState != null) {
                _log.info(String.format("Updating the access state to %s for target volume %s.",
                        accessState.name(), targetVolume.getId()));
                targetVolume.setAccessState(accessState.name());
                dbClient.updateObject(targetVolume);
            } else {
                _log.warn(String.format("Invalid access state.  Could not update access state for target volume %s.", targetVolume.getId()));
            }
        }
    }

    /**
     * Update the snapshot's syncActive field and associated volume's access state post image access change.
     *
     * @param snapshot the snapshot
     * @param volume the snapshot's parent volume
     * @param accessState accessState the volume access state
     * @param setSnapshotSyncActive
     * @param dbClient the database client reference
     * @throws URISyntaxException
     */
    public static void updateRPSnapshotPostImageAccessChange(BlockSnapshot snapshot, Volume volume, VolumeAccessState accessState,
            boolean setSnapshotSyncActive, DbClient dbClient) throws URISyntaxException {
        // If we are performing a disable image access as part of a snapshot create for an array snapshot + RP bookmark,
        // we want to set the syncActive field to true. This will enable us to perform snapshot exports and
        // remove snapshots from exports.
        snapshot.setIsSyncActive(setSnapshotSyncActive);

        _log.info(String.format("Updating the isSyncActive field to %s for BlockSnapshot %s.",
                setSnapshotSyncActive, snapshot.getId()));
        dbClient.updateObject(snapshot);

        // Update the access state of the snapshot's associated target volume
        updateAccessState(snapshot, volume, accessState, dbClient);
    }

    /**
     * Determines if the given snapshot references an RP bookmark.
     *
     * @param snapshot the snapshot to check
     * @return true if the given snapshot is an RP bookmark, false otherwise.
     */
    public static boolean hasRpBookmark(BlockSnapshot snapshot) {
        return NullColumnValueGetter.isNotNullValue(snapshot.getEmName());
    }

    /**
     * Checks to see if the journal multiplier policy is on the vpool passed in. 
     * 
     * @param vpool Vpool to check for journal multiplier policy
     * @return true is journal multiplier policy is on vpool, false otherwise.
     */
    public static boolean vpoolHasJournalMultiplier(VirtualPool vpool) {
        if (vpool == null) {
            return false;
        } else {
            return (vpool.getJournalSize() != null 
                    && (vpool.getJournalSize().endsWith("x") || vpool.getJournalSize().endsWith("X")));
        }
    }

    /**
     * Checks if the Export has any RP-tagged volume. Will return true on encountering the first instance of RP-tagged
     * volume.
     * 
     * @param exportGroup
     * @param dbClient
     * @return true if EG has RP-tagged volume, false otherwise
     */
    public static boolean validateForRPVolumes(ExportGroup exportGroup, DbClient dbClient) {
        if (CollectionUtils.isEmpty(exportGroup.getVolumes().keySet())) {
            return false;
        }
        List<Volume> vols = dbClient.queryObject(Volume.class, URIUtil.uris(exportGroup.getVolumes().keySet()));
        for (Volume vol : vols) {
            if (vol.checkForRp()) {
                return true;
            }
        }
        return false;
    }
}
