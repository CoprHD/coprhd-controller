/*
 * Copyright (c) 2008-2015 EMC Corporation
 * All Rights Reserved
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
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.CommonStatsProcessor;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * Processor responsible to process the Hitachi StorageSystem statistics & persist them db.
 * 
 * Supported metrics: [TotalIOs, KBytesTransferred]
 * Other attributes: [ElementType, StatisticTime]
 * 
 */
public class StorageSystemStatsProcessor extends CommonStatsProcessor {
    private Logger logger = LoggerFactory.getLogger(StorageSystemStatsProcessor.class);

    private static final String TOTALIOS = "TotalIOs";
    private static final String KBYTESTRANSFERRED = "KBytesTransferred";
    private static final String STATISTICTIME = "StatisticTime";
    private static final String INSTANCE_ID = "InstanceID";

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        Iterator<CIMInstance> systemStatsResponseItr = (Iterator<CIMInstance>) resultObj;
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
        logger.info("Processing storagesystem {} stats response", profile.getSystemId());
        try {
            List<Stat> metricsObjList = (List<Stat>) keyMap.get(Constants._Stats);
            if (systemStatsResponseItr != null) {
                while (systemStatsResponseItr.hasNext()) {
                    CIMInstance systemStatInstance = (CIMInstance) systemStatsResponseItr
                            .next();
                    Stat systemStat = new Stat();
                    // HUS VM.211643
                    systemStat.setNativeGuid(getSystemNativeGuid(systemStatInstance, dbClient));
                    systemStat.setResourceId(profile.getSystemId());
                    systemStat.setServiceType(Constants._Block);
                    systemStat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));

                    Long providerCollectionTime = convertCIMStatisticTime(getCIMPropertyValue(systemStatInstance, STATISTICTIME));

                    if (0 != providerCollectionTime) {
                        systemStat.setTimeInMillis(providerCollectionTime);
                    } else {
                        systemStat.setTimeInMillis((Long) keyMap.get(Constants._TimeCollected));
                    }
                    systemStat.setTotalIOs(ControllerUtils.getLongValue(getCIMPropertyValue(
                            systemStatInstance, TOTALIOS)));
                    systemStat.setKbytesTransferred(ControllerUtils.getLongValue(getCIMPropertyValue(
                            systemStatInstance, KBYTESTRANSFERRED)));
                    metricsObjList.add(systemStat);
                }
            }
        } catch (Exception ex) {
            logger.error("Failed while extracting Stats for storage System: ", ex);
        } finally {
            resultObj = null;
        }
        logger.info("Processing storagesystem {} stats response completed", profile.getSystemId());
    }

    /**
     * Return the System NativeGuid based on the Stat instance returned from provider.
     * 
     * @param systemStatInstance
     * @param dbClient
     * @return
     */
    private String getSystemNativeGuid(CIMInstance systemStatInstance, DbClient dbClient) {
        String systemNativeGuid = null;
        String instanceId = getCIMPropertyValue(systemStatInstance, INSTANCE_ID);
        URIQueryResultList result = new URIQueryResultList();
        Iterable<String> instanceIdSplitter = Splitter.on(HDSConstants.DOT_OPERATOR).limit(2).split(instanceId);
        String serialNo = Iterables.getLast(instanceIdSplitter);

        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStorageDeviceSerialNumberConstraint(serialNo), result);

        Iterator<URI> activeSystemListItr = result.iterator();
        if (activeSystemListItr.hasNext()) {
            StorageSystem system = dbClient.queryObject(StorageSystem.class,
                    activeSystemListItr.next());
            systemNativeGuid = system.getNativeGuid();
        }

        return systemNativeGuid;
    }

}
