/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.metering.plugins.vplex;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.CustomConfigResolver;
import com.emc.storageos.customconfigcontroller.CustomConfigTypeProvider;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbAggregatorItf;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.TimeSeriesMetadata;
import com.emc.storageos.db.client.TimeSeriesQueryResult;
import com.emc.storageos.db.client.constraint.Constraint;
import com.emc.storageos.db.client.constraint.QueryResultList;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.TimeSeries;
import com.emc.storageos.db.client.model.TimeSeriesSerializer;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.services.util.EnvConfig;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.ListVPlexPerpetualCSVFileNames;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.ReadAndParseVPlexPerpetualCSVFile;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileCollector;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vplex.VPlexPerpetualCSVFileData;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.iwave.ext.linux.LinuxSystemCLI;

/**
 * Tester class for VPlex metering related classes and functions
 */
public class VPlexMeteringTest {
    public static final String SANITY = "sanity";
    public static final String VPLEX_HOST = "vplex.host";
    public static final String VPLEX_USERNAME = "vplex.username";
    public static final String VPLEX_PASSWORD = "vplex.password";
    public static final String VPLEX_DIRECTORS = "vplex.directors";
    public static final String VPLEX_PORTS_PER_DIRECTOR = "vplex.portsPerDirector";
    private final static String HOST = EnvConfig.get(SANITY, VPLEX_HOST);
    private final static String USERNAME = EnvConfig.get(SANITY, VPLEX_USERNAME);
    private final static String PASSWORD = EnvConfig.get(SANITY, VPLEX_PASSWORD);
    private final static String[] DIRECTORS = EnvConfig.get(SANITY, VPLEX_DIRECTORS).split(",");
    private final static int PORT_PER_DIRECTOR = Integer.valueOf(EnvConfig.get(SANITY, VPLEX_PORTS_PER_DIRECTOR));

    private static boolean alreadyPrinted = false;
    private static boolean outputToLog = Boolean.valueOf(EnvConfig.get(SANITY, "output_to_log"));

    private static Logger log = LoggerFactory.getLogger(VPlexMeteringTest.class);

    @Test
    public void testListingPerpetualDataFilenames() {
        LinuxSystemCLI cli = new LinuxSystemCLI(HOST, USERNAME, PASSWORD);
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);

        List<String> filenames = listDataFileNamesCmd.getResults();

        Assert.assertFalse("Expected to find file names", filenames.isEmpty());
        out("Following files were found {}", filenames);

