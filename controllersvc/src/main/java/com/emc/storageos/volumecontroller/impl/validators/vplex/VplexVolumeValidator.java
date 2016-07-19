package com.emc.storageos.volumecontroller.impl.validators.vplex;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VplexMirror;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.VPlexDrillDownParser;
import com.emc.storageos.util.VPlexDrillDownParser.NodeType;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.impl.validators.ValCk;
import com.emc.storageos.volumecontroller.impl.validators.ValidatorLogger;
import com.emc.storageos.vplex.api.VPlexApiClient;
import com.emc.storageos.vplex.api.VPlexApiConstants;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.vplex.api.VPlexApiFactory;
import com.emc.storageos.vplex.api.VPlexDeviceInfo;
import com.emc.storageos.vplex.api.VPlexDistributedDeviceInfo;
import com.emc.storageos.vplex.api.VPlexExtentInfo;
import com.emc.storageos.vplex.api.VPlexResourceInfo;
import com.emc.storageos.vplex.api.VPlexStorageVolumeInfo;
import com.emc.storageos.vplex.api.VPlexVirtualVolumeInfo;
import com.emc.storageos.vplexcontroller.VPlexControllerUtils;

public class VplexVolumeValidator extends AbstractVplexValidator {
    VPlexApiClient client = null;
    private Logger log = LoggerFactory.getLogger(VplexVolumeValidator.class);
    private List<Volume> remediatedVolumes = new ArrayList<Volume>();

    public VplexVolumeValidator(DbClient dbClient, ValidatorLogger logger) {
        super(dbClient, logger);
    }

    public List<Volume> validateVolumes(StorageSystem storageSystem,
            List<Volume> volumes, boolean delete, boolean remediate,
            ValCk... checks) {
        try {
            client = VPlexControllerUtils.getVPlexAPIClient(VPlexApiFactory.getInstance(), storageSystem, dbClient);
            for (Volume volume : volumes) {
                try {
                    log.info(String.format("Validating %s (%s)(%s) checks %s",
                            volume.getLabel(), volume.getNativeId(), volume.getId(), checks.toString()));
                    validateVolume(volume, delete, remediate, checks);
                } catch (Exception ex) {
                    log.error("Exception validating volume: " + volume.getId(), ex);
                }
            }
        } catch (Exception ex) {
            log.error("Unexpected exception validating VPLEX: " + storageSystem.getId(), ex);
        }
        return remediatedVolumes;
    }

    public void validateVolume(Volume virtualVolume, boolean delete, boolean remediate, ValCk... checks) {
        List<ValCk> checkList = Arrays.asList(checks);
        String volumeId = String.format("%s (%s)(%s)", virtualVolume.getLabel(), virtualVolume.getNativeGuid(), virtualVolume.getId());
        log.info("Initiating Vplex Volume validation: " + volumeId);
        VPlexVirtualVolumeInfo vvinfo = null;
        try {
            vvinfo = client.findVirtualVolumeAndUpdateInfo(virtualVolume.getDeviceLabel());
        } catch (VPlexApiException ex) {
            log.info(ex.getMessage());
        }
        if (vvinfo == null) {
            try {
                // Didn't find the virtual volume. Look at the storage volume, and from that determine
                // the deviceName. Then lookup the Deivce or DistributedDevice and check to see if
                // the device has been reassigned to a different virtual volume.
                Volume storageVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, true, dbClient, false);
                if (storageVolume != null) {
                    StorageSystem system = dbClient.queryObject(StorageSystem.class, storageVolume.getStorageController());
                    // Look up the corresponding device name to our Storage Volumej
                    String deviceName = client.getDeviceForStorageVolume(storageVolume.getNativeId(),
                            storageVolume.getWWN(), system.getSerialNumber());
                    if (deviceName == null) {
                        if (!delete) {
                            // We didn't find a device name for the storage volume. Error if not deleting.
                            logger.logDiff(volumeId, "Vplex device-name", system.getSerialNumber() + "-" + storageVolume.getNativeId(),
                                    "<not-found>");
                            return;
                        }
                    }
                    if (!deviceName.matches(VPlexApiConstants.STORAGE_VOLUME_NOT_IN_USE)) {
                        String volumeType = VPlexApiConstants.LOCAL_VIRTUAL_VOLUME;
                        if (virtualVolume.getAssociatedVolumes() != null && virtualVolume.getAssociatedVolumes().size() > 1) {
                            volumeType = VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME;
                        }
                        VPlexResourceInfo resourceInfo = client.getDeviceStructure(deviceName, volumeType);
                        if (resourceInfo instanceof VPlexDeviceInfo) {
                            // local device
                            VPlexDeviceInfo localDeviceInfo = (VPlexDeviceInfo) resourceInfo;
                            String virtualVolumeName = localDeviceInfo.getVirtualVolume();
                            if (virtualVolumeName != null && !virtualVolumeName.equals(virtualVolume.getDeviceLabel())) {
                                logger.logDiff(volumeId, "virtual-volume name changed", virtualVolume.getDeviceLabel(), virtualVolumeName);
                            }
                        } else if (resourceInfo instanceof VPlexDistributedDeviceInfo) {
                            VPlexDistributedDeviceInfo distDeviceInfo = (VPlexDistributedDeviceInfo) resourceInfo;
                            String virtualVolumeName = distDeviceInfo.getVirtualVolume();
                            if (virtualVolumeName != null && !virtualVolumeName.equals(virtualVolume.getDeviceLabel())) {
                                logger.logDiff(volumeId, "virtual-volume name changed", virtualVolume.getDeviceLabel(), virtualVolumeName);
                            }
                        }
                    }
                }
            } catch (VPlexApiException ex) {
                log.info("Unable to determine if VPLEX device reused: " + volumeId, ex);
                throw ex;
            }
            if (!delete) {
                // If we didn't log an error above indicating that the volume was reused,
                // and we are not deleting, it is still an error.
                // If we are deleting we won't error if it's just not there.
                logger.logDiff(volumeId, "virtual-volume", virtualVolume.getDeviceLabel(), "no corresponding virtual volume information");
            }
            log.info("Vplex Validation complete (no vvinfo found); " + volumeId);
            return;
        }

