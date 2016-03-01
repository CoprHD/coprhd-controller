/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.google.common.base.Joiner;

/**
 * Data structure do be used for placing volumes into existing ExportMasks.
 * It will be shared between the VplexBackEndManager and the VplexBackEndMaskingOrchestrator.
 *
 */
public class ExportMaskPlacementDescriptor {

    // INPUT
    // ExportMask to ExportGroup mapping
    // This is a reference to the ExportGroup to which an ExportMask should belong
    private Map<URI, ExportGroup> maskExportGroupMap;

    // INPUT
    // Volume URI to Volume object mapping.
    // These are volumes that need to be placed
    private Map<URI, Volume> volumesToPlace;

    // INPUT - Default VOLUMES_TO_SINGLE_MASK
    // This is used to indicate how placement should be done
    private PlacementHint placementHint;

    // INPUT: Initiators that are applicable to this placement
    private Collection<Initiator> initiators;

    // INPUT: VPlex array to which the initiators belong
    private StorageSystem vplex;

    // INPUT: Backend array where the ExportMasks will be evaluated
    private StorageSystem backendArray;

    // INPUT: VirtualArray that covers the VPlex and the backend StorageSystem
    private URI virtualArray;

    // INPUT: Tenant to which the objects, such as the volumes, belong
    private URI tenant;

    // INPUT: Project to which the object, such as the volumes, belong
    private URI project;

    // OUTPUT: getter is read/write
    // ExportMask URI to ExportMask object mapping.
    // These are the ExportMasks that we have to work with for placement
    private Map<URI, ExportMask> masks;

    // OUTPUT: getter is ead-only
    // ExportMask URI to Volume mapping
    // This is the resultant mapping. Each ExportMask URI key has a Map<URI, Volume> value that
    // indicates that the volumes that are supposed placed.
    private Map<URI, Map<URI, Volume>> maskToVolumes;

    // OUTPUT: getter is read-only
    // Volume URI to Volume object mapping.
    // These are volumes that could not be placed in an ExportMask.
    private Map<URI, Volume> unplacedVolumes;

    // OUTPUT:
    // Mapping of ExportMask URI to its ExportMaskPolicy
    private Map<URI, ExportMaskPolicy> exportMaskPolicy;

    // OUTPUT:
    // This is a map of a key String to a set of ExportMasks that are
    // equivalent in terms of properties, but # of volumes can differ.
    // The key String is generated based on the ExportMask's ExportMaskPolicy
    // components.
    private Map<String, Set<URI>> equivalentMasks;

    // OUTPUT:
    // Mapping of volume to alternative ExportMask placements
    private Map<URI, Set<URI>> volumeToAlternativeMasks;

    public enum PlacementHint {
        VOLUMES_TO_SINGLE_MASK, VOLUMES_TO_SEPARATE_MASKS
    }

    /**
     * Creates a new instance of ExportMaskPlacementDescriptor with provided parameters as context
     *
     * @param tenantURI [IN] - Tenant to which the volumes belong
     * @param projectURI [IN] - Project to which the volumes belon
     * @param vplex [IN] - Vplex array to which this placement applies
     * @param array [IN] - Backend array to which this placement applies
     * @param virtualArrayURI [IN] - Vplex+Backend array associated VirtualArray
     * @param volumeMap [IN] - Volumes to be placed
     * @param initiators [IN] - VPlex initiators to be used for ExportMask selection (if ExportMask already exists)
     * @return ExportMaskPlacementDescriptor
     */
    public static ExportMaskPlacementDescriptor create(URI tenantURI, URI projectURI, StorageSystem vplex, StorageSystem array,
            URI virtualArrayURI,
            Map<URI, Volume> volumeMap, Collection<Initiator> initiators) {
        ExportMaskPlacementDescriptor descriptor = new ExportMaskPlacementDescriptor(volumeMap);
        descriptor.setTenant(tenantURI);
        descriptor.setProject(projectURI);
        descriptor.setVplex(vplex);
        descriptor.setVirtualArray(virtualArrayURI);
        descriptor.setBackendArray(array);
        descriptor.setInitiators(initiators);
        descriptor.exportMaskPolicy = new HashMap<>();
        descriptor.equivalentMasks = new HashMap<>();
        descriptor.volumeToAlternativeMasks = new HashMap<>();
        return descriptor;
    }

