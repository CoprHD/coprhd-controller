package com.emc.storageos.validation;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
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
	
	private void validateVolume(Volume volume, boolean delete, boolean remediate, StringBuilder msgs, ValCk... checks) {
		List<ValCk> checkList = Arrays.asList(checks);
		String volumeId = String.format("%s (%s)(%s)", volume.getLabel(), volume.getNativeGuid(), volume.getId());
		VPlexVirtualVolumeInfo vvinfo = client.getVirtualVolumeStructure(volume.getNativeId());
		if (checkList.contains(ValCk.ID)) {
			if (!volume.getNativeId().equals(vvinfo.getName())) {
				logDiff(volumeId, "native-id", volume.getNativeId(), vvinfo.getName());
			}
			if (!NullColumnValueGetter.isNullValue(volume.getWWN()) && vvinfo.getWwn() != null
					&& !volume.getWWN().equalsIgnoreCase(vvinfo.getWwn())) {
				logDiff(volumeId, "wwn", volume.getWWN(), vvinfo.getWwn());
			}
			if (volume.getAllocatedCapacity() != vvinfo.getCapacityBytes()) {
				logDiff(volumeId, "capacity", volume.getAllocatedCapacity().toString(), vvinfo.getCapacityBytes().toString());
			}
			if (!volume.getAssociatedVolumes().isEmpty()) {
				String locality = volume.getAssociatedVolumes().size() > 1 ? 
						VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME : VPlexApiConstants.LOCAL_VIRTUAL_VOLUME;
				if (!locality.equalsIgnoreCase(vvinfo.getLocality())) {
					logDiff(volumeId, "locality", locality, vvinfo.getLocality());
				}
			}
		}

		if (checkList.contains(ValCk.VPLEX)) {
			VPlexDistributedDeviceInfo ddInfo = (VPlexDistributedDeviceInfo) vvinfo
                    .getSupportingDeviceInfo();
            List<VPlexDeviceInfo> localDeviceInfoList = ddInfo.getLocalDeviceInfo();
			if (volume.getAssociatedVolumes() != null) {
				for (String assocVolume : volume.getAssociatedVolumes()) {
					Volume associatedVolume = dbClient.queryObject(Volume.class, URI.create(assocVolume));
					if (associatedVolume != null && !associatedVolume.getInactive()) {
					}
					
				}
			}
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
