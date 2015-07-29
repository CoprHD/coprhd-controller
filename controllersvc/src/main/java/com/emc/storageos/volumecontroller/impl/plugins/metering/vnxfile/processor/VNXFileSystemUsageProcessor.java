/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.io.IOException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.nas.vnxfile.xmlapi.FileSystemSetUsageStats;
import com.emc.nas.vnxfile.xmlapi.FileSystemSetUsageStats.Item;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.Status.Problem;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

/**
 * FileshareUsageProcessor responsible to process the response received from the
 * XMLAPI server and parse the stream and populates the java objects.
 */
public class VNXFileSystemUsageProcessor extends VNXFileProcessor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory
            .getLogger(VNXFileSystemUsageProcessor.class);

    private ZeroRecordGenerator _zeroRecordGenerator;

    private CassandraInsertion _statsColumnInjector;

    @SuppressWarnings("unchecked")
    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.info("processing fileshare usage response" + resultObj);
        final PostMethod result = (PostMethod) resultObj;
        try {
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                List<Problem> problems = status.getProblem();
                Iterator<Problem> problemsItr = problems.iterator();
                while (problemsItr.hasNext()) {
                    Problem problem = problemsItr.next();
                    _logger.error(
                            "Fault response received due to {} possible cause {}",
                            problem.getDescription(), problem.getDiagnostics());
                }
            } else {
                List<Object> fsUsageInfo = getQueryStatsResponse(responsePacket);
                final List<Stat> statList = (List<Stat>) keyMap
                        .get(Constants._Stats);
                processFileShareInfo(fsUsageInfo, keyMap, statList, dbClient);
                _zeroRecordGenerator.identifyRecordstobeZeroed(keyMap, statList,
                        FileShare.class);
            }

        } catch (final IOException ioEx) {
            _logger.error(
                    "IOException occurred while processing the Fileshare capacity response due to {}",
                    ioEx.getMessage());
            throw new VNXFilePluginException(
                    "IOException occurred while processing the Fileshare capacity response.",
                    ioEx.getCause());
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the Fileshare capacity response due to {}",
                    ex.getMessage());
            throw new VNXFilePluginException(
                    "Exception occurred while processing the Fileshare capacity response.",
                    ex.getCause());
        } finally {
            result.releaseConnection();
        }
    }

    /**
     * process the FileShareUsage response of the VNX XML API Server.
     * 
     * @param fsUsageList
     *            : fileShareUsage map.
     * @param keyMap
     *            : attribute map.
     * @param statList
     *            : list of stat objects.
     */
    @SuppressWarnings("rawtypes")
    private void processFileShareInfo(final List<Object> fsUsageList, final Map<String, Object> keyMap,
            final List<Stat> statList, DbClient dbClient) throws VNXFilePluginException {

        final String serialId = keyMap.get(Constants._serialID).toString();
        Iterator iterator = fsUsageList.iterator();
        keyMap.put(Constants._TimeCollected, System.currentTimeMillis());
        while (iterator.hasNext()) {
            FileSystemSetUsageStats fsSetUsageStats = (FileSystemSetUsageStats) iterator.next();
            List<Item> fsUsageItems = fsSetUsageStats.getItem();
            _logger.info("Received {} fileShareUsage records at server time {}", fsUsageItems.size(),
                    fsSetUsageStats.getTime());
            for (Item item : fsUsageItems) {
                if (null == item.getFileSystem()) {
                    continue;
                }
                final String nativeGuid = NativeGUIDGenerator.generateNativeGuid(Type.vnxfile.toString(), serialId,
                        item.getFileSystem());
                Stat stat = _zeroRecordGenerator.injectattr(keyMap, nativeGuid, null);
                if (null != stat) {
                    stat.setTimeInMillis((Long) keyMap.get(Constants._TimeCollected));
                    stat.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));
                    injectProvisionedCapacity(stat, keyMap);
                    // The data coming in is in KB. Converting to Bytes
                    stat.setAllocatedCapacity(item.getSpaceUsed() * 1024);
                    _statsColumnInjector.injectColumns(stat, dbClient);
                    statList.add(stat);
                    // Persists the file system, only if change in used capacity.
                    DbClient client = (DbClient) keyMap.get(Constants.dbClient);
                    if (client != null) {
                        FileShare fileSystem = client.queryObject(FileShare.class, stat.getResourceId());
                        if (fileSystem != null) {
                            if (!fileSystem.getInactive() && fileSystem.getUsedCapacity() != stat.getAllocatedCapacity()) {
                                fileSystem.setUsedCapacity(stat.getAllocatedCapacity());
                                client.persistObject(fileSystem);
                            }
                        }
                    }
                }
            }
        }
        _logger.info("No. of stat objects: {}", statList.size());
    }

    /**
     * injects the ProvisionedCapacity from provisioning capacity.
     * 
     * @param stat
     * @param keyMap
     */
    private void injectProvisionedCapacity(final Stat stat, final Map<String, Object> keyMap) {
        final DbClient dbClient = (DbClient) keyMap.get(VNXFileConstants.DBCLIENT);
        try {
            final FileShare fileObj = dbClient.queryObject(FileShare.class, stat.getResourceId());
            _logger.info("injectProvisioned Capacity existing {} from File System {}", stat.getProvisionedCapacity(), fileObj.getCapacity());
            stat.setProvisionedCapacity(fileObj.getCapacity());
        } catch (final Exception e) {
            _logger.error("No FileShare found using resource {}", stat.getResourceId());
        }

    }

    // @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws VNXFilePluginException {
    }

    /**
     * set the cachesyncher.
     * 
     * @param cachesync
     */
    public void setZeroRecordGenerator(ZeroRecordGenerator recordGenerator) {
        _zeroRecordGenerator = recordGenerator;
    }

    public void setStatsColumnInjector(CassandraInsertion statsColumnInjector) {
        _statsColumnInjector = statsColumnInjector;
    }

    public CassandraInsertion getStatsColumnInjector() {
        return _statsColumnInjector;
    }
}
