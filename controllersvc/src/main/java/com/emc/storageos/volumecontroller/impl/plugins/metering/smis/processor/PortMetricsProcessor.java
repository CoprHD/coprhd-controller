/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedExportMask;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.google.common.collect.Sets;

/**
 * Handle port metrics computations for all array types.
 * This code computes the port metrics, determines if the port is above any of its ceiling limits,
 * and maintains the average calculations and database entries for the port.
 * 
 * @author watson
 *         September-October, 2014.
 * 
 */
public class PortMetricsProcessor {
    static final private Logger _log = LoggerFactory.getLogger(PortMetricsProcessor.class);

    private static volatile DbClient _dbClient;
    private static volatile CoordinatorClient _coordinator;
    @Autowired
    private static CustomConfigHandler customConfigHandler;

    final private static int DEFAULT_PORT_UTILIZATION_CEILING = 100;
    final private static int DEFAULT_CPU_UTILIZATION_CEILING = 100;
    final private static int DEFAULT_INITIATOR_CEILING = Integer.MAX_VALUE;
    final private static int DEFAULT_VOLUME_CEILING = Integer.MAX_VALUE;

    final private static int DEFAULT_DAYS_OF_AVG = 1;
    final private static double DEFAULT_EMA_FACTOR = 0.6;

    final private static Long MSEC_PER_SEC = 1000L;
    final private static Long MSEC_PER_MIN = 60000L;
    final private long KBYTES_PER_GBIT = 1024L * 1024L / 8;
    /** Sample valid if received in the last 48 hours. This allows for nodes down/connectivity issues. */
    final static private long MAX_SAMPLE_AGE_MSEC = 48 * 60 * 60 * 1000;
    final static private int MINUTES_PER_DAY = 60 * 24;
    final static private long SECONDS_PER_YEAR = 60 * 60 * 24 * 365;

    public PortMetricsProcessor() {
    };

    /**
     * Process a cpu metric sample.
     * In this method, the cpu percent busy is passed directly as a double.
     *
     * @param percentBusy   -- double from 0 to 100.0 indicating percent busy
     * @param iops          -- a cumulative count of the I/O operations (read and write). This counter is ever increasing (but rolls over).
     * @param haDomain      -- the StorageHADomain corresponding to this cpu.
     * @param statisticTime -- The statistic time that the collection was made on the array.
     */
    public void processFEAdaptMetrics(Double percentBusy, Long iops, StorageHADomain haDomain, String statisticTime) {
        processFEAdaptMetrics(percentBusy, iops, haDomain, statisticTime, true);
    }

