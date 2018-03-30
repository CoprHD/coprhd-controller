/*
 * Copyright (c) 2017 Dell-EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMInstance;
import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;

public class VmaxPortGroupProcessor extends StorageProcessor {
    private Logger log = LoggerFactory.getLogger(VmaxPortGroupProcessor.class);
    private Set<String> allPortGroupNativeGuids = new HashSet<String>();
    private DbClient dbClient;

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        log.info("Process port group");
        try {
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            String serialID = (String) keyMap.get(Constants._serialID);
            dbClient = (DbClient) keyMap.get(Constants.dbClient);
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            StorageSystem device = dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            boolean hasVolume = hasAnyVolume(device.getId());

            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            while (it.hasNext()) {
                CIMInstance groupInstance = it.next();
                CIMObjectPath groupPath = groupInstance.getObjectPath();
                if (groupPath.toString().contains(serialID)) {
                    String portGroupName = groupInstance.getPropertyValue(Constants.ELEMENTNAME).toString();
                    if (StringUtils.isEmpty(portGroupName)) {
                        log.info(String.format("The port group %s name is null, skip", groupPath.toString()));
                        continue;
                    }
                    log.info(String.format("Got the port group: %s", portGroupName));
                    List<String> storagePorts = new ArrayList<String>();
                    CloseableIterator<CIMInstance> iterator = client.associatorInstances(groupPath, null,
                            Constants.CIM_PROTOCOL_ENDPOINT, null, null, false, Constants.PS_NAME);
                    while (iterator.hasNext()) {
                        CIMInstance cimInstance = iterator.next();
                        String portName = CIMPropertyFactory.getPropertyValue(cimInstance,
                                Constants._Name);
                        String fixedName = Initiator.toPortNetworkId(portName);
                        log.debug("Storage Port: {}", fixedName);
                        storagePorts.add(fixedName);
                    }
                    if (!storagePorts.isEmpty()) {
                        StoragePortGroup portGroup = getPortGroupInDB(portGroupName, device);
                        if (portGroup == null) {
                            // Check if the port group is used in any export mask. If the port group is not in the DB,
                            // but
                            // it is used by any existing ExportMask, then this is a upgrade case, and this is the first
                            // time
                            // discovery after the upgrade.
                            if (hasVolume) {
                                List<ExportMask> masks = getExportMasksForPortGroup(client, groupPath, portGroupName, device);
                                boolean viprCreated = (!masks.isEmpty());
                                portGroup = createPortGroup(portGroupName, device, viprCreated);

                                for (ExportMask mask : masks) {
                                    mask.setPortGroup(portGroup.getId());
                                }
                                dbClient.updateObject(masks);
                            } else {
                                portGroup = createPortGroup(portGroupName, device, false);
                            }
                        }
                        allPortGroupNativeGuids.add(portGroup.getNativeGuid());
                        List<URI> storagePortURIs = new ArrayList<URI>();
                        storagePortURIs.addAll(transform(ExportUtils.storagePortNamesToURIs(dbClient, storagePorts),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI));
                        if (!portGroup.getStoragePorts().isEmpty()) {
                            portGroup.getStoragePorts().replace(StringSetUtil.uriListToStringSet(storagePortURIs));
                        } else {
                            portGroup.setStoragePorts(StringSetUtil.uriListToStringSet(storagePortURIs));
                        }

                        dbClient.updateObject(portGroup);
                    } else {
                        // no storage ports in the port group, remove it
                        log.info(String.format("The port group %s does not have any storage ports, ignore", portGroupName));
                    }

                }
            }
            if (!allPortGroupNativeGuids.isEmpty()) {
                doBookKeeping(device.getId());
            } else {
                log.info("Did not get any port group, skip book keeping");
            }
        } catch (Exception e) {
            log.error("port group discovery failed ", e);
        } finally {
            allPortGroupNativeGuids.clear();
        }
    }

    /**
     * Get the port group from DB if it is discovered before.
     * 
     * @param pgName
     *            - port group name
     * @param storage
     *            - storage system
     * @return - the existing or newly created port group
     */
    private StoragePortGroup getPortGroupInDB(String pgName, StorageSystem storage) {
        String guid = String.format("%s+%s", storage.getNativeGuid(), pgName);
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getPortGroupNativeGuidConstraint(guid), result);
        StoragePortGroup portGroup = null;
        Iterator<URI> it = result.iterator();
        if (it.hasNext()) {
            portGroup = dbClient.queryObject(StoragePortGroup.class, it.next());
        }
        return portGroup;
    }

    /**
     * Create a port group instance in ViPR DB
     * 
     * @param pgName
     *            - port group name
     * @param storage
     *            - storage system
     * @param viprCreated
     *            - if the port group is implicitly created by ViPR
     * @return created storage port group
     */
    private StoragePortGroup createPortGroup(String pgName, StorageSystem storage, boolean viprCreated) {
        String guid = String.format("%s+%s", storage.getNativeGuid(), pgName);
        StoragePortGroup portGroup = new StoragePortGroup();
        portGroup.setId(URIUtil.createId(StoragePortGroup.class));
        portGroup.setLabel(pgName);
        portGroup.setNativeGuid(guid);
        portGroup.setStorageDevice(storage.getId());
        portGroup.setInactive(false);
        if (viprCreated) {
            portGroup.setRegistrationStatus(RegistrationStatus.UNREGISTERED.name());
        } else {
            portGroup.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        }
        portGroup.setMutable(viprCreated);
        dbClient.createObject(portGroup);
        return portGroup;
    }

    /**
     * Check if any port group in DB does not show up in the array anymore
     * 
     * @param deviceURI
     *            - storage system URI
     */
    private void doBookKeeping(URI deviceURI) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDevicePortGroupConstraint(deviceURI), result);
        List<StoragePortGroup> portGroups = dbClient.queryObject(StoragePortGroup.class, result);
        if (portGroups != null) {
            for (StoragePortGroup portGroup : portGroups) {
                String nativeGuid = portGroup.getNativeGuid();
                if (nativeGuid != null && !nativeGuid.isEmpty() &&
                        !allPortGroupNativeGuids.contains(nativeGuid)) {
                    // the port group does not exist in the array. remove it
                    log.info(String.format("The port group %s does not exist in the array, remove it from DB", nativeGuid));
                    dbClient.removeObject(portGroup);
                }
            }
        }
    }

    /**
     * Check if there is any volume for the storage system. this is to check if this is a newly created storage system.
     * 
     * @param systemUri
     *            Storage system URI
     * @return if the storage system has any volumes
     */
    private boolean hasAnyVolume(URI systemUri) {
        boolean result = false;
        URIQueryResultList queryList = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(systemUri),
                queryList);

        Iterator<URI> volumeIter = queryList.iterator();
        if (volumeIter.hasNext()) {
            result = true;
        }
        return result;
    }

    /**
     * Get export masks using the specified port group
     * 
     * @param client
     *            - WBEMClient
     * @param groupPath
     *            - port group CIMObjectPath
     * @param portGroupName
     *            - port group name
     * @param device
     *            - Storage system
     * @return List of export mask using this port group
     */
    private List<ExportMask> getExportMasksForPortGroup(WBEMClient client, CIMObjectPath groupPath, String portGroupName,
            StorageSystem device) {
        List<ExportMask> result = new ArrayList<ExportMask>();
        CloseableIterator<CIMInstance> iterator = null;
        try {
            iterator = client.associatorInstances(groupPath, null,
                    Constants.SYMM_LUNMASKINGVIEW, null, null, false, Constants.PS_NAME);

            if (!iterator.hasNext()) {
                // No lun masking view associated.
                return result;
            }

            while (iterator.hasNext()) {
                CIMInstance cimInstance = iterator.next();
                String maskName = CIMPropertyFactory.getPropertyValue(cimInstance, Constants._Name);

                if (maskName != null) {
                    // Try to see if we could find export mask in ViPR has the same name
                    URIQueryResultList exportMaskURIs = new URIQueryResultList();
                    dbClient.queryByConstraint(
                            AlternateIdConstraint.Factory.getExportMaskByNameConstraint(maskName), exportMaskURIs);
                    Iterator<URI> maskIt = exportMaskURIs.iterator();
                    while (maskIt.hasNext()) {
                        URI maskURI = maskIt.next();
                        ExportMask mask = dbClient.queryObject(ExportMask.class, maskURI);
                        if (device.getId().equals(mask.getStorageDevice()) &&
                                mask.getUserAddedVolumes() != null && !mask.getUserAddedVolumes().isEmpty()) {
                            result.add(mask);
                            log.info(String.format("The port group %s is used by the export mask %s %s", portGroupName, maskName,
                                    maskURI.toString()));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Exception while getting port gorup association:", e);
        } finally {
            if (null != iterator) {
                iterator.close();
            }
        }
        return result;
    }
}