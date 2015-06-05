/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.db.client.upgrade.callbacks;

import static com.emc.storageos.db.client.constraint.AlternateIdConstraint.Factory.getVolumesByConsistencyGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockConsistencyGroup.Types;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ProtectionSet;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.util.BlockConsistencyGroupUtils;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.client.util.NullColumnValueGetter;

/**
 * Migration handler to migrate BlockObject consistencyGroup to the new
 * consistencyGroups list field.
 * 
 */
public class BlockObjectMultipleConsistencyGroupsMigration extends BaseCustomMigrationCallback {
    private static final Logger log = LoggerFactory.getLogger(BlockObjectMultipleConsistencyGroupsMigration.class);
    
    @Override
    public void process() {
        migrateRpConsistencyGroups();
        migrateBlockConsistencyGroups();
        migrateBlockVolumes();
        migrateBlockMirrors();
        migrateBlockSnapshots();
    }
    
    /**
     * Migrates the RP/RP+VPlex BlockConsistencyGroups and BlockObjects.  
     */
    private void migrateRpConsistencyGroups() {
        log.info("Migrating RP+VPlex BlockConsistencyGroup objects and Volume references.");
        
        List<URI> protectionSetURIs = dbClient.queryByType(ProtectionSet.class, true);
        
        for (URI protectionSetURI : protectionSetURIs) {
            log.info("Scanning ProtectionSets for RP+VPlex Volume references.");
            ProtectionSet protectionSet = dbClient.queryObject(ProtectionSet.class, protectionSetURI);
            
            if (protectionSet != null && protectionSet.getVolumes() != null) {
                // Get the first volume to determine if it has 2 BlockConsistencyGroup references.
                // This would identify that the volume, and all volumes in this ProtectionSet
                // are RP+VPlex volumes.
                if (protectionSet.getVolumes() != null && !protectionSet.getVolumes().isEmpty()) {
                    Volume firstVolume = dbClient.queryObject(Volume.class, 
                            URI.create(protectionSet.getVolumes().iterator().next()));
                    BlockConsistencyGroup primaryCg = null;

                    // RP+VPlex volumes will have references to exactly 2 BlockConsistencyGroups.  Every
                    // other type of volume will only reference a single BlockConsistencyGroup.
                    // Note: It is possible that firstVolume can be null because of an issue with RP
                    // volume delete (see CTRL-9806).  In this case, the delete would fail, leaving the
                    // ProtectionSet referencing a volume that was actually deleted from the database. So,
                    // if firstVolume is null, we simply ignore it.
                    if (firstVolume != null && firstVolume.getConsistencyGroups() != null 
                            && !firstVolume.getConsistencyGroups().isEmpty()
                            && firstVolume.getConsistencyGroups().size() == 2) {
                        log.info("Found RP+VPlex ProtectionSet {}.  Preparing to migrated referenced RP+VPlex "
                                + "volumes and associated BlockConsistencyGroups.", protectionSet.getLabel());
                        // There are references to 2 different BlockConsistencyGroup objects,
                        // so this is an RP+VPlex volume.
                        Iterator<String> cgUriItr = firstVolume.getConsistencyGroups().iterator();
                        while (cgUriItr.hasNext()) {
                            BlockConsistencyGroup cg = 
                                    dbClient.queryObject(BlockConsistencyGroup.class, URI.create(cgUriItr.next()));
                            // The RP BlockConsistencyGroup will be the primary BlockConsistencyGroup so we must
                            // first find that.  All VPlex BlockConsistencyGroups for Volumes in the protection set
                            // will be mapped to this primary BlockConsistencyGroup.
                            if (cg.getType().equals(Types.RP.name())) {
                                log.info("Primary RP BlockConsistencyGroup {} found for ProtectionSet {}.", cg.getLabel(), protectionSet.getLabel());
                                primaryCg = cg;
                                // Add the RP type
                                primaryCg.addConsistencyGroupTypes(Types.RP.name());
                                break;
                            }
                        }

                        // Migrate the protection system/cg entry that replaces the use of the deviceName field.
                        primaryCg.addSystemConsistencyGroup(protectionSet.getProtectionSystem().toString(), "ViPR-" + primaryCg.getLabel());
                        
                        Iterator<String> volumeUriItr = protectionSet.getVolumes().iterator();
                        
                        while (volumeUriItr.hasNext()) {
                            String volumeUri = volumeUriItr.next();
                            Volume volume = dbClient.queryObject(Volume.class, URI.create(volumeUri));
                            
                            if (volume != null) {                           
                                log.info("Scanning volume {} for protection set {}.", volume.getLabel(), protectionSet.getLabel());
                                
                                // Get the volume's VPlex CG, copy the info in the primary CG, and remove
                                // the VPlex CG from Cassandra.
                                BlockConsistencyGroup vplexCg = null;
                                
                                if (volume.getConsistencyGroups() != null && !volume.getConsistencyGroups().isEmpty()) {
                                    cgUriItr = volume.getConsistencyGroups().iterator();
                                    while (cgUriItr.hasNext()) {
                                        BlockConsistencyGroup cg = 
                                                dbClient.queryObject(BlockConsistencyGroup.class, URI.create(cgUriItr.next()));
                                        if (cg.getType().equals(Types.VPLEX.name())) {
                                            vplexCg = cg;
                                            log.info("Volume {} belongs to VPLEX BlockConsistencyGroup {}.", volume.getLabel(), cg.getLabel());
                                            break;
                                        }
                                    }
                                }
                                
                                if (vplexCg != null && volume.getAssociatedVolumes() != null) {
                                    // Copy the VPlex CG info over to the primary CG
                                    StorageSystem vplexStorageSystem = dbClient.queryObject(StorageSystem.class, vplexCg.getStorageController());
                                    String clusterId = getVPlexClusterFromVolume(volume);
                                    
                                    primaryCg.addSystemConsistencyGroup(vplexStorageSystem.getId().toString(), BlockConsistencyGroupUtils.buildClusterCgName(clusterId, vplexCg.getLabel()));
                                    
                                    if (primaryCg.getStorageController() == null) {
                                        primaryCg.setStorageController(vplexStorageSystem.getId());
                                    }
                                    
                                    if (!primaryCg.getTypes().contains(Types.VPLEX.name())) {
                                        // Add the VPlex type
                                        primaryCg.addConsistencyGroupTypes(Types.VPLEX.name());
                                    }
                                    
                                    primaryCg.setType(NullColumnValueGetter.getNullStr());
                                    primaryCg.setDeviceName(NullColumnValueGetter.getNullStr());
                                    
                                    // Persist the changes to the primary CG, update the volume reference to the single
                                    // primary CG, and remove the VPlex CG.
                                    dbClient.persistObject(primaryCg);
                                    
                                    volume.setConsistencyGroup(primaryCg.getId());
                                    StringSet cgs = volume.getConsistencyGroups();
                                    cgs.remove(vplexCg.getId().toString());
                                    volume.setConsistencyGroups(cgs);
                                    
                                    dbClient.persistObject(volume);
                                    log.info("Volume {} fields have been migrated.", volume.getLabel());
                                    
                                    dbClient.markForDeletion(vplexCg);
                                    log.info("VPlex BlockConsistencyGroup {} has been migrated over to RP BlockConsistencyGroup and has been deleted.", vplexCg.getLabel(), primaryCg.getLabel());
                                }
                            }
                        }
                    } else if (firstVolume != null && firstVolume.getConsistencyGroups() != null 
                            && !firstVolume.getConsistencyGroups().isEmpty()
                            && firstVolume.getConsistencyGroups().size() == 1) {
                        Iterator<String> cgUriItr = firstVolume.getConsistencyGroups().iterator();
                        BlockConsistencyGroup cg = 
                                dbClient.queryObject(BlockConsistencyGroup.class, URI.create(cgUriItr.next()));
                        
                        // Migration logic for RP only BlockConsistencyGroup
                        cg.addConsistencyGroupTypes(cg.getType());
                        // In ViPR 2.0, the RP deviceName was not being prefixed with 'ViPR-', which is what is used on 
                        // the actual RP appliance for the CG name.
                        cg.addSystemConsistencyGroup(protectionSet.getProtectionSystem().toString(), "ViPR-" + cg.getLabel());
                        
                        // Remove type and deviceName fields
                        cg.setType(NullColumnValueGetter.getNullStr());
                        cg.setDeviceName(NullColumnValueGetter.getNullStr());
                        
                        dbClient.persistObject(cg);
                    }
                }
            }
        }
    }
    
