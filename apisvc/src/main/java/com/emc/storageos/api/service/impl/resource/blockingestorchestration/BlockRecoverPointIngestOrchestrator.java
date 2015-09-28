/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.api.service.impl.resource.blockingestorchestration;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.utils.PropertySetterUtil;
import com.emc.storageos.api.service.impl.resource.utils.VolumeIngestionUtil;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.ProtectionSet.ProtectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.PersonalityTypes;
import com.emc.storageos.db.client.model.VpoolRemoteCopyProtectionSettings;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedProtectionSet.SupportedCGInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.RemoteMirrorObject;
import com.google.common.base.Joiner;

/**
 * Remote Replication Ingestion
 * S1 - Source Volume ,T1 - Target Volume
 * S1 under ViPR control  & T1 not yet
 * ****************************************
 * If user tries to ingest volumes of S1, S1 will be ingested with usable flag bit set to false.
 * usable bit set to false indicates, that this volume cannot be used for any provisioning operations 
 * If we find S1 as source for multiple protected volumes, check whether ALL expected target volumes are already ingested.
 * If found true, then create ViPR SRDF links between source and targets, by making them as if these source and targets are created via ViPR using SRDF protected VirtualPool, and set usable bit ot TRUE.
 * If not, usable bit remains in false state.
 * T1 under ViPR control and S1 not yet
 * **************************************** 
 * If user tries to ingest volumes of T1, T1 will be ingested with usable flag bit set to false.
 * usable bit set to false indicates, that this volume cannot be used for any provisioning operations 
 * If we find T1 as target for  a source volume, check whether source volume and ALL its expected target volumes are already ingested, exclusing the target which we work on. 
 * If found true, then create ViPR SRDF links between source and targets, by making them as if these source and targets are created via ViPR using SRDF protected VirtualPool, and set usable bit ot TRUE.
 * If not, usable bit remains in false state.
 */


public class BlockRecoverPointIngestOrchestrator extends BlockVolumeIngestOrchestrator {

    private static final Logger _logger = LoggerFactory.getLogger(BlockRecoverPointIngestOrchestrator.class);

    // the ingest strategy factory, used for ingesting the backend volume
    private IngestStrategyFactory ingestStrategyFactory;

    public void setIngestStrategyFactory(IngestStrategyFactory ingestStrategyFactory) {
        this.ingestStrategyFactory = ingestStrategyFactory;
    }

