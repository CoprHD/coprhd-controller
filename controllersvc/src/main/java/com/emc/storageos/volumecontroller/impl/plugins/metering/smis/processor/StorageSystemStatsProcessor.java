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

import java.util.List;
import java.util.Map;
import javax.cim.CIMArgument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;


/**
 * StorageSystemStatsProcessor used in retrieving KBytesWritten,KBytesRead,TotalIOs,ReadIOs,
 * WriteIOs,KBytesTransferred,ReadHitIOs,WriteHitIOs and nativeGuid of System.
 */
public class StorageSystemStatsProcessor extends CommonStatsProcessor {

    private Logger _logger = LoggerFactory.getLogger(StorageSystemStatsProcessor.class);
    
    public static enum SystemMetric
    {
        UnKnown,
        InstanceID,
        ElementType,
        KBytesWritten,
        KBytesRead,
        TotalIOs,
        ReadIOs,
        WriteIOs,
        KBytesTransferred,
        ReadHitIOs,
        WriteHitIOs;
        
        private static final SystemMetric[] metricCopyOfValues = values();

        public static SystemMetric lookup(String name) {
            for (SystemMetric value : metricCopyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return UnKnown;
        }
    }

    /**
     * System metrics sequence: string CSVSequence[] = InstanceID, ElementType,
     * TotalIOs, KBytesTransferred, ReadIOs, ReadHitIOs, KBytesRead, WriteIOs,
     * WriteHitIOs, KBytesWritten;
     * 
     */
    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws SMIPluginException {
        long timeInMillis;
        try {
            timeInMillis = (Long) keyMap.get(Constants._TimeCollected);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) resultObj;
            List<Stat> metricsObjList = (List<Stat>) keyMap.get(Constants._Stats);
            String[] arrayStats = ((String[]) outputArguments[0].getValue())[0].split("\n");
            List<String> metricSequence = (List<String>) keyMap.get(Constants.STORAGEOS_SYSTEM_MANIFEST);
            _logger.debug("System metricNames Sequence {}", metricSequence);
            for (String arrayStat : arrayStats) {
                if (arrayStat.isEmpty()) {
                    _logger.debug("Empty arrayStat returned as part of Statistics Response");
                    continue;
                }
                Stat systemStat = new Stat();
                Iterable<String> splitIterator = Splitter.on(Constants.SEMI_COLON).split(arrayStat);
                List<String> systemMetricList = Lists.newLinkedList(splitIterator);
                String nativeGuid = getSystemNativeGuidFromMetric(systemMetricList.get(0).toUpperCase(), keyMap);
                systemStat.setNativeGuid(nativeGuid);
                systemStat.setResourceId(profile.getSystemId());
                systemStat.setServiceType(Constants._Block);
                systemStat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));
                systemStat.setTimeInMillis(timeInMillis);
                if (null != metricSequence && !metricSequence.isEmpty()) {
                    injectSystemStatMetrics(metricSequence, systemStat, systemMetricList);
                } else {
                    _logger.error("failed processing System Metric values as metric sequence is null.");
                }
                metricsObjList.add(systemStat);
            }
            _logger.info("injected system statistics in DB.");
        } catch (Exception e) {
            _logger.error("Failed while extracting Stats for storage Systems: ", e);
        }
        resultObj = null;
    }

    private void injectSystemStatMetrics(List<String> metricSequence, Stat systemStat, List<String> systemMetricList) {
        int count = 0;
        for (String metricName : metricSequence) {
            String metricValue = systemMetricList.get(count);
            switch (SystemMetric.lookup(metricName)) {
            case InstanceID: case ElementType:
                count++;
                break;
            case KBytesWritten:
                systemStat.setBandwidthIn(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case KBytesRead:
                systemStat.setBandwidthOut(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case TotalIOs:
                systemStat.setTotalIOs(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case ReadIOs:
                systemStat.setReadIOs(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case WriteIOs:
                systemStat.setWriteIOs(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case KBytesTransferred:
                systemStat.setKbytesTransferred(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case ReadHitIOs:
                systemStat.setReadHitIOs(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            case WriteHitIOs:
                systemStat.setWriteHitIOs(ControllerUtils.getLongValue(metricValue));
                count++;
                break;
            default:
                _logger.warn("Ignoring unknown metric {} during system metric processing:", metricName);
                count++;
                break;
            }
        }  
    }
}