    /**
     * Migrates all BlockConsistencyGroups.  Migrates the type and deviceName fields
     * too types and deviceNames fields.  For VPlex only BlockConsistencyGroup objects,
     * a mapping of VPlex storage system to VPlex cluster/cg name is added.
     */
    private void migrateBlockConsistencyGroups() {
        log.info("Migrating BlockConsistencyGroup objects.");
        List<URI> cgURIs = dbClient.queryByType(BlockConsistencyGroup.class, true);
        
        for (URI cgURI : cgURIs) {
            BlockConsistencyGroup cg = dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
            
            // Determine if the types field has not been set.  No types specified indicates 
            // this BlockConsistencyGroup has not been migrated.  We've already migrated the
            // RP+VPlex BlockConsistencyGroups at this point.
            if (cg.getTypes() == null || cg.getTypes().isEmpty()) {
                log.info("Migrating BlockConsistencyGroup {}.", cg.getLabel());
                
                if (cg.getType() != null) {
                	cg.addConsistencyGroupTypes(cg.getType());
                }
                
                // If this is a VPlex BlockConsistencyGroup, we have a bit more work to do.
                // We need to keep a map of the volume's storage controller to VPlex cluster/cg.
                if (isVPlexCG(cg)) {
                    log.info("Migrating fields for VPlex BlockConsistencyGroup {}.", cg.getLabel());
                    
                    // Set the type, as VPLEX CGS prior to 2.2 do not have a type set.
                    cg.addConsistencyGroupTypes(Types.VPLEX.name());
                    
                    // Get the VPlex volumes associated with this CG. 
                    final List<Volume> activeCGVolumes = CustomQueryUtility
                            .queryActiveResourcesByConstraint(dbClient, Volume.class,
                             getVolumesByConsistencyGroup(cg.getId().toString()));
                    
                    log.info("Found " + activeCGVolumes.size() + " volumes that belong to BlockConsistencyGroup " + cg.getLabel());
                    
                    for (Volume cgVolume : activeCGVolumes) {
                        // Look at each of the associated volumes and add a mapping of VPlex storage
                        // system to cluster/cg.
                        String clusterId = getVPlexClusterFromVolume(cgVolume);
                        log.info("Adding storage system to cluster/cg mapping for VPlex BlockConsistencyGroup " + cg.getLabel());
                        cg.addSystemConsistencyGroup(cgVolume.getStorageController().toString(), BlockConsistencyGroupUtils.buildClusterCgName(clusterId, cg.getLabel()));
                    }
                } else {
                    // Non-RP/Non-VPLEX/Non-RP+VPLEX
                    // Add an entry for the storage system -> consistency group name
                	if (cg.getStorageController() != null) {
                		cg.addSystemConsistencyGroup(cg.getStorageController().toString(), cg.getDeviceName());
                	}
                }
                
                // Remove type and deviceName fields
                cg.setType(NullColumnValueGetter.getNullStr());
                cg.setDeviceName(NullColumnValueGetter.getNullStr());
                
                dbClient.persistObject(cg);
                log.info("Migration of BlockConsistencyGroup {} complete.", cg.getLabel());
            }
        }
    }
    