    @Override
    protected void checkUnmanagedVolumeReplicas(UnManagedVolume unmanagedVolume) {
        return;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends BlockObject> T ingestBlockObjects(List<URI> systemCache, List<URI> poolCache, StorageSystem system, UnManagedVolume unManagedVolume, 
            VirtualPool vPool, VirtualArray virtualArray, Project project, TenantOrg tenant, List<UnManagedVolume> unManagedVolumesSuccessfullyProcessed, 
            Map<String, BlockObject> createdObjectMap, Map<String, List<DataObject>> updatedObjectMap, boolean unManagedVolumeExported, Class<T> clazz, 
            Map<String, StringBuffer> taskStatusMap, String vplexIngestionMethod) throws IngestionException {
        String volumeNativeGuid = unManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        BlockObject blockObject = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);

        validateUnManagedVolumeProperties(unManagedVolume, virtualArray, vPool, project);
        // Check if ingested volume has exportmasks pending for ingestion.
        //if (isExportIngestionPending(blockObject, unManagedVolume.getId(), unManagedVolumeExported)) {
        //    return clazz.cast(blockObject);
        //}

        // Make sure there's an unmanaged protection set
        UnManagedProtectionSet umpset = getUnManagedProtectionSet(unManagedVolume);
        if (umpset == null) {
            _logger.warn("No unmanaged protection set could be found for unmanaged volume: " + unManagedVolume.getNativeGuid() + " Please run unmanaged CG discovery of registerd protection system");
            // TODO: Need a new exception that says more like the .warn above
            throw IngestionException.exceptions.unmanagedVolumeMasksNotIngested(unManagedVolume.getNativeGuid());
        }

        if (null == blockObject) {
            // We need to ingest the volume w/o the context of RP.  (So, ingest a VMAX if it's VMAX, VPLEX if it's VPLEX, etc)
            IngestStrategy ingestStrategy = ingestStrategyFactory.buildIngestStrategy(unManagedVolume, true);
            blockObject = ingestStrategy.ingestBlockObjects(systemCache, poolCache, system, unManagedVolume, vPool, virtualArray,
                    project, tenant, unManagedVolumesSuccessfullyProcessed, createdObjectMap, updatedObjectMap, true /* force true exported field */,
                    VolumeIngestionUtil.getBlockObjectClass(unManagedVolume), taskStatusMap, vplexIngestionMethod);
            _logger.info("Ingestion ended for unmanagedvolume {}", unManagedVolume.getNativeGuid());
            if (null == blockObject) {
                throw IngestionException.exceptions.generalVolumeException(
                        unManagedVolume.getLabel(), "check the logs for more details");
            }
        } else {
            // blockObject already ingested, now just update internalflags &
            // RP relationships. Run this logic always when volume NO_PUBLIC_ACCESS
            if (markUnManagedVolumeInactive(unManagedVolume, blockObject, unManagedVolumesSuccessfullyProcessed,
                    createdObjectMap, updatedObjectMap, taskStatusMap)) {
                _logger.info("All the related replicas and parent of unManagedVolume {} has been ingested ",
                        unManagedVolume.getNativeGuid());
                unManagedVolume.setInactive(true);
                unManagedVolumesSuccessfullyProcessed.add(unManagedVolume);
            } else {
                _logger.info(
                        "Not all the parent/replicas of unManagedVolume {} have been ingested , hence marking as internal",
                        unManagedVolume.getNativeGuid());
                blockObject.addInternalFlags(INTERNAL_VOLUME_FLAGS);
            }

        }
        
        // Decorate blockobjects with RP Properties.
        decorateBlockObjectWithRPProperties(blockObject, unManagedVolume);

        // Add the volume to the list of managed volumes we have so far.
        if (!umpset.getManagedVolumeIds().contains(blockObject.getId().toString())) {
            umpset.getManagedVolumeIds().add(blockObject.getId().toString());
        }
        
        // Remove the unmanaged volume from the list we have so far since that is going inactive.
        if (umpset.getUnManagedVolumeIds().contains(unManagedVolume.getId().toString())) {
            umpset.getUnManagedVolumeIds().remove(unManagedVolume.getId().toString());
        }
           
        _dbClient.persistObject(umpset);

        // Create the managed protection set/CG objects when we have all of the volumes ingested
        if (validateAllVolumesInCGIngested(unManagedVolume, umpset)) {
            _logger.info("Successfully ingested all volumes associated with RP consistency group");
            ProtectionSet pset = createProtectionSet(umpset);
            BlockConsistencyGroup bcg = createBlockConsistencyGroup(pset);
            decorateVolumeInformationFinalIngest(pset, bcg);
        }
        
        return clazz.cast(blockObject);
    }
	
