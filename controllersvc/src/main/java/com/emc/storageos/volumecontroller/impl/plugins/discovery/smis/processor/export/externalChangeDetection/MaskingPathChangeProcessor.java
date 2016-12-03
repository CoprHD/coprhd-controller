package com.emc.storageos.volumecontroller.impl.plugins.discovery.smis.processor.export.externalChangeDetection;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.plugins.ServiceOptions.serviceParameters;
import com.emc.storageos.volumecontroller.impl.smis.SmisConstants;
import com.google.common.base.Joiner;

public class MaskingPathChangeProcessor extends Processor {
    
    private Logger _logger = LoggerFactory.getLogger(MaskingPathChangeProcessor.class);
    
    @SuppressWarnings({ "unused", "unchecked", "deprecation" })
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            List<URI> maskUris = (List<URI>) keyMap.get(serviceParameters.EXPORTMASKS);
            _logger.info("Masking views to get processed :{}", Joiner.on("@@").join(maskUris));
            if (null == maskUris) {
                return;
            }
            List<ExportMask> exportMasks = dbClient.queryObject(ExportMask.class, maskUris);
            
            String serialID = (String) keyMap.get(Constants._serialID);
            Iterator<CIMObjectPath> it = (Iterator<CIMObjectPath>) resultObj;
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
                        _logger.info("Mask {} found to get refreshed", maskName);
                        addPath(keyMap, operation.getResult(), path);
                    }
                    
                }
                
            }
        } catch (Exception e) {
            _logger.error("Masking path discovery failed during array affinity discovery", e);
        }
    }
    
}
