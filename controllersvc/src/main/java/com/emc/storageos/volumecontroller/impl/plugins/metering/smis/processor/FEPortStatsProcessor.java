/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

/**
 * FEPortStatsProcessor used in retrieving TotalIOs,KBytesTransferred and nativeGuid of FEPort.
 */
public class FEPortStatsProcessor extends CommonStatsProcessor {
    private Logger _logger = LoggerFactory.getLogger(FEPortStatsProcessor.class);
    PortMetricsProcessor portMetricsProcessor;

    public static enum FEPortMetric
    {
        UnKnown,
        InstanceID,
        ElementType,
        TotalIOs,
        KBytesTransferred,
        StatisticTime;

        private static final FEPortMetric[] metricCopyOfValues = values();

        public static FEPortMetric lookup(String name) {
            for (FEPortMetric value : metricCopyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return UnKnown;
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws SMIPluginException {
        try {
            CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) resultObj;
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            List<Stat> metricsObjList = (List<Stat>) keyMap.get(Constants._Stats);
            List<String> metricSequence = (List<String>) keyMap.get(Constants.STORAGEOS_FEPORT_MANIFEST);
            String[] feportsMetricValues = ((String[]) outputArguments[0].getValue())[0].split("\n");
            List<StoragePort> systemPorts = ControllerUtils.getSystemPortsOfSystem(dbClient, profile.getSystemId());
            _logger.debug("FEPort metricNames Sequence {}", metricSequence);
            // Step1: If there is no metric sequence, there is no need to
            // process the results.
            if (null != metricSequence && !metricSequence.isEmpty()) {
                // Step2: For each feport metric record
                for (String fePortMetricValue : feportsMetricValues) {
                    if (fePortMetricValue.isEmpty()) {
                        _logger.debug("Empty FEPort stats returned as part of Statistics Response");
                        continue;
                    }
                    String metrics[] = fePortMetricValue.split(Constants.SEMI_COLON);
                    // Step 3: For each port in db for a given system.
                    for (StoragePort port : systemPorts) {
                        // Step 4: if port in db is null just continue.
                        if (null == port) {
                            continue;
                        } else if (!port.getInactive() && metrics[0].endsWith(port.getPortName())) {
                            // Step 5: Check whether provider returned port
                            // exists in db or not. if port exists in db,
                            // then create a PortStat object for it.
                            _logger.debug("found FEPort in db for {}", port.getPortName());
                            createPortStatMetric(metricSequence, port, keyMap, metricsObjList, metrics);
                        }
                    }

                }

                //
                // compute port metric to trigger if any port allocation qualification changed. If there is
                // changes, run vpool matcher
                //
                portMetricsProcessor.triggerVpoolMatcherIfPortAllocationQualificationChanged(profile.getSystemId(), systemPorts);

                //
                // compute storage system's average of port metrics. Then, persist it into storage system object.
                //
                portMetricsProcessor.computeStorageSystemAvgPortMetrics(profile.getSystemId());
            } else {
                _logger.error("failed processing FEPOrt Metric values as metric sequence is null.");
            }
        } catch (Exception e) {
            _logger.error("Failed while extracting stats for FEPorts: ", e);
        }
        resultObj = null;
    }

    /**
     * Create a new PortStat.
     * 
     * @param metrics
     * @param portStatsList
     * @param port
     * @param keyMap
     */
    private void createPortStatMetric(List<String> metricSequence, StoragePort port, Map<String, Object> keyMap,
            List<Stat> portStatsList, String metrics[]) {
        int count = 0;
        Stat portStat = new Stat();

        Long kbytes = 0L;
        Long iops = 0L;
        String statisticTime = "";

        for (String metricName : metricSequence) {
            portStat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));
            portStat.setTimeInMillis((Long) keyMap.get(Constants._TimeCollected));
            portStat.setNativeGuid(port.getNativeGuid());
            portStat.setResourceId(port.getId());
            portStat.setServiceType(Constants._Block);

            switch (FEPortMetric.lookup(metricName)) {
                case InstanceID:
                case ElementType:
                    break;
                case TotalIOs:
                    iops = ControllerUtils.getLongValue(metrics[count]);
                    portStat.setTotalIOs(iops);
                    break;
                case KBytesTransferred:
                    kbytes = ControllerUtils.getLongValue(metrics[count]);
                    portStat.setKbytesTransferred(kbytes);
                    break;
                case StatisticTime:
                    statisticTime = metrics[count];
                    break;
                default:
                    _logger.warn("Ignoring unknown metric {} during system metric processing:", metricName);
                    break;
            }
            count++;
        }
        portStatsList.add(portStat);

        // Process the port metrics.
        portMetricsProcessor.processFEPortMetrics(kbytes, iops, port, convertCIMStatisticTime(statisticTime));
    }

    public PortMetricsProcessor getPortMetricsProcessor() {
        return portMetricsProcessor;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }
}