    /**
     * Check to see if all of the volumes associated with the RP CG are now ingested.
     * @param unManagedVolume unmanaged volume object
     * 
     * @return true if all volumes in CG are ingested
     */
    private boolean validateAllVolumesInCGIngested(UnManagedVolume unManagedVolume, UnManagedProtectionSet umpset) {
        if (umpset == null) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + unManagedVolume.getId() + " Please run unmanaged CG discovery of registered protection systems");
            return false;
        }
        
        // Make sure the managed volumes match the unmanaged volumes and WWN list
        if (umpset.getUnManagedVolumeIds() != null && umpset.getManagedVolumeIds() != null && umpset.getVolumeWwns() != null &&
                umpset.getUnManagedVolumeIds().size() == umpset.getManagedVolumeIds().size() &&
                umpset.getManagedVolumeIds().size() == umpset.getVolumeWwns().size()) {
            _logger.info("Found that all volumes associated with the RP CG have been ingested: " + umpset.getCgName());
            return true;
        }

        // Extremely unlikely #1: No unmanaged volume IDs in the protection set.  We wouldn't have stored the unmanaged protection set in this case.
        if (umpset.getUnManagedVolumeIds() == null) {
            String msg = String.format("INGEST VALIDATION: No unmanaged volumes found in unmanaged protection set: " + umpset.getCgName());                    
            _logger.error(msg);
            return false;
        }
        
        // Extremely unlikely #2: Every ingest operation puts a volume in this list.
        if (umpset.getManagedVolumeIds() == null) {
            String msg = String.format("INGEST VALIDATION: No managed volumes found in unmanaged protection set: " + umpset.getCgName());                    
            _logger.error(msg);
            return false;
        }

        // Extremely unlikely #3: See #1.  We would not have created the protection set if there weren't volumes.
        if (umpset.getVolumeWwns() == null) {
            String msg = String.format("INGEST VALIDATION: No volume WWNs found in unmanaged protection set: " + umpset.getCgName());                    
            _logger.error(msg);
            return false;
        }
        
        // Very likely: We haven't quite ingested everything yet.
        if (!umpset.getUnManagedVolumeIds().isEmpty()) {
            String msg = String.format("INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                    "are %d volumes to be ingested, however only %d volume have been ingested.", umpset.getCgName(), umpset.getVolumeWwns().size(), umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the unmanaged volumes that we haven't ingested yet and print them up.
            return false;
        } 
        
        if (umpset.getManagedVolumeIds().size() != umpset.getVolumeWwns().size()) {
            String msg = String.format("INGEST VALIDATION: Found that the unmanaged protection set: %s is not yet ingestable because there " +
                    " are %d volumes in the RP CG that are on arrays that are not under management.", umpset.getCgName(), umpset.getVolumeWwns().size()-umpset.getManagedVolumeIds().size());
            _logger.info(msg);
            // TODO: Iterate over the volume WWNs (maybe the array serial number?) that aren't in our management.
            return false;
        }

        _logger.info("INGEST VALIDATION: All of the volumes associated with RP CG " + umpset.getCgName() + " have been ingested.");
        return true;
    }

    /**
     * Find the unmanaged protection set associated with the unmanaged volume
     * 
     * @param unManagedVolume unmanaged volume
     * @return unmanaged protection set associated with that volume
     */
    private UnManagedProtectionSet getUnManagedProtectionSet(UnManagedVolume unManagedVolume) {
        // Find the UnManagedProtectionSet associated with this unmanaged volume
        List<UnManagedProtectionSet> umpsets = CustomQueryUtility.getUnManagedProtectionSetByUnManagedVolumeId(_dbClient, unManagedVolume.getId().toString());
        Iterator<UnManagedProtectionSet> umpsetsItr = umpsets.iterator();
        if (!umpsetsItr.hasNext()) {
            _logger.error("Unable to find unmanaged protection set associated with volume: " + unManagedVolume.getId());
            return null;
        }
        
        return umpsetsItr.next();
    }
    
    @Override
    protected void validateAutoTierPolicy(String autoTierPolicyId, UnManagedVolume unManagedVolume, VirtualPool vPool) {
        super.validateAutoTierPolicy(autoTierPolicyId, unManagedVolume, vPool);
    }

    /**
     * Decorates the block objects with RP properties.
     * 
     * @param blockObject block object that is the result of the ingest
     * @param unManagedVolume unmanaged volume with RP properties (VolumeInformation) on it
     */
    private void decorateBlockObjectWithRPProperties(BlockObject blockObject, UnManagedVolume unManagedVolume) {
        Volume volume = (Volume) blockObject;
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PERSONALITY.toString(), unManagedVolumeInformation);
        if (Volume.PersonalityTypes.SOURCE.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.SOURCE.toString());
            // The managed source volume needs to store the list of RP targets, however that is
            // stored in the unmanaged volume target volume, so you'll find the management/update
            // of the source volume with the RP targets in the UnManagedVolume processing for TARGET
            // volumes (currently below)
        } else if (Volume.PersonalityTypes.TARGET.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.TARGET.toString());
            
            // Add this target volume to the RP source's target list
            String rpSourceVolume = PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.RP_SOURCE_VOLUME.toString(), unManagedVolumeInformation);
            if (rpSourceVolume == null) {
                _logger.error("Unable to find unmanaged source volume for target volume: " + volume.getLabel());
                return;
            }
            
            // Find the unmanaged source volume
            UnManagedVolume rpSourceUnManagedVolume = _dbClient.queryObject(UnManagedVolume.class, URI.create(rpSourceVolume));
            if (rpSourceUnManagedVolume == null) {
                _logger.error("Unable to get source unmanaged volume from DB for ID: " + rpSourceVolume);
                return;
            }
            
            // Find the managed source volume
            String volumeNativeGuid = rpSourceUnManagedVolume.getNativeGuid().replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                    VolumeIngestionUtil.VOLUME);
            BlockObject rpSourceManagedBlockObject = VolumeIngestionUtil.checkIfVolumeExistsInDB(volumeNativeGuid, _dbClient);
            if (!(rpSourceManagedBlockObject instanceof Volume)) {
                _logger.error("Source block object found is not a block object.  Can not populate target volumes: " + volumeNativeGuid);
                return;
            }
            
            // Add the target volume to this source volume
            Volume rpSourceManagedVolume = (Volume)rpSourceManagedBlockObject;
            if (rpSourceManagedVolume.getRpTargets() == null) {
                rpSourceManagedVolume.setRpTargets(new StringSet());
            }
            rpSourceManagedVolume.getRpTargets().add(volume.getId().toString());
            _dbClient.updateAndReindexObject(rpSourceManagedVolume);
            
        } else if (Volume.PersonalityTypes.METADATA.toString().equalsIgnoreCase(type)) {
            volume.setPersonality(PersonalityTypes.METADATA.toString());
        }
        
        // Set the various RP related fields
        String rpCopyName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_COPY_NAME.toString(), unManagedVolumeInformation);
        String rpRSetName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_RSET_NAME.toString(), unManagedVolumeInformation);
        String rpProtectionSystem = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_PROTECTIONSYSTEM.toString(), unManagedVolumeInformation);
        String rpInternalSiteName = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.RP_INTERNAL_SITENAME.toString(), unManagedVolumeInformation);
        
        volume.setRpCopyName(rpCopyName); // This comes from UNMANAGED_CG discovery of Protection System
        volume.setRSetName(rpRSetName); // This comes from UNMANAGED_CG discovery of Protection System
        volume.setInternalSiteName(rpInternalSiteName); // This comes from UNMANAGED_CG discovery of Protection System
        volume.setProtectionController(URI.create(rpProtectionSystem)); // This comes from UNMANAGED_CG discovery of Protection System
        if (null != _dbClient.queryObject(Volume.class, volume.getId())) {
            _dbClient.updateAndReindexObject(volume);
        } else {
            _dbClient.createObject(volume);
        }
    }

    /**
     * Create the managed protection set associated with the ingested RP volumes.
     * Also, as a side-effect, insert the protection set ID into each of the impacted volumes.
     * 
     * @param umpset unmanaged protection set (cg)
     * @return a new protection set object
     */
    private ProtectionSet createProtectionSet(UnManagedProtectionSet umpset) {
     StringSetMap unManagedCGInformation = umpset.getCGInformation();
     String rpProtectionId = PropertySetterUtil.extractValueFromStringSet(
             SupportedCGInformation.PROTECTION_ID.toString(), unManagedCGInformation);

     ProtectionSet pset = new ProtectionSet();
     pset.setId(URIUtil.createId(ProtectionSet.class));
     pset.setLabel(umpset.getCgName());
     pset.setProtectionId(rpProtectionId);
     pset.setProtectionStatus(ProtectionStatus.ENABLED.toString());
     pset.setProtectionSystem(umpset.getProtectionSystemUri());
     
     if (umpset.getManagedVolumeIds() != null) {
         for (String volumeID : umpset.getManagedVolumeIds()) {
             Volume volume = _dbClient.queryObject(Volume.class, URI.create(volumeID));
             if (volume == null) {
                 _logger.error("Unable to retrieve volume : " + volume + " from database.  Ignoring in protection set ingestion.");
                 continue;
             }
          
             // Set the project value
             if (pset.getProject() == null) {
                 pset.setProject(volume.getProject().getURI());
             }
             
             // Add all volumes (managed only) to the new protection set
             if (pset.getVolumes() == null) {
                 pset.setVolumes(new StringSet());
             }

             pset.getVolumes().add(volumeID);
         }         
     }

     _dbClient.createObject(pset);
     return pset;
    }
    
    /**
     * Create the block consistency group object associated with the CG as part of ingestion.
     * 
     * @param pset protection set object
     * @return a new block consistency group object
     */
    private BlockConsistencyGroup createBlockConsistencyGroup(ProtectionSet pset) {
        BlockConsistencyGroup cg = new BlockConsistencyGroup();
        cg.setId(URIUtil.createId(BlockConsistencyGroup.class));
        cg.setLabel(pset.getLabel());
        Project project = _dbClient.queryObject(Project.class, pset.getProject());
        cg.setProject(new NamedURI(pset.getProject(), project.getLabel()));
        StringSet types = new StringSet();
        types.add(BlockConsistencyGroup.Types.RP.toString());
        cg.setRequestedTypes(types);
        cg.setTypes(types);
        cg.setTenant(project.getTenantOrg());
        cg.addSystemConsistencyGroup(pset.getProtectionSystem().toString(), pset.getLabel());
        _dbClient.createObject(cg);
        return cg;
    }
    
    /**
     * This method will perform all of the final decorations (attribute setting) on the Volume
     * object after creating the required BlockConsistencyGroup and ProtectionSet objects.
     * 
     * Fields such as rpCopyName and rSetName were already filled in when we did the ingest of 
     * the volume itself.  In this method, we worry about stitching together all of the object
     * references within the Volume object so it will act like a native CoprHD-created RP volume.
     * 
     * @param pset protection set
     * @param cg block consistency group
     */
    private void decorateVolumeInformationFinalIngest(ProtectionSet pset, BlockConsistencyGroup cg) {
        if (pset.getVolumes() == null) {
            // TODO: Throw exception
            _logger.error("No volumes found in protection set: " + pset.getLabel() + ", cannot process ingestion");
            return;
        }
        
        // Set references to protection set/CGs properly in each volume
        for (String volumeID: pset.getVolumes()) {
            Volume volume = _dbClient.queryObject(Volume.class, URI.create(volumeID));
            if (volume == null) {
                // TODO: Throw exception
                _logger.error("Volume " + volumeID + " could not be found in the database.  Cannot process ingestion.");
                return;
            }
            
            volume.setConsistencyGroup(cg.getId());
            volume.setProtectionSet(new NamedURI(pset.getId(), pset.getLabel()));
        }
    }
    
    /**
     * Validates the UnManagedVolume Properties to make sure it has everything needed to be ingested.
     * 
     * @param unManagedVolume unmanaged volume 
     * @param virtualArray virtual array 
     * @param virtualPool virtual pool
     * @param project project
     */
    private void validateUnManagedVolumeProperties(UnManagedVolume unManagedVolume, VirtualArray virtualArray,
            VirtualPool virtualPool, Project project) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();

        
        /*
        URI rdfGroupId = getRDFGroupBasedOnPersonality(unManagedVolumeInformation);
        // To make sure rdfGroup is populated for both R1 & R2 volumes.
        if (null == rdfGroupId) {
            _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {} as not able to find RDFGroup.",
                    unManagedVolume.getNativeGuid());
            throw IngestionException.exceptions.unmanagedVolumeRDFGroupMissing(unManagedVolume.getNativeGuid());
        }
        RemoteDirectorGroup rdfGroup = _dbClient.queryObject(RemoteDirectorGroup.class, rdfGroupId);
        // Validate the project Name with the unmanaged volume rdfGroup name.
        if (null == rdfGroup.getLabel() || !rdfGroup.getLabel().equalsIgnoreCase(project.getLabel())) {
            _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {} due to mismatch in rdfgroup name",
                    unManagedVolume.getNativeGuid());
            throw IngestionException.exceptions.unmanagedVolumeRDFGroupMismatch(unManagedVolume.getNativeGuid(),
                    rdfGroup.getLabel(), project.getLabel());
        }
        
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(), unManagedVolumeInformation);
        if (null == type) {
            _logger.warn("SRDF Volume ingestion failed for unmanagedVolume {}", unManagedVolume.getNativeGuid());
            throw IngestionException.exceptions.unmanagedVolumeVolumeTypeNotSet(unManagedVolume.getNativeGuid());
        }
        _logger.info("Type {} Source Native Guid {}", type, unManagedVolume.getNativeGuid());

        if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)) {
            validateSourceVolumeVarrayWithTargetVPool(unManagedVolume, virtualPool);
        } else if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
            validateTargetVolumeVpoolWithSourceVolume(unManagedVolume, virtualArray);
        }
        */

    }

    /**
     * Return the rdfGroupId based on the personality.
     * For source volume, we will not have RDFGroup hence we should get it from its targets.
     * 
     * @param unManagedVolumeInformation
     * @return
     */
    private URI getRDFGroupBasedOnPersonality(StringSetMap unManagedVolumeInformation) {
        String type = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_VOLUME_TYPE.toString(), unManagedVolumeInformation);
        URI rdfGroupId = null;
        if (RemoteMirrorObject.Types.SOURCE.toString().equalsIgnoreCase(type)) {
            StringSet targetUnManagedVolumeGuids = unManagedVolumeInformation.get(SupportedVolumeInformation.REMOTE_MIRRORS
                    .toString());
            if (null != targetUnManagedVolumeGuids && !targetUnManagedVolumeGuids.isEmpty()) {
                StringSet targetVolumeNativeGuids = VolumeIngestionUtil.getListofVolumeIds(targetUnManagedVolumeGuids);
                List<URI> targetUris = VolumeIngestionUtil.getVolumeUris(targetVolumeNativeGuids, _dbClient);
                if (null == targetUris || targetUris.isEmpty()) {
                    List<URI> unmanagedTargetVolumes = VolumeIngestionUtil.getUnManagedVolumeUris(targetUnManagedVolumeGuids, _dbClient);
                    for (URI targetUmv : unmanagedTargetVolumes) {
                        _logger.info("RDFGroup Found using unmanaged Target volume {}", targetUmv);
                        UnManagedVolume umv = _dbClient.queryObject(UnManagedVolume.class, targetUmv);
                        rdfGroupId = URI.create(PropertySetterUtil.extractValueFromStringSet(
                                SupportedVolumeInformation.REMOTE_MIRROR_RDF_GROUP.toString(), umv.getVolumeInformation()));
                        break;
                    }
                } else {
                    // If targets are already ingested.
                    List<Volume> targetVolumes = _dbClient.queryObject(Volume.class, targetUris);
                    if (null != targetVolumes && !targetVolumes.isEmpty()) {
                        for (Volume targetVolume : targetVolumes) {
                            _logger.info("RDFGroup Found for using ingested Target volumes {}.", targetVolume.getNativeGuid());
                            rdfGroupId = targetVolume.getSrdfGroup();
                            break;
                        }
                    }
                }
            }
            
        } else if (RemoteMirrorObject.Types.TARGET.toString().equalsIgnoreCase(type)) {
            rdfGroupId = URI.create(PropertySetterUtil.extractValueFromStringSet(
                    SupportedVolumeInformation.REMOTE_MIRROR_RDF_GROUP.toString(), unManagedVolumeInformation));
        }
        
        return rdfGroupId;
    }

    /**
     * Validate the SourceVolume VArray details with ingested target volumes
     * VArray.
     * 
     * @param unManagedVolume
     * @param VirtualPool
     * @return
     */
    private void validateSourceVolumeVarrayWithTargetVPool(UnManagedVolume unManagedVolume, VirtualPool sourceVPool) {
        StringSetMap unManagedVolumeInformation = unManagedVolume.getVolumeInformation();
        // find whether all targets are ingested
        StringSet targetUnManagedVolumeGuids = unManagedVolumeInformation.get(SupportedVolumeInformation.REMOTE_MIRRORS
                .toString());
        if (null != targetUnManagedVolumeGuids && !targetUnManagedVolumeGuids.isEmpty()) {
            StringSet targetVolumeNativeGuids = VolumeIngestionUtil.getListofVolumeIds(targetUnManagedVolumeGuids);
            // check whether target exists
            List<URI> targetUris = VolumeIngestionUtil.getVolumeUris(targetVolumeNativeGuids, _dbClient);
            if (null == targetUris || targetUris.isEmpty()) {
                _logger.info("None of the targets ingested for source volume: {}", unManagedVolume.getNativeGuid());
            } else {
                List<Volume> targetVolumes = _dbClient.queryObject(Volume.class, targetUris);
                for (Volume targetVolume : targetVolumes) {
                    Map<URI, VpoolRemoteCopyProtectionSettings> settings = sourceVPool.getRemoteProtectionSettings(
                            sourceVPool, _dbClient);
                    if (null == settings || settings.size() == 0
                            || !settings.containsKey(targetVolume.getVirtualArray())) {
                        _logger.info(
                                "Target Volume's VArray {} is not matching already ingested source volume virtual pool's remote VArray {}",
                                targetVolume.getVirtualArray(), Joiner.on(",").join(settings.keySet()));
                        throw IngestionException.exceptions.unmanagedSRDFSourceVolumeVArrayMismatch(
                                unManagedVolume.getLabel(), targetVolume.getVirtualArray().toString());
                    }
                }
            }
        }
    }

    /**
     * Validate the Target Volume VirtualArray with the Source Volume VPool
     * VirtualArray.
     * 
     * @param type
     * @param unManagedVolume
     * @param virtualArray
     */
    private void validateTargetVolumeVpoolWithSourceVolume(UnManagedVolume unManagedVolume, VirtualArray virtualArray) {
        String sourceUnManagedVolumeId = PropertySetterUtil.extractValueFromStringSet(
                SupportedVolumeInformation.REMOTE_MIRROR_SOURCE_VOLUME.toString(),
                unManagedVolume.getVolumeInformation());
        String sourceVolumeId = sourceUnManagedVolumeId.replace(VolumeIngestionUtil.UNMANAGEDVOLUME,
                VolumeIngestionUtil.VOLUME);
        List<URI> sourceUris = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVolumeNativeGuidConstraint(sourceVolumeId));
        if (sourceUris.isEmpty()) {
            _logger.info("Source {} Not found for target {}", sourceVolumeId, unManagedVolume.getNativeGuid());
        } else {
            // if source volume is ingested, then
            Volume sourceVolume = _dbClient.queryObject(Volume.class, sourceUris.get(0));
            // check whether the source Volume's VPool is actually having this
            // target Volume's varray
            // specified as remote
            VirtualPool sourceVPool = _dbClient.queryObject(VirtualPool.class, sourceVolume.getVirtualPool());
            Map<URI, VpoolRemoteCopyProtectionSettings> settings = sourceVPool.getRemoteProtectionSettings(sourceVPool,
                    _dbClient);
            if (null == settings || settings.isEmpty() || !settings.containsKey(virtualArray.getId())) {
                _logger.info(
                        "Target Volume's VArray {} is not matching already ingested source volume virtual pool's remote VArray ",
                        virtualArray.getId());
                throw IngestionException.exceptions.unmanagedSRDFTargetVolumeVArrayMismatch(
                        unManagedVolume.getLabel(), sourceVolume.getVirtualArray().toString());
            }
        }
    }
}
