/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor;

import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMArgument;
import javax.cim.CIMDataType;
import javax.cim.CIMObjectPath;
import javax.cim.CIMProperty;
import java.util.List;
import java.util.Map;

/**
 * VolumeProcessor used in retrieving ReadIOs,WriteIOs,nativeGuid
 */
public class VolumeProcessor extends CommonStatsProcessor {
    /*
     * Getting CIMObjectPaths for Volumes, is a single SMI-S Call, and if 1000s
     * of volumes are there, the time taken to retrieve them is huge, and its
     * going to impact performance a lot too.Hence, manually at runtime using
     * the volume IDs got from Statistics, I have created Volume CIMObjectPaths,
     * rather than getting it from Provider. Its a huge performance gain, but
     * the down side is having to identify the type, i.e. Symmetrix or Clariion
     * at runtime.
     * 
     * To-Do: Currently,Volume Instances, paths got from Providers are kept in
     * Memory, and are short lived till the job dies.But if really it becomes a
     * scale problem,need to decide on using Native Heaps solutions like
     * EhCache, Memcache.
     */
    private Logger _logger = LoggerFactory.getLogger(VolumeProcessor.class);

    private CassandraInsertion _statsColumnInjector;

    public static enum VolumeMetric
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
        IdleTimeCounter,
        IOTimeCounter,
        EMCQueueLength;

        private static final VolumeMetric[] metricCopyOfValues = values();

