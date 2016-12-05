/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.externalChangeDetection;

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.StorageSystem.DiscoveryModules;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;

public class MaskingPathChangeProcessor extends Processor {
    
    private Logger _logger = LoggerFactory.getLogger(MaskingPathChangeProcessor.class);
    
    @SuppressWarnings({ "unused", "unchecked", "deprecation" })
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            Map<String, List<URI>> subNamespaces = (Map<String, List<URI>>) keyMap.get(Constants.SUBNAMESPACES);
            if (null == subNamespaces ||
                    !subNamespaces.containsKey(DiscoveryModules.MASKING) || subNamespaces.get(DiscoveryModules.MASKING.name()).isEmpty()) {
               _logger.info("Skipping Detection of masking views");
               return;
            }
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            List<URI> maskUris = subNamespaces.get(DiscoveryModules.MASKING.name());
            _logger.info("Masking views to get processed :{}", Joiner.on("@@").join(maskUris));
            if (null == maskUris) {
                _logger.warn("Change Detection received 0 masks to process");
                return;
            }
            String serialID = (String) keyMap.get(Constants._serialID);
            Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
            /**
             * Loop through each export Mask, get its entry from database. Check
             * if the URI is available in the list to get processed if yes, then
             * add it to the map, the next SMI-S call would detect or resolve
             * changes on the masking view.
             */
            while (it.hasNext()) {
                CIMObjectPath path = it.next();
                if (path.toString().contains(serialID)) {
                    String maskName = getCIMPropertyValue(path, SmisConstants.CP_DEVICE_ID);
                    _logger.info("Processing mask {}", maskName);
                    List<URI> maskUri = dbClient.queryByConstraint(AlternateIdConstraint.Factory
                            .getExportMaskByNameConstraint(maskName));
                    if (null == maskUri || maskUri.isEmpty()) {
                        _logger.debug("Mask {} Not found in DB", maskName);
                    } else if (maskUris.contains(maskUri)) {
                        _logger.info("Mask {} found will be added to detect or resolve changes", maskName);
                        addPath(keyMap, operation.getResult(), path);
                    }
                    
                }
                
            }
            //After processing of all the masks, we have to remove the subNamespace masking. The reason being if more than one subNamespace was issued, then
            // we should not process the current Namespace.
           subNamespaces.get(DiscoveryModules.MASKING.name()).clear();
           keyMap.put(Constants.SUBNAMESPACES, subNamespaces);
        } catch (Exception e) {
            _logger.error("Change Detection on Masking failed, ", e);
        }
    }
    
}