        if (checkList.contains(ValCk.ID)) {
            if (!virtualVolume.getDeviceLabel().equals(vvinfo.getName())) {
                logger.logDiff(volumeId, "native-id", virtualVolume.getNativeId(), vvinfo.getName());
            }
            if (!NullColumnValueGetter.isNullValue(virtualVolume.getWWN()) && vvinfo.getWwn() != null
                    && !virtualVolume.getWWN().equalsIgnoreCase(vvinfo.getWwn())) {
                logger.logDiff(volumeId, "wwn", virtualVolume.getWWN(), vvinfo.getWwn());
            }
            if (!virtualVolume.getProvisionedCapacity().equals(vvinfo.getCapacityBytes())) {
                logger.logDiff(volumeId, "capacity", virtualVolume.getAllocatedCapacity().toString(), vvinfo.getCapacityBytes().toString());
            }
            if (virtualVolume.getAssociatedVolumes() != null && !virtualVolume.getAssociatedVolumes().isEmpty()) {
                String locality = virtualVolume.getAssociatedVolumes().size() > 1 ? VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME
                        : VPlexApiConstants.LOCAL_VIRTUAL_VOLUME;
                if (!locality.equalsIgnoreCase(vvinfo.getLocality())) {
                    logger.logDiff(volumeId, "locality", locality, vvinfo.getLocality());
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
                    String clusterName = VPlexControllerUtils.getVPlexClusterName(dbClient, virtualVolume);
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
                logger.logDiff(volumeId, "exception trying to validate storage volumes", virtualVolume.getDeviceLabel(), "");
            }

        }
        log.info("Vplex Validation complete; " + volumeId);

    }

    /**
     * Validates either storage volumes for either distributed or local vplex volumes.
     * 
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
        Map<String, VPlexStorageVolumeInfo> wwnToStorageVolumeInfos = client.getStorageVolumeInfoForDevice(
                topLevelDeviceName, distributed ? VPlexApiConstants.DISTRIBUTED_VIRTUAL_VOLUME : VPlexApiConstants.LOCAL_VIRTUAL_VOLUME,
                cluster, hasMirror);

        // Check local volume is present
        Volume assocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, true, dbClient);
        if (!wwnToStorageVolumeInfos.keySet().contains(assocVolume.getWWN())) {
            logger.logDiff(volumeId, "SOURCE storage volume WWN", assocVolume.getWWN(),
                    wwnToStorageVolumeInfos.keySet().toString());
            failed = true;
        } else {
            wwnToStorageVolumeInfos.remove(assocVolume.getWWN());
        }

        // Check HA volume is present
        if (distributed) {
            assocVolume = VPlexUtil.getVPLEXBackendVolume(virtualVolume, false, dbClient);
            if (!wwnToStorageVolumeInfos.keySet().contains(assocVolume.getWWN())) {
                logger.logDiff(volumeId, "HA storage volume WWN", assocVolume.getWWN(),
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
                            logger.logDiff(volumeId, "Mirror WWN", mirrorVolume.getWWN(),
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
            logger.logDiff(volumeId, "Extra storage volumes found", "<not present>", wwnToStorageVolumeInfos.keySet().toString());
            failed = true;
        }
        return failed;
    }

    /**
     * Locates the VPlexStorageVolumeInfo for a given systemId and wwn
     * 
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