        Matcher matcher = Pattern.compile(".*?/([\\w\\-_]+)_PERPETUAL_vplex_sys_perf_mon.log").matcher(filenames.get(0));
        Assert.assertTrue("Expected filename matcher to match", matcher.matches());
        out("The director name for file '{}' is '{}'", filenames.get(0), matcher.group(1));
    }

    @Test
    public void testReadingDataFiles() {
        LinuxSystemCLI cli = new LinuxSystemCLI(HOST, USERNAME, PASSWORD);
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);

        List<String> filenames = listDataFileNamesCmd.getResults();
        Assert.assertFalse("Expected to find file names", filenames.isEmpty());

        for (String filename : filenames) {
            ReadAndParseVPlexPerpetualCSVFile readDataFile = new ReadAndParseVPlexPerpetualCSVFile(filename);
            cli.executeCommand(readDataFile);
            VPlexPerpetualCSVFileData fileData = readDataFile.getResults();
            Assert.assertNotNull("Expect file data to be non-null", fileData);
            out("For file {} these are the headers:\n{}", filename, Joiner.on('\n').join(fileData.getHeaders()));
            List<Map<String, String>> dataLines = fileData.getDataLines();
            Assert.assertTrue("Expect file data to have data values", !dataLines.isEmpty());
            out("For file {} there are {} data lines", filename, dataLines.size());
            fileData.close();
        }
    }

    @Test
    public void testReadAndParseMetrics() {
        LinuxSystemCLI cli = new LinuxSystemCLI(HOST, USERNAME, PASSWORD);
        ListVPlexPerpetualCSVFileNames listDataFileNamesCmd = new ListVPlexPerpetualCSVFileNames();
        cli.executeCommand(listDataFileNamesCmd);
        List<String> fileNames = listDataFileNamesCmd.getResults();
        for (String fileName : fileNames) {
            ReadAndParseVPlexPerpetualCSVFile readDataFile = new ReadAndParseVPlexPerpetualCSVFile(fileName);
            cli.executeCommand(readDataFile);
            VPlexPerpetualCSVFileData fileData = readDataFile.getResults();
            // Read each data line from the file
            int processedLines = 0;
            int headerCount = fileData.getHeaders().size();
            for (Map<String, String> dataLine : fileData.getDataLines()) {
                // Extract the metrics and their values, translate them into ViPR statistics
                Set<String> keys = dataLine.keySet();
                Assert.assertTrue("Expected number of data keys to match headers", headerCount == keys.size());
                for (String metricName : keys) {
                    String value = dataLine.get(metricName);
                    // If the value is not "no data" for the value, then parse it...
                    if (!VPlexPerpetualCSVFileData.NO_DATA.equals(value)) {
                        readAndParseMetrics(metricName, value);
                    }
                }
                processedLines++;
                alreadyPrinted = true;
            }
            Assert.assertTrue("Data lines processed does not match total expected", fileData.getTotalLines() == (processedLines + 1));
            // Clean up fileData resources
            fileData.close();
        }
    }

    private void readAndParseMetrics(String metric, String value) {
        if (alreadyPrinted) {
            return;
        }
        Pattern pattern = Pattern.compile("([\\w+\\-\\.]+)\\s+([\\w+\\-]*+)\\s*\\(([\\w+/%]+)\\)");
        Matcher matcher = pattern.matcher(metric);
        if (matcher.matches()) {
            String name = matcher.group(1);
            String qualifier = matcher.group(2);
            String units = matcher.group(3);
            if (Strings.isNullOrEmpty(qualifier)) {
                qualifier = "N/A";
            }
            out("metric = {}, name = {} qualifier = {} units = {} --> {}", metric, name, qualifier, units, value);
        }
    }

    @Test
    public void testVPlexPerpetualCSVFileCollector() throws InstantiationException, IllegalAccessException {
        MockDbClient mockDbClient = new MockDbClient();

        StorageSystem storageSystem = mockStorageSystem("vplex-1", "000123ABC000XYZ");
        mockDbClient.MOCK_DB.put(storageSystem.getId(), storageSystem);

        int directorIndex = 1;
        for (String directorName : DIRECTORS) {
            int adapterIndex = (directorIndex % 2);
            String aOrB = directorName.substring(directorName.length() - 1);
            String adapterSerial = String.format("CF23K00000%d%d", directorIndex, adapterIndex);
            StorageHADomain director = mockVPlexAdapter(storageSystem, directorName, adapterSerial);
            mockDbClient.MOCK_DB.put(director.getId(), director);
            for (int cpuIndex = 0; cpuIndex < 2; cpuIndex++) {
                for (int portIndex = 0; portIndex < PORT_PER_DIRECTOR; portIndex++) {
                    String portName = String.format("%s%d-FC%02d", aOrB, cpuIndex, portIndex);
                    String wwn = String.format("5C:CC:DD:EE:FF:00:%02d:%02d", directorIndex, portIndex);
                    StoragePort port = mockStoragePort(director, portName, wwn, adapterSerial);
                    mockDbClient.MOCK_DB.put(port.getId(), port);
                }
            }
            directorIndex++;
        }

        AccessProfile accessProfile = new AccessProfile();
        accessProfile.setIpAddress(HOST);
        accessProfile.setUserName(USERNAME);
        accessProfile.setPassword(PASSWORD);
        accessProfile.setSystemId(storageSystem.getId());

        Map<String, Object> context = new HashMap<>();
        context.put(Constants.dbClient, mockDbClient);

        VPlexPerpetualCSVFileCollector collector = new VPlexPerpetualCSVFileCollector();
        PortMetricsProcessor portMetricsProcessor = mockPortMetricsProcessor(mockDbClient);
        collector.setPortMetricsProcessor(portMetricsProcessor);
        collector.collect(accessProfile, context);
    }

    private PortMetricsProcessor mockPortMetricsProcessor(MockDbClient mockDbClient) {
        CustomConfigHandler customConfigHandler = new MockCustomConfigHandler();
        customConfigHandler.setDbClient(mockDbClient);
        PortMetricsProcessor portMetricsProcessor = new PortMetricsProcessor();
        portMetricsProcessor.setDbClient(mockDbClient);
        portMetricsProcessor.setCustomConfigHandler(customConfigHandler);
        return portMetricsProcessor;
    }

    private StorageHADomain mockVPlexAdapter(StorageSystem storageSystem, String name, String serialNumber)
            throws InstantiationException, IllegalAccessException {
        StorageHADomain director = mockObject(StorageHADomain.class, name);
        director.setAdapterName(name);
        director.setName(name);
        director.setSerialNumber(serialNumber);
        director.setProtocol(StoragePort.TransportType.FC.name());
        director.setNumberofPorts("8");
        director.setNativeGuid(String.format("%s:+ADAPTER+%s", storageSystem.getNativeGuid(), serialNumber));
        director.setStorageDeviceURI(storageSystem.getId());
        return director;
    }

    private StorageSystem mockStorageSystem(String name, String serialNumber) throws InstantiationException, IllegalAccessException {
        StorageSystem storageSystem = mockObject(StorageSystem.class, name);
        storageSystem.setSerialNumber(serialNumber);
        storageSystem.setNativeGuid(String.format("VPLEX+%s", serialNumber));
        storageSystem.setSystemType(DiscoveredDataObject.Type.vplex.name());
        return storageSystem;
    }

    private StoragePort mockStoragePort(StorageHADomain director, String name, String wwn, String serialNumber)
            throws InstantiationException, IllegalAccessException {
        StoragePort port = mockObject(StoragePort.class, name);
        port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        port.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());
        port.setLabel(String.format("%s+PORT+%s", director.getNativeGuid(), wwn));
        port.setNativeGuid(String.format("%s+PORT+%s", director.getNativeGuid(), wwn));
        port.setPortGroup(director.getAdapterName());
        port.setPortName(name);
        port.setPortNetworkId(wwn);
        port.setPortSpeed(8L);
        port.setPortType(StoragePort.PortType.frontend.name());
        port.setStorageDevice(director.getStorageDeviceURI());
        port.setStorageHADomain(director.getId());
        port.setTransportType(StoragePort.TransportType.FC.name());
        return port;
    }

    private <T extends DataObject> T mockObject(Class<T> clazz, String name) throws IllegalAccessException, InstantiationException {
        URI uri = URI.create(String.format("%s:%s", clazz.getSimpleName(), name));
        DataObject object = clazz.newInstance();
        object.setId(uri);
        object.setLabel(name);
        return (T) object;
    }

    private void out(String format, Object... args) {
        if (outputToLog) {
            log.info(format, args);
        } else {
            String modFormat = format.replaceAll("\\{\\}", "%s");
            System.out.println(String.format(modFormat, args));
        }
    }

    /**
     * Used for mocking up DataObjects used by the collector: StorageSystem, StorageHADomain, and StoragePort.
     */
    static private class MockDbClient implements DbClient {
        private Map<URI, DataObject> MOCK_DB = new HashMap<>();

        @Override
        public DataObject queryObject(URI id) {
            return null;
        }

        @Override
        public <T extends DataObject> T queryObject(Class<T> clazz, URI id) {
            return (T) MOCK_DB.get(id);
        }

        @Override
        public <T extends DataObject> T queryObject(Class<T> clazz, NamedURI id) {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids) {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, Collection<URI> ids, boolean activeOnly) {
            return null;
        }

        @Override
        public <T extends DataObject> List<T> queryObject(Class<T> clazz, URI... id) {
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> id) {
            List<T> list = new ArrayList<>();
            for (URI uri : id) {
                T object = (T) MOCK_DB.get(uri);
                if (object != null) {
                    list.add(object);
                }
            }
            return list.iterator();
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjects(Class<T> clazz, Collection<URI> ids, boolean activeOnly) {
            List<T> list = new ArrayList<>();
            Iterator<URI> iterator = ids.iterator();
            while (iterator.hasNext()) {
                URI uri = iterator.next();
                T object = (T) MOCK_DB.get(uri);
                if (object != null) {
                    list.add(object);
                }
            }
            return list.iterator();
        }

        @Override
        public <T extends DataObject> List<T> queryObjectField(Class<T> clazz, String fieldName, Collection<URI> ids) {
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjectField(Class<T> clazz, String fieldName, Collection<URI> ids) {
            return null;
        }

        @Override
        public <T extends DataObject> void aggregateObjectField(Class<T> clazz, Iterator<URI> ids, DbAggregatorItf aggregator) {

        }

        @Override
        public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly) {
            return null;
        }

        @Override
        public <T extends DataObject> List<URI> queryByType(Class<T> clazz, boolean activeOnly, URI startId, int count) {
            return null;
        }

        @Override
        public <T extends DataObject> void queryInactiveObjects(Class<T> clazz, long timeBefore, QueryResultList<URI> result) {

        }

        @Override
        public List<URI> queryByConstraint(Constraint constraint) {
            return null;
        }

        @Override
        public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result) {
            List<T> list = new ArrayList<>();
            Class<? extends DataObject> type = constraint.getDataObjectType();
            List<Object> objects = constraint.toConstraintDescriptor().getArguments();
            if (type.equals(StorageHADomain.class)) {
                // Looking for StorageHADomains that are associated with a particular StorageSystem URI
                for (DataObject dataObject : MOCK_DB.values()) {
                    if (dataObject instanceof StorageHADomain) {
                        T domain = (T) dataObject;
                        URI uri = (URI) objects.iterator().next();
                        if (((StorageHADomain) domain).getStorageDeviceURI().equals(uri)) {
                            list.add(result.createQueryHit(dataObject.getId()));
                        }
                    }
                }
            }
            if (type.equals(StoragePort.class)) {
                // Looking for StoragePorts associated with StorageHADomain
                for (DataObject dataObject : MOCK_DB.values()) {
                    if (dataObject instanceof StoragePort) {
                        T port = (T) dataObject;
                        URI uri = (URI) objects.iterator().next();
                        if (((StoragePort) port).getStorageHADomain().equals(uri)) {
                            list.add(result.createQueryHit(dataObject.getId()));
                        }
                    }
                }
            }
            result.setResult(list.iterator());
        }

        @Override
        public <T> void queryByConstraint(Constraint constraint, QueryResultList<T> result, URI startId, int maxCount) {

        }

        @Override
        public Integer countObjects(Class<? extends DataObject> type, String columnField, URI uri) {
            return null;
        }

        @Override
        public <T extends DataObject> void createObject(T object) {

        }

        @Override
        public <T extends DataObject> void createObject(Collection<T> objects) {

        }

        @Override
        public <T extends DataObject> void createObject(T... object) {

        }

        @Override
        public <T extends DataObject> void persistObject(T object) {

        }

        @Override
        public <T extends DataObject> void persistObject(Collection<T> objects) {

        }

        @Override
        public <T extends DataObject> void persistObject(T... object) {

        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(T object) {

        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(Collection<T> objects) {

        }

        @Override
        public <T extends DataObject> void updateAndReindexObject(T... object) {

        }

        @Override
        public <T extends DataObject> void updateObject(T object) {

        }

        @Override
        public <T extends DataObject> void updateObject(Collection<T> objects) {

        }

        @Override
        public <T extends DataObject> void updateObject(T... object) {

        }

        @Override
        public Operation ready(Class<? extends DataObject> clazz, URI id, String opId) {
            return null;
        }

        @Override
        public Operation ready(Class<? extends DataObject> clazz, URI id, String opId, String message) {
            return null;
        }

        @Override
        public Operation pending(Class<? extends DataObject> clazz, URI id, String opId, String message) {
            return null;
        }

        @Override
        public Operation error(Class<? extends DataObject> clazz, URI id, String opId, ServiceCoded serviceCoded) {
            return null;
        }

        @Override
        public void setStatus(Class<? extends DataObject> clazz, URI id, String opId, String status) {

        }

        @Override
        public void setStatus(Class<? extends DataObject> clazz, URI id, String opId, String status, String message) {

        }

        @Override
        public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId, Operation newOperation) {
            return null;
        }

        @Override
        public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId, ResourceOperationTypeEnum type) {
            return null;
        }

        @Override
        public Operation createTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId, ResourceOperationTypeEnum type,
                String associatedResources) {
            return null;
        }

        @Override
        public Operation updateTaskOpStatus(Class<? extends DataObject> clazz, URI id, String opId, Operation updateOperation) {
            return null;
        }

        @Override
        public void markForDeletion(DataObject object) {

        }

        @Override
        public void markForDeletion(Collection<? extends DataObject> objects) {

        }

        @Override
        public <T extends DataObject> void markForDeletion(T... object) {

        }

        @Override
        public void removeObject(DataObject... object) {

        }

        @Override
        public <T extends TimeSeriesSerializer.DataPoint> String insertTimeSeries(Class<? extends TimeSeries> tsType, T... data) {
            return null;
        }

        @Override
        public <T extends TimeSeriesSerializer.DataPoint> String insertTimeSeries(Class<? extends TimeSeries> tsType, DateTime time,
                T data) {
            return null;
        }

        @Override
        public <T extends TimeSeriesSerializer.DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType, DateTime timeBucket,
                TimeSeriesQueryResult<T> callback, ExecutorService workerThreads) {

        }

        @Override
        public <T extends TimeSeriesSerializer.DataPoint> void queryTimeSeries(Class<? extends TimeSeries> tsType, DateTime timeBucket,
                TimeSeriesMetadata.TimeBucket bucket, TimeSeriesQueryResult<T> callback, ExecutorService workerThreads) {

        }

        @Override
        public TimeSeriesMetadata queryTimeSeriesMetadata(Class<? extends TimeSeries> tsType) {
            return null;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public <T extends DataObject> Collection<T> queryObjectFields(Class<T> clazz, Collection<String> fieldNames, Collection<URI> ids) {
            return null;
        }

        @Override
        public <T extends DataObject> Iterator<T> queryIterativeObjectFields(Class<T> clazz, Collection<String> fieldNames,
                Collection<URI> ids) {
            return null;
        }

        @Override
        public String getSchemaVersion() {
            return null;
        }

        @Override
        public String getLocalShortVdcId() {
            return null;
        }

        @Override
        public URI getVdcUrn(String shortVdcId) {
            return null;
        }

        @Override
        public void invalidateVdcUrnCache() {

        }

        @Override
        public boolean checkGeoCompatible(String expectVersion) {
            return false;
        }

        @Override
        public boolean hasUsefulData() {
            return false;
        }
    }

    static private class MockCustomConfigHandler extends CustomConfigHandler {
        private Map<String, String> MOCK_CONFIG_DB = new HashMap<>();

        public MockCustomConfigHandler() {
            MOCK_CONFIG_DB.put(CustomConfigConstants.PORT_ALLOCATION_DAYS_TO_AVERAGE_UTILIZATION, "1");
            MOCK_CONFIG_DB.put(CustomConfigConstants.PORT_ALLOCATION_EMA_FACTOR, "0.6");
        }

        @Override
        public void setConfigResolvers(Map<String, CustomConfigResolver> configResolvers) {

        }

        @Override
        public void setConfigTypeProvider(CustomConfigTypeProvider configTypeProvider) {

        }

        @Override
        public String getCustomConfigValue(String configName, StringMap scope) throws CustomConfigControllerException {
            return "";
        }

        @Override
        public String getComputedCustomConfigValue(String name, StringMap scope, DataSource dataSource)
                throws CustomConfigControllerException {
            return "";
        }

        @Override
        public String getComputedCustomConfigValue(String name, String scope, DataSource sources) throws CustomConfigControllerException {
            return MOCK_CONFIG_DB.get(name);
        }

        @Override
        public String resolve(String name, String scope, DataSource dataSource) throws CustomConfigControllerException {
            return "";
        }

        @Override
        public void validate(String name, StringMap scope, String value, boolean isCheckDuplicate) {

        }

        @Override
        public String getCustomConfigPreviewValue(String name, String value, StringMap scope, Map<String, String> variables) {
            return "";
        }
    }
}