    /**
     * Initialize the placement with the set of volumes
     *
     * @param volumes
     *            [IN] - Volume URI to Volume object mapping
     */
    private ExportMaskPlacementDescriptor(Map<URI, Volume> volumes) {
        if (masks != null) {
            this.masks = new HashMap<>(masks);
        }
        this.unplacedVolumes = new HashMap<>(volumes);
        this.volumesToPlace = new HashMap<>(volumes);
        this.maskToVolumes = new HashMap<>();
        this.maskExportGroupMap = new HashMap<>();
        this.placementHint = PlacementHint.VOLUMES_TO_SINGLE_MASK;
        this.exportMaskPolicy = new HashMap<>();
        this.equivalentMasks = new HashMap<>();
        this.volumeToAlternativeMasks = new HashMap<>();
    }

    /**
     * Create a mapping for the exportMaskURI to the set 'volumes'
     * 
     * @param exportMaskURI
     *            [IN] - ExportMask URI
     * @param volumes
     *            [IN] - Mapping of Volume URI to Volume object
     */
    public void placeVolumes(URI exportMaskURI, Map<URI, Volume> volumes) {
        maskToVolumes.put(exportMaskURI, new HashMap<>(volumes));
        // Once a volume placement has taken place, we should remove it from the unplacedVolumes list
        for (URI volumeId : volumes.keySet()) {
            unplacedVolumes.remove(volumeId);
        }
    }

    /**
     * Remove the placement of the volume from the mappings for the ExportMask and
     * also remove the maskToVolumes entry if we remove the last volume for that
     * ExportMask URI key.
     *
     * @param volumeURI [IN] - Volume URI reference to be removed
     * @param exportMaskURI [IN] - ExportMask URI
     */
    public void unplaceVolumeFromMask(URI volumeURI, URI exportMaskURI) {
        Map<URI, Volume> map = maskToVolumes.get(exportMaskURI);
        if (map != null) {
            map.remove(volumeURI);
            if (map.isEmpty()) {
                maskToVolumes.remove(exportMaskURI);
            }
        }
    }

    /**
     * Get the set of volumes that has been mapped to the ExportMask
     * 
     * @param exportMaskURI
     *            [IN] - ExportMask URI
     * 
     * @return Volume URI to Volume object map (Read-only)
     */
    public Map<URI, Volume> getPlacedVolumes(URI exportMaskURI) {
        Map<URI, Volume> map = maskToVolumes.get(exportMaskURI);
        return (map != null) ? Collections.unmodifiableMap(map) : Collections.<URI, Volume> emptyMap();
    }

    /**
     * Return the set of volumes that need to be placed
     * 
     * @return Volume URI to Volume object map (Read-only)
     */
    public Map<URI, Volume> getVolumesToPlace() {
        return Collections.unmodifiableMap(volumesToPlace);
    }

    /**
     * Return the ExportMask mapping
     *
     * @return ExportMask URI to ExportMask object (Read/write)
     */
    public Map<URI, ExportMask> getMasks() {
        return masks;
    }

    /**
     * Set the Masks that apply to this descriptor
     *
     * @param masks map of ExportMask URIs to ExportMask objects
     */
    public void setMasks(Map<URI, ExportMask> masks) {
        this.masks = new HashMap<>(masks);
    }

    /**
     * Get all the ExportMask URIs for the descriptor
     *
     * @return Set or ExportMask URIs
     */
    public Set<URI> getExportMaskURIs() {
        return masks.keySet();
    }

