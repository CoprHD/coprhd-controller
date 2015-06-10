/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.cim.CIMArgument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;

public class FEAdaptStatsProcessor extends CommonStatsProcessor {
    private final Logger _logger = LoggerFactory.getLogger(FEAdaptStatsProcessor.class);
    private final static Long VNX_CLOCK_TICK_INTERVAL_MSEC = 100L;
    private PortMetricsProcessor portMetricsProcessor;
    
    public static enum FEAdaptMetric
    {
        UnKnown,
        InstanceID,
        ElementType,
        TotalIOs,
        IOTimeCounter,
        IdleTimeCounter,
        EMCCollectionTimeDir,
        EMCIdleTimeDir,
        StatisticTime,
        EMCPercentBusyTime;
        
        private static final FEAdaptMetric[] metricCopyOfValues = values();

        public static FEAdaptMetric lookup(String name) {
            for (FEAdaptMetric value : metricCopyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return UnKnown;
        }
    }

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        try {
            CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) resultObj;
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
            StorageSystem system = dbClient.queryObject(StorageSystem.class, profile.getSystemId());
            List<String> metricSequence = (List<String>) keyMap.get(Constants.STORAGEOS_FEADAPT_MANIFEST);
            String[] feadaptMetricValues = ((String[]) outputArguments[0].getValue())[0].split("\n");
            Map<String, StorageHADomain> haDomains = getHADomainOfSystem(dbClient, profile.getSystemId());
            if (null == metricSequence || metricSequence.isEmpty()) {
                _logger.error("No metric sequence for FEAdaptStatsProcessor; no processing will happen");
                return;
            }
            for (String metricValue : feadaptMetricValues) {
                if (metricValue.isEmpty()) continue;
                String metrics[] = metricValue.split(Constants.SEMI_COLON);
                String instanceId = metrics[0];
                String instanceName;
                if (instanceId.contains(Constants.SMIS80_DELIMITER)) {
                	instanceName = instanceId.replaceAll(".*\\Q" + Constants.SMIS80_DELIMITER + "\\E", "");
                } else {
                	instanceName = instanceId.replaceAll(".*\\+", "");
                }
                StorageHADomain haDomain = haDomains.get(instanceName);
                if (haDomain == null) {
                    _logger.error("No StorageHADomain for instanceName: " + instanceName);
                    continue;
                }
                updateMetrics(metrics, metricSequence,haDomain, system, keyMap);
            }
        } catch (Exception e) {
            _logger.error("Failed while extracting stats for FEAdapts: ", e);
        }
    }
    
    private void updateMetrics(String[] metrics, List<String> metricSequence, 
            StorageHADomain haDomain, StorageSystem system, 
            Map<String, Object> keyMap) {
        // Determine if there were previous metrics values
        boolean isVmax = StorageSystem.Type.vmax.name().equals(system.getSystemType());
        boolean isVnx = StorageSystem.Type.vnxblock.name().equals(system.getSystemType());
        
        Double percentBusy = 0.0;
        Long idleTicks = 0L;
        Long cumTicks = 0L;
        Long ioTicks = 0L;
        Long iops = 0L;
        String statisticTime = "";
        
        int count = 0;
        for (String metric : metricSequence) {
            FEAdaptMetric mx = FEAdaptMetric.lookup(metric);
            switch(mx) {
            case EMCIdleTimeDir:
                if (isVmax) idleTicks = ControllerUtils.getModLongValue(metrics[count]);
                break;
            case IdleTimeCounter:
                if (isVnx) idleTicks = ControllerUtils.getModLongValue(metrics[count]);
                break;
            case EMCCollectionTimeDir:
                if (isVmax) cumTicks = ControllerUtils.getModLongValue(metrics[count]);
                break;
            case IOTimeCounter:
                if (isVnx) ioTicks = ControllerUtils.getModLongValue(metrics[count]);
                break;
            case TotalIOs:
                iops = ControllerUtils.getModLongValue(metrics[count]);
                break;
            case StatisticTime:
                statisticTime = metrics[count];
                break;
            case EMCPercentBusyTime:
            	percentBusy = ControllerUtils.getDoubleValue(metrics[count]);
            	break;
            default:
                break;
            }
            count++;
        }
        
        // If Vnx, calculate the cumlative ticks as staticTimeMsec * (usecPerMsec / clockTickIntervalUsec)
        Long statisticTimeMsec = portMetricsProcessor.convertCIMStatisticTime(statisticTime);
        _logger.info(String.format("%s: IdleTimeDir %d CollectionTimeDir %d IdleTimeCounter %d IOTimeCounter %d" 
                + " Total IOs %d StatisticTime %s StatisticTimeMsec %d", 
                haDomain.getNativeGuid(), idleTicks, cumTicks, idleTicks,  ioTicks, iops, statisticTime, statisticTimeMsec));
        if (isVnx) {
            // According to email from Praveen Kumar dated 9/10/2014 2:18 AM CDT:
            // The clock tick interval is 100 msec. and the value in 
            // CIM_BlockStatisticsCapabilities.CLOCK_TICK_INTERVAL (which is returning 10 usec.) is wrong.
            Long clockTickIntervalMsec = VNX_CLOCK_TICK_INTERVAL_MSEC;
            cumTicks = statisticTimeMsec / clockTickIntervalMsec;
            
            // Alternate ways of computing the denominator
            // Long clockTickIntervalUsec = new Long(keyMap.get(Constants.CLOCK_TICK_INTERVAL).toString());
            //Long usecPerMsec = portMetricsProcessor.USEC_PER_MSEC;
            // Use this line to calculate based on the StatisticTime clock interval
            // cumTicks = statisticTimeMsec * (usecPerMsec/clockTickIntervalUsec);
            // Use this line to compute based on ratio of idleTicks/(idleTicks+ioTicks)
            // cumTicks = idleTicks + ioTicks;
        }
        if (isVmax && system.checkIfVmax3()) {
        	// VMAX3 systems only return percent busy directly.
        	portMetricsProcessor.processFEAdaptMetrics(percentBusy, iops, haDomain, statisticTime);
        } else {
        	portMetricsProcessor.processFEAdaptMetrics(
        			idleTicks, cumTicks, iops, haDomain, statisticTime);
    	}
    }
    
    /**
     * Query the database to get all the HA Domains
     * @param dbClient
     * @param systemURI
     * @return Map of adapterName to StorageHADomain objects
     */
    public Map<String, StorageHADomain> getHADomainOfSystem(final DbClient dbClient, final URI systemURI) {
        Map<String, StorageHADomain> haDomains = new HashMap<String, StorageHADomain>();
        URIQueryResultList haDomainQueryResult = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.
                Factory.getStorageDeviceStorageHADomainConstraint(systemURI), haDomainQueryResult);
        for (Iterator<URI> haIter = haDomainQueryResult.iterator(); haIter.hasNext(); ) {
            StorageHADomain haDomain = dbClient.queryObject(StorageHADomain.class, haIter.next());
            // The replace all fixes up SP_A to SP-A so that it will match the metric records.
            haDomains.put(haDomain.getAdapterName().replaceAll("_",  "-"), haDomain);
        }
        return haDomains;
    }

    public PortMetricsProcessor getPortMetricsProcessor() {
        return portMetricsProcessor;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }
}