    /**
     * Update the Volume object to migrate the old consistencyGroups field
     * into the new consistencyGroup list field.
     */
    private void migrateBlockVolumes() {
        log.info("Migrating BlockConsistencyGroup references on Volume objects."); 
        DbClient dbClient = getDbClient();
        List<URI> volumeURIs = dbClient.queryByType(Volume.class, false);
        Iterator<Volume> volumes = dbClient.queryIterativeObjects(Volume.class, volumeURIs, true);
        
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        
        while (volumes.hasNext()) {
            blockObjects.add(volumes.next());
        }
        
        migrateBlockObjects(blockObjects);
    }
    
    /**
     * Update the BlockMirror object to migrate the old consistencyGroups field
     * into the new consistencyGroup list field.
     */
    private void migrateBlockMirrors() {
        log.info("Migrating BlockConsistencyGroup references on BlockMirror objects."); 
        DbClient dbClient = getDbClient();
        List<URI> blockMirrorURIs = dbClient.queryByType(BlockMirror.class, false);
        Iterator<BlockMirror> blockMirrors = dbClient.queryIterativeObjects(BlockMirror.class, blockMirrorURIs, true);
        
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        
        while (blockMirrors.hasNext()) {
            blockObjects.add(blockMirrors.next());
        }
        
        migrateBlockObjects(blockObjects);
    }
    