    /**
     * Get the ExportMask object for a given exportMaskURI
     * 
     * @param exportMaskURI
     *            [IN] - ExportMask URI
     * @return ExportMask associated with the exportMaskURI
     */
    public ExportMask getExportMask(URI exportMaskURI) {
        return masks.get(exportMaskURI);
    }

    /**
     * Map the ExportGroup to the ExportMask
     * 
     * @param exportMask
     *            [IN] - ExportMask URI
     * @param exportGroup
     *            [IN] - ExportGroup URI
     */
    public void mapExportMaskToExportGroup(URI exportMask, ExportGroup exportGroup) {
        maskExportGroupMap.put(exportMask, exportGroup);
    }

    /**
     * Get the ExportGroup for the ExportMask
     * 
     * @param exportMaskURI
     *            [IN] - ExportMask URI
     * @return ExportGroup that is associated with the ExportMask
     */
    public ExportGroup getExportGroupForMask(URI exportMaskURI) {
        return maskExportGroupMap.get(exportMaskURI);
    }

    /**
     * Set the indicator of how the placement can be done.
     *
     * @param placementHint
     *            [IN] - ExportMaskPlacementDescriptor.Strategy enum
     */
    public void setPlacementHint(PlacementHint placementHint) {
        this.placementHint = placementHint;
    }

    /**
     * Get the indicator of how the placement can be done.
     * 
     * @return ExportMaskPlacementDescriptor.Strategy enum
     */
    public PlacementHint getPlacementHint() {
        return placementHint;
    }

    /**
     * Remove references to the ExportMask in internal data structures
     *
     * @param uri [IN] - ExportMask URI
     */
    public void invalidateExportMask(URI uri) {
        masks.remove(uri);
        maskExportGroupMap.remove(uri);
        // Remove the entry from maskToVolumes, then get the volume map entry for the export
        // we removed. Use that as a tentative list of volumes that are unplaced. We will
        // determine below, if indeed the volumes are not placed elsewhere.
        Map<URI, Volume> tentativelyUnplacedVolumes = new HashMap<>(maskToVolumes.remove(uri));
        // Search through the mask to volumes mapping to see if any of the
        // tentatively unplaced volumes show up there.
        for (URI exportURI : maskToVolumes.keySet()) {
            Map<URI, Volume> volumeMap = maskToVolumes.get(exportURI);
            // Go through the volumes mapped to this ExportMask ...
            for (URI volumeURI : volumeMap.keySet()) {
                // If the volume is in the tentative list, then we can remove
                // it because we know it's been associated with something
                if (tentativelyUnplacedVolumes.containsKey(volumeURI)) {
                    tentativelyUnplacedVolumes.remove(volumeURI);
                }
            }
        }
        // Check if there's anything still left in tentative list after the above processing
        if (!tentativelyUnplacedVolumes.isEmpty()) {
            // Yep - there are volumes still not placed anywhere
            unplacedVolumes.putAll(tentativelyUnplacedVolumes);
        }
    }

    /**
     * Get the initiators that are applicable to this placement
     *
     * @return Collection of Initiators
     */
    public Collection<Initiator> getInitiators() {
        return initiators;
    }

    /**
     * Set the initiators that are applicable to this placement
     *
     * @param initiators [IN] - Collection of Initiators
     */
    public void setInitiators(Collection<Initiator> initiators) {
        this.initiators = initiators;
    }

    /**
     * Get the VPlex array to which the initiators belong
     *
     * @return StorageSystem
     */
    public StorageSystem getVplex() {
        return vplex;
    }

    /**
     * Set the VPlex array to which the initiators belong
     *
     * @param vplex [IN] - StorageSystem representing the VPlex array
     */
    public void setVplex(StorageSystem vplex) {
        this.vplex = vplex;
    }

    /**
     * Get the array that will provide backend volumes
     *
     * @return StorageSystem representing the backend array
     */
    public StorageSystem getBackendArray() {
        return backendArray;
    }

    /**
     * Set the array that will provider backend volumes
     *
     * @param backendArray [IN] - StorageSystem representing the backend array
     */
    public void setBackendArray(StorageSystem backendArray) {
        this.backendArray = backendArray;
    }

