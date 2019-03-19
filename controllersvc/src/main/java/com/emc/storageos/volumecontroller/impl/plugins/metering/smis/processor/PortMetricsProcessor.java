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
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.TransportType;
import com.emc.storageos.db.client.model.StoragePortGroup;
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
import com.emc.storageos.db.client.util.StringSetUtil;
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
    private static CustomConfigHandler customConfigHandler;

    final private static int DEFAULT_PORT_UTILIZATION_CEILING = 100;
    final private static int DEFAULT_CPU_UTILIZATION_CEILING = 100;
    final private static int DEFAULT_PORT_UTILIZATION_FLOOR = 0;
    final private static int DEFAULT_CPU_UTILIZATION_FLOOR = 0;
    final private static int DEFAULT_INITIATOR_CEILING = Integer.MAX_VALUE;
    final private static int DEFAULT_VOLUME_CEILING = Integer.MAX_VALUE;
    final private static double DEFAULT_VOLUME_COEFFICIENT = 1.0;

    final private static int DEFAULT_DAYS_OF_AVG = 1;
    final private static double DEFAULT_EMA_FACTOR = 0.6;

    final private static Long MSEC_PER_SEC = 1000L;
    final private static Long MSEC_PER_MIN = 60000L;
    final private long KBYTES_PER_GBIT = 1024L * 1024L / 8;
    /** Sample valid if received in the last 48 hours. This allows for nodes down/connectivity issues. */
    final static private long MAX_SAMPLE_AGE_MSEC = 48 * 60 * 60 * 1000;
    final static private int MINUTES_PER_DAY = 60 * 24;
    final static private long SECONDS_PER_YEAR = 60 * 60 * 24 * 365;
    
    /** Maximum volumes on a port normally */
    final private static Long MAX_VOLUMES_PER_PORT = 2048L;

    public PortMetricsProcessor() {
    };

    /**
     * Process a cpu metric sample.
     * In this method, the cpu percent busy is passed directly as a double.
     *
     * @param percentBusy -- double from 0 to 100.0 indicating percent busy
     * @param iops -- a cumulative count of the I/O operations (read and write). This counter is ever increasing (but rolls over).
     * @param haDomain -- the StorageHADomain corresponding to this cpu.
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
        _dbClient.updateObject(haDomain);
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
        _dbClient.updateObject(haDomain);
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
        if (portSpeed == null || portSpeed == 0) {
            _log.info("Port speed is zero or null- assuming 8 GBit: " + port.getNativeGuid());
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
        _dbClient.updateObject(port);
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
            _log.info("Port speed is zero- assuming 1 GBit: " + port.getNativeGuid());
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
        _dbClient.updateObject(port);
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
        // The port and cpu Busy Floor values were added as a way to remove background idle load from
        // the calculations. The idea is even with no traffic, the port (and especially the cpu) may show
        // a small load which should be ignored in favor of balancing the number of volumes.
        // If the percent busy is below the floor for either one, then the value is not added into the metric.
        // A volume term is added to the metric in computeStoragePortUsage and will dominate the usage value
        // on idle systems. The floors are expressed in percent from 0 - 100 %.
        double portBusyFloor = getPortBusyFloor(DiscoveredDataObject.Type.valueOf(system.getSystemType()));
        double cpuBusyFloor = getCpuBusyFloor(DiscoveredDataObject.Type.valueOf(system.getSystemType()));
        if (emaFactor > 1.0)
        {
            emaFactor = 1.0;  // in case of invalid user input
        }
        Double portAvgBusy = MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, portMap);
        Double portEmaBusy = MetricsKeys.getDouble(MetricsKeys.emaPercentBusy, portMap);
        Double portPercentBusy = (portAvgBusy * emaFactor) + ((1 - emaFactor) * portEmaBusy);
        MetricsKeys.putDouble(MetricsKeys.avgPortPercentBusy, portPercentBusy, port.getMetrics());

        // The port metric contains portPercentBusy if it's over the floor.
        // If the usage is less than the floor, don't count it in order to make volume component predominate.
        Double portMetricDouble = ((portPercentBusy >= portBusyFloor) ? portPercentBusy : 0.0);

        // Calculate the overall port metric, which is a percent 0-100%
        Double cpuAvgBusy = null;
        Double cpuEmaBusy = null;

        // compute port cpu busy if applicable
        if (type == DiscoveredDataObject.Type.vmax ||
                type == DiscoveredDataObject.Type.vnxblock ||
                type == DiscoveredDataObject.Type.vplex) {
            StorageHADomain haDomain = _dbClient.queryObject(StorageHADomain.class, port.getStorageHADomain());
            StringMap cpuMap = haDomain.getMetrics();

            cpuAvgBusy = MetricsKeys.getDouble(MetricsKeys.avgPercentBusy, cpuMap);
            cpuEmaBusy = MetricsKeys.getDouble(MetricsKeys.emaPercentBusy, cpuMap);
            // Update port bandwidth and cpu usage average. These are used by the UI.
            Double cpuPercentBusy = (cpuAvgBusy * emaFactor) + ((1 - emaFactor) * cpuEmaBusy);
            MetricsKeys.putDouble(MetricsKeys.avgCpuPercentBusy, cpuPercentBusy, port.getMetrics());
            // If cpuPercentBusy is greater than the cpuBusyFloor, average it in to port metric.
            if (cpuPercentBusy >= cpuBusyFloor) {
                portMetricDouble += cpuPercentBusy;
                portMetricDouble /= 2;
            }
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
            Iterator<VirtualNAS> virtualNASIterator = _dbClient.queryIterativeObjects(VirtualNAS.class, vNASURIs);

            while (virtualNASIterator.hasNext()) {

                VirtualNAS vNAS = virtualNASIterator.next();

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
                        _dbClient.updateObject(vNAS);

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
     * Compute storage system's average port metrics. The answer is in percent. This is averaged over all the usable ports.
     * For XtremIO, it is StorageHADomain's CPU usage metrics. Port metrics are not available to collect.
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
            if (!DiscoveredDataObject.Type.xtremio.name().equals(storageDevice.getSystemType())) {

                URIQueryResultList storagePortURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystemURI),
                        storagePortURIs);
                // query iteratively to avoid dbsvc warning of too many objects being queried at once
                // this will prevent the warning message in the log but will still result in all storage ports
                // for a storage system to be in memory at once. There is usually less than 100 but at one
                // customer site there are between 100 and 150 for a few storage systems
                Iterator<StoragePort> storagePortItr = _dbClient.queryIterativeObjects(StoragePort.class, storagePortURIs);
                List<StoragePort> storagePorts = new ArrayList<StoragePort>();
                while (storagePortItr.hasNext()) {
                    storagePorts.add(storagePortItr.next());
                }

                if (!metricsValid(storageDevice, storagePorts)) {
                    // The metrics are not valid for this array. Log it and return 50.0%.
                    _log.info(String.format("Port metrics not valid for array %s (%s), using 50.0 percent for array metric",
                            storageDevice.getLabel(), storageSystemURI.toString()));
                    // clear the previous value
                    storageDevice.setAveragePortMetrics(-1.0);
                    _dbClient.updateObject(storageDevice);
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
                _dbClient.updateObject(storageDevice);

            } else {

                // For XtremIO, it is StorageHADomain's CPU usage metrics
                URIQueryResultList storageHADomainURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStorageHADomainConstraint(storageSystemURI),
                        storageHADomainURIs);
                // query iteratively to avoid dbsvc warning of too many objects being queried at once
                // this will prevent the warning message in the log but will still result in all StorageHADomain objects
                // for a storage system to be in memory at once.
                Iterator<StorageHADomain> storageHADomainItr = _dbClient.queryIterativeObjects(StorageHADomain.class, storageHADomainURIs);
                List<StorageHADomain> storageHADomains = new ArrayList<StorageHADomain>();
                while (storageHADomainItr.hasNext()) {
                    storageHADomains.add(storageHADomainItr.next());
                }

                if (!isMetricsValid(storageDevice, storageHADomains)) {
                    // The metrics are not valid for this array. Log it and return 50.0%.
                    _log.info(String.format("CPU usage metrics not valid for array %s (%s), using 50.0 percent for array metric",
                            storageDevice.getLabel(), storageSystemURI.toString()));
                    // clear the previous value
                    storageDevice.setAveragePortMetrics(-1.0);
                    _dbClient.updateObject(storageDevice);
                    return 50.0;
                }

                // compute sum of all CPU usages
                for (StorageHADomain storageHADomain : storageHADomains) {
                    if (!storageHADomain.getInactive()) {
                        portMetricsSum += MetricsKeys.getDouble(MetricsKeys.avgCpuPercentBusy, storageHADomain.getMetrics());
                        usablePortCount++;
                    }
                }

                storageSystemPortsMetrics = (Double.compare(usablePortCount, 0) == 0) ? 0.0 : portMetricsSum / usablePortCount;
                _log.info(String.format("Array %s CPU usage %f", storageDevice.getLabel(), storageSystemPortsMetrics));

                // persisted into storage system object for later retrieval
                storageDevice.setAveragePortMetrics(storageSystemPortsMetrics);
                _dbClient.updateObject(storageDevice);

            }
        }

        // if no usable port, return null. Otherwise compute average.
        return storageSystemPortsMetrics;
    }

    /**
     * Computes the usage of a set of candidate StoragePorts.
     * The usage value is what is actually used by the StoragePortsAllocator to choose between two otherwise 
     * equivalent (from a redundancy standpoint) ports. The algorithm has gotten a bit more complicated over time.
     * 1. Ports that are above a ceiling value can be eliminated from consideration (in eliminatePortsOverCeiling).
     * There can be ceilings on port percent busy, cpu percent busy, initiator count, or volume count.
     * Initiator and volume counts are updated before checking as a side effect of calling eliminatePortsOverCeiling.
     * 2. Ports not disqualified because of a ceiling compute a usage value. There are two cases: 
     * a) there are valid metrics for all the ports being ranked (metricsValid = true), or b) one or more ports
     * do not have valid metrics (i.e. their samples are not sufficient to compute metric values).
     * 3. For valid metrics, there are two components added together, the metric and the volume component.
     * The metric component is the EMA of the percent busy for port and cpu summed together and divided by 2
     * on a 0 to 100% scale.
     * The volume component is as follows: the number of volumes * 100.0 / 2048 to give the percentage from 0-100%
     * indicating the number of volumes on a 0-2048 scale. When added to the usage, the volume component is
     * multiplied by a volumeCoefficient so that the amount of effect the volumeComponent has is tunable in the field.
     * 4. The usage consisting of both components is converted to a long value where a 1% metric change = 1000 and 2048
     * and a 1% volume contribution is also 1000.
     *  
     * @param candidatePorts -- List of StoragePort
     * @param system StorageSystem
     * @param updatePortUsages -- If true, recomputes port initiator and volume count usages
     * @return Map of StoragePort to Integer usage metric that is count of Initiators using port
     */
    public Map<StoragePort, Long> computeStoragePortUsage(
            List<StoragePort> candidatePorts, StorageSystem system, boolean updatePortUsages) {
        Map<StoragePort, Long> usages = new HashMap<StoragePort, Long>();
        boolean metricsValid = metricsValid(system, candidatePorts);
        Double volumeCoefficient = getVolumeCoefficient(StorageSystem.Type.valueOf(system.getSystemType()));

        // Disqualify any ports over one of their ceilings. This will recalculate the volume counts and
        // initiator counts if updatePortUsages is true.
        List<StoragePort> portsUnderCeiling = eliminatePortsOverCeiling(candidatePorts, system, updatePortUsages);

        for (StoragePort sp : portsUnderCeiling) {
            // only compute port metric for front end port
            if (sp.getPortType().equals(StoragePort.PortType.frontend.name())) {
                Long usage = 0L;
                Long volumeCount = MetricsKeys.getLong(MetricsKeys.volumeCount, sp.getMetrics());
                if (metricsValid) {
                    // If metrics valid, the metric is the sum of:
                    // 1) the port metric (which includes port and cpu percent busy terms if applicable)
                    // 2) the volumeCoefficient * volumeCount * 100.0 / 2048 (volumes expressed as percent of 2048)
                    // At standard settings, about 21 volumes is equivalent to a 1% difference in port busy.
                    Double metric = MetricsKeys.getDouble(MetricsKeys.portMetric, sp.getMetrics());
                    metric += (volumeCoefficient * volumeCount * 100.0) / MAX_VOLUMES_PER_PORT;
                    usage = new Double(metric * 1000.0).longValue();
                } else {
                    usage = volumeCount * 1000;
                }
                usages.put(sp, usage);
                _log.info(String.format("Port usage: port %s metric %d volumes %d %s", portName(sp), usage, volumeCount,
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
            // COP-35850 In case of provisioning volume to new host we should set checkInitiatorCountOverCeiling flag as true
            boolean overCeiling = isPortOverCeiling(sp, system, updatePortUsages, true);
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
     * @param checkInitiatorCountOverCeiling This flag is used to whether initiator count ceiling check is required or not.
     *            In case of provisioning volume to new host we should set this flag as true, while in case of Implicit pool matcher
     *            we shouldn't check initiator count..
     * @return
     */
    public boolean isPortOverCeiling(StoragePort sp, StorageSystem system, boolean updatePortUsages,
            boolean checkInitiatorCountOverCeiling) {
        boolean overCeiling = false;
        Integer ceiling;
        boolean metricsValid = metricsValid(system, Collections.singletonList(sp));

        // to optimize performance, avoid redundant update port usage. When this method invoked
        // locally, port usage is already computed. Hence, usage values generally do not need to update
        if (updatePortUsages) {
            updateStaticPortUsage(Collections.singletonList(sp));
        }

        StringMap metrics = sp.getMetrics();

        if (checkInitiatorCountOverCeiling) {
            Long initiatorCount = MetricsKeys.getLong(MetricsKeys.initiatorCount, metrics);
            ceiling = getInitiatorCeiling(StorageSystem.Type.valueOf(system.getSystemType()));
            if (initiatorCount >= ceiling) {
            _log.info(String.format("Port %s disqualified because initiator count %d over or equal-to ceiling %d",
                    portName(sp), initiatorCount, ceiling));
            overCeiling = true;
            }
        } else {
            _log.info("Skipping the initiator ceiling count check for port: {} as request is coming from Implicit Matcher", sp.getLabel());
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
        _dbClient.updateObject(sp);
        return overCeiling;
    }

    /**
     * Determines if all the ports have valid (dynamic) metrics. If so
     * returns true; otherwise returns false, which would cause static usage
     * data to be used for metric.
     * 
     * @param system storage system where candidate ports are
     * @param candidatePorts - List<StoragePort>
     * @return boolean true if all ports have valid metrics
     */
    public boolean metricsValid(StorageSystem system, List<StoragePort> candidatePorts) {
        if (candidatePorts == null || candidatePorts.isEmpty()) {
            return false;
        }

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
     * Determines if all the storage HADomains have valid (dynamic) metrics. If so
     * returns true; otherwise returns false, which would cause static usage
     * data to be used for metric.
     * 
     * @param system storage system where candidate adapters are
     * @param candidateAdapters - List<StorageHADomain>
     * @return boolean true if all storage HADomains have valid metrics
     */
    public boolean isMetricsValid(StorageSystem system, List<StorageHADomain> candidateAdapters) {
        if (candidateAdapters == null || candidateAdapters.isEmpty()) {
            return false;
        }

        // if port metrics allocation is disabled, than ports metrics are not used for allocation.
        if (!isPortMetricsAllocationEnabled(DiscoveredDataObject.Type.valueOf(system.getSystemType()))) {
        	_log.info("PortMetricsEnabled : false");
            return false;
        }

        Long currentTime = System.currentTimeMillis();
        for (StorageHADomain adapter : candidateAdapters) {
            Long lastProcessingTime = MetricsKeys.getLong(MetricsKeys.lastProcessingTime, adapter.getMetrics());
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

    /**
     * Overloaded method for isPortUsable for No Network use case
     * 
     * @param storagePort
     * @param vArrays
     * @return TRUE or FALSE
     */
    public boolean isPortUsable(StoragePort storagePort, Set<String> vArrays) {
        return isPortUsable(storagePort, vArrays, true);
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

    private boolean isPortUsable(StoragePort storagePort, Set<String> vArrays, boolean doLogging) {
        boolean usable = false;

        if (storagePort != null
                && CompatibilityStatus.COMPATIBLE.name().equalsIgnoreCase(storagePort.getCompatibilityStatus())
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
                            if (!storagePort.getOperationalStatus().equals(StoragePort.OperationalStatus.NOT_OK.name())) {
                                usable = true;
                            } else {
                                if (doLogging) {
                                    _log.info("StoragePort OperationalStatus NOT_OK: " + storagePort.getNativeGuid());
                                }
                            }
                        } else {
                            if (doLogging) {
                                _log.info("StoragePort has no Network association: "
                                        + storagePort.getNativeGuid());
                            }
                        }
                    }
                    else {
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
            _dbClient.updateObject(sp);

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
     * 
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
                    && (exportMask.getNativeId() != null && exportMask.getNativeId().equals(nativeId))
                    && exportMask.getStorageDevice().equals(device)) {
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
            _log.warn(e.getMessage(), e);
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
            _log.warn(e.getMessage(), e);
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
            _log.warn(e.getMessage(), e);
        }
        return ceiling;
    }

    /**
     * Get port utilization percentage floor value for given storage system's type. Valid range is b/w 0 - 100
     * 
     * @param systemType - storage system type {@link DiscoveredDataObject.Type}
     * @return 0 - 100
     */
    static public Integer getPortBusyFloor(StorageSystem.Type systemType) {
        int floor = DEFAULT_PORT_UTILIZATION_FLOOR;  // default to 0% if not specified

        try {
            floor = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_PORT_UTILIZATION_FLOOR,
                            getStorageSystemTypeName(systemType), null));
            if (floor <= 0 || floor > 100) {
                floor = DEFAULT_PORT_UTILIZATION_FLOOR;
            }
        } catch (Exception e) {
            _log.warn(e.getMessage(), e);
        }
        return floor;
    }

    /**
     * Get cpu utilization percentage floor value for given storage system's type. Valid range is b/w 0 - 100
     * 
     * @param systemType - storage system type {@link DiscoveredDataObject.Type}
     * @return 0 - 100
     */
    static public Integer getCpuBusyFloor(StorageSystem.Type systemType) {
        int floor = DEFAULT_CPU_UTILIZATION_FLOOR;  // default to 0% if not specified

        try {
            floor = Integer.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_CPU_UTILIZATION_FLOOR,
                            getStorageSystemTypeName(systemType), null));
            if (floor <= 0 || floor > 100) {
                floor = DEFAULT_CPU_UTILIZATION_FLOOR;
            }
        } catch (Exception e) {
            _log.warn(e.getMessage(), e);
        }
        return floor;
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
            _log.warn(e.getMessage(), e);
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
            _log.warn(e.getMessage(), e);
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
            _log.warn(e.getMessage(), e);
        }
        return emaFactor;
    }

    /**
     * Get volume coefficient. This controlls how much weight volume count has in the port metric.
     * Range is 0 through 2.0 where 1.0 is "normal weight" and 0.0 is disabled.
     * 
     * @return 0 - 2.0
     */
    static public Double getVolumeCoefficient(StorageSystem.Type systemType) {
        double volumeCoefficient = DEFAULT_VOLUME_COEFFICIENT;  // default to factor of 1.0

        try {
            volumeCoefficient = Double.valueOf(
                    customConfigHandler.getComputedCustomConfigValue(
                            CustomConfigConstants.PORT_ALLOCATION_VOLUME_COEFFICIENT,
                            getStorageSystemTypeName(systemType), null));
            if (volumeCoefficient < 0.0 || volumeCoefficient > 5.0) {
                volumeCoefficient = DEFAULT_VOLUME_COEFFICIENT;
            }
        } catch (Exception e) {
            _log.warn(e.getMessage(), e);
        }
        return volumeCoefficient;
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
        } else if (!StorageSystem.Type.vmax.equals(systemType) &&
                !StorageSystem.Type.hds.equals(systemType) &&
                !StorageSystem.Type.vplex.equals(systemType)) {
            // System types other than vnx, vmax, hds, and vplex are categorized as "other_arrays"
            name = "other_arrays";
        }
        return name;
    }

    /**
     * Compute and set each storage pool's average port usage metric. The average port metrics is
     * actually pool's storage system's port metric.
     *
     * @param storagePools
     * @return storageSystemAvgPortMetricsMap
     */
    public Map<URI, Double> computeStoragePoolsAvgPortMetrics(List<StoragePool> storagePools) {
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
        return storageSystemAvgPortMetricsMap;
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
            StringBuffer errorMessage = new StringBuffer();
            ImplicitPoolMatcher.matchStorageSystemPoolsToVPools(storageSystem.getId(), _dbClient, _coordinator, errorMessage);
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

    /**
     * Compute port group metrics (portMetric and volume counts) for vmax
     * 
     * @param systemURI
     *            - storage system URI
     */
    public void computePortGroupMetrics(URI systemURI) {
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, systemURI);
        DiscoveredDataObject.Type type = DiscoveredDataObject.Type.valueOf(system.getSystemType());
        if (type != DiscoveredDataObject.Type.vmax) {
            return;
        }
        _log.info("Calculating port group metrics");
        URIQueryResultList portGroupURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDevicePortGroupConstraint(systemURI),
                portGroupURIs);

        Iterator<URI> portGroupIter = portGroupURIs.iterator();
        while (portGroupIter.hasNext()) {
            URI pgURI = portGroupIter.next();
            StoragePortGroup portGroup = _dbClient.queryObject(StoragePortGroup.class, pgURI);
            if (portGroup != null && !portGroup.getInactive() &&
                    !portGroup.checkInternalFlags(Flag.INTERNAL_OBJECT)) {
                StringSet ports = portGroup.getStoragePorts();
                List<StoragePort> portMembers = _dbClient.queryObject(StoragePort.class, StringSetUtil.stringSetToUriList(ports));
                Double portMetricTotal = 0.0;
                StringMap dbMetrics = portGroup.getMetrics();
                boolean isMetricsSet = true;
                for (StoragePort port : portMembers) {
                    StringMap portMetrics = port.getMetrics();
                    if (portMetrics == null) {
                        isMetricsSet = false;
                        break;
                    }
                    Double portMetric = MetricsKeys.getDouble(MetricsKeys.portMetric, portMetrics);
                    if (portMetric == null) {
                        isMetricsSet = false;
                        break;
                    }
                    portMetricTotal += portMetric;
                }
                if (isMetricsSet && portMetricTotal != null) {
                    _log.info(String.format("port group %s portMetric %s", portGroup.getNativeGuid(), portMetricTotal.toString()));
                    MetricsKeys.putDouble(MetricsKeys.portMetric, portMetricTotal / portMembers.size(),
                            dbMetrics);
                }
                computePortGroupVolumeCounts(portGroup, dbMetrics, _dbClient);
                portGroup.setMetrics(dbMetrics);
                _dbClient.updateObject(portGroup);
            }
        }
    }

    /**
     * Updates port group volume counts for vmax.
     * 
     * @param portGroup
     *            - Port group to be updated
     * @param dbMetrics
     *            - dbMetrics to be updated
     * @param dbClient
     *            - DbClient
     */
    public static void computePortGroupVolumeCounts(StoragePortGroup portGroup, StringMap dbMetrics, DbClient dbClient) {
        _log.debug(String.format("computePortGroupVolumeCounts: %s", portGroup.getNativeGuid()));
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, portGroup.getStorageDevice());

        Long volumeCount = 0L;
        // Find all the Export Masks containing the port group.
        URIQueryResultList queryResult = new URIQueryResultList();
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getExportMasksByPortGroup(portGroup.getId().toString()), queryResult);
        Iterator<URI> maskIt = queryResult.iterator();
        while (maskIt.hasNext()) {
            URI maskURI = maskIt.next();
            ExportMask mask = dbClient.queryObject(ExportMask.class, maskURI);
            if (mask == null || mask.getInactive()) {
                continue;
            }

            if (mask.getExistingVolumes() != null) {
                volumeCount += mask.getExistingVolumes().size();
            }
            if (system.checkIfVmax3()) {
                // VMAX3 does not have a dependency on meta-luns, so these are not counted.
                if (mask.getUserAddedVolumes() != null) {
                    volumeCount += mask.getUserAddedVolumes().size();
                }
            } else {
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
        }
        MetricsKeys.putLong(MetricsKeys.volumeCount, volumeCount, dbMetrics);

    }
}
