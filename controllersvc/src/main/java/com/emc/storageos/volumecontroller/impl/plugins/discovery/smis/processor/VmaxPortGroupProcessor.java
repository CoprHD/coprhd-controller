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
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
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

public class VmaxPortGroupProcessor extends StorageProcessor{
    private Logger log = LoggerFactory.getLogger(VmaxPortGroupProcessor.class);
    private Set<String> allPortGroupNativeGuids = new HashSet<String> ();
    private DbClient dbClient;
    
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            String serialID = (String) keyMap.get(Constants._serialID);
            dbClient = (DbClient) keyMap.get(Constants.dbClient);
            WBEMClient client = SMICommunicationInterface.getCIMClient(keyMap);
            StorageSystem device = dbClient.queryObject(StorageSystem.class, profile.getSystemId());

            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            while (it.hasNext()) {
                CIMInstance groupInstance = it.next();
                CIMObjectPath groupPath = groupInstance.getObjectPath();
                if (groupPath.toString().contains(serialID)) {
                    String portGroupName = groupInstance.getPropertyValue(Constants.ELEMENTNAME).toString().toLowerCase();
                    String guid = groupPath.toString();
                    log.info("Got the port group " + guid);
                    List<String> storagePorts = new ArrayList<String>();
                    CloseableIterator<CIMInstance> iterator = null;
                    iterator = client.associatorInstances(groupPath, null,
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
                        ExportPathParams portGroup = getPortGroupInDB(guid, portGroupName, device);
                        allPortGroupNativeGuids.add(guid);
                        List<URI> storagePortURIs = new ArrayList<URI>();
                        storagePortURIs.addAll(transform(ExportUtils.storagePortNamesToURIs(dbClient, storagePorts),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI));
                        portGroup.setStoragePorts(StringSetUtil.uriListToStringSet(storagePortURIs));
                        dbClient.updateObject(portGroup);
                    } else {
                        // no storage ports in the port group, remove it
                        log.info(String.format("The port group %s does not have any storage ports, ignore", guid));
                    }
                    
                }
            }
            doBookKeeping(device.getId());
        } catch (Exception e) {
            log.error("Masking path discovery failed during array affinity discovery", e);
        }
    }
    
    /**
     * Get the port group from DB if it is discovered before, or create a new port group if it is a new one.
     * 
     * @param guid - nativeGuid of the port group
     * @param pgName - port group name
     * @param storage - storage system
     * @return - the existing or newly created port group
     */
    private ExportPathParams getPortGroupInDB(String guid, String pgName, StorageSystem storage) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getExportPathParamsNativeGUIdConstraint(guid), result);
        ExportPathParams portGroup = null;
        Iterator<URI> it = result.iterator();
        if (it.hasNext()) {
            portGroup = dbClient.queryObject(ExportPathParams.class, it.next());
        }
        if (portGroup != null && !portGroup.getInactive()) {
        
            return portGroup;
        } else {
            portGroup = new ExportPathParams();
            portGroup.setId(URIUtil.createId(ExportPathParams.class));
            portGroup.setLabel(storage.getSerialNumber() + pgName);
            portGroup.setPortGroup(pgName);
            portGroup.setStorageDevice(storage.getId());
            portGroup.setInactive(false);
            portGroup.setExplicitlyCreated(true);
            dbClient.createObject(portGroup);
        }
        return portGroup;
    }
    
    /**
     * Check if any port group in DB does not show up in the array anymore
     * 
     * @param deviceURI - storage system URI
     */
    private void doBookKeeping(URI deviceURI) {
        URIQueryResultList result = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getPortGroupByStorageSystemConstraint(deviceURI), result);
        List<ExportPathParams> portGroups = dbClient.queryObject(ExportPathParams.class, result);
        if (portGroups != null) {
            for (ExportPathParams portGroup : portGroups) {
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
}