    /**
     * Get the VirtualArray that brings together VPlex initiators with the backend array
     *
     * @return VirtualArray URI
     */
    public URI getVirtualArray() {
        return virtualArray;
    }

    /**
     * Set the VirtualArray that brings together VPlex initiators with the backend array
     *
     * @param virtualArray [IN] - VirtualArray URI
     */
    public void setVirtualArray(URI virtualArray) {
        this.virtualArray = virtualArray;
    }

    /**
     * Get the Tenant to which volume objects belong
     *
     * @return TenantOrg URI
     */
    public URI getTenant() {
        return tenant;
    }

    /**
     * Set the Tenant to which volume objects belong
     *
     * @param tenant [IN] - TenantOrg URI
     */
    public void setTenant(URI tenant) {
        this.tenant = tenant;
    }

    /**
     * Get the Project to which the volumes belong
     *
     * @return Project URI
     */
    public URI getProject() {
        return project;
    }

    /**
     * Set the Project to which the volumes belong
     *
     * @param project [IN] - Project URI
     */
    public void setProject(URI project) {
        this.project = project;
    }

    /**
     * Indicates if there are any masks found for placement
     *
     * @return True, IFF there are any masks
     */
    public boolean hasMasks() {
        return (masks != null && !masks.isEmpty());
    }

    /**
     * Indicates if there are any unplaced volumes
     *
     * @return true, IFF there are unplaced volumes list is not empty
     */
    public boolean hasUnPlacedVolumes() {
        return unplacedVolumes != null && !unplacedVolumes.isEmpty();
    }

    /**
     * Returns a copy of the unplaced volumes;
     *
     * @return Map of Volume URI to Volume object
     */
    public Map<URI, Volume> getUnplacedVolumes() {
        return new HashMap<>(unplacedVolumes);
    }

    /**
     * Returns set of ExportMask URIs that have been matched and have associated Volumes
     *
     * @return Set of ExportMask URIs
     */
    public Set<URI> getPlacedMasks() {
        return (maskToVolumes != null && !maskToVolumes.isEmpty()) ? Collections.unmodifiableSet(maskToVolumes.keySet())
                : Collections.EMPTY_SET;
    }

    /**
     * Groups ExportMask based on ExportMaskPolicy equivalence (minus the #volumes)
     *
     * @param exportMask [IN] - ExportMask representing the mapping/masking component on the array
     * @param policy [IN] - ExportMaskPolicy describes attributes of the ExportMask
     */
    public void addToEquivalentMasks(ExportMask exportMask, ExportMaskPolicy policy) {
        String key = generatePolicyKey(policy);
        Set<URI> similarMasks = equivalentMasks.get(key);
        if (similarMasks == null) {
            similarMasks = new HashSet<>();
            equivalentMasks.put(key, similarMasks);
        }
        similarMasks.add(exportMask.getId());
        exportMaskPolicy.put(exportMask.getId(), policy);
    }

    /**
     * Get a list of ExportMasks that are equivalent (except for #volumes)
     *
     * @param exportMaskURI [IN] - ExportMask representing the mapping/masking component on the array
     * @return Set URIs pointing to ExportMasks that are equivalent
     */
    public Set<URI> getEquivalentExportMasks(URI exportMaskURI) {
        Set<URI> result = new HashSet<>();
        ExportMaskPolicy policy = exportMaskPolicy.get(exportMaskURI);
        if (policy != null) {
            String key = generatePolicyKey(policy);
            Set<URI> equivalent = equivalentMasks.get(key);
            // If we found equivalent masks ...
            if (equivalent != null) {
                // Add all of them to the result ...
                result.addAll(equivalent);
                // And then remove the one we're looking at
                result.remove(exportMaskURI);
            }
        }
        return result;
    }

