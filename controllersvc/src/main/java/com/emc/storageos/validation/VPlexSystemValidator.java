package com.emc.storageos.validation;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.VPlexDrillDownParser;
import com.emc.storageos.util.VPlexDrillDownParser.NodeType;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexDeviceInfo;
import com.emc.storageos.vplex.api.VPlexDistributedDeviceInfo;
import com.emc.storageos.vplex.api.VPlexExtentInfo;
import com.emc.storageos.vplex.api.VPlexStorageVolumeInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;

public class VPlexSystemValidator extends AbstractSystemValidator {
	VPlexApiClient client = null;
	
	public VPlexSystemValidator(DbClient dbClient) {
		super(dbClient, LoggerFactory.getLogger(VPlexSystemValidator.class));
	}

	@Override
	public List<Volume> volumes(StorageSystem storageSystem,
			List<Volume> volumes, boolean delete, boolean remediate,
			StringBuilder msgs, ValCk... checks) {
		try {
			client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), storageSystem, dbClient);
			for (Volume volume : volumes) {
				try {
					log.info(String.format("Validating %s (%s)(%s) checks %s", 
							volume.getLabel(), volume.getNativeId(), volume.getId(), checks.toString()));
					validateVolume(volume, delete, remediate, msgs, checks);
				} catch (Exception ex) {
					log.error("Exception validating volume: " + volume.getId(), ex);
				}
			}
		} catch (Exception ex) {
			log.error("Exception validating VPLEX: " + storageSystem.getId(), ex);
		}
		return remediatedVolumes;
	}
	
	private void validateVolume(Volume virtualVolume, boolean delete, boolean remediate, StringBuilder msgs, ValCk... checks) {
		List<ValCk> checkList = Arrays.asList(checks);
		String volumeId = String.format("%s (%s)(%s)", virtualVolume.getLabel(), virtualVolume.getNativeGuid(), virtualVolume.getId());
		VPlexVirtualVolumeInfo vvinfo = null;
		try {
		    vvinfo = client.findVirtualVolumeAndUpdateInfo(virtualVolume.getDeviceLabel());
		} catch (VPlexApiException ex) {
		    log.info(ex.getMessage());
		}
		if (vvinfo == null) {
		    // Didn't find the virtual volume. Error if we're not deleting. 
		    if (!delete) {
		        logDiff(volumeId, "virtual-volume", virtualVolume.getDeviceLabel(), "no corresponding virtual volume information");
		    }
		    return;
		}
		
		if (checkList.contains(ValCk.ID)) {
			if (!virtualVolume.getNativeId().equals(vvinfo.getName())) {
				logDiff(volumeId, "native-id", virtualVolume.getNativeId(), vvinfo.getName());
			}
			if (!NullColumnValueGetter.isNullValue(virtualVolume.getWWN()) && vvinfo.getWwn() != null
					&& !virtualVolume.getWWN().equalsIgnoreCase(vvinfo.getWwn())) {
				logDiff(volumeId, "wwn", virtualVolume.getWWN(), vvinfo.getWwn());
			}
			if (virtualVolume.getAllocatedCapacity() != vvinfo.getCapacityBytes()) {
				logDiff(volumeId, "capacity", virtualVolume.getAllocatedCapacity().toString(), vvinfo.getCapacityBytes().toString());
			}
			if (!virtualVolume.getAssociatedVolumes().isEmpty()) {
				String locality = virtualVolume.getAssociatedVolumes().size() > 1 ? 
						VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME : VPlexApiConstants.LOCAL_VIRTUAL_VOLUME;
				if (!locality.equalsIgnoreCase(vvinfo.getLocality())) {
					logDiff(volumeId, "locality", locality, vvinfo.getLocality());
				}
			}
		}

		if (checkList.contains(ValCk.VPLEX)) {
		    try {
		    String drillDownInfo = client.getDrillDownInfoForDevice(vvinfo.getSupportingDevice());
		    VPlexDrillDownParser parser = new VPlexDrillDownParser(drillDownInfo);
		    VPlexDrillDownParser.Node root = parser.parse();
		    boolean distributed = (root.getType() == VPlexDrillDownParser.NodeType.DIST) ? true : false;
		    if (distributed) {
		        List<VPlexDrillDownParser.Node> svols = root.getNodesOfType(NodeType.SVOL);
                boolean hasMirror = svols.size() > 2;
		        String clusterName = VPlexControllerUtils.getVPlexClusterName( dbClient, virtualVolume);
		        for (VPlexDrillDownParser.Node child : root.getChildren()) {
		           if (child.getArg2().equals(clusterName)) {
		               validateStorageVolumes(virtualVolume, volumeId, root.getArg1(), true, child.getArg2(), hasMirror);
		           } 
		        }
		    } else {
		        List<VPlexDrillDownParser.Node> svols = root.getNodesOfType(NodeType.SVOL);
		        boolean hasMirror = svols.size() > 1;
		        validateStorageVolumes(virtualVolume, volumeId, root.getArg1(), false, root.getArg2(), hasMirror);
		    }
		    } catch (Exception ex) {
		        logDiff(volumeId, "exception trying to validate storage volumes", virtualVolume.getDeviceLabel(), "");
		    }
		    
		}
		
	}
	
	/**
	 * Validates either storage volumes for either distributed or local vplex volumes.
	 * @param virtualVolume -- Vplex virtual volume
	 * @param volumeId -- identification for log
	 * @param topLevelDeviceName -- top level VPLEX device name
	 * @param distributed -- if true VPLEX distributed, false if VPLEX local
	 * @param clusterName cluster-1 or cluster-2
	 * @param hasMirror -- if true this cluster has a mirror
	 * @return failed -- If true a discrepancy was detected
	 * 
	 */
	private boolean validateStorageVolumes(Volume virtualVolume, String volumeId, 
	        String topLevelDeviceName, boolean distributed, String cluster, boolean hasMirror) {
	    boolean failed = false;
        Map<String, VPlexStorageVolumeInfo> wwnToStorageVolumeInfos = 
            client.getStorageVolumeInfoForDevice(
                topLevelDeviceName, distributed ? VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME : VPlexApiConstants.LOCAL_VIRTUAL_VOLUME, 
                cluster, hasMirror);
        
        // Check local volume is present
        Volume assocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, true, dbClient);
        if (!wwnToStorageVolumeInfos.keySet().contains(assocVolume.getWWN())) {
            logDiff(volumeId, "SOURCE storage volume WWN",assocVolume.getWWN(), 
                    wwnToStorageVolumeInfos.keySet().toString());
            failed = true;
        } else {
            wwnToStorageVolumeInfos.remove(assocVolume.getWWN());
        }
        
        // Check HA volume is present
        if (distributed) {
            assocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, false, dbClient);
            if (!wwnToStorageVolumeInfos.keySet().contains(assocVolume.getWWN())) {
                logDiff(volumeId, "HA storage volume WWN", assocVolume.getWWN(),
                        wwnToStorageVolumeInfos.keySet().toString());
                failed = true;
            } else {
                wwnToStorageVolumeInfos.remove(assocVolume.getWWN());
            }
        }
        
        // Process any mirrors that were found
        if (virtualVolume.getMirrors() != null) {
           for (String mirrorId : virtualVolume.getMirrors()) {
               VplexMirror mirror = dbClient.queryObject(VplexMirror.class, URI.create(mirrorId));
               if (mirror == null || mirror.getInactive() || mirror.getAssociatedVolumes() == null) {
                   // Not fully formed for some reason, skip it
                   continue;
               }
               // Now get the underlying Storage Volume
               for (String mirrorAssociatedId : mirror.getAssociatedVolumes()) {
                   Volume mirrorVolume = dbClient.queryObject(Volume.class, URI.create(mirrorAssociatedId));
                   if (mirrorVolume != null && !NullColumnValueGetter.isNullValue(mirrorVolume.getWWN())) {
                       if (!wwnToStorageVolumeInfos.keySet().contains(mirrorVolume.getWWN())) {
                           logDiff(volumeId, "Mirror WWN", mirrorVolume.getWWN(), 
                                wwnToStorageVolumeInfos.keySet().toString());
                           failed = true;
                       } else {
                           wwnToStorageVolumeInfos.remove(mirrorVolume.getWWN());
                       }
                   }
               }
           }
        }

        if (!wwnToStorageVolumeInfos.isEmpty()) {
            logDiff(volumeId, "Extra storage volumes found", "<not present>", wwnToStorageVolumeInfos.keySet().toString());
            failed = true;
        }
        return failed;
	}
	
	/**
	 * Locates the VPlexStorageVolumeInfo for a given systemId and wwn
	 * @param systemId
	 * @param wwn
	 * @param vvinfo
	 * @return
	 */
	private VPlexStorageVolumeInfo getStorageVolumeInfo(String systemId, String wwn, VPlexVirtualVolumeInfo vvinfo) {
		VPlexDistributedDeviceInfo ddInfo = (VPlexDistributedDeviceInfo) vvinfo
                .getSupportingDeviceInfo();
        List<VPlexDeviceInfo> localDeviceInfoList = ddInfo.getLocalDeviceInfo();	
        for (VPlexDeviceInfo ldevinfo : localDeviceInfoList) {
        	for (VPlexExtentInfo extinfo : ldevinfo.getExtentInfo()) {
        		VPlexStorageVolumeInfo svinfo = extinfo.getStorageVolumeInfo();
        		if (svinfo.getSystemId().equals(systemId) && svinfo.getWwn().equals(wwn)) {
        			    return svinfo;
        		}

        	}
        }
        return null;
	}
}