        public static VolumeMetric lookup(String name) {
            for (VolumeMetric value : metricCopyOfValues) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return UnKnown;
        }
    }

    @Override
    public void processResult(
            Operation operation, Object resultObj, Map<String, Object> keyMap)
            throws SMIPluginException {
        long timeinMilis;
        try {
            timeinMilis = (Long) keyMap.get(Constants._TimeCollected);
            CIMArgument<?>[] outputArguments = (CIMArgument<?>[]) resultObj;
            @SuppressWarnings("unchecked")
            List<Stat> metricsObjList = (List<Stat>) keyMap.get(Constants._Stats);
            DbClient dbClient = (DbClient) keyMap.get(Constants.dbClient);
            if ((null == outputArguments[0]) ||
                    ((String[]) outputArguments[0].getValue()).length == 0) {
                _logger.warn("Empty Statistics response returned from Provider");
                return;
            }
            String[] volumes = ((String[]) outputArguments[0].getValue())[0].split("\n");
            List<String> metricSequence = (List<String>) keyMap.get(Constants.STORAGEOS_VOLUME_MANIFEST);
            _logger.debug("volume metricNames Sequence {}", metricSequence);
            for (String volume : volumes) {
                if (volume.isEmpty()) {
                    _logger.debug("Empty Volume returned as part of Statistics Response");
                    continue;
                }
                if (null != metricSequence && !metricSequence.isEmpty()) {
                    addMetricstoMetricsObject(keyMap, volume, metricsObjList, timeinMilis, metricSequence, dbClient);
                } else {
                    _logger.error("failed processing Volume Metric values as metric sequence is null.");
                }
            }
            _zeroRecordGenerator.identifyRecordstobeZeroed(keyMap, metricsObjList, Volume.class);
        } catch (Exception e) {
            _logger.error(
                    "Failed while extracting Read & WriteIOs for Volumes : ", e);
        }
        resultObj = null;
    }

    /**
     * Create CIMObjectPath using the nativeGUID got from Statistics Call.
     * 
     * @param nativeGUID
     * @param symmsystem
     * @param symmvolume
     * @return CIMObjectPath
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private CIMObjectPath createCIMPath(
            String nativeGUID, String volume, String system, Map<String, Object> keyMap) {
        String[] tokens = nativeGUID.split(Constants.PATH_DELIMITER_REGEX);
        String SystemName = tokens[0] + Constants._plusDelimiter + tokens[1];
        String deviceID = tokens[3];
        // CLARIION+APM00120400480+VOLUME+UID+600601605B1029004B318218D588E111 -
        // Snapshot targets format
        if (tokens.length > 4) {
            for (int i = 4; i < tokens.length; i++) {
                deviceID = deviceID + "+" + tokens[i];
            }
        }

        CIMProperty<?> CreationClassName = new CIMProperty(CreationClassNamestr,
                CIMDataType.STRING_T, volume, true, false, null);
        CIMProperty<?> SystemCreationClassName = new CIMProperty(
                SystemCreationClassNamestr, CIMDataType.STRING_T, system, true, false, null);
        CIMProperty<?> systemName = new CIMProperty(SystemNamestr, CIMDataType.STRING_T,
                SystemName, true, false, null);
        CIMProperty<?> Id = new CIMProperty(DeviceIDstr, CIMDataType.STRING_T, deviceID, true, false, null);
        CIMProperty<?>[] keys = new CIMProperty<?>[4];
        keys[0] = CreationClassName;
        keys[1] = SystemCreationClassName;
        keys[2] = systemName;
        keys[3] = Id;
        // To-DO : "root/emc - get it from outside"
        return CimObjectPathCreator.createInstance(volume, keyMap.get(Constants._InteropNamespace)
                .toString(), keys);

    }

    /**
     * Parse NativeGuid, create Volume CIMObjectPaths by using nativeGuids. Get
     * the associated Metrics Object for each Volume, update with
     * ReadIOs,WriteIOs, guid content.
     * 
     * string CSVSequence[] = InstanceID,
     * ElementType, TotalIOs, KBytesTransferred, ReadIOs, KBytesRead, WriteIOs,
     * KBytesWritten;
     * 
     * @param keyMap
     * @param volume
     * @param metricsObjList
     * @throws SMIPluginException
     */
    private void addMetricstoMetricsObject(
            Map<String, Object> keyMap, String volume, List<Stat> metricsObjList,
            long timeinMillis, List<String> metricSequence, DbClient dbClient) throws SMIPluginException {
        String nativeGuid = null;
        try {
            _logger.debug("Volumes :" + volume);
            Iterable<String> splitIterator = Splitter.on(Constants.SEMI_COLON).split(volume);
            List<String> metricValuesList = Lists.newLinkedList(splitIterator);
            // translated Attributes is needed to create CIMObject at runtime
            // without querying it from Provider,
            // which increases performance a lot, by reducing SMI-S calls
            nativeGuid = translatedAttributes(metricValuesList.get(0).toUpperCase(), keyMap);
            CIMObjectPath path = null;
            if (nativeGuid.contains(_symm)) {
                path = createCIMPath(nativeGuid, _symmvolume, _symmsystem, keyMap);
            } else if (nativeGuid.contains(_clar)) {
                path = createCIMPath(nativeGuid, _clarvolume, _clarsystem, keyMap);
            }
            // Inject Project, COS, tenant ID
            Stat statObj = _zeroRecordGenerator.injectattr(keyMap, nativeGuid, null);
            if (statObj != null) {
                @SuppressWarnings("unchecked")
                List<CIMObjectPath> volList = (List<CIMObjectPath>) keyMap.get(Constants._Volumes);
                volList.add(path);
                statObj.setServiceType(Constants._Block);
                statObj.setTimeCollected((Long) keyMap.get(Constants._TimeCollected));
                statObj.setTimeInMillis(timeinMillis);
                _statsColumnInjector.injectColumns(statObj, dbClient);
                // Default Capacity in Model is -1. As snapshotCount and
                // capacity is cumulative Count of multiple Snapshots, making to
                // default 0
                // for each retrieved Volume.
                int count = 0;
                for (String metricName : metricSequence) {
                    String metricValue = metricValuesList.get(count);
                    switch (VolumeMetric.lookup(metricName)) {
                        case InstanceID:
                        case ElementType:
                            count++;
                            break;
                        case KBytesWritten:
                            statObj.setBandwidthIn(ControllerUtils.getLongValue(metricValue));
                            count++;
                            break;
                        case KBytesRead:
                            statObj.setBandwidthOut(ControllerUtils.getLongValue(metricValue));
                            count++;
                            break;
                        case TotalIOs:
                            statObj.setTotalIOs(ControllerUtils.getLongValue(metricValue));
                            count++;
                            break;
                        case ReadIOs:
                            statObj.setReadIOs(ControllerUtils.getLongValue(metricValue));
                            count++;
                            break;
                        case WriteIOs:
                            statObj.setWriteIOs(ControllerUtils.getLongValue(metricValue));
                            count++;
                            break;
                        case KBytesTransferred:
                            statObj.setKbytesTransferred(ControllerUtils.getLongValue(metricValue));
                            count++;
                            break;
                        case IdleTimeCounter:
                            if (null != metricValue && 0 < metricValue.trim().length()) {
                                statObj.setIdleTimeCounter(ControllerUtils.getLongValue(metricValue));
                            }
                            count++;
                            break;
                        case IOTimeCounter:
                            if (null != metricValue && 0 < metricValue.trim().length()) {
                                statObj.setIoTimeCounter(ControllerUtils.getLongValue(metricValue));
                            }
                            count++;
                            break;
                        case EMCQueueLength:
                            if (null != metricValue && 0 < metricValue.trim().length()) {
                                statObj.setQueueLength(ControllerUtils.getLongValue(metricValue));
                            }
                            count++;
                            break;
                        default:
                            _logger.warn("Ignoring unknown metric {} during system metric processing:", metricName);
                            count++;
                            break;
                    }
                }
                metricsObjList.add(statObj);
            }
        } catch (Exception ex) {
            _logger.error("Processing Volume : {} failed : ", volume, ex);
        }
    }

    public void setStatsColumnInjector(CassandraInsertion statsColumnInjector) {
        _statsColumnInjector = statsColumnInjector;
    }

    public CassandraInsertion getStatsColumnInjector() {
        return _statsColumnInjector;
    }
}
