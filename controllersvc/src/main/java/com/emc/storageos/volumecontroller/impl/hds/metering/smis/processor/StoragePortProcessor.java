/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds.metering.smis.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Processor;
import com.emc.storageos.plugins.common.domainmodel.Operation;


public class StoragePortProcessor extends Processor {
	private Logger _logger = LoggerFactory.getLogger(StoragePortProcessor.class);
	
	private static final String LINKTECHNOLOGY = "LinkTechnology";
	
	@Override
	public void processResult(Operation operation, Object resultObj,
			Map<String, Object> keyMap) throws BaseCollectionException {
		try {
			@SuppressWarnings("unchecked")
			final Iterator<CIMInstance> it = (Iterator<CIMInstance>) resultObj;
			while (it.hasNext()) {
				CIMInstance storagePortInstance = it.next();
				if ("4".equalsIgnoreCase(getCIMPropertyValue(
						storagePortInstance, LINKTECHNOLOGY))) {
					addPath(keyMap, operation.getResult(),
							storagePortInstance.getObjectPath());
				}
			}
		} catch (Exception e) {
			_logger.error("Fetching Storage Ports Info failed -->{}",
					getMessage(e));
		}
	}

	@Override
	protected void setPrerequisiteObjects(List<Object> inputArgs)
			throws BaseCollectionException {
		// TODO Auto-generated method stub
		
	}

}
