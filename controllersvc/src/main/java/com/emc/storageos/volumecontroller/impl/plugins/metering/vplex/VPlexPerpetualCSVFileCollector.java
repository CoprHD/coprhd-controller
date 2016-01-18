/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vplex;

import java.net.URI;
import java.util.HashMap;
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
import com.google.common.base.Strings;
import com.iwave.ext.linux.LinuxSystemCLI;

/**
 * VPlexStatsCollector implementation that reads the perpetual VPlex stats file on the management station
 * and translates them into the ViPR metrics.
 */
public class VPlexPerpetualCSVFileCollector implements VPlexStatsCollector {
    public static final Pattern METRIC_NAME_PATTERN = Pattern.compile("([\\w+\\-\\.]+)\\s+([\\w+\\-]*+)\\s*\\(([\\w+/]+)\\)");
    public static final String EMPTY = "";
    private static final Set<String> NOT_PORTS = new ConcurrentHashSet<>();
    private static final Map<String, DataObject> OBJECT_CACHE = new ConcurrentHashMap<>();
    private static Logger log = LoggerFactory.getLogger(VPlexPerpetualCSVFileCollector.class);

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

            // Process the metric data values
            for (Map<String, String> dataLine : fileData.getDataLines()) {
                // Extract the metrics and their values, translate them into ViPR statistics
                for (String metricName : metricNamesToHeaderInfo.keySet()) {
                    String value = dataLine.get(metricName);
                    MetricHeaderInfo headerInfo = metricNamesToHeaderInfo.get(metricName);
                    if (headerInfo != null) {
                        processMetric(headerInfo, metricName, value);
                    }
                }
            }
            // Clean up fileData resources
            fileData.close();
        }
        return null;
    }

    /**
     * Method to do some initialization before the meat of the collect() operation runs.
     *
     */
    private void init() {
        NOT_PORTS.clear();
        OBJECT_CACHE.clear();
    }

    /**
     * Return a mapping of the metric name and the DataObject (StorageHADomain or StoragePort) to which the
     * metric applies.
     *
     * @param dbClient [IN] - DbClient used for DB access
     * @param storageSystem [IN] - StorageSystem representing the VPlex array
     * @param directorName [IN] - Name of VPlex director that this applies to
     * @param headers [IN] - Metric names that show up in the file
     * @return Map of String (metric name) to DataObject to which the metric applies
     */
    private Map<String, MetricHeaderInfo> processCSVFileDataHeader(DbClient dbClient, StorageSystem storageSystem, String directorName,
            List<String> headers) {
        Map<String, MetricHeaderInfo> metricToObjectMap = new HashMap<>();
        for (String header : headers) {
            Matcher matcher = METRIC_NAME_PATTERN.matcher(header);
            if (matcher.matches()) {
                String name = matcher.group(1);
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
                        metricToObjectMap.put(name, headerInfo);
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
     * Process the metric. The VPlex metric names will reference the object. Example:
     * <p/>
     * "director.per-cpu-busy CPU3"
     * "fe-prt.read A0-FC02"
     * "fe-prt.write-avg-lat A0-FC03"
     * "fe-prt.ops A0-FC00"
     * "metricName [qualifier] units"
     * <p/>
     * This method will have to figure out to which object the metric belongs and
     * update it appropriately.
     *
     * @param headerInfo [IN] - The DB object that this affects (expecting StorageHADomain or StoragePort)
     * @param metricName [IN] - String name of metric
     * @param value [IN] - Value of metric
     */
    private void processMetric(MetricHeaderInfo headerInfo, String metricName, String value) {
        String units = headerInfo.units;
        if (headerInfo.type == MetricHeaderInfo.Type.DIRECTOR) {
            StorageHADomain director = headerInfo.director;
        } else if (headerInfo.type == MetricHeaderInfo.Type.PORT) {
            StoragePort port = headerInfo.port;
        }
    }

    /**
     * Helper data structure class
     */
    static class MetricHeaderInfo {
        StorageHADomain director;
        StoragePort port;
        String units;
        Type type;

        enum Type {
            PORT, DIRECTOR
        };
    }
}