    /**
     * Generates a String key based on the fields in the ExportMaskPolicy
     * 
     * @param policy [IN] - ExportMaskPolicy describes attributes of the ExportMask
     * @return String key representing the policy
     */
    private String generatePolicyKey(ExportMaskPolicy policy) {
        // policy.tierPolicies is a StringSet, so lets make sure that the names are in some order
        // to prevent getting different keys with the same set of policy names
        Set<String> sortedPolicyNames = (policy.getTierPolicies() != null && policy.getTierPolicies().isEmpty())
                ? new TreeSet<>(policy.getTierPolicies()) : Collections.<String> emptySet();
        String sortedPolicyNamesString = Joiner.on(';').join(sortedPolicyNames);
        return String.format("type=%s,ig=%s,localTier=%s,policies=%s,cascaded=%s,simple=%s,hostIObw=%d,hostIOPs=%d", policy.getExportType(),
                policy.getIgType(), policy.getLocalTierPolicy(), sortedPolicyNamesString, policy.isCascadedIG(), policy.isSimpleMask(),
                policy.getHostIOLimitBandwidth(), policy.getHostIOLimitIOPs());
    }

    /**
     * Return a set of ExportMask URIs that represent the ExportMasks that the volume could *have* been placed into
     * 
     * @param volumeURI [IN] - Volume URI
     * @return Set of ExportMask URIs that represent the ExportMasks that the volume could *have* been placed into
     */
    public Set<URI> getAlternativeExportsForVolume(URI volumeURI) {
        Set<URI> alternatesForVolume = volumeToAlternativeMasks.get(volumeURI);
        return (alternatesForVolume != null) ? Collections.unmodifiableSet(alternatesForVolume) : Collections.<URI> emptySet();
    }

    /**
     * Associate that the volume, with given URI, can be potentially be placed into the ExportMask, given by its URI.
     * 
     * @param volumeURI [IN] - Volume URI
     * @param exportMaskURI [IN] - ExportMask URI to associate with volume
     */
    public void addAsAlternativeExportForVolume(URI volumeURI, URI exportMaskURI) {
        Set<URI> alternatesForVolume = volumeToAlternativeMasks.get(volumeURI);
        if (alternatesForVolume == null) {
            alternatesForVolume = new HashSet<>();
            volumeToAlternativeMasks.put(volumeURI, alternatesForVolume);
        }
        alternatesForVolume.add(exportMaskURI);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("placementHint     : ").append(placementHint.name()).append('\n').
                append("vplexArray        : ").append(vplex.forDisplay()).append('\n').
                append("backendArray      : ").append(backendArray.forDisplay()).append('\n').
                append("masks             : ").append(CommonTransformerFunctions.collectionString(masks.keySet())).append('\n').
                append("unplacedVolumes   : ").append(CommonTransformerFunctions.collectionString(unplacedVolumes.keySet())).append('\n').
                append("volumesToPlace    : ").append(CommonTransformerFunctions.collectionString(volumesToPlace.keySet())).append('\n').
                append("maskToExportGroup : [").append(displayMaskToExportGroup()).append("]\n").
                append("maskToVolumes     :\n").append(displayMaskToVolumes());
        return builder.toString();
    }

    private String displayMaskToVolumes() {
        StringBuilder builder = new StringBuilder();
        for (URI exportMaskURI : maskToVolumes.keySet()) {
            Map<URI, Volume> volumeMap = maskToVolumes.get(exportMaskURI);
            builder.append("\t\t").append(exportMaskURI).append(" [");
            int count = 0;
            for (Volume volume : volumeMap.values()) {
                if (count > 0) {
                    builder.append(", ");
                }
                builder.append(volume.getLabel());
                count++;
            }
            builder.append("]\n");
        }
        return builder.toString();
    }

    private String displayMaskToExportGroup() {
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (URI exportMaskURI : maskExportGroupMap.keySet()) {
            ExportGroup exportGroup = maskExportGroupMap.get(exportMaskURI);
            if (count > 0) {
                builder.append(", ");
            }
            builder.append(String.format("%s=%s", exportMaskURI, exportGroup.getId()));
            count++;
        }
        return builder.toString();
    }
}