    /**
     * Process a cpu metric sample.
     * In this method, the cpu percent busy is passed directly as a double.
     *
     * @param percentBusy -- double from 0 to 100.0 indicating percent busy
     * @param iops -- a cumulative count of the I/O operations (read and write). This counter is ever increasing (but rolls over).
     * @param haDomain -- the StorageHADomain corresponding to this cpu.
     * @param statisticTime -- The statistic time that the collection was made on the array. Given as a string, see convertCimStatisticTime.
     * @param usingCIMTime -- Indicates if 'statisticsTime' is in UTC. If false, it will be assumed to be CIM StatisticTime and converted
     *            (see convertCimStatisticTime)
     */
    public void processFEAdaptMetrics(Double percentBusy, Long iops, StorageHADomain haDomain, String statisticTime,
            boolean usingCIMTime) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, haDomain.getStorageDeviceURI());
        StringMap dbMetrics = haDomain.getMetrics();
        Long sampleTime = (usingCIMTime) ? convertCIMStatisticTime(statisticTime) : Long.valueOf(statisticTime);
        _log.info(String.format("FEAdaptMetrics %s %s percentBusy %f  iops %d sampleTime %d",
                haDomain.getAdapterName(), haDomain.getNativeGuid(), percentBusy, iops, sampleTime));

        // Read the current value of the database variables
        Long iopsValue = MetricsKeys.getLong(MetricsKeys.iopsValue, dbMetrics);
        Long iopsDelta = iops - iopsValue;

        // Scale percentBusy to 1/10 percent for computing the averages
        percentBusy *= 10.0;
        if (percentBusy >= 0.0) {
        	computePercentBusyAverages(percentBusy.longValue(), 1000L, iopsDelta,
        			dbMetrics, haDomain.getNativeGuid(),
        			haDomain.getAdapterName() + " [cpu]", sampleTime, system);
        }

        // Save the new values and persist.
        MetricsKeys.putLong(MetricsKeys.iopsValue, iops, dbMetrics);

        MetricsKeys.putLong(MetricsKeys.lastSampleTime, sampleTime, dbMetrics);
        haDomain.setMetrics(dbMetrics);
        _dbClient.persistObject(haDomain);
    }

    /**
     * Process a cpu metric sample. The metrics are compared with the last sample to develop deltas,
     * and those are then used to compute the average metric which is eventually merged into the long term EMA average.
     * 
     * @param idleTicks -- a cumulative idleTicks value reported in the metrics. This counter is ever increasing (but rolls over).
     * @param cumTicks -- a cumulative ticks value (representing the time between samples in ticks). This counter is ever increasing.
     * @param iops -- a cumulative count of the I/O operations (read and write). This counter is ever increasing (but rolls over).
     * @param haDomain -- the StorageHADomain corresponding to this cpu.
     * @param statisticTime -- The statistic time that the collection was made on the array. Given as a string, see convertCimStatisticTime.
     */
    public void processFEAdaptMetrics(Long idleTicks, Long cumTicks, Long iops,
            StorageHADomain haDomain, String statisticTime) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, haDomain.getStorageDeviceURI());
        StringMap dbMetrics = haDomain.getMetrics();
        Long sampleTime = convertCIMStatisticTime(statisticTime);
        _log.info(String.format("FEAdaptMetrics %s %s idleTicks %d cumTicks %d iops %d sampleTime %d",
                haDomain.getAdapterName(), haDomain.getNativeGuid(), idleTicks, cumTicks, iops, sampleTime));

        // Read the current value of the database variables
        Long idleTicksValue = MetricsKeys.getLong(MetricsKeys.idleTicksValue, dbMetrics);
        Long cumTicksValue = MetricsKeys.getLong(MetricsKeys.cumTicksValue, dbMetrics);
        Long iopsValue = MetricsKeys.getLong(MetricsKeys.iopsValue, dbMetrics);

        Long idleTicksDelta = idleTicks - idleTicksValue;
        // Handle roll over, where the number will be negative
        if (idleTicksDelta < 0) {
            idleTicksDelta = -idleTicksDelta;
        }
        Long cumTicksDelta = cumTicks - cumTicksValue;
        // Handle roll over, where the number will be negative
        if (cumTicksDelta < 0) {
            cumTicksDelta = -cumTicksDelta;
        }
        Long iopsDelta = iops - iopsValue;
        Long busyTicks = cumTicksDelta - idleTicksDelta;

        // If we have had a previous sample, and this sample has accumulated time
        if (busyTicks >= 0 && cumTicksValue > 0L && cumTicksDelta > 0L) {
            computePercentBusyAverages(busyTicks, cumTicksDelta, iopsDelta,
                    dbMetrics, haDomain.getNativeGuid(),
                    haDomain.getAdapterName() + " [cpu]", sampleTime, system);
        }

        // Save the new values and persist.
        MetricsKeys.putLong(MetricsKeys.idleTicksValue, idleTicks, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.cumTicksValue, cumTicks, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.iopsValue, iops, dbMetrics);

        MetricsKeys.putLong(MetricsKeys.lastSampleTime, sampleTime, dbMetrics);
        haDomain.setMetrics(dbMetrics);
        _dbClient.persistObject(haDomain);
    }

    /**
     * Process a port metric sample. The values passed in are compared with the previous sample that was captured
     * and used to calculate deltas and are then converted to a port percent busy metric. Short and long term
     * averages for the port percent busy metric are updated.
     * 
     * @param kbytes -- a cumulative counter of the kilobytes transferred. This counter is ever increasing (but rolls over).
     * @param iops -- a cumulative counter of the iops (I/O operations). This counter is ever increasing (but rolls over).
     * @param port -- the StoragePort this port metric is for.
     * @param sampleTime -- The statistic time that the collection was made on the array. Given as a string, see convertCimStatisticTime.
     */
    public void processFEPortMetrics(Long kbytes, Long iops, StoragePort port, Long sampleTime) {
        StringMap dbMetrics = port.getMetrics();
        _log.info(String.format("FEPortMetrics %s %s kbytes %d iops %d sampleTime %d",
                port.getNativeGuid(), portName(port), kbytes, iops, sampleTime));

        // Read the current value of the database variables
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, port.getStorageDevice());
        Long iopsValue = MetricsKeys.getLong(MetricsKeys.iopsValue, dbMetrics);
        Long kbytesValue = MetricsKeys.getLong(MetricsKeys.kbytesValue, dbMetrics);
        Long lastSampleTimeValue = MetricsKeys.getLong(MetricsKeys.lastSampleTime, dbMetrics);

        // Compute the deltas, numerator, and denominator.
        Long kbytesDelta = kbytes - kbytesValue;
        // Handle roll over, where the number will be negative
        if (kbytesDelta < 0) {
            _log.info("Kbytes rolled over - delta is negative: " + kbytesDelta);
        }
        Long iopsDelta = iops - iopsValue;
        Long portSpeed = port.getPortSpeed();
        if (portSpeed == 0) {
            _log.error("Port speed is zero- assuming 8 GBit: " + port.getNativeGuid());
            portSpeed = 8L;
        }
        // portSpeed is in Gbit/sec. Compute kbytes/sec.
        Long maxKBytesPerSecond = portSpeed * KBYTES_PER_GBIT;
        // Convert the maximum port speed to the maximum data transferred in the sample,
        // by multiplying by the number of seconds we collected data.
        Long secondsDelta = (sampleTime - lastSampleTimeValue) / MSEC_PER_SEC;
        // Handle rollover, where the number will be negative
        if (secondsDelta < 0) {
            secondsDelta = -secondsDelta;
        }

        // We do this to avoid sampling from the beginning of time in one
        // giant sample, which makes the starting sample unreasonable.
        // If time has progressed, but the delta time is less than a year
        // and the kbytesDelta is not negative, add it to the average.
        if (kbytesDelta >= 0 && secondsDelta > 0 && secondsDelta < SECONDS_PER_YEAR) {
            computePercentBusyAverages(kbytesDelta / secondsDelta, maxKBytesPerSecond, iopsDelta,
                    dbMetrics, port.getNativeGuid(), portName(port), sampleTime, system);
            // Compute the current port metric.
            List<StoragePort> portList = new ArrayList<StoragePort>();
            portList.add(port);
            updateStaticPortUsage(portList);
            Double portMetric = computePortMetric(port);
            MetricsKeys.putDouble(MetricsKeys.portMetric, portMetric, dbMetrics);
            MetricsKeys.putLong(MetricsKeys.lastProcessingTime, System.currentTimeMillis(), dbMetrics);
        }

        // Save the new values and persist.
        MetricsKeys.putLong(MetricsKeys.kbytesValue, kbytes, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.iopsValue, iops, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.lastSampleTime, sampleTime, dbMetrics);
        // Update the Unmanaged Initiator and Volume Count.
        // We count meta-members for the volumes only if it's a VMAX2
        boolean countMetaMembers = (
                system.getSystemType().equals(DiscoveredDataObject.Type.vmax.name())
                && !system.checkIfVmax3());
        updateUnmanagedVolumeAndInitiatorCounts(port, countMetaMembers, dbMetrics);
        port.setMetrics(dbMetrics);
        _dbClient.persistObject(port);
    }

    /**
     * Process a port metric sample. The values passed in are compared with the previous sample that was captured
     * and used to calculate deltas and are then converted to a port percent busy metric. Short and long term
     * averages for the port percent busy metric are updated.
     * 
     * @param kbytes -- a cumulative counter of the kilobytes transferred. This counter is ever increasing (but rolls over).
     * @param iops -- a cumulative counter of the iops (I/O operations). This counter is ever increasing (but rolls over).
     * @param port -- the StoragePort this port metric is for.
     * @param sampleTime -- The statistic time that the collection was made on the array. Given as a string, see convertCimStatisticTime.
     */
    public void processIPPortMetrics(Long kbytes, Long iops, StoragePort port, Long sampleTime) {
        StringMap dbMetrics = port.getMetrics();
        _log.info(String.format("IP PortMetrics %s %s kbytes %d iops %d sampleTime %d",
                port.getNativeGuid(), portName(port), kbytes, iops, sampleTime));

        // Read the current value of the database variables
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, port.getStorageDevice());
        Long iopsValue = MetricsKeys.getLong(MetricsKeys.iopsValue, dbMetrics);
        Long kbytesValue = MetricsKeys.getLong(MetricsKeys.kbytesValue, dbMetrics);
        Long lastSampleTimeValue = MetricsKeys.getLong(MetricsKeys.lastSampleTime, dbMetrics);

        // Compute the deltas, numerator, and denominator.
        Long kbytesDelta = kbytes - kbytesValue;
        // Handle roll over, where the number will be negative
        if (kbytesDelta < 0) {
            _log.info("Kbytes rolled over - delta is negative: " + kbytesDelta);
        }
        Long iopsDelta = iops - iopsValue;
        Long portSpeed = port.getPortSpeed();
        if (portSpeed == 0) {
            _log.error("Port speed is zero- assuming 1 GBit: " + port.getNativeGuid());
            portSpeed = 1L;
        }
        // portSpeed is in Gbit/sec. Compute kbytes/sec.
        Long maxKBytesPerSecond = portSpeed * KBYTES_PER_GBIT;
        // Convert the maximum port speed to the maximum data transferred in the sample,
        // by multiplying by the number of seconds we collected data.
        Long secondsDelta = (sampleTime - lastSampleTimeValue) / MSEC_PER_SEC;
        // Handle rollover, where the number will be negative
        if (secondsDelta < 0) {
            secondsDelta = -secondsDelta;
        }

        // We do this to avoid sampling from the beginning of time in one
        // giant sample, which makes the starting sample unreasonable.
        // If time has progressed, but the delta time is less than a year
        // and the kbytesDelta is not negative, add it to the average.
        if (kbytesDelta >= 0 && secondsDelta > 0 && secondsDelta < SECONDS_PER_YEAR) {
            computePercentBusyAverages(kbytesDelta / secondsDelta, maxKBytesPerSecond, iopsDelta,
                    dbMetrics, port.getNativeGuid(), portName(port), sampleTime, system);
            // Compute the current port metric.
            List<StoragePort> portList = new ArrayList<StoragePort>();
            portList.add(port);
            updateStaticPortUsage(portList);
            Double portMetric = computePortMetric(port);
            MetricsKeys.putDouble(MetricsKeys.portMetric, portMetric, dbMetrics);
            MetricsKeys.putLong(MetricsKeys.lastProcessingTime, System.currentTimeMillis(), dbMetrics);
        }

        // Save the new values and persist.
        MetricsKeys.putLong(MetricsKeys.kbytesValue, kbytes, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.iopsValue, iops, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.lastSampleTime, sampleTime, dbMetrics);

        port.setMetrics(dbMetrics);
        _dbClient.persistObject(port);
    }

    /**
     * Compute the overall port metric given the port. The overall port metric is
     * a equally weighted average of the port%busy and cpu%busy (if both port and cpu metrics are supported)
     * normalized to a 0-100% scale. So 75% port busy and 25% cpu busy would be 100%/2 = 50% overall busy.
     * The port%busy and cpu%busy are computed from the combination of short term and long term averages for each (respectively).
     * This is (emaFactor * portAvgBusy + (1-emaFactor) * portEmaBusy) for example, where the first term is the short term
     * average and the second term is the longer term average.
     * 
     * @param port -- StoragePort the metric is to be computed for
     * @return Double indicating the dbMetric b/w 0.0 < value <= 100.0
     */
    Double computePortMetric(StoragePort port) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, port.getStorageDevice());
        DiscoveredDataObject.Type type = DiscoveredDataObject.Type.valueOf(system.getSystemType());
        StringMap portMap = port.getMetrics();
        double emaFactor = getEmaFactor(DiscoveredDataObject.Type.valueOf(system.getSystemType()));
        if (emaFactor > 1.0)
        {
            emaFactor = 1.0;  // in case of invalid user input
        }
        Double portAvgBusy = MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, portMap);
        Double portEmaBusy = MetricsKeys.getDouble(MetricsKeys.emaPercentBusy, portMap);
        Double portPercentBusy = (portAvgBusy * emaFactor) + ((1 - emaFactor) * portEmaBusy);
        MetricsKeys.putDouble(MetricsKeys.avgPortPercentBusy, portPercentBusy, port.getMetrics());

        // Calculate the overall port metric, which is a percent 0-100%
        Double cpuAvgBusy = null;
        Double cpuEmaBusy = null;
        Double portMetricDouble = portPercentBusy;

        // compute port cpu busy if applicable
        if (type == DiscoveredDataObject.Type.vmax || type == DiscoveredDataObject.Type.vnxblock) {
            StorageHADomain haDomain = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
            StringMap cpuMap = haDomain.getMetrics();

            cpuAvgBusy = MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, cpuMap);
            cpuEmaBusy = MetricsKeys.getDouble(MetricsKeys.emaPercentBusy, cpuMap);
            // Update port bandwidth and cpu usage average. These are used by the UI.
            Double cpuPercentBusy = (cpuAvgBusy * emaFactor) + ((1 - emaFactor) * cpuEmaBusy);
            MetricsKeys.putDouble(MetricsKeys.avgCpuPercentBusy, cpuPercentBusy, port.getMetrics());

            portMetricDouble += cpuPercentBusy;
            portMetricDouble /= 2.0;        // maintain on a scale of 0 - 100%
        }

        _log.info(String.format("%s %s: portMetric %f port %f %f cpu %s %s",
                port.getNativeGuid(), portName(port),
                portMetricDouble, portAvgBusy, portEmaBusy, cpuAvgBusy == null ? "n/a" : cpuAvgBusy.toString(), cpuEmaBusy == null ? "n/a"
                        : cpuEmaBusy.toString()));

        return portMetricDouble;
    }

    /**
     * Common routine used for both port and cpu metrics to update the short and long term
     * averages. Will compute percent busy of whatever is presented.
     * 
     * @param numeratorDelta -- The numerator of the percent calculated as the delta between two samplpes.
     * @param denomDelta -- The denominator of the percent calculated as the delta between two samples.
     * @param iopsDelta -- The iops delta between the two samples. Used for informational purposes now.
     * @param dbMetrics -- The db metrics field of the appropriate structure (StoragePort or StorageHADomain).
     * @param nativeGuid -- The native guid of the element (for logging).
     * @param name -- The name of the port or cpu (for logging).
     * @param sampleTime -- The sample time of this sample.
     */
    private void computePercentBusyAverages(Long numeratorDelta, Long denomDelta, Long iopsDelta,
            StringMap dbMetrics, String nativeGuid, String name, Long sampleTime, StorageSystem system) {
        // Read existing values.
        Long avgCountValue = MetricsKeys.getLong(MetricsKeys.avgCount, dbMetrics);
        Long avgStartTimeValue = MetricsKeys.getLong(MetricsKeys.avgStartTime, dbMetrics);
        Double avgPercentBusyValue = MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, dbMetrics);
        Double emaPercentBusy = MetricsKeys.getDouble(MetricsKeys.emaPercentBusy, dbMetrics);

        // Compute percentbBusy and avgPercentBusy.
        Double percentBusy = (numeratorDelta * 100.0 / denomDelta);
        // Do some sanity checking. Will be negative when one of the counters in a delta rolls.
        if (percentBusy < 0.0) {
            _log.error(String.format("Percent busy negative, num %d denom %d", numeratorDelta, denomDelta));
            // Do not process this sample
            return;
        }
        if (percentBusy > 100.0) {
            percentBusy = 100.0;
        }

        if (avgPercentBusyValue.isNaN() || avgPercentBusyValue.isInfinite()) {
            _log.error("avgPercentBusyValue invalid: " + avgPercentBusyValue.toString());
            avgPercentBusyValue = percentBusy;
        }
        Double avgPercentBusy = (1.0 / (avgCountValue + 1.0)) * percentBusy
                + ((double) avgCountValue / (avgCountValue + 1.0)) * avgPercentBusyValue;

        // Check to see if the average should be reset. It is reset when
        // a sample arrives whoose time difference from the avgStartTime
        // (i.e. the time the current average was started) exceeds
        // the averagePeriod (expressed in msec.)
        // In this case we reset the avgStartTime to the current time, and update
        // the emaPercentBusy with our current avgPercentBusy.
        Long currentTime = System.currentTimeMillis();
        Long averagePeriod = getMinutesToAverage(DiscoveredDataObject.Type.valueOf(system.getSystemType()))
                * MSEC_PER_MIN;
        avgCountValue++;
        if ((currentTime - avgStartTimeValue) > averagePeriod) {
            _log.debug("Resetting average for: " + nativeGuid + " " + name);
            avgCountValue = 0L;
            MetricsKeys.putLong(MetricsKeys.avgStartTime, currentTime, dbMetrics);
            double emaFactor = getEmaFactor(DiscoveredDataObject.Type.valueOf(system.getSystemType()));
            if (emaFactor > 1.0)
            {
                emaFactor = 1.0;  // in case of invalid user input
            }
            if (emaPercentBusy.isNaN() || emaPercentBusy.isInfinite() || emaPercentBusy < 0.0) {
                _log.error("emaPercentBusy invalid: " + emaPercentBusy.toString());
                emaPercentBusy = avgPercentBusy;
            }
            emaPercentBusy = avgPercentBusy * emaFactor + (1.0 - emaFactor) * emaPercentBusy;
            MetricsKeys.putDouble(MetricsKeys.emaPercentBusy, emaPercentBusy, dbMetrics);
        }

        // Save new values and persist
        MetricsKeys.putLong(MetricsKeys.avgCount, avgCountValue, dbMetrics);
        MetricsKeys.putDouble(MetricsKeys.avgPercentBusy, avgPercentBusy, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.lastSampleTime, currentTime, dbMetrics);

        // Log results
        Date sampleDate = new Date(sampleTime);
        _log.info(String.format(
                "%s (%s): numDelta %d denomDelta %d iops %d percentBusy %f avgPercentBusy %f emaPercentbusy %f avgCount %d sampleTime %s",
                name, nativeGuid, numeratorDelta, denomDelta, iopsDelta,
                percentBusy, avgPercentBusy, emaPercentBusy, avgCountValue, sampleDate.toString()));
    }

    /**
     * Converts the CIM property StatisticTime to msec since the epoch.
     * 
     * @param statisticTime - CIM propertiy in CIM_BlockStatisticalData
     * @return Long time in milliseconds in format similar to System.getMillis()
     */
    public Long convertCIMStatisticTime(String statisticTime) {
        if (statisticTime == null || statisticTime.equals("")) {
            return 0L;
        }
        String[] parts = statisticTime.split("[\\.\\+\\-]");
        Integer year = Integer.parseInt(parts[0].substring(0, 4), 10) - 1900;
        Integer month = Integer.parseInt(parts[0].substring(4, 6), 10) - 1;
        Integer day = Integer.parseInt(parts[0].substring(6, 8), 10);
        Integer hour = Integer.parseInt(parts[0].substring(8, 10), 10);
        Integer min = Integer.parseInt(parts[0].substring(10, 12), 10);
        Integer sec = Integer.parseInt(parts[0].substring(12, 14), 10);
        Integer msec = Integer.parseInt(parts[1].substring(0, 3), 10);
        @SuppressWarnings("deprecation")
        Date date = new Date(year, month, day, hour, min, sec);
        Long millis = date.getTime() + msec;
        date = new Date(millis);
        _log.debug("sample date: " + date.toString());
        return millis;
    }

    /**
     * Compute DataMover or Virtual Data Mover average port metrics. The answer is in percent.
     * This is averaged over all the usable port in a VirtualNAS .The Computed
     * value get stored in DB.
     * 
     * @param storageSystemURI -- URI for the storage system.
     * 
     */
    public void dataMoverAvgPortMetrics(URI storageSystemURI) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);

        StringSet storagePorts = null;
        Double portPercentBusy = 0.0;
        Double avgPortPercentBusy = 0.0;
        Double percentBusy = 0.0;
        Double avgPercentBusy = 0.0;

        int noOfInterface = 0;
        if (storageSystem != null) {
            URIQueryResultList vNASURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceVirtualNasConstraint(storageSystemURI),
                    vNASURIs);
            List<VirtualNAS> virtualNAS = _dbClient.queryObject(VirtualNAS.class, vNASURIs);

            for (VirtualNAS vNAS : virtualNAS) {

                if (vNAS != null && !vNAS.getInactive()) {
                    storagePorts = vNAS.getStoragePorts();
                    if (storagePorts != null && !storagePorts.isEmpty()) {
                        for (String sp : storagePorts) {

                            StoragePort storagePort = _dbClient.queryObject(StoragePort.class, URI.create(sp));
                            portPercentBusy = portPercentBusy
                                    + MetricsKeys.getDouble(MetricsKeys.avgPortPercentBusy, storagePort.getMetrics());

                            percentBusy = percentBusy
                                    + MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, storagePort.getMetrics());
                        }
                        noOfInterface = storagePorts.size();
                        if (noOfInterface != 0) {

                            avgPortPercentBusy = portPercentBusy / noOfInterface;
                            avgPercentBusy = percentBusy / noOfInterface;
                        }
                        StringMap dbMetrics = vNAS.getMetrics();
                        MetricsKeys.putDouble(MetricsKeys.avgPortPercentBusy, avgPortPercentBusy, dbMetrics);
                        MetricsKeys.putDouble(MetricsKeys.avgPercentBusy, avgPercentBusy, dbMetrics);
                        _dbClient.persistObject(vNAS);

                    }
                }

            }
        }

    }

    /* find the port for given portGuid */
    /**
     * 
     * @param portGuid
     * @param dbClient
     * @return
     */
    private StoragePort findExistingPort(String portGuid, DbClient dbClient) {
        URIQueryResultList results = new URIQueryResultList();
        StoragePort port = null;

        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            StoragePort tmpPort = dbClient.queryObject(StoragePort.class, iter.next());

            if (tmpPort != null && !tmpPort.getInactive()) {
                port = tmpPort;
                _log.info("found port {}", tmpPort.getNativeGuid() + ":" + tmpPort.getPortName());
                break;
            }
        }
        return port;
    }

    /**
     * Compute storage system's average port metrics. The answer is in percent.
     * This is averaged over all the usable ports.
     * 
     * @param storageSystemURI -- URI for the storage system.
     * @return -- A percent busy from 0 to 100%.
     */
    public Double computeStorageSystemAvgPortMetrics(URI storageSystemURI) {
        StorageSystem storageDevice = _dbClient.queryObject(StorageSystem.class, storageSystemURI);
        Double portMetricsSum = 0.0;
        double usablePortCount = 0;
        Double storageSystemPortsMetrics = null;
        if (storageDevice != null) {

            URIQueryResultList storagePortURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystemURI),
                    storagePortURIs);
            List<StoragePort> storagePorts = _dbClient.queryObject(StoragePort.class, storagePortURIs);

            if (!metricsValid(storagePorts)) {
                // The metrics are not valid for this array. Log it and return 50.0%.
                _log.info(String.format("Port metrics not valid for array %s (%s), using 50.0 percent for array metric",
                        storageDevice.getLabel(), storageSystemURI.toString()));
                return 50.0;
            }

            // compute sum of all usable port metrics
            for (StoragePort storagePort : storagePorts) {
                // if port is usable, compute its port metrics
                if (isPortUsable(storagePort, false)) {
                    portMetricsSum += MetricsKeys.getDouble(MetricsKeys.portMetric, storagePort.getMetrics());
                    usablePortCount++;
                }
            }

            storageSystemPortsMetrics = (Double.compare(usablePortCount, 0) == 0) ? 0.0 : portMetricsSum / usablePortCount;
            _log.info(String.format("Array %s metric %f", storageDevice.getLabel(), storageSystemPortsMetrics));

            // persisted into storage system object for later retrieval
            storageDevice.setAveragePortMetrics(storageSystemPortsMetrics);
            _dbClient.persistObject(storageDevice);

        }

        // if no usable port, return null. Otherwise compute average.
        return storageSystemPortsMetrics;
    }

    /**
     * Computes the usage of a set of candidate StoragePorts.
     * This is done by finding all the ExportMasks containing the ports, and then
     * totaling the number of Initiators across all masks that are using the port.
     * 
     * @param candidatePorts -- List of StoragePort
     * @param system StorageSystem
     * @param updatePortUsages -- If true, recomputes port initiator and volume count usages
     * @return Map of StoragePort to Integer usage metric that is count of Initiators using port
     */
    public Map<StoragePort, Long> computeStoragePortUsage(
            List<StoragePort> candidatePorts, StorageSystem system, boolean updatePortUsages) {
        Map<StoragePort, Long> usages = new HashMap<StoragePort, Long>();
        boolean metricsValid = metricsValid(candidatePorts);

        // Disqualify any ports over one of their ceilings
        List<StoragePort> portsUnderCeiling = eliminatePortsOverCeiling(candidatePorts, system, true);

        for (StoragePort sp : portsUnderCeiling) {
            // only compute port metric for front end port
            if (sp.getPortType().equals(StoragePort.PortType.frontend.name())) {
                Long usage = 0L;
                if (metricsValid) {
                    Double metric = MetricsKeys.getDouble(MetricsKeys.portMetric, sp.getMetrics());
                    usage = new Double(metric * 10.0).longValue();
                } else {
                    usage = MetricsKeys.getLong(MetricsKeys.volumeCount, sp.getMetrics());
                }
                usages.put(sp, usage);
                _log.info(String.format("Port usage: port %s metric %d %s", portName(sp), usage,
                        metricsValid ? "portMetric" : "volumeCount"));
            }
        }
        return usages;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        if (_coordinator == null) {
            _coordinator = coordinator;
        }
    }

    public CoordinatorClient getCoordinator() {
        return _coordinator;
    }

    public void setDbClient(DbClient dbClient) {
        if (_dbClient == null) {
            _dbClient = dbClient;
        }
    }

    public DbClient getDbClient() {
        return _dbClient;
    }

    /**
     * Eliminates ports from the candidate list that are over one of their ceilings.
     * 
     * @param ports -- List<StoragePort> the allocation candidates
     * @param system -- StorageSystem
     * @param updatePortUsages -- if true, recomputes the static use counts for initiators and volumes
     * @return updated list of candidate ports
     */
    private List<StoragePort> eliminatePortsOverCeiling(
            List<StoragePort> ports, StorageSystem system, boolean updatePortUsages) {
        List<StoragePort> portList = new ArrayList<StoragePort>();
        for (StoragePort sp : ports) {
            // since this method is invoked locally, port metrics are ready
            // updated. Hence, no need to update in its callee --set "false" to avoid
            // redundant update
            boolean overCeiling = isPortOverCeiling(sp, system, updatePortUsages);
            if (!overCeiling) {
                portList.add(sp);
            }
        }
        return portList;
    }

    /**
     * Returns true if a port is over one or more ceilings.
     * 
     * @param sp
     * @param system
     * @param updatePortUsages - update port usage computation
     * @return
     */
    public boolean isPortOverCeiling(StoragePort sp, StorageSystem system, boolean updatePortUsages) {
        boolean overCeiling = false;
        boolean metricsValid = metricsValid(Collections.singletonList(sp));

        // to optimize performance, avoid redundant update port usage. When this method invoked
        // locally, port usage is already computed. Hence, usage values generally do not need to update
        if (updatePortUsages) {
            updateStaticPortUsage(Collections.singletonList(sp));
        }

        StringMap metrics = sp.getMetrics();
        Long initiatorCount = MetricsKeys.getLong(MetricsKeys.initiatorCount, metrics);
        Integer ceiling = getInitiatorCeiling(StorageSystem.Type.valueOf(system.getSystemType()));
        if (initiatorCount >= ceiling) {
            _log.info(String.format("Port %s disqualified because initiator count %d over or equal-to ceiling %d",
                    portName(sp), initiatorCount, ceiling));
            overCeiling = true;
        }
        Long volumeCount = MetricsKeys.getLong(MetricsKeys.volumeCount, metrics);
        ceiling = getVolumeCeiling(StorageSystem.Type.valueOf(system.getSystemType()));
        if (volumeCount >= ceiling) {
            _log.info(String.format("Port %s disqualified because volume count %d over or equal-to ceiling %d",
                    portName(sp), volumeCount, ceiling));
            overCeiling = true;
        }
        // We only eliminate ports over the port percent busy or cpu percent busy if metrics are valid.
        // Otherwise we would be eliminating them based on stale (old) data.
        if (metricsValid) {
            Double portPercentBusy = MetricsKeys.getDouble(MetricsKeys.avgPortPercentBusy, metrics);
            if (portPercentBusy == null) {
                portPercentBusy = 0.0;
            }

            portPercentBusy *= 10.0;
            ceiling = 10 * getPortBusyCeiling(StorageSystem.Type.valueOf(system.getSystemType()));
            if (portPercentBusy.intValue() >= ceiling) {
                _log.info(String.format("Port %s disqualified because port busy %d over or equal-to ceiling %d",
                        portName(sp), portPercentBusy.intValue(), ceiling));
                overCeiling = true;
            }

            Double cpuPercentBusy = MetricsKeys.getDouble(MetricsKeys.avgCpuPercentBusy, metrics);
            if (cpuPercentBusy == null) {
                cpuPercentBusy = 0.0;
            }
            cpuPercentBusy *= 10.0;
            ceiling = 10 * getCpuBusyCeiling(StorageSystem.Type.valueOf(system.getSystemType()));
            if (cpuPercentBusy.intValue() >= ceiling) {
                _log.info(String.format("Port %s disqualified because cpu busy %d over or equal-to ceiling %d",
                        portName(sp), cpuPercentBusy.intValue(), ceiling));
                overCeiling = true;
            }
        } else {
            // Clear out the avgPortPercentBusy and avgCpuPercentBusy so UI will show N/A.
            metrics.put(MetricsKeys.avgPortPercentBusy.name(), "");
            metrics.put(MetricsKeys.avgCpuPercentBusy.name(), "");
        }

        // Save the over ceiling value for display on the UI.
        MetricsKeys.putBoolean(MetricsKeys.allocationDisqualified, overCeiling, sp.getMetrics());
        _dbClient.persistObject(sp);
        return overCeiling;
    }

    /**
     * Determines if all the ports have valid (dynamic) metrics. If so
     * returns true; other returns false, which would cause static usage
     * data to be used for metric.
     * 
     * @param candidatePorts - List<StoragePort>
     * @return boolean true if all ports have valid metrics
     */
    public boolean metricsValid(List<StoragePort> candidatePorts) {
        if (candidatePorts == null || candidatePorts.isEmpty()) {
            return false;
        }
        StoragePort aPort = candidatePorts.iterator().next();
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, aPort.getStorageDevice());

        // if port metrics allocation is disabled, than ports metrics are not used for
        // allocation. Just used volume count
        if (!isPortMetricsAllocationEnabled(DiscoveredDataObject.Type.valueOf(system.getSystemType()))) {
            return false;
        }

        Long currentTime = System.currentTimeMillis();
        for (StoragePort port : candidatePorts) {
            // Only consider front-end ports
            if (!port.getPortType().equals(StoragePort.PortType.frontend.name())) {
                continue;
            }
            Long lastProcessingTime = MetricsKeys.getLong(MetricsKeys.lastProcessingTime, port.getMetrics());
            if (lastProcessingTime == 0 /* no sample received */
                    || (currentTime - lastProcessingTime) > MAX_SAMPLE_AGE_MSEC) {
                return false;
            }
        }
        return true;
    }

    /**
     * convenient method to check whether given storage port is usable to compute port metric
     * 
     * @param storagePort
     * @return -- True if the port is operationally ok/unknown, Registered, FrontEnd, and has Network association.
     */
    public boolean isPortUsable(StoragePort storagePort) {
        return isPortUsable(storagePort, true);
    }

    private boolean isPortUsable(StoragePort storagePort, boolean doLogging) {
        boolean usable = false;
        if (storagePort != null && CompatibilityStatus.COMPATIBLE.name().equalsIgnoreCase(storagePort.getCompatibilityStatus())
                && !storagePort.getInactive()
                && DiscoveryStatus.VISIBLE.name().equals(storagePort.getDiscoveryStatus())) {
            StoragePort.TransportType storagePortTransportType = TransportType.valueOf(storagePort.getTransportType());
            if (storagePortTransportType == TransportType.FC || storagePortTransportType == TransportType.IP) {
                if (storagePort.getPortType().equals(StoragePort.PortType.frontend.name())) {
                    // must be registered
                    if (storagePort.getRegistrationStatus().equals(RegistrationStatus.REGISTERED.name())) {
                        // Must be associated with a Network
                        if (URIUtil.isValid(storagePort.getNetwork())) {
                            // must not be OperationalStatus.NOT_OK
                            if (!storagePort.getOperationalStatus().equals(
                                    StoragePort.OperationalStatus.NOT_OK.name())) {
                                usable = true;
                            } else {
                                if (doLogging) {
                                    _log.info("StoragePort OperationalStatus NOT_OK: " + storagePort.getNativeGuid());
                                }
                            }
                        } else {
                            if (doLogging) {
                                _log.info("StoragePort has no Network association: " + storagePort.getNativeGuid());
                            }
                        }
                    } else {
                        if (doLogging) {
                            _log.info("StoragePort not REGISTERED: " + storagePort.getNativeGuid());
                        }
                    }
                }
            }
        }
        return usable;
    }

    /**
     * Updates the static port usage parameters for a set of ports.
     * 
     * @param candidatePorts List<StoragePort>
     */
    static private void updateStaticPortUsage(List<StoragePort> candidatePorts) {
        _log.debug(String.format("updateStaticPortUsage: %s", candidatePorts.toString()));
        StorageSystem system = null;
        Map<StoragePort, Long> portCache = new HashMap<StoragePort, Long>();
        if (!candidatePorts.isEmpty()) {
            system = _dbClient.queryObject(StorageSystem.class, candidatePorts.get(0).getStorageDevice());
        }
        for (StoragePort sp : candidatePorts) {
            Long initiatorCount = 0L;
            Long volumeCount = 0L;
            // Find all the Export Masks containing the port.
            URIQueryResultList queryResult = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMasksByPort(sp.getId().toString()), queryResult);
            Iterator<URI> maskIt = queryResult.iterator();
            while (maskIt.hasNext()) {
                URI maskURI = maskIt.next();
                ExportMask mask = _dbClient.queryObject(ExportMask.class, maskURI);
                if (mask == null || mask.getInactive()) {
                    continue;
                }
                initiatorCount += computeInitiatorCountInMask(mask, sp.getId().toString());
                // VMAX2 volume count is handled separately below.
                // VMAX3 volume count is handled here. VMAX3 does not have a dependency on
                // meta-luns, so these are not counted, and the limit is 4K volumes per port
                // (there is no limit on the director as with VMAX2).
                // This path also handles non-VMAX arrays.
                if (false == system.getSystemType().equals(DiscoveredDataObject.Type.vmax.name())
                        || system.checkIfVmax3() == true) {
                    if (mask.getUserAddedVolumes() != null) {
                        volumeCount += mask.getUserAddedVolumes().size();
                    }
                    if (mask.getExistingVolumes() != null) {
                        // Collect volume counts for non-VMAX
                        volumeCount += mask.getExistingVolumes().size();
                    }
                }
            }

            // Add volumes and initiators from unmanaged Export Masks.
            // We only add unManagedVolumeCount in (below) if not VMAX2;
            // otherwise it is figured in getVmax2VolumeCount().
            initiatorCount += MetricsKeys.getLong(MetricsKeys.unmanagedInitiatorCount, sp.getMetrics());

            // Add volume counts for VMAX2.
            if (system.getSystemType().equals(DiscoveredDataObject.Type.vmax.name())
                    && !system.checkIfVmax3()) {
                volumeCount += getVmax2VolumeCount(sp, system, portCache);
            } else { // VMAX3 and other arrays use the value computed above + unmanaged volumes
                volumeCount += MetricsKeys.getLong(MetricsKeys.unmanagedVolumeCount, sp.getMetrics());
            }

            // Update the counts.
            MetricsKeys.putLong(MetricsKeys.initiatorCount, initiatorCount, sp.getMetrics());
            MetricsKeys.putLong(MetricsKeys.volumeCount, volumeCount, sp.getMetrics());
            _dbClient.persistObject(sp);

            _log.debug(String.format("Port %s %s updated initiatorCount %d volumeCount %d",
                    sp.getNativeGuid(), portName(sp), initiatorCount, volumeCount));
        }
    }

    /**
     * For the VMAX2, we calculate the number of volumes using a port differently.
     * We identify all the volumes using either of the ports on a cpu, and then sum the
     * meta-lun count for each of those volumes. The result is then the volume count
     * for both ports on the cpu.
     * 
     * @param sp
     * @param system
     * @param portCache
     * @return
     */
    static private long getVmax2VolumeCount(StoragePort sp, StorageSystem system, Map<StoragePort, Long> portCache) {
        if (portCache.isEmpty()) {
            URIQueryResultList storagePortURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(system.getId()),
                    storagePortURIs);
            List<StoragePort> storagePorts = _dbClient.queryObject(StoragePort.class, storagePortURIs);
            for (StoragePort port : storagePorts) {
                portCache.put(port, -1L);
            }
        }
        // Check to see if a value is already there for our port.
        Map<URI, StoragePort> portsToSum = new HashMap<URI, StoragePort>();
        for (StoragePort port : portCache.keySet()) {
            if (port.getStorageHADomain().equals(sp.getStorageHADomain())) {
                portsToSum.put(port.getId(), port);
            }
            // If the cache already has a sum > 0, we're good to go.
            if (sp.getId().equals(port.getId())) {
                Long count = portCache.get(port);
                if (count >= 0) {
                    return count;
                }
            }
        }
        // Nothing in the cache. Compute for all the ports on a single cpu.
        // First, determine all the export masks to be visited. Note that
        // a single mask may include both paired ports, we only want to
        // visit it once.
        Set<URI> masksToVisit = new HashSet<URI>();
        Long volumeCount = 0L;
        for (URI portURI : portsToSum.keySet()) {
            StoragePort portToSum = _dbClient.queryObject(StoragePort.class, portURI);

            // Find all the Export Masks containing the port.
            URIQueryResultList queryResult = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getExportMasksByPort(portURI.toString()), queryResult);
            Iterator<URI> maskIt = queryResult.iterator();
            while (maskIt.hasNext()) {
                masksToVisit.add(maskIt.next());
            }

            // Add in any ports in UnManagedExportMasks to the volume count for the port.
            volumeCount += MetricsKeys.getLong(MetricsKeys.unmanagedVolumeCount, portToSum.getMetrics());
        }

        // Iterate through all the masks, finding the volumes. At the meta member counts for the volumes
        // if available.
        for (URI maskURI : masksToVisit) {
            ExportMask mask = _dbClient.queryObject(ExportMask.class, maskURI);
            if (mask == null || mask.getInactive()) {
                continue;
            }
            if (mask.getExistingVolumes() != null) {
                long existingVolumeCount = mask.getExistingVolumes().size();
                volumeCount += existingVolumeCount;
                _log.debug(String.format("Mask %s existing volumes %d", mask.getMaskName(), existingVolumeCount));
            }

            // Loop through all the volumes in the mask.
            StringMap volumes = mask.getVolumes();
            if (volumes == null) {
                continue;
            }
            for (String volURI : volumes.keySet()) {
                if (NullColumnValueGetter.isNotNullValue(volURI)) {
                    BlockObject blkobj = BlockObject.fetch(_dbClient, URI.create(volURI));
                    if (blkobj != null && !blkobj.getInactive()) {
                        if (blkobj instanceof Volume) {
                            Volume vol = (Volume) blkobj;
                            if (vol.getMetaMemberCount() != null) {
                                volumeCount += vol.getMetaMemberCount();
                                _log.info(String.format("Volume %s meta count %d", vol.getLabel(), vol.getMetaMemberCount()));
                            } else {
                                volumeCount += 1;
                            }
                        } else {
                            volumeCount += 1;
                        }
                    }
                }
            }
        }
        for (StoragePort port : portsToSum.values()) {
            portCache.put(port, volumeCount);
            _log.debug(String.format("Port %s count %d", port.getPortName(), volumeCount));
        }
        return volumeCount;
    }

    /**
     * Updates the volumes and initiators that are mapped to the port by UnManagedExportMasks.
     * Note that if there is also a corresponding (managed) ExportMask the unmanaged information is not used.
     * (COP-16349).
     * This is called only from the processing of the port metrics.
     * 
     * @param sp -- StoragePort
     * @param countMetaMembers -- count meta members instead of volumes
     * @param dbMetrics -- the MetricsKeys values from the database record to be updated
     */
    private void updateUnmanagedVolumeAndInitiatorCounts(
            StoragePort sp, boolean countMetaMembers, StringMap dbMetrics) {
        Long volumeCount = 0L;
        Long initiatorCount = 0L;
        // Find all the Export Masks containing the port.
        URIQueryResultList queryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedMaskByPort(sp.getId().toString()), queryResult);
        Iterator<URI> maskIt = queryResult.iterator();
        while (maskIt.hasNext()) {
            UnManagedExportMask umask = _dbClient.queryObject(UnManagedExportMask.class, maskIt.next());
            if (umask != null && umask.getInactive() == false 
            		&& !checkForMatchingExportMask(umask.getMaskName(), 
            				umask.getNativeId(), umask.getStorageSystemUri())) {
            	
                StringSet unmanagedVolumeUris = umask.getUnmanagedVolumeUris();
                Long unmanagedVolumes = (unmanagedVolumeUris != null ? unmanagedVolumeUris.size() : 0L);
                if (countMetaMembers && unmanagedVolumeUris != null) {
                	unmanagedVolumes = 0L;
                	// For VMAX2, count the meta-members instead of the volumes.
                	for (String unmanagedVolumeUri : unmanagedVolumeUris) {
                		UnManagedVolume uVolume = _dbClient.queryObject(
                				UnManagedVolume.class, URI.create(unmanagedVolumeUri));
                		Long metaMemberCount = getUnManagedVolumeMetaMemberCount(uVolume);
                		unmanagedVolumes += (metaMemberCount != null ? metaMemberCount : 1L);
                	}
                }
                
                // Determine initiator count from zoning map in unmanaged export mask.
                // If the zoningInfoMap is empty, assume one initiator.
                Long unmanagedInitiators = 0L;
                ZoneInfoMap zoneInfoMap = umask.getZoningMap();
                if (!zoneInfoMap.isEmpty()) {
                    for (ZoneInfo info : zoneInfoMap.values()) {
                        if (info.getPortWwn().equals(sp.getPortNetworkId())) {
                            unmanagedInitiators += 1L;
                        }
                    }
                } else {
                    // Assume one initiator for the unmanaged mask
                    unmanagedInitiators += 1L;
                }

                _log.info(String.format("Port %s UnManagedExportMask %s " +
                        "unmanagedVolumes %d unmanagedInitiators %d",
                        sp.getPortName(), umask.getMaskName(),
                        unmanagedVolumes, unmanagedInitiators));
                volumeCount += unmanagedVolumes;
                initiatorCount += unmanagedInitiators;
            }
        }
        MetricsKeys.putLong(MetricsKeys.unmanagedInitiatorCount, initiatorCount, dbMetrics);
        MetricsKeys.putLong(MetricsKeys.unmanagedVolumeCount, volumeCount, dbMetrics);
    }
    
    /**
     * Checks to see if there is an ExportMask of the given maskName belonging 
     * to specified device with same nativeId.
     * @param maskName -- String mask name. It's an alternate index to ExportMask.
     * @param nativeId -- String native id of mask.
     * @param device -- URI of device
     * @return true if there is a matching ExportMask, false otherwise
     */
    private boolean checkForMatchingExportMask(String maskName, String nativeId, URI device) {
    	URIQueryResultList uriQueryList = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getExportMaskByNameConstraint(maskName), uriQueryList);
        while (uriQueryList.iterator().hasNext()) {
        	ExportMask exportMask = _dbClient.queryObject(ExportMask.class, uriQueryList.iterator().next());
        	if (exportMask != null && !exportMask.getInactive() 
        			&& exportMask.getNativeId().equals(nativeId) && exportMask.getStorageDevice().equals(device)) {
        		return true;
        	}
        }
    	return false;
    }

    /**
     * Returns the MetaMemberCount of an unmanaged volume if available;
     * otherwise returns null
     * 
     * @param umVolume -- an UnManagedVolume
     * @return Long meta member count or null if not available
     */
    private static Long getUnManagedVolumeMetaMemberCount(UnManagedVolume umVolume) {
        Long retval = null;
        if (umVolume != null && umVolume.getVolumeInformation() != null) {
            StringSet availableValueSet = umVolume.getVolumeInformation()
                    .get(SupportedVolumeInformation.META_MEMBER_COUNT.toString());
            if (availableValueSet != null) {
                for (String value : availableValueSet) {
                    retval = Long.parseLong(value);
                }

            }
        }
        return retval;
    }

    /**
     * Computes the usage of a port in an ExportMask
     * 
     * @param mask
     * @param portId
     * @return Integer initiator usage count
     */
    static private Long computeInitiatorCountInMask(ExportMask mask, String portId) {
        long count = 0L;
        if (mask.getZoningMap() != null) {
            // Determine from the zoning map if present
            for (AbstractChangeTrackingSet<String> portSet : mask.getZoningMap().values()) {
                if (portSet.contains(portId)) {
                    count++;
                }
            }
        } else if (mask.getInitiators() != null) {
            // Otherwise count all initiators
            count = mask.getInitiators().size();
        }
        return count;
    }

    /**
     * Return the port name (including the director component for VPlex).
     * 
     * @param port StoragePort
     * @return String port name
     */
    static private String portName(StoragePort port) {
        return BlockStorageScheduler.portName(port);
    }

    /**
     * Get number of utilized initiator ceiling value for given storage system's type. Value must be 1 or greater.
     * 
     * @param systemType - storage system type {@link DiscoveredDataObject.Type}
     * @return 1 - 100
     */
    static public Integer getInitiatorCeiling(StorageSystem.Type systemType) {

        int ceiling = DEFAULT_INITIATOR_CEILING;  // default to max value if not specified

        try {
            ceiling = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_INITIATOR_CEILLING,
                            getStorageSystemTypeName(systemType), null));
            // if specified value is 0 or less, default is max value
            if (ceiling <= 0) {
                ceiling = DEFAULT_VOLUME_CEILING;
            }
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return ceiling;

    }

    /**
     * Get number of utilized volume ceiling value for given storage system's type. Value must be 1 or greater.
     * 
     * @param systemType - storage system type {@link DiscoveredDataObject.Type}
     * @return 1 - 100
     */
    static public Integer getVolumeCeiling(StorageSystem.Type systemType) {
        int ceiling = DEFAULT_VOLUME_CEILING;  // default to max value if not specified

        try {
            ceiling = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_VOLUME_CEILLING,
                            getStorageSystemTypeName(systemType), null));
            // if specified value is 0 or less, default is max value
            if (ceiling <= 0) {
                ceiling = DEFAULT_VOLUME_CEILING;
            }
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return ceiling;
    }

    /**
     * Get port utilization percentage ceiling value for given storage system's type. Valid range is b/w 1 - 100
     * 
     * @param systemType - storage system type {@link DiscoveredDataObject.Type}
     * @return 1 - 100
     */
    static public Integer getPortBusyCeiling(StorageSystem.Type systemType) {
        int ceiling = DEFAULT_PORT_UTILIZATION_CEILING;  // default to 100% if not specified

        try {
            ceiling = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_PORT_UTILIZATION_CEILLING,
                            getStorageSystemTypeName(systemType), null));
            if (ceiling <= 0 || ceiling > 100) {
                ceiling = DEFAULT_PORT_UTILIZATION_CEILING;
            }
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return ceiling;
    }

    /**
     * Get CPU utilization percentage ceiling value for given storage system's type. Valid range is b/w 1 - 100
     * 
     * @param systemType - storage system type {@link DiscoveredDataObject.Type}
     * @return 1 - 100
     */
    static public Integer getCpuBusyCeiling(StorageSystem.Type systemType) {
        int ceiling = DEFAULT_CPU_UTILIZATION_CEILING;  // default to 100% if not specified

        try {
            ceiling = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_CPU_UTILIZATION_CEILLING,
                            getStorageSystemTypeName(systemType), null));
            if (ceiling <= 0 || ceiling > 100) {
                ceiling = DEFAULT_CPU_UTILIZATION_CEILING;
            }
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return ceiling;
    }

    /**
     * Get number of minutes for utilization running average of given storage system's type. Valid range is 1 through 30
     * to specify time in days (which is converted to minutes). If the number is a negative number (which is undocumented),
     * the time is interpreted in minutes.
     * 
     * @return time to average in minutes
     */
    static public Integer getMinutesToAverage(StorageSystem.Type systemType) {
        int minutesToAverage = DEFAULT_DAYS_OF_AVG;  // default to 7 days
        try {
            minutesToAverage = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_DAYS_TO_AVERAGE_UTILIZATION,
                            getStorageSystemTypeName(systemType), null));
            if (minutesToAverage > 10000) {
                minutesToAverage -= 10000;  // undocumented; use as minutes
                return minutesToAverage;
            }
            if (minutesToAverage <= 0) {
                minutesToAverage = DEFAULT_DAYS_OF_AVG;
            }
            if (minutesToAverage > 30) {
                minutesToAverage = DEFAULT_DAYS_OF_AVG;
            }
            if (minutesToAverage > 0) {
                minutesToAverage = minutesToAverage * MINUTES_PER_DAY;
            }  // convert days to minutes
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return minutesToAverage;
    }

    /**
     * Get EMA factor to compute CPU and port utilization's historical running average. Valid range 0 < value <= 1
     * 
     * @return 1 - 14
     */
    static public Double getEmaFactor(StorageSystem.Type systemType) {
        double emaFactor = DEFAULT_EMA_FACTOR;  // default to factor of 0.6

        try {
            emaFactor = Double.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_EMA_FACTOR,
                            getStorageSystemTypeName(systemType), null));
            if (emaFactor <= 0.0 || emaFactor > 1.0) {
                emaFactor = DEFAULT_EMA_FACTOR;
            }
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return emaFactor;
    }

    /**
     * Check whether port metrics allocation is enabled.
     * 
     * @return
     * @see storageos-properties-config.def
     */
    static public Boolean isPortMetricsAllocationEnabled(StorageSystem.Type systemType) {
        Boolean portMetricsAllocationEnabled = true;

        try {
            portMetricsAllocationEnabled = Boolean.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_METRICS_ENABLED,
                            getStorageSystemTypeName(systemType), null));
        } catch (Exception e) {
            _log.debug(e.getMessage());
        }
        return portMetricsAllocationEnabled;
    }

    /**
     * ViPR allocate port based on collected usage metrics, used initiator and volume. The ports which are being heavily used and
     * exceeded configured ceiling, will be eliminated from candidate pools.
     * The ceiling values are configurable. For clarity, they are name with their system type as prefix. E.g., VMAXZ type, the ceiling
     * parameters are named as follow:
     * vmax_cieling_volume, vmax_ceiling_initiator, etc.
     * To avoid specified each and everyone of parameter names, this method determined parameters' prefix based on storage system type.
     * 
     * @see storageos-properties-config.def
     * @param systemType
     * @return
     */
    static private String getStorageSystemTypeName(StorageSystem.Type systemType) {
        String name = systemType.name();
        // For all VNX type, prefix is "vnxblock"
        if (StorageSystem.Type.vnxblock.equals(systemType) || StorageSystem.Type.vnxfile.equals(systemType)
                || StorageSystem.Type.vnxe.equals(systemType)) {
            name = StorageSystem.Type.vnxblock.name();
        } else if (!StorageSystem.Type.vmax.equals(systemType) && !StorageSystem.Type.hds.equals(systemType)) {
            // for other system type besides vnx, vmax, hds are categorirzed as "other_arrays"
            name = "other_arrays";
        }
        return name;
    }

    /**
     * Compute and set each storage pool's average port usage metric. The average port metrics is
     * actually pool's storage system's port metric.
     * 
     * @param storagePools
     */
    public void computeStoragePoolsAvgPortMetrics(List<StoragePool> storagePools) {
        Map<URI, Double> storageSystemAvgPortMetricsMap = new HashMap<URI, Double>();

        // compute storage system average port metric
        for (StoragePool storagePool : storagePools) {
            URI storageSystemURI = storagePool.getStorageDevice();

            storagePool.setAvgStorageDevicePortMetrics(null); // reset metric before compute

            // avoid recompute average if it already computed
            if (!storageSystemAvgPortMetricsMap.containsKey(storageSystemURI)) {
                Double avgPortMetrics = computeStorageSystemAvgPortMetrics(storageSystemURI);

                // put in a map for later reference if metric was computable
                storageSystemAvgPortMetricsMap.put(storageSystemURI, avgPortMetrics);
            }

            storagePool.setAvgStorageDevicePortMetrics(storageSystemAvgPortMetricsMap.get(storageSystemURI));
        }
    }

    /**
     * Compute storage ports for all active storage systems in ViPR
     */
    public void computeStoragePortUsage() {
        _log.debug("Begin - recompute all storage ports' usage metrics for all storage systems");

        List<URI> storageSysteIds = _dbClient.queryByType(StorageSystem.class, true);
        if (storageSysteIds != null) {
            for (URI storageSystemId : storageSysteIds) {
                StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
                List<StoragePort> systemPorts = ControllerUtils.getSystemPortsOfSystem(_dbClient, storageSystemId);
                computeStoragePortUsage(systemPorts, storageSystem, true);
                computeStorageSystemAvgPortMetrics(storageSystemId);
            }
        }
        _log.debug("End - recompute all storage ports' usage metrics for all storage systems");
    }

    /**
     * Run storage system vpool matcher if ports allocation qualification changed
     * 
     * @param storageSystemId
     * @param storagePorts
     * @param portMetricsProcessor
     */
    public void triggerVpoolMatcherIfPortAllocationQualificationChanged(URI storageSystemId, List<StoragePort> storagePorts) {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);

        // get list of ports that are disqualified from allocation process before they are compute for
        // usage
        Set<StoragePort> disqualifiedPortBeforeCompute = filterAllocationDisqualifiedPorts(storagePorts);

        // compute ports usage which also determine whether their qualification changed
        computeStoragePortUsage(storagePorts, storageSystem, true);

        // get list of ports that are disqualified from allocation process after they are compute for
        // usage
        Set<StoragePort> disqualifiedPortAfterCompute = filterAllocationDisqualifiedPorts(storagePorts);

        // if before and after lists are not the same, implied one of the port allocation disqualification status
        // has changed. Then, invoke pool matcher
        if (!disqualifiedPortAfterCompute.equals(disqualifiedPortBeforeCompute)) {
            ImplicitPoolMatcher.matchStorageSystemPoolsToVPools(storageSystem.getId(), _dbClient, _coordinator);
        }
    }

    /**
     * Get only disqualified ports from given list of ports.
     * 
     * @param candidatePorts
     * @return
     */
    private Set<StoragePort> filterAllocationDisqualifiedPorts(List<StoragePort> candidatePorts) {
        Set<StoragePort> allocationDisqualifiedPorts = Sets.newHashSet();
        for (StoragePort storagePort : candidatePorts) {
            boolean disqualifiedPort = MetricsKeys.getBoolean(MetricsKeys.allocationDisqualified, storagePort.getMetrics());
            if (disqualifiedPort) {
                allocationDisqualifiedPorts.add(storagePort);
            }
        }
        return allocationDisqualifiedPorts;
    }

    public CustomConfigHandler getCustomConfigHandler() {
        return customConfigHandler;
    }

    public void setCustomConfigHandler(
            CustomConfigHandler customConfigHandler) {
        PortMetricsProcessor.customConfigHandler = customConfigHandler;
    }
}
