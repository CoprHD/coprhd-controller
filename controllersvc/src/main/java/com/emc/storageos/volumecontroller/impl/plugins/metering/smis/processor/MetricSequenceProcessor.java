/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.cim.CIMInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;

/**
 * MetricSequenceProcessor is responsible to get the sequence of metrics in CSV format retrieved from
 * the provider for each component.
 * 
 */
public class MetricSequenceProcessor extends Processor {
    private Logger _logger = LoggerFactory.getLogger(MetricSequenceProcessor.class);

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws BaseCollectionException {
        try {
            final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
            while (it.hasNext()) {
                CIMInstance blockManifestInstance = null;
                try {
                    blockManifestInstance = it.next();
                    String elementType = getCIMPropertyValue(blockManifestInstance, Constants.ELEMENTTYPE);
                    String elementName = getCIMPropertyValue(blockManifestInstance, Constants.ELEMENTNAME);
                    String[] csvSeq = (String[]) blockManifestInstance.getPropertyValue(Constants.CSV_SEQUENCE);
                    List<String> csvSequenceList = new LinkedList<String>(Arrays.asList(csvSeq));
                    _logger.debug("csvSequenceList: {}", csvSequenceList);
                    if (elementType.equals(Constants.VOLUME_ELEMENTTYPE)
                            && elementName.equals(Constants.STORAGEOS_VOLUME_MANIFEST)) {
                        keyMap.put(Constants.STORAGEOS_VOLUME_MANIFEST, csvSequenceList);
                    } else if (elementType.equals(Constants.FEPORT_ELEMENTTYPE) &&
                            elementName.equals(Constants.STORAGEOS_FEPORT_MANIFEST)) {
                        keyMap.put(Constants.STORAGEOS_FEPORT_MANIFEST, csvSequenceList);
                    } else if (elementType.equals(Constants.FEADAPT_ELEMENTTYPE) &&
                            elementName.equals(Constants.STORAGEOS_FEADAPT_MANIFEST)) {
                        keyMap.put(Constants.STORAGEOS_FEADAPT_MANIFEST, csvSequenceList);
                    } else if (elementType.equals(Constants.SYSTEM_ELEMENTTYPE) &&
                            elementName.equals(Constants.STORAGEOS_SYSTEM_MANIFEST)) {
                        keyMap.put(Constants.STORAGEOS_SYSTEM_MANIFEST, csvSequenceList);
                    }
                } catch (Exception e) {
                    _logger.warn("MetricSequence call failed for {}",
                            getCIMPropertyValue(blockManifestInstance, Constants.INSTANCEID), e);
                }
            }
        } catch (Exception e) {
            _logger.error("Processing MetricSequence call failed", e);
        }
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {
        // TODO Auto-generated method stub
    }
}
