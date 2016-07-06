package com.emc.storageos.validation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.VPlexDrillDownParser;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
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
		VPlexVirtualVolumeInfo vvinfo = client.findVirtualVolumeAndUpdateInfo(virtualVolume.getDeviceLabel());
		
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
		        String clusterName = VPlexControllerUtils.getVPlexClusterName( dbClient, virtualVolume);
		        for (VPlexDrillDownParser.Node child : root.getChildren()) {
		           if (child.getArg2().equals(clusterName)) {
		               validateStorageVolumes(virtualVolume, volumeId, true,child);
		           } else {
		               validateStorageVolumes(virtualVolume, volumeId, false, child);
		           }
		        }
		        
		    } else {
		        validateStorageVolumes(virtualVolume, volumeId, true, root);
		    }
		    } catch (Exception ex) {
		        logDiff(volumeId, "exception trying to validate storage volumes", virtualVolume.getDeviceLabel(), "");
		    }
		    
		}
		
	}
	
	/**
	 * Validates either storage volumes in the local or remote side of a virtual volume.
	 * @param virtualVolume -- Vplex virtual volume
	 * @param volumeId -- identification for log
	 * @param local -- if true the local side, else remote
	 * @param node - drill down Node from parser containing hardware info, should be
	 * type LOCAL or type DISTCOMP
	 */
	private void validateStorageVolumes(Volume virtualVolume, String volumeId, 
	        boolean local, VPlexDrillDownParser.Node node) {
	    boolean hasMirror = false;
        Map<String, VPlexStorageVolumeInfo> wwnToStorageVolumeInfos = 
            client.getStorageVolumeInfoForDevice(
                node.getArg1(), VPlexApiConstants.LOCAL_VIRTUAL_VOLUME, node.getArg2(), hasMirror);
        Volume assocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, true, dbClient);
        if (wwnToStorageVolumeInfos.size() != 1 || 
                !wwnToStorageVolumeInfos.keySet().contains(assocVolume.getWWN())) {
            logDiff(volumeId, "local volume WWN",assocVolume.getWWN(), 
                    wwnToStorageVolumeInfos.keySet().toString());
        }
	    
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
