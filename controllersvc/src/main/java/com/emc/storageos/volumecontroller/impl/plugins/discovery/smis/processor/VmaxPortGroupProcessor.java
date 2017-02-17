package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.collect.Collections2.transform;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePortGroup;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.model.portgroup.StoragePortGroupList;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.impl.plugins.SMICommunicationInterface;
import com.emc.storageos.volumecontroller.impl.smis.CIMPropertyFactory;

public class VmaxPortGroupProcessor extends StorageProcessor{
    private Logger log = LoggerFactory.getLogger(VmaxPortGroupProcessor.class);
    private Set<String> allPortGroupNativeGuids = new HashSet<String> ();
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
                    String portGroupName = groupInstance.getPropertyValue(Constants.ELEMENTNAME).toString().toLowerCase();
                    if (portGroupName == null || portGroupName.isEmpty()) {
                        log.info(String.format("The port group %s name is null, skip", groupPath.toString()));
                        continue;
                    }
                    log.info("Got the port group: " + portGroupName);
                    List<String> storagePorts = new ArrayList<String>();
                    CloseableIterator<CIMInstance> iterator = client.associatorInstances(groupPath, null,
                            Constants.CIM_PROTOCOL_ENDPOINT, null, null, false, Constants.PS_NAME);
                    while (iterator.hasNext()) {
                        CIMInstance cimInstance = iterator.next();
                        String portName = CIMPropertyFactory.getPropertyValue(cimInstance,
                                Constants._Name);
                        String fixedName = Initiator.toPortNetworkId(portName);
                        storagePorts.add(fixedName);
                        log.info("port member: " + fixedName);
                    }
                    if (!storagePorts.isEmpty()) {
                        StoragePortGroup portGroup = getPortGroupInDB(portGroupName, device);
                        if (portGroup == null) {
                            // Check if the port group is ViPR created
                            if (!hasVolume ||!checkViPRCreated(client, groupPath, portGroupName, device)) {
                                portGroup = createPortGroup(portGroupName, device, false);
                            } else {
                                portGroup = createPortGroup(portGroupName, device, true);
                            }
                        }
                        allPortGroupNativeGuids.add(portGroup.getNativeGuid());
                        List<URI> storagePortURIs = new ArrayList<URI>();
                        storagePortURIs.addAll(transform(ExportUtils.storagePortNamesToURIs(dbClient, storagePorts),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI));
                        portGroup.setStoragePorts(StringSetUtil.uriListToStringSet(storagePortURIs));
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
     * Get the port group from DB if it is discovered before, or create a new port group if it is a new one.
     * @param pgName - port group name
     * @param storage - storage system
     * @return - the existing or newly created port group
     */
    private StoragePortGroup getPortGroupInDB(String pgName, StorageSystem storage) {
        String guid = String.format("%s+%s" , storage.getNativeGuid(), pgName);
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getPortGroupNativeGUIdConstraint(guid), result);
        StoragePortGroup portGroup = null;
        Iterator<URI> it = result.iterator();
        if (it.hasNext()) {
            portGroup = dbClient.queryObject(StoragePortGroup.class, it.next());
        }
        return portGroup;
    }
    
    private StoragePortGroup createPortGroup(String pgName, StorageSystem storage, boolean viprCreated) {
        String guid = String.format("%s+%s" , storage.getNativeGuid(), pgName);
        StoragePortGroup portGroup = new StoragePortGroup();
        portGroup.setId(URIUtil.createId(StoragePortGroup.class));
        portGroup.setLabel(pgName);
        portGroup.setNativeGuid(guid);
        portGroup.setStorageDevice(storage.getId());
        portGroup.setInactive(false);
        if (viprCreated) {
            portGroup.setRegistrationStatus(RegistrationStatus.UNREGISTERED.name());
            portGroup.addInternalFlags(Flag.INTERNAL_OBJECT);
        } else {
            portGroup.setRegistrationStatus(RegistrationStatus.REGISTERED.name());
        }
        dbClient.createObject(portGroup);
        return portGroup;
    }
    
    /**
     * Check if any port group in DB does not show up in the array anymore
     * 
     * @param deviceURI - storage system URI
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
     * @param dbClient
     * @return
     */
    private boolean hasAnyVolume(URI systemUri) {
        boolean result = false;
        URIQueryResultList queryList = new URIQueryResultList();
        dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceVolumeConstraint(systemUri),
                queryList);
        
        Iterator<URI> volumeIter = queryList.iterator();
        if (volumeIter.hasNext()) {
            // has export mask
            log.info("The system has volume");
            result = true;
        }
        return result;
    }
    
    /**
     * Check if the port group is created by ViPR as part of volume export without reuse port group
     * 
     * @param client - WBEMClient
     * @param groupPath - port group CIMObjectPath
     * @param portGroupName - port group name
     * @param device - Storage system
     * @return true or false
     */
    private boolean checkViPRCreated(WBEMClient client, CIMObjectPath groupPath, String portGroupName, StorageSystem device) {
        boolean result = false;
        try {
            CloseableIterator<CIMInstance> iterator = client.associatorInstances(groupPath, null,
                    Constants.SYMM_LUNMASKINGVIEW, null, null, false, Constants.PS_NAME);
        
            if (!iterator.hasNext()) {
                // No lun masking view associated. 
                return result;
            }
            int count = 0;
            String maskName = null;
            while (iterator.hasNext() ) {
                count ++;
                if (count > 1) {
                    // ViPR created port group could only be associated with one lun masking view
                    return false;
                    
                }
                CIMInstance cimInstance = iterator.next();
                maskName = CIMPropertyFactory.getPropertyValue(cimInstance, Constants._Name);
                log.info("maskName: " + maskName);
            }
            if (maskName != null) {
                // Try to see if we could find export mask in ViPR has the same name
                URIQueryResultList exportMaskURIs = new URIQueryResultList();
                dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getExportMaskByNameConstraint(maskName), exportMaskURIs);
                Iterator<URI> maskIt = exportMaskURIs.iterator();
                while (maskIt.hasNext()) {
                    URI maskURI = maskIt.next();
                    ExportMask mask = dbClient.queryObject(ExportMask.class, maskURI);
                    if (device.getId().equals(mask.getStorageDevice())) {
                        log.info(String.format("The port group %s is used by the export mask %s %s", portGroupName, maskName, 
                                maskURI.toString()));
                        result = true;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.info("Exception while getting port gorup association:", e);
            result = false;
        }
        return result;
    }
}
