/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

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
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
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

    private static final String TIME = "Time";
    private static final String DIRECTOR_BUSY = "director.busy";
    private static final String DIRECTOR_FE_OPS = "director.fe-ops";
    private static final String FE_PORT_READ = "fe-prt.read";
    private static final String FE_PORT_WRITE = "fe-prt.write";
    private static final String FE_PORT_OPS = "fe-prt.ops";

    private static final String HEADER_KEY_DIRECTOR_BUSY = "director.busy (%)";
    private static final String HEADER_KEY_DIRECTOR_FE_OPS = "director.fe-ops (counts/s)";
    private static final String HEADER_KEY_TIME_UTC = "Time (UTC)";

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
    public StringMap collect(AccessProfile accessProfile, Map<String, Object> context) {
        init();
        DbClient dbClient = (DbClient) context.get(Constants.dbClient);
        // Get which VPlex array that this applies to
        URI storageSystemURI = accessProfile.getSystemId();
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, storageSystemURI);
        if (storageSystem == null) {
            log.error("Could not find StorageSystem '{}' in DB", storageSystemURI);
            return null;
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
            // There are at least one data point
            if (lineCount > 1) {
                Map<String, String> last = dataLines.get(lineCount - 1);
                processDirectorStats(metricNamesToHeaderInfo, last);
                processPortStats(metricNamesToHeaderInfo, last);
            }
            // Clean up fileData resources
            fileData.close();
        }
        // Clean out the cache data, so that it's not laying around
        clearCaches();
        return null;
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
     * @param lastSample - [IN] Last data sample (the latest)
     */
    private void processDirectorStats(Map<String, MetricHeaderInfo> metricHeaderInfoMap, Map<String, String> lastSample) {
        MetricHeaderInfo headerInfo = metricHeaderInfoMap.get(HEADER_KEY_DIRECTOR_BUSY);
        if (headerInfo != null) {
            String directorBusyString = lastSample.get(HEADER_KEY_DIRECTOR_BUSY);
            if (directorBusyString != null) {
                Double percentBusy = Double.valueOf(directorBusyString);
                Double iops = Double.valueOf(lastSample.get(HEADER_KEY_DIRECTOR_FE_OPS)); // NB: This is really for informational purposes
                String lastSampleTime = lastSample.get(HEADER_KEY_TIME_UTC);
                portMetricsProcessor.processFEAdaptMetrics(percentBusy, iops.longValue(), headerInfo.director, lastSampleTime, false);
            }
        }
    }

    /**
     * Process all the port metrics found in metricHeaderInfoMap.
     *
     * @param metricHeaderInfoMap - [IN] Metric name to structure holding information about that metric
     * @param lastSample - [IN] Last data sample (the latest)
     */
    private void processPortStats(Map<String, MetricHeaderInfo> metricHeaderInfoMap, Map<String, String> lastSample) {
        Map<URI, PortStat> portStatMap = new HashMap<>();
        // Each key will reference the metric name, an optional object, and units. As we process the
        // keys, we will keep track of the values and persist them in the portStatMap for each port.
        for (String metricKey : metricHeaderInfoMap.keySet()) {
            MetricHeaderInfo headerInfo = metricHeaderInfoMap.get(metricKey);
            // We only care about the port metrics ...
            if (headerInfo.type == MetricHeaderInfo.Type.PORT) {
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
                    stat.iops = Double.valueOf(lastSample.get(metricKey)).longValue();
                } else if (metricKey.contains(FE_PORT_READ)) {
                    stat.kbytes += Double.valueOf(lastSample.get(metricKey)).longValue();
                } else if (metricKey.contains(FE_PORT_WRITE)) {
                    stat.kbytes += Double.valueOf(lastSample.get(metricKey)).longValue();
                } else if (metricKey.equals(HEADER_KEY_TIME_UTC)) {
                    stat.sampleTime = Long.valueOf(lastSample.get(HEADER_KEY_TIME_UTC));
                }
            }
        }

        // We've processed metricHeaderInfoMap for all port stats. Now, look through
        // the stat entries that we've collected and call into the PortMetricsProcessor
        // for the port and its associated stats.
        for (URI portURI : portStatMap.keySet()) {
            PortStat stat = portStatMap.get(portURI);
            if (stat.allFilled()) {
                portMetricsProcessor.processFEPortMetrics(stat.kbytes, stat.iops, stat.port, stat.sampleTime);
            } else {
                log.warn("Failed to process stats for port {}", portURI);
            }
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
