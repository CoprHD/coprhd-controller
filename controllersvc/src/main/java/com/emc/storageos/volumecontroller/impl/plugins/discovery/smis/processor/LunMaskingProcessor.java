/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.detailedDiscovery.VolHostIOObject;
import com.emc.storageos.volumecontroller.impl.smis.SmisCommandHelper;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.emc.storageos.volumecontroller.impl.smis.SmisUtils;

public class LunMaskingProcessor extends StorageProcessor {
    private static final String STORAGE_VOLUME_PREFIX = "storagevolume";
    private Logger _logger = LoggerFactory.getLogger(LunMaskingProcessor.class);
    private List<Object> _args;

    private DbClient _dbClient;

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        @SuppressWarnings("unchecked")
        final Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
        Map<String, VolHostIOObject> volToIolimits = new HashMap<String, VolHostIOObject>();
        List<CIMObjectPath> processedSGCoPs = new ArrayList<CIMObjectPath>();
        Map<String, String> volToFastPolicy = new HashMap<String, String>();
        WBEMClient client = (WBEMClient) keyMap.get(Constants._cimClient);
        CIMObjectPath maskingViewPath = getObjectPathfromCIMArgument(_args);
        _dbClient = (DbClient) keyMap.get(Constants.dbClient);
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        URI systemId = profile.getSystemId();
        try {
            StorageSystem device = _dbClient.queryObject(StorageSystem.class, systemId);

            while (it.hasNext()) {
                CIMObjectPath path = it.next();
                // if cascaded SG, note down the Host Io limits, and get the
                // child SGs Host Io limits and volumes.
                // If io limit is not set on child SG, then use the parent, else
                // use io limit on child always
                if (path.toString().contains(SmisCommandHelper.MASKING_GROUP_TYPE.SE_DeviceMaskingGroup.name())) {
                    List<CIMObjectPath> paths = getChildGroupsifCascaded(path, client);
                    if (!paths.isEmpty()) {
                        _logger.info("Cascaded SG Detected");
                        CIMInstance csgInstance = client.getInstance(path, false, true, SmisConstants.PS_HOST_IO);
                        String parentHostIoBw = String.valueOf(csgInstance.getPropertyValue(SmisConstants.EMC_MAX_BANDWIDTH));
                        String parentHostIoPs = String.valueOf(csgInstance.getPropertyValue(SmisConstants.EMC_MAX_IO));
                        for (CIMObjectPath childPath : paths) {
                            addIoLimitsOnVolume(client, childPath, volToIolimits, volToFastPolicy, parentHostIoBw, parentHostIoPs);
                        }
                        processedSGCoPs.addAll(paths);
                    } else {
                        _logger.info("Non cascaded SG Detected");
                        addIoLimitsOnVolume(client, path, volToIolimits, volToFastPolicy, "0", "0");
                        processedSGCoPs.add(path);
                    }
                }

                // Clar_LunMaskingSCSIProtocolController-->StorageVolume, if volume entry is there,
                // then consider those as exported Volumes.
                String systemName = (String) maskingViewPath.getKey(Constants.SYSTEMNAME).getValue();
                if (systemName.toLowerCase().contains(Constants.CLARIION) && path.toString().toLowerCase().contains(STORAGE_VOLUME_PREFIX)) {
                    String volumeNativeGuid = getVolumeNativeGuid(path);
                    VolHostIOObject obj = new VolHostIOObject();
                    obj.setVolNativeGuid(volumeNativeGuid);
                    obj.setHostIoBw("0");
                    obj.setHostIops("0");
                    volToIolimits.put(volumeNativeGuid, obj);
                }

            }

            // Store all the exported Volumes of all Protocol Controllers
            // During creation of UnManaged Volume in
            // StorageVolumeInfoProcesssor, this collection
            // will be used to filter out already exported Volumes
            if (!keyMap.containsKey(Constants.EXPORTED_VOLUMES)) {
                keyMap.put(Constants.EXPORTED_VOLUMES, volToIolimits);
            } else {
                @SuppressWarnings("unchecked")
                Map<String, VolHostIOObject> alreadyExportedVolumes = (Map<String, VolHostIOObject>) keyMap
                        .get(Constants.EXPORTED_VOLUMES);
                alreadyExportedVolumes.putAll(volToIolimits);
            }

            if (device.checkIfVmax3()) {
                // set the CoPs of SG's processed in the keyMap.
                // This list will be used as a reference to skip them to fetch unexported volume
                // SLO Names.
                if (!keyMap.containsKey(Constants.STORAGE_GROUPS_PROCESSED)) {
                    keyMap.put(Constants.STORAGE_GROUPS_PROCESSED, processedSGCoPs);
                } else {
                    List<CIMObjectPath> volumesWithFastPolicy = (List<CIMObjectPath>) keyMap
                            .get(Constants.STORAGE_GROUPS_PROCESSED);
                    volumesWithFastPolicy.addAll(processedSGCoPs);
                }

                // Set the volumesWithSLO in the keyMap for further processing.
                if (!keyMap.containsKey(Constants.VOLUMES_WITH_SLOS)) {
                    keyMap.put(Constants.VOLUMES_WITH_SLOS, volToFastPolicy);
                } else {
                    Map<String, String> volumesWithFastPolicy = (Map<String, String>) keyMap
                            .get(Constants.VOLUMES_WITH_SLOS);
                    volumesWithFastPolicy.putAll(volToFastPolicy);
                }
            }
        } catch (Exception e) {
            _logger.error("Extracting already exported Volumes failed", e);
        }
    }

    /**
     * get All Child SGs if cascaded
     * 
     * @param path
     * @param client
     * @return
     */
    private List<CIMObjectPath> getChildGroupsifCascaded(CIMObjectPath path, WBEMClient client) {
        CloseableIterator<CIMObjectPath> pathItr = null;
        List<CIMObjectPath> childSGs = new ArrayList<CIMObjectPath>();
        try {
            pathItr = client.referenceNames(path, SmisConstants.SE_MEMBER_OF_COLLECTION_DMG_DMG, null);
            if (!pathItr.hasNext()) {
                // There are no references in this SG, it is a standalone.
                return Collections.emptyList();
            }
            while (pathItr.hasNext()) {
                CIMObjectPath objPath = pathItr.next();
                CIMProperty prop = objPath.getKey(SmisConstants.MEMBER);
                CIMObjectPath comparePath = (CIMObjectPath) prop.getValue();
                if (comparePath.toString().endsWith(path.toString())) {
                    return Collections.emptyList();
                }
                _logger.debug("Found Child SG {}", comparePath.toString());
                childSGs.add(comparePath);
            }

        } catch (Exception e) {
            _logger.info("Got exception trying to retrieve cascade status of SG.  Assuming cascaded: ", e);
        } finally {
            if (null != pathItr) {
                pathItr.close();
            }
        }
        return childSGs;
    }

    /**
     * Add IO limits on volume based on SG they belong to.
     * 
     * Also sets the SLO name in which the SG is configured.
     * 
     * @param client
     * @param path
     * @param volToIolimits
     * @param parentHostIoBw
     * @param parentHostIoPs
     */
    private void addIoLimitsOnVolume(WBEMClient client, CIMObjectPath path, Map<String, VolHostIOObject> volToIolimits,
            Map<String, String> volToFastPolicy, String parentHostIoBw, String parentHostIoPs) {
        try {
            CIMInstance instance = client.getInstance(path, false, true, SmisConstants.PS_HOST_IO);
            String hostIoBw = String.valueOf(instance.getPropertyValue(SmisConstants.EMC_MAX_BANDWIDTH));
            String hostIoPs = String.valueOf(instance.getPropertyValue(SmisConstants.EMC_MAX_IO));
            String fastSetting = SmisUtils.getSLOPolicyName(instance);

            _logger.info("Bw {} and Iops {} found for SG : {} ",
                    new Object[] { hostIoBw, hostIoPs, String.valueOf(instance.getPropertyValue(Constants.ELEMENTNAME)) });
            if (hostIoBw.equals("0") && hostIoPs.equals("0")) {
                hostIoBw = parentHostIoBw;
                hostIoPs = parentHostIoPs;
            }
            CloseableIterator<CIMObjectPath> volPaths = client.associatorNames(path, null, Constants.STORAGE_VOLUME, null, null);
            while (volPaths.hasNext()) {
                CIMObjectPath volPath = volPaths.next();
                String volumeNativeGuid = getVolumeNativeGuid(volPath);
                VolHostIOObject obj = new VolHostIOObject();
                obj.setVolNativeGuid(volumeNativeGuid);
                obj.setHostIoBw(hostIoBw);
                obj.setHostIops(hostIoPs);
                _logger.debug("Volume key: {}..obj : {}", volumeNativeGuid, obj.toString());
                volToIolimits.put(volumeNativeGuid, obj);
                if (!Strings.isNullOrEmpty(fastSetting)) {
                    volToFastPolicy.put(volumeNativeGuid, fastSetting);
                }

            }
        } catch (Exception e) {
            _logger.warn("Finding HostIO limits failed during unmanaged volume discovery", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        _args = inputArgs;
    }

}
