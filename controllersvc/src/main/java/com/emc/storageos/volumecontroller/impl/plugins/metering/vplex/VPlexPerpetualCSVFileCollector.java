/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.DIRECTOR_BUSY;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.DIRECTOR_FE_OPS;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.FE_PORT_OPS;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.FE_PORT_READ;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.FE_PORT_WRITE;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.HEADER_KEY_DIRECTOR_BUSY;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.HEADER_KEY_DIRECTOR_FE_OPS;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.HEADER_KEY_TIME_UTC;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.NO_DATA;
import static com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData.TIME;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.google.common.base.Strings;
import com.iwave.ext.linux.LinuxSystemCLI;

/**
 * VPlexStatsCollector implementation that reads the perpetual VPlex stats file on the management station
 * and translates them into the ViPR metrics.
 */
public class VPlexPerpetualCSVFileCollector implements VPlexStatsCollector {
    public static final Pattern METRIC_NAME_PATTERN = Pattern.compile("([\\w+\\-\\.]+)\\s+([\\w+\\-]*+)\\s*\\(([\\w+/%]+)\\)");
    public static final String EMPTY = "";
    private static final Set<String> NOT_PORTS = new ConcurrentHashSet<>();
    private static final Map<String, DataObject> OBJECT_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> METRICS_NAMES_TO_GATHER = new HashSet<>();
    private static final String DOUBLE_DATA_PATTERN = "\\d+\\.?\\d*";

    private static Logger log = LoggerFactory.getLogger(VPlexPerpetualCSVFileCollector.class);

    private PortMetricsProcessor portMetricsProcessor;

    static {
        // Establish a set of metrics that we would like to process from the CSV data files
        // NB: These are just the names, not the exact header. For example, "director.busy" is the
        // name, but what you would find in the CSV file is a header called "director.busy (%)"
        METRICS_NAMES_TO_GATHER
                .addAll(Arrays.asList(DIRECTOR_BUSY, DIRECTOR_FE_OPS, FE_PORT_READ, FE_PORT_WRITE, FE_PORT_OPS, TIME));
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }

    public VPlexPerpetualCSVFileCollector() {
    }

