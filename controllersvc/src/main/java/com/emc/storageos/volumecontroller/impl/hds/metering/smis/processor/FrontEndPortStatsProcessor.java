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

import java.net.URI;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.util.SanUtils;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.CommonStatsProcessor;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * Processor responsible to process the Hitachi StorageSystem statistics & persist them db.
 * 
 * Supported metrics: [TotalIOs, KBytesTransferred]
 * Other attributes:  [ElementType, StatisticTime]
 *
 */
public class FrontEndPortStatsProcessor extends CommonStatsProcessor {
	private Logger logger = LoggerFactory.getLogger(FrontEndPortStatsProcessor.class);
	
	private static final String TOTALIOS = "TotalIOs"; 
	private static final String KBYTESTRANSFERRED = "KBytesTransferred";
	private static final String STATISTICTIME = "StatisticTime";
	private static final String INSTANCE_ID = "InstanceID";
	
	private PortMetricsProcessor portMetricsProcessor;

	@SuppressWarnings("unchecked")
	@Override
	public void processResult(Operation operation, Object resultObj,
			Map<String, Object> keyMap) throws BaseCollectionException {
		Iterator<CIMInstance> storagePortStatsResponseItr = (Iterator<CIMInstance>) resultObj;
		AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
		URI systemId = profile.getSystemId();
		DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
		logger.info("Processing FrontEnd Ports response");
		try {
			List<Stat> metricsObjList = (List<Stat>) keyMap.get(Constants._Stats);
			while (storagePortStatsResponseItr.hasNext()) {
				CIMInstance storagePortStatInstance = (CIMInstance) storagePortStatsResponseItr
						.next();
				Stat fePortStat = new Stat();
                fePortStat.setServiceType(Constants._Block);
                fePortStat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));
                
                Long providerCollectionTime = convertCIMStatisticTime(getCIMPropertyValue(storagePortStatInstance, STATISTICTIME));
                
                if (0 != providerCollectionTime) {
                	fePortStat.setTimeInMillis(providerCollectionTime);
                } else {
                	fePortStat.setTimeInMillis((Long) keyMap.get(Constants._TimeCollected));
                }
                fePortStat.setTotalIOs(ControllerUtils.getModLongValue(getCIMPropertyValue(
						storagePortStatInstance, TOTALIOS)));
                fePortStat.setKbytesTransferred(ControllerUtils.getModLongValue(getCIMPropertyValue(
						storagePortStatInstance, KBYTESTRANSFERRED)));
                setPortRelatedInfo(storagePortStatInstance, systemId, dbClient, fePortStat);
                metricsObjList.add(fePortStat);
			}
		} catch (Exception ex) {
			logger.error("Failed while extracting Stats for Front end ports: ", ex);
		} finally {
			resultObj = null;
		}
		logger.info("Processing FrontEnd Ports response completed");
	}

	/**
	 * Return the port nativeGuid based on the Stat objects wwn.
	 * @param storagePortStatInstance
	 * @param systemId
	 * @param dbClient
	 * @return
	 */
	private void setPortRelatedInfo(CIMInstance storagePortStatInstance,
			URI systemId, DbClient dbClient, Stat fePortStat) {
		String instanceId = getCIMPropertyValue(storagePortStatInstance,
				INSTANCE_ID);
		Iterable<String> splitter = Splitter.on(HDSConstants.DOT_OPERATOR)
				.limit(3).split(instanceId);
		String portWWN = Iterables.getLast(splitter);
		URIQueryResultList storagePortURIList = new URIQueryResultList();
		dbClient.queryByConstraint(AlternateIdConstraint.Factory
				.getStoragePortEndpointConstraint(SanUtils.normalizeWWN(portWWN).toUpperCase()), storagePortURIList);
		Iterator<URI> itr = storagePortURIList.iterator();
		while (itr.hasNext()) {
			StoragePort port = dbClient.queryObject(StoragePort.class,
					itr.next());
			if (port.getStorageDevice().equals(systemId)) {
				fePortStat.setNativeGuid(port.getNativeGuid());
				fePortStat.setResourceId(port.getId());
				portMetricsProcessor.processFEPortMetrics(fePortStat.getKbytesTransferred(),
                                    fePortStat.getTotalIOs(), port, fePortStat.getTimeInMillis());
				break;
			}
		}
	}
	
    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }

}
