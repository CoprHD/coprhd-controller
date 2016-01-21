/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.workflow.Workflow;

public interface VplexBackEndMaskingOrchestrator extends MaskingOrchestrator {

    /**
     * Searches the storage device for any ExportMask (e.g. MaskingView) that contains
     * any of the initiators. For any ExportMasks that it finds, it will map the set
     * of Volumes that are appropriate for the ExportMask. Placement of the volume
     * to an ExportMask will be array dependent. It's up to the client to determine if
     * one or more of the ExportMasks are preferable to update.
     *
     * @param storage
     *            [IN] - StorageSystem object
     * @param device
     *            [IN] - BlockStorageDevice device
     * @param initiators
     *            [IN] - List of Initiators to match with ExportMasks on the array
     * @param placementDescriptor
     *            [IN] - URI to Volume object mapping. These are the volumes to place
     *
     */
            void suggestExportMasksForPlacement(StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
                    ExportMaskPlacementDescriptor placementDescriptor);

    /**
     * Searches the storage device for any ExportMask (e.g. MaskingView) that contains
     * any of the initiators.
     * 
     * @param storage -- Storage system to be searched.
     * @param device -- Storage device to be searched for Export Masks
     * @param initiators - List of Initiator objects to be searched for.
     * @return a map of URIs to Export Masks that contain any of the initiators
     */
            Map<URI, ExportMask> readExistingExportMasks(
            StorageSystem storage, BlockStorageDevice device,
            List<Initiator> initiators);

    /**
     * Refreshes an ExportMask with data from the array.
     * 
     * @param storage -- Storage device to be searched for Export Masks
     * @param device -- Storage device to be searched for Export Masks
     * @param mask -- ExportMask
     * @return -- updated ExportMask that is also persisted
     */
            ExportMask refreshExportMask(StorageSystem storage,
            BlockStorageDevice device, ExportMask mask);

    /**
     * Generate the PortGroups to be used. Each Masking View may use a
     * distinct port group. The mapping of available ports to port groups
     * is array dependent.
     * <p>
     * Each PortGroup is a Map of Network URI to a List of StoragePort objects. For XtremIO, it is a Map of Network URI to a list of list of
     * StoragePorts. i.e Each network has multiple sets of StoragePorts. This is needed so that each director will map to different set of
     * StoragePorts for a network.
     * 
     * <p>
     * 
     * @param allocatablePorts -- Map of Network URI to list of allocatable ports
     *            in that Network.
     * @param networkMap -- Map of Network URI to NetworkLite structure
     * @param varrayURI -- URI of virtual array used for allocation
     * @param nInitiatorGroups -- the number of Initiator Groups created
     * @return Set of PortGroups.
     */
    Set<Map<URI, List<List<StoragePort>>>> getPortGroups(Map<URI,
            List<StoragePort>> allocatablePorts,
            Map<URI, NetworkLite> networkMap, URI varrayURI, int nInitiatorGroups);

    /**
     * Configure the zoning for an ExportMask given it's PortGroup and InitiatorGroup.
     * 
     * @param portGroup -- Map of Network URI to List of StoragePort objects.
     * @param initiatorGroup -- Map of VPlex director name to Map of Network URI to Set of Initiator objects.
     * @param networkMap -- map of Network URI to NetworkLite structures.
     * @param assigner an instance of StoragePortsAssigner
     * @return StringSetMap -- the zoningMap entry that should be used for the ExportMask.
     */
    StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup,
            Map<URI, NetworkLite> networkMap, StoragePortsAssigner assigner);

    /**
     * Return a Workflow method for createOrAddVolumesToExportMask.
     * 
     * @param arrayURI -- Storage Array URI
     * @param exportGroupURI -- ExportGroupURI
     * @param exportMaskURI -- ExportMask URI
     * @param volumeMap -- Map of Volume URI to HLU Integer
     * @param completer -- TaskCompleter to be fired when complete.
     * @return Workflow.Method
     */
            Workflow.Method createOrAddVolumesToExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer);

    /**
     * Create an ExportMask (VMAX: Masking View, VNX: Storage Group) on the backend array
     * it it does not exist. If it does exist, just add the indicated volumes to the mask.
     * 
     * @param arrayURI -- Storage Array URI
     * @param exportGroupURI -- ExportGroup URI
     * @param exportMaskURI -- ExportMask URI
     * @param volumeMap -- Map of Volume URI to HLU Integer
     * @param completer -- TaskCompleter to be fired when complete.
     * @param stepId --Step id of Workflow Step.
     */
            void createOrAddVolumesToExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer, String stepId);

    /**
     * Return a Workflow Method for:
     * Delete an Export Mask (VMAX: Masking View, VNX: Storage Group) on the backend array
     * if there will be no remaining volumes. Otherwise, just remove the indicated volumes
     * and leave those that remain.
     * 
     * @param arrayURI -- Storage Array URI
     * @param exportGroupURI -- ExportGroup URI
     * @param exportMaskURI -- ExportMask URI
     * @param volumes -- List of Volume URIs to be removed from the Mask
     * @param completer -- Task completer to be fired when complete.
     * @return
     */
            Workflow.Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer);

    /**
     * Delete an Export Mask (VMAX: Masking View, VNX: Storage Group) on the backend array
     * if there will be no remaining volumes. Otherwise, just remove the indicated volumes
     * and leave those that remain.
     * 
     * @param arrayURI -- Array URI
     * @param exportGroupURI -- ExportGroup URI
     * @param exportMaskURI -- ExportMask URI
     * @param volumes -- List of volume URIs
     * @param completer -- TaskCompleter to be fired when complete.
     * @param stepId -- Workflow step id.
     */
            void deleteOrRemoveVolumesFromExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer, String stepId);
}