    /**
     * Update the BlockSnapshot object to migrate the old consistencyGroups field
     * into the new consistencyGroup list field.
     */
    private void migrateBlockSnapshots() {
        log.info("Migrating BlockConsistencyGroup references on BlockSnapshot objects."); 
        DbClient dbClient = getDbClient();
        List<URI> blockSnapshotURIs = dbClient.queryByType(BlockSnapshot.class, false);
        Iterator<BlockSnapshot> blockSnapshots = dbClient.queryIterativeObjects(BlockSnapshot.class, blockSnapshotURIs, true);
        
        List<BlockObject> blockObjects = new ArrayList<BlockObject>();
        
        while (blockSnapshots.hasNext()) {
            blockObjects.add(blockSnapshots.next());
        }
        
        migrateBlockObjects(blockObjects);
    }
    
    /**
     * Gets the VPlex cluster Id for a given VPlex virtual volume.
     * @param virtualVolume
     * @return
     */
    private String getVPlexClusterFromVolume(Volume virtualVolume) {
        String clusterId = null;

        if (virtualVolume != null && virtualVolume.getNativeId() != null) {
            String[] nativeIdSplit = virtualVolume.getNativeId().split("/");   
            clusterId = nativeIdSplit[2];
        }

        return clusterId;
    }
    
    /**
     * Performs the migration of BlockObjects.  Ensures the BlockConsistencyGroup.consistencyGroups
     * field gets migrated to BlockConsistencyGroup.consistencyGroup.
     * @param blockObjects
     */
    private void migrateBlockObjects(List<BlockObject> blockObjects) {
        for (BlockObject blockObject : blockObjects) {
        	String consistencyGroups = "No consistency groups to migrate";
        	
            // Only migrate BlockConsistencyGroup references if the BlockObject
            // references a single BlockConsistencyGroup.  RP+VPlex BlockObjects
            // are the only ones that would reference 2 BlockConsistencyGroups and
            // those would have been handled earlier via migrateRpVplexConsistencyGroups()
            if (blockObject.getConsistencyGroups() != null
                    && !blockObject.getConsistencyGroups().isEmpty()
                    && blockObject.getConsistencyGroups().size() == 1) {
            	
                consistencyGroups = blockObject.getConsistencyGroups().toString();
                String cgUriStr = blockObject.getConsistencyGroups().iterator().next();
                blockObject.setConsistencyGroup(URI.create(cgUriStr));
    
                // Remove the old reference
                StringSet cgs = blockObject.getConsistencyGroups();
                cgs.remove(cgUriStr);
                blockObject.setConsistencyGroups(cgs);
                    
                dbClient.persistObject(blockObject);
            }
                
            log.info("Migrated BlockConsistencyGroups [{}] on BlockObject (label={}).", 
            		consistencyGroups, blockObject.getLabel());
        }
    }
    
    /**
     * Determines if the passed CG is a VPLEX CG.
     * 
     * @param cg A reference to the CG.
     * 
     * @return true if the CG is a VPLEX CG, false otherwise.
     */
    private boolean isVPlexCG(BlockConsistencyGroup cg) {
        boolean isVPlex = false;
        // If this is a VPlex BlockConsistencyGroup, we have a bit more work to do.
        // We need to keep a map of the volume's storage controller to VPlex cluster/cg.
        URI cgSystemURI = cg.getStorageController();
        if (!NullColumnValueGetter.isNullURI(cgSystemURI)) {
            StorageSystem cgSystem = dbClient.queryObject(StorageSystem.class, cgSystemURI);
            if ((cgSystem != null) && (DiscoveredDataObject.Type.vplex.name().equals(cgSystem.getSystemType()))) {
                isVPlex = true;
            }
        }
        
        return isVPlex;
    }
}
