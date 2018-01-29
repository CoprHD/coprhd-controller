/*
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.WorkflowException;

public class NetworkZoningParam implements Serializable {
	private static final long serialVersionUID = 678350596864970920L;
    private static final Logger _log = LoggerFactory.getLogger(NetworkZoningParam.class);
	
	/**
	 * This is the zoning map obtained from the ExportMask.
	 * It is a map of initiator URI string to a set of port URI strings.
	 * Zones will be added or removed as derived from the zoning map.
	 */
	private StringSetMap zoningMap;
	/**
	 * Boolean indicating the the ExportMask had additional additional volumes
	 * to be considered. If this boolean is true, the zone references will be
	 * removed, but zones will not be removed.
	 */
	private boolean hasExistingVolumes;
	/**
	 * The virtualArray is used to determine if zoning is enabled.
	 */
	private URI virtualArray;
	/**
	 * THe altVirtualArray is used in searching for initiators, ports, etc. sed by
	 * the VPLEX in the alternate virtual array.
	 */
	private URI altVirtualArray;
	
	/**
	 * The exportGroupId is used as part of the FCZoneReferences.
	 */
	private URI exportGroupId;
	
	/**
	 * Alternate Export Group ids.
	 */
	private List<URI> alternateExportGroupIds;
	
	/**
	 * The ExportGroup display string, used for logging.
	 */
	private String exportGroupDisplay;
	
	/**
	 * Name of the ExportMask the parameters were derived from. Used for logging.
	 */
	private String maskName;
	
	/**
	 * URI of the ExportMask the parameters were derived from. Used for logging.
	 */
	private URI maskId;
	
	/*
	 * Export mask volumes.
	 */
	private List<URI> volumes;
	
	/**
	 * Generates the zoning parameters from an ExportGroup/ExportMask pair.
	 * @param exportGroup ExportGroup
	 * @param exportMask ExportMask
	 * @param dbClient Database Handle
	 */
	public NetworkZoningParam(ExportGroup exportGroup, ExportMask exportMask, DbClient dbClient) {
		String storageSystem = exportMask.getStorageDevice().toString();
		setVirtualArray(exportGroup.getVirtualArray());
		if (exportGroup.hasAltVirtualArray(storageSystem)) {
			setAltVirtualArray(URI.create(exportGroup.getAltVirtualArrays().get(storageSystem)));
		}
		setHasExistingVolumes(exportMask.hasAnyExistingVolumes());
		setExportGroup(exportGroup.getId());
		setExportGroupDisplay(exportGroup.forDisplay());
		setMaskName(exportMask.getMaskName());
		setMaskId(exportMask.getId());
		if (exportMask.getVolumes() != null) {
		    setVolumes(StringSetUtil.stringSetToUriList(exportMask.getVolumes().keySet()));
		} else {
		    setVolumes(new ArrayList<URI>());
		}
		Set<Initiator> initiators = ExportMaskUtils.getInitiatorsForExportMask(dbClient, exportMask, Transport.FC);
		NetworkScheduler.checkZoningMap(exportGroup, exportMask, initiators, dbClient);
		setZoningMap(exportMask.getZoningMap());
	}
	
	/**
	 * Converts a list of ExportMask to a list of NetworkZoningParam blocks containing
	 * the required attributes from the ExportMask.
	 * @param exportGroupURI -- URI of ExportGroup that is being operated on
	 * @param exportMaskURIs -- List of URIs for ExportMasks to be converted
	 * @param dbClient -- database handle
	 * @return list of NetworkZoningParam
	 * @throws WorkflowException if any mask is not active
	 */
	static public List<NetworkZoningParam> convertExportMasksToNetworkZoningParam(
			URI exportGroupURI, List<URI> exportMaskURIs, DbClient dbClient) {
		ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
		List<NetworkZoningParam> zoningParams = new ArrayList<NetworkZoningParam>();
		for (URI exportMaskURI : exportMaskURIs) {
			ExportMask exportMask = dbClient.queryObject(ExportMask.class, exportMaskURI);
			if (exportMask == null || exportMask.getInactive()) {
				throw WorkflowException.exceptions.workflowConstructionError(
						"ExportMask is null: " + exportMaskURI.toString());
			}
			NetworkZoningParam zoningParam = new NetworkZoningParam(exportGroup, exportMask, dbClient);
			zoningParams.add(zoningParam);
		}
		return zoningParams;
	}
	
	/**
	 * Generates a list of NetworkZoningParam objects from a map of export mask URI to a list of initiator URIs.
	 * Only the initiators in the exportMaskToInitiators map are retained from the ExportMask initiators.
	 * @param exportGroupURI
	 * @param exportMaskToInitiators
	 * @param dbClient
	 * @return
	 */
	static public List<NetworkZoningParam> convertExportMaskInitiatorMapsToNetworkZoningParam(
			URI exportGroupURI, Map<URI, List<URI>> exportMaskToInitiators, DbClient dbClient) {
		ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
		List<NetworkZoningParam> zoningParams = new ArrayList<NetworkZoningParam>();
		for (Map.Entry<URI, List<URI>> entry : exportMaskToInitiators.entrySet()) {
			ExportMask exportMask = dbClient.queryObject(ExportMask.class, entry.getKey());
			if (exportMask == null || exportMask.getInactive()) {
				throw WorkflowException.exceptions.workflowConstructionError(
						"ExportMask is null: " + entry.getKey().toString());
			}
			NetworkZoningParam zoningParam = new NetworkZoningParam(exportGroup, exportMask, dbClient);
			// Filter out entries in the zoning map not in the initiator list.
			// This is done by retaining all the initiators in the exportMaskToInitiators value.
			Set<String> retainedInitiators = StringSetUtil.uriListToSet(entry.getValue());
			zoningParam.getZoningMap().keySet().retainAll(retainedInitiators);
			// Add zoningParam to result
			zoningParams.add(zoningParam);
		}
		return zoningParams;
	}

    /**
     * Build the zoningMap attribute of zoningParam using FCZoneReference and exportGroup
     * 
     * @param zoningParam
     * @param initsToRemoveOnlyFromZone
     * @param exportGroup
     */
    public static void updateZoningParamUsingFCZoneReference(List<NetworkZoningParam> zoningParam, List<URI> initsToRemoveOnlyFromZone,
            ExportGroup exportGroup, DbClient dbClient) {

        HashMap<String, Initiator> initiatorMap = new HashMap<String, Initiator>();
        for (URI initiatorURI : initsToRemoveOnlyFromZone) {
            Initiator iniObject = dbClient.queryObject(Initiator.class, initiatorURI);
            String iniString = iniObject.getInitiatorPort().toUpperCase();
            initiatorMap.put(iniString, iniObject);
        }

        // Retrieve FCZoneReference zone references that have the same initiator WWN.
        // These zone should be removed. since the initiator is no longer available.

        List<FCZoneReference> fcRefs = NetworkUtil.getFCZoneReferencesFromExportGroup(dbClient, exportGroup);
        Set<String> iniConsidered = new HashSet<String>();
        for (NetworkZoningParam networkZoningParam : zoningParam) {
            StringSetMap zoneMap = networkZoningParam.getZoningMap();
            if (zoneMap.isEmpty()) {
                for (FCZoneReference fcZoneReference : fcRefs) {
                    String[] initiatorAndPort = getInitiatorAndPortFromPwwnKey(fcZoneReference.getPwwnKey());
                    if (initiatorAndPort != null) {
                        String initiator = initiatorAndPort[0];
                        String port = initiatorAndPort[1];
                        Initiator iniObject = initiatorMap.get(initiator);
                        if (iniObject != null) {
                            StoragePort sp = NetworkUtil.getStoragePort(port, dbClient);
                            if (sp != null) {
                                iniConsidered.add(iniObject.getInitiatorPort());
                                zoneMap.put(iniObject.getId().toString(), sp.getId().toString());
                            }
                        }
                    } else {
                        _log.warn("Could not obtain initiator and port correctly from the PwwnKey {}", fcZoneReference.getPwwnKey());

                    }
                }
                // removed the initiator from map the as this initiator is considered for zone map
                if (!iniConsidered.isEmpty()) {
                    initiatorMap.keySet().removeAll(iniConsidered);
                }
            }
        }

    }

    /**
     * This takes pWwnKey string attribute of FCZoneReference
     * and return the String array containing initiator and port.
     * 
     * @param pWwnKey example:- "1011121310101042_5000144280342802"
     * @return String Array of size 2 example:- [10:11:12:13:10:10:10:42 , 50:00:14:42:80:34:28:02]
     *         or null for invalid pWwnKey .
     */
    private static String[] getInitiatorAndPortFromPwwnKey(String pWwnKey) {
        String[] result = null;
        String[] initiatorAndPort = pWwnKey.split("_");
        if ((initiatorAndPort.length < 2)
                || (initiatorAndPort[0].length() != 16)
                || (initiatorAndPort[1].length() != 16)) {

            _log.warn("Wrong format for PwwnKey {}", pWwnKey);

        } else {
            result = new String[2];
            result[0] = WWNUtility.getWWNWithColons(initiatorAndPort[0]);
            result[1] = WWNUtility.getWWNWithColons(initiatorAndPort[1]);

        }

        return result;

    }

	/**
	 * Generate a list of NetworkZoningParam objects when removing paths in path adjustment (port rebalance).
	 * This is not as straight forward as it might appear, because each ExportMask may also be
	 * referenced by alternate Export Groups, and their references would also need to be removed.
	 * 
	 * @param exportGroupURI -- The invoking EG URI
	 * @param exportMaskURI -- The export mask being processed
	 * @param maskRemovePaths -- paths that will be removed
	 * @param dbClient -- database client
	 * @return
	 */
	static public List<NetworkZoningParam> convertPathsToNetworkZoningParam(
	        URI exportGroupURI, Map<URI, Map<URI, List<URI>>> maskRemovePaths, DbClient dbClient) {
	    List<NetworkZoningParam> result = new ArrayList<NetworkZoningParam>();
        ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, exportGroupURI);
	    // Iterate over all the Export Masks
        for (Map.Entry<URI, Map<URI, List<URI>>> maskEntry : maskRemovePaths.entrySet()) {
            URI maskURI = maskEntry.getKey();
            ExportMask mask = dbClient.queryObject(ExportMask.class, maskURI);
            Map<URI, List<URI>> maskPaths = maskEntry.getValue();
            if (maskPaths.isEmpty()) {
                continue;
            }
            NetworkZoningParam zoningParam = new NetworkZoningParam(exportGroup, mask, dbClient);
            StringSetMap zoningMap = mask.getZoningMap();
            StringSetMap convertedZoningMap = new StringSetMap();
            // Get the path entries in both of the exportMask zoning map and the paths
            for (Map.Entry<URI, List<URI>> entry : maskPaths.entrySet()) {
                URI init = entry.getKey();
                List<URI> pathPorts = entry.getValue();
                StringSet ports = zoningMap.get(init.toString());
                if (ports != null && !ports.isEmpty()) {
                    StringSet convertedPorts = new StringSet();
                    for (URI pathPort : pathPorts) {
                        if (ports.contains(pathPort.toString())) {
                            convertedPorts.add(pathPort.toString());
                        }
                    }
                    if (!convertedPorts.isEmpty()) {
                        convertedZoningMap.put(init.toString(), convertedPorts);
                    }
                }
            }
            zoningParam.setZoningMap(convertedZoningMap);
            List<ExportGroup> allExportGroupsUsingMask = ExportMaskUtils.getExportGroups(dbClient,  mask);
            for (ExportGroup altExportGroup : allExportGroupsUsingMask) {
                if (zoningParam.getAlternateExportGroupIds() == null) {
                    zoningParam.setAlternateExportGroupIds(new ArrayList<URI>());
                }
                if (!altExportGroup.getId().equals(exportGroupURI)) {
                    zoningParam.getAlternateExportGroupIds().add(altExportGroup.getId());
                }
            }
            result.add(zoningParam);
        }
        
	    return result;
	}
	
	public StringSetMap getZoningMap() {
		return zoningMap;
	}
	public void setZoningMap(StringSetMap zoningMap) {
		this.zoningMap = zoningMap;
	}
	public boolean hasExistingVolumes() {
		return hasExistingVolumes;
	}
	public void setHasExistingVolumes(boolean hasExistingVolumes) {
		this.hasExistingVolumes = hasExistingVolumes;
	}
	public URI getVirtualArray() {
		return virtualArray;
	}
	public void setVirtualArray(URI virtualArray) {
		this.virtualArray = virtualArray;
	}
	public URI getAltVirtualArray() {
		return altVirtualArray;
	}
	public void setAltVirtualArray(URI altVirtualArray) {
		this.altVirtualArray = altVirtualArray;
	}

	public URI getExportGroupId() {
		return exportGroupId;
	}

	public void setExportGroup(URI exportGroupId) {
		this.exportGroupId = exportGroupId;
	}

	public String getMaskName() {
		return maskName;
	}

	public void setMaskName(String maskName) {
		this.maskName = maskName;
	}

	public URI getMaskId() {
		return maskId;
	}

	public void setMaskId(URI maskURI) {
		this.maskId = maskURI;
	}

	public String getExportGroupDisplay() {
		return exportGroupDisplay;
	}

	public void setExportGroupDisplay(String exportGroupDisplay) {
		this.exportGroupDisplay = exportGroupDisplay;
	}

	public List<URI> getVolumes() {
		return volumes;
	}

	public void setVolumes(List<URI> volumes) {
		this.volumes = volumes;
	}

    public List<URI> getAlternateExportGroupIds() {
        return alternateExportGroupIds;
    }

    public void setAlternateExportGroupIds(List<URI> alternateExportGroupIds) {
        this.alternateExportGroupIds = alternateExportGroupIds;
    }
	
}