    @Override
    public void collect(AccessProfile accessProfile, Map<String, Object> context) {
        init();
        DbClient dbClient = (DbClient) context.get(Constants.dbClient);
        // Get which VPlex array that this applies to
        URI storageSystemURI = accessProfile.getSystemId();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
        if (storageSystem == null) {
            log.error("Could not find StorageSystem '{}' in DB", storageSystemURI);
            return;
        }

        LinuxSystemCLI cli = new LinuxSystemCLI(accessProfile.getIpAddress(), accessProfile.getUserName(), accessProfile.getPassword());
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);
        // Process each of the data files that we found on the VPlex management station
        List<String> fileNames = listDataFileNamesCmd.getResults();
        for (String fileName : fileNames) {
            // Extract and hold the data for this data file
            ReadAndParseVPlexPerpetualCSVFile readDataFile = new ReadAndParseVPlexPerpetualCSVFile(fileName);
            cli.executeCommand(readDataFile);
            VPlexPerpetualCSVFileData fileData = readDataFile.getResults();

            // Read the headers and extract those metric names that we're interested in and to which
            // DataObject (StorageHADomain or StoragePort) that it should be associated with. This
            // will be used as a way to look up the object when processing the actual metric data
            Map<String, MetricHeaderInfo> metricNamesToHeaderInfo = processCSVFileDataHeader(dbClient, storageSystem,
                    fileData.getDirectorName(), fileData.getHeaders());

            List<Map<String, String>> dataLines = fileData.getDataLines();
            int lineCount = dataLines.size();
            // There is at least one data point
            if (lineCount > 1) {
                // Determine the last time that metrics were collected.
                Long lastCollectionTimeUTC = getLastCollectionTime(metricNamesToHeaderInfo);
                // Try to find the index into dataLines based on the last collection time.
                // What we're trying to do here is determine the maximum value for the metrics
                // from the last collection time in ViPR, until the last data line in the file.
                int start = fileData.getDataIndexForTime(lastCollectionTimeUTC);
                // Have a mapping of metrics to their maximum value found in the dataLines
                Map<String, Double> maxValues = findMaxMetricValues(dataLines, start, lineCount);
                // Process the metrics for this file
                Map<String, String> last = dataLines.get(lineCount - 1);
                processDirectorStats(metricNamesToHeaderInfo, maxValues, last);
                processPortStats(context, metricNamesToHeaderInfo, maxValues, last);
            }
            // Clean up fileData resources
            fileData.close();
        }
        // Clean out the cache data, so that it's not laying around
        clearCaches();
    }

    /**
     * Examines the metricHeaderInfoMap to find an entry referencing a StorageHADomain's (i.e. VPlex director)
     * metrics. In the metrics, we will lookup the value for the lastSampleTime. We know that all stats are
     * processed at one time, so this will be considered the last time metrics were collected for *all* objects.
     *
     * @param metricHeaderInfoMap - [IN] Metric name to structure holding information about that metric
     * 
     * @return Long - Time value in UTC representing the last time metrics were collected. If
     *         metrics were never collected, then 0 is returned.
     */
    private Long getLastCollectionTime(Map<String, MetricHeaderInfo> metricHeaderInfoMap) {
        Long timeUTC = null;
        MetricHeaderInfo headerInfo = metricHeaderInfoMap.get(HEADER_KEY_DIRECTOR_BUSY);
        if (headerInfo != null) {
            // Note: this call will return 0 if 'lastSampleTime' is not found
            timeUTC = MetricsKeys.getLong(MetricsKeys.lastSampleTime, headerInfo.director.getMetrics());
        }
        return timeUTC;
    }

    /**
     * Given the data for a file, starting from 'start' index until 'end' index, calculate
     * the maximum value for all metrics found. Only numeric values are calculated.
     * 
     * @param dataLines [IN] - Data from the CSV file
     * @param start [IN] - Index value to start from in 'dataLines'
     * @param end [IN] - Index value to end in 'dataLines'
     * @return Map of String metricName to max value found for that metric based on search
     *         of 'dataLines' from 'start' to 'end'
     */
    private Map<String, Double> findMaxMetricValues(List<Map<String, String>> dataLines, int start, int end) {
        assert (start < end);
        Map<String, Double> maxValuesMap = new HashMap<>();
        // Search through dataLines starting from the 'start' index
        for (int index = start; index < end; index++) {
            // Get the data at the 'index' line
            Map<String, String> data = dataLines.get(index);
            // Look at the metrics (which would be considered the columns in the CSV file)
            for (String metricKey : data.keySet()) {
                String dataValueString = data.get(metricKey);
                // Skip over 'no data' and non-numeric values
                if (dataValueString.equals(NO_DATA) || !dataValueString.matches(DOUBLE_DATA_PATTERN)) {
                    continue;
                }
                // Get the values from dataLine and from what has been previously found (if it exists)
                Double currentMaxValue = maxValuesMap.get(metricKey);
                Double dataValue = Double.valueOf(dataValueString);
                if (currentMaxValue == null) {
                    // Cache miss: add it
                    maxValuesMap.put(metricKey, dataValue);
                } else {
                    // Cache hit: check if the current data value is greater than the current max value
                    if (dataValue > currentMaxValue) {
                        maxValuesMap.put(metricKey, dataValue);
                    }
                }
            }
        }
        return maxValuesMap;
    }

    /**
     * Method to do some initialization before the meat of the collect() operation runs.
     *
     */
    private void init() {
        clearCaches();
    }

    private void clearCaches() {
        NOT_PORTS.clear();
        OBJECT_CACHE.clear();
    }

    /**
     * Return a mapping of the metric name and the DataObject (StorageHADomain or StoragePort) to which the
     * metric applies.
     *
     * First line from the CSV file will look like this:
     *
     * Time,Time (UTC),be-prt.write A1-FC02 (KB/s),be-prt.write A1-FC03 (KB/s),...
     *
     * 'headers' will contain each of the Strings delimited by ','. This function will parse that to determine
     * what DataObject it should be associated with and its units.
     *
     * @param dbClient [IN] - DbClient used for DB access
     * @param storageSystem [IN] - StorageSystem representing the VPlex array
     * @param directorName [IN] - Name of VPlex director that this applies to
     * @param headers [IN] - Metric names that show up in the file
     * @return Map of String (header) to DataObject to which the metric applies
     */
    private Map<String, MetricHeaderInfo> processCSVFileDataHeader(DbClient dbClient, StorageSystem storageSystem, String directorName,
            List<String> headers) {
        Map<String, MetricHeaderInfo> metricToObjectMap = new HashMap<>();
        for (String header : headers) {
            Matcher matcher = METRIC_NAME_PATTERN.matcher(header);
            if (matcher.matches()) {
                String name = matcher.group(1);
                // Limit the processing to only those metrics that we care about
                if (!METRICS_NAMES_TO_GATHER.contains(name)) {
                    continue;
                }

                String objectName = matcher.group(2);
                String units = matcher.group(3);
                if (Strings.isNullOrEmpty(objectName)) {
                    objectName = EMPTY;
                }
                StorageHADomain vplexDirector = lookupVPlexDirectorByName(dbClient, storageSystem, directorName);
                if (objectName.equals(EMPTY)) {
                    // This applies to the director
                    MetricHeaderInfo headerInfo = new MetricHeaderInfo();
                    headerInfo.type = MetricHeaderInfo.Type.DIRECTOR;
                    headerInfo.director = vplexDirector;
                    headerInfo.units = units;
                    metricToObjectMap.put(header, headerInfo);
                } else {
                    // Let's assume that this is for a StoragePort with name 'objectName'
                    StoragePort storagePort = lookupVPlexFrontStoragePortByName(dbClient, vplexDirector, objectName);
                    if (storagePort != null) {
                        MetricHeaderInfo headerInfo = new MetricHeaderInfo();
                        headerInfo.type = MetricHeaderInfo.Type.PORT;
                        headerInfo.director = vplexDirector;
                        headerInfo.port = storagePort;
                        headerInfo.units = units;
                        metricToObjectMap.put(header, headerInfo);
                    }
                }
            }
        }
        return metricToObjectMap;
    }

    /**
     * Find the StoragePort with 'name' and associated to the StorageHADomain representing the VPlex director.
     *
     * @param dbClient [IN] - DbClient used for DB access
     * @param vplexDirector [IN] - StorageHADomain representing a VPlex director
     * @param name [IN] - The name to look up. It could be a StoragePort.
     * @return StoragePort with portName='name' is found out to be StoragePort associated to the 'vplexDirector', otherwise null
     */
    private StoragePort lookupVPlexFrontStoragePortByName(DbClient dbClient, StorageHADomain vplexDirector, String name) {
        String cacheKey = generateStoragePortKey(vplexDirector, name);
        // Check if this is something that we encountered that does not look to be a StoragePort
        if (NOT_PORTS.contains(cacheKey)) {
            return null;
        }
        StoragePort port = (StoragePort) OBJECT_CACHE.get(cacheKey);
        if (port == null) {
            port = findStoragePortByNameInDB(dbClient, vplexDirector, name);
            if (port != null) {
                OBJECT_CACHE.put(cacheKey, port);
                return port;
            } else {
                // Stash as not a StoragePort
                NOT_PORTS.add(cacheKey);
            }
        }
        return port;
    }

    /**
     * Get the StoragePort in the DB with the given 'portName' and associated to the StorageHADomain.
     *
     * @param dbClient [IN] - DbClient used for DB access
     * @param vplexDirector [IN] - StorageHADomain representing a VPlex director
     * @param portName [IN] Name of storage port to look up
     * @return StoragePort found in DB with 'portName' and associated to StorageHADomain, otherwise null
     */
    private StoragePort findStoragePortByNameInDB(DbClient dbClient, StorageHADomain vplexDirector, String portName) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageHADomainStoragePortConstraint(vplexDirector.getId()), results);
        Iterator<StoragePort> portIterator = dbClient.queryIterativeObjects(StoragePort.class, results, true);
        while (portIterator.hasNext()) {
            StoragePort port = portIterator.next();
            if (port.getPortName().equals(portName)) {
                return port;
            }
        }
        log.info("Could not find StoragePort with portName '{}' for VPlex director {}", portName, vplexDirector.getNativeGuid());
        return null;
    }

    /**
     * Generate a unique key for caching purposes
     *
     * @param vplexDirector [IN] - StorageHADomain representing the VPlex director
     * @param objectName [IN] - String name of object
     * @return String
     */
    private String generateStoragePortKey(StorageHADomain vplexDirector, String objectName) {
        return String.format("%s-%s", vplexDirector.getNativeGuid(), objectName);
    }

    /**
     * Lookup the StorageHADomain with name 'directorName' and associated with VPlex array 'storageSystem'.
     *
     * @param dbClient [IN] - DbClient used for DB access
     * @param storageSystem [IN] - StorageSystem representing the VPlex array
     * @param directorName [IN] - Name of VPlex director to lookup
     * @return StorageHADomain with named 'directorName' and associated to StorageSystem 'storageSystem', otherwise null
     */
    private StorageHADomain lookupVPlexDirectorByName(DbClient dbClient, StorageSystem storageSystem, String directorName) {
        String cacheKey = generateStorageHADomainKey(storageSystem, directorName);
        StorageHADomain vplexDirector = (StorageHADomain) OBJECT_CACHE.get(cacheKey);
        if (vplexDirector == null) {
            vplexDirector = findStorageHADomainByNameInDB(dbClient, storageSystem, directorName);
            if (vplexDirector != null) {
                OBJECT_CACHE.put(cacheKey, vplexDirector);
                return vplexDirector;
            } else {
                // TODO: Error!!!
            }
        }
        return vplexDirector;
    }

    /**
     * Return a unique key used for caching
     *
     * @param storageSystem [IN] - StorageSystem representing the VPlex array
     * @param directorName [IN] - Name of VPlex director
     * @return String
     */
    private String generateStorageHADomainKey(StorageSystem storageSystem, String directorName) {
        return String.format("%s-%s", storageSystem.getNativeGuid(), directorName);
    }

    /**
     * Retrieve from the DB the StorageHADomain named 'directorName' and associated with VPlex array 'storageSystem'
     *
     * @param dbClient [IN] - DbClient used for DB access
     * @param storageSystem [IN] - StorageSystem representing the VPlex array
     * @param directorName [IN] - Name of the VPlex director to find
     * @return StorageHADomain with name 'directorName' and associated with VPlex array 'storageSystem', otherwise null
     */
    private StorageHADomain findStorageHADomainByNameInDB(DbClient dbClient, StorageSystem storageSystem, String directorName) {
        URIQueryResultList results = new URIQueryResultList();
        dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceStorageHADomainConstraint(storageSystem.getId()), results);
        Iterator<StorageHADomain> directorIterator = dbClient.queryIterativeObjects(StorageHADomain.class, results, true);
        while (directorIterator.hasNext()) {
            StorageHADomain director = directorIterator.next();
            if (director.getAdapterName().equals(directorName)) {
                return director;
            }
        }
        log.warn("Could not find StorageHADomain with adapterName '{}' for StorageSystem {}", directorName, storageSystem.getNativeGuid());
        return null; // Could not be found
    }

    /**
     * Process the director metrics found in metricHeaderInfoMap
     * 
     * @param metricHeaderInfoMap - [IN] Metric name to structure holding information about that metric
     * @param maxValues [IN] - Mapping of metrics to maximum values found in the file data
     * @param lastSample - [IN] Last data sample (the latest)
     */
    private void processDirectorStats(Map<String, MetricHeaderInfo> metricHeaderInfoMap, Map<String, Double> maxValues,
            Map<String, String> lastSample) {
        MetricHeaderInfo headerInfo = metricHeaderInfoMap.get(HEADER_KEY_DIRECTOR_BUSY);
        if (headerInfo != null) {
            String directorBusyString = lastSample.get(HEADER_KEY_DIRECTOR_BUSY);
            if (directorBusyString != null) {
                // Percent busy is a non-counter value; we can use it raw
                Double percentBusy = Double.valueOf(directorBusyString);
                // Note: 'iops' is really for informational purposes. We're going to find the maximum
                // value in the data or use the latest value, if it's not found
                Double iops = (maxValues.containsKey(HEADER_KEY_DIRECTOR_FE_OPS)) ? maxValues.get(HEADER_KEY_DIRECTOR_FE_OPS)
                        : Double.valueOf(lastSample.get(HEADER_KEY_DIRECTOR_FE_OPS));
                String lastSampleTime = lastSample.get(HEADER_KEY_TIME_UTC);
                portMetricsProcessor.processFEAdaptMetrics(percentBusy, iops.longValue(), headerInfo.director, lastSampleTime, false);
            }
        }
    }

    /**
     * Process all the port metrics found in metricHeaderInfoMap.
     *
     * @param context [IN/OUT] - Metering context structure. Will contain a value for filling in metering data.
     * @param metricHeaderInfoMap - [IN] Metric name to structure holding information about that metric
     * @param maxValues [IN] - Mapping of metrics to maximum values found in the file data
     * @param lastSample - [IN] Last data sample (the latest)
     */
    private void processPortStats(Map<String, Object> context, Map<String, MetricHeaderInfo> metricHeaderInfoMap,
            Map<String, Double> maxValues, Map<String, String> lastSample) {
        Map<URI, PortStat> portStatMap = new HashMap<>();
        // Each key will reference the metric name, an optional object, and units. As we process the
        // keys, we will keep track of the values and persist them in the portStatMap for each port.
        for (String metricKey : metricHeaderInfoMap.keySet()) {
            MetricHeaderInfo headerInfo = metricHeaderInfoMap.get(metricKey);
            // We only care about the metrics for port objects ...
            if (headerInfo.type == MetricHeaderInfo.Type.PORT) {
                handlePortStat(metricKey, headerInfo, portStatMap, maxValues, lastSample);
            }
        }

        // We've processed metricHeaderInfoMap for all port stats. Now, look through
        // the stat entries that we've collected and call into the PortMetricsProcessor
        // for the port and its associated stats.
        for (URI portURI : portStatMap.keySet()) {
            PortStat stat = portStatMap.get(portURI);
            if (stat.allFilled()) {
                portMetricsProcessor.processFEPortMetrics(stat.kbytes, stat.iops, stat.port, stat.sampleTime);
                addPortMetric(context, stat);
            } else {
                log.warn("Failed to process stats for port {}", portURI);
            }
        }
    }

    /**
     * Create a new metric for the VPlex FE port, which is held in 'stat' and add it to the metering data.
     *
     * @param context [IN/OUT] - Metering context structure. Will contain a value for filling in metering data.
     * @param stat [IN] - PortStat for a particular VPlex FE port
     */
    private void addPortMetric(Map<String, Object> context, PortStat stat) {
        Stat fePortStat = new Stat();
        fePortStat.setServiceType(Constants._Block);
        fePortStat.setTimeCollected(stat.sampleTime);
        fePortStat.setTotalIOs(stat.iops);
        fePortStat.setKbytesTransferred(stat.kbytes);
        fePortStat.setNativeGuid(stat.port.getNativeGuid());
        fePortStat.setResourceId(stat.port.getId());
        @SuppressWarnings("unchecked")
        List<Stat> metrics = (List<Stat>) context.get(Constants._Stats);
        metrics.add(fePortStat);
    }

    /**
     * Create or update the PortStat pertaining to the port indicated by 'metricKey'.
     * Requires:
     * - 'metricKey' points to a headerInfo that is associated with entry of type =
     * VPlexPerpetualCSVFileCollector.MetricHeaderInfo.Type.PORT
     * 
     * @param metricKey [IN] -- Key to which the headerInfo applies
     * @param headerInfo [IN] -- Meta info for a port stat
     * @param portStatMap [IN/OUT] -- StoragePort URI to PortStat mapping. Will be filled in new PortStat if
     *            it's the first time that the port has been encountered.
     * @param maxValues [IN] - Mapping of metrics to maximum values found in the file data
     * @param lastSample [IN] -- The last collected port stat.
     */
    private void handlePortStat(String metricKey, MetricHeaderInfo headerInfo, Map<URI, PortStat> portStatMap,
            Map<String, Double> maxValues, Map<String, String> lastSample) {
        StoragePort port = headerInfo.port;
        PortStat stat = portStatMap.get(port.getId());
        if (stat == null) {
            // We don't have a stats entry for this port yet.
            // Create it and add it to the map.
            stat = new PortStat(port, 0, 0, 0);
            portStatMap.put(port.getId(), stat);
        }
        // Which stat is this?
        if (metricKey.contains(FE_PORT_OPS)) {
            stat.iops = maxValues.containsKey(metricKey) ? maxValues.get(metricKey).longValue()
                    : Double.valueOf(lastSample.get(metricKey)).longValue();
        } else if (metricKey.contains(FE_PORT_READ)) {
            stat.kbytes += maxValues.containsKey(metricKey) ? maxValues.get(metricKey).longValue()
                    : Double.valueOf(lastSample.get(metricKey)).longValue();
        } else if (metricKey.contains(FE_PORT_WRITE)) {
            stat.kbytes += maxValues.containsKey(metricKey) ? maxValues.get(metricKey).longValue()
                    : Double.valueOf(lastSample.get(metricKey)).longValue();
        }
        // The sampleTime is not associated with a particular port, so we will
        // check if the stat.sampleTime is not set yet and then set it once.
        if (stat.sampleTime == null || stat.sampleTime == 0) {
            stat.sampleTime = Long.valueOf(lastSample.get(HEADER_KEY_TIME_UTC));
        }
    }

    /**
     * Helper data structure class
     */
    static class MetricHeaderInfo {
        private StorageHADomain director;
        private StoragePort port;
        private String units;
        private Type type;

        enum Type {
            PORT, DIRECTOR
        };
    }

    /**
     * Class for holding stat values for ports as we look through metricHeaderInfoMap
     */
    static class PortStat {
        private StoragePort port;
        private Long iops;
        private Long kbytes;
        private Long sampleTime;

        public PortStat(StoragePort port, long iops, long kbytes, long sampleTime) {
            this.port = port;
            this.iops = iops;
            this.kbytes = kbytes;
            this.sampleTime = sampleTime;
        }

        boolean allFilled() {
            return port != null && iops != null && kbytes != null && sampleTime != null;
        }
    }
}
