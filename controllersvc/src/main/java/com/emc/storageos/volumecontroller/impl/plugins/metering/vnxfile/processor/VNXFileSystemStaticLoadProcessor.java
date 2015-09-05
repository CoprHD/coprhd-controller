/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.Mount;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

/**
 * VNXFileSystemStaticLoadProcessor is responsible to process the result received from XML API
 * Server during VNX File System Static load stream processing.
 * 
 */
public class VNXFileSystemStaticLoadProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXFileSystemStaticLoadProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub
        _logger.info("Processing VNX Mount Query response: {}", resultObj);
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            // Extract session information from the response header.
            Header[] headers = result
                    .getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.CELERRA_SESSION,
                        headers[0].getValue());
                _logger.info("Received celerra session info from the Server.");
            }
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> mountList = getQueryResponse(responsePacket);
                // process the mount list
                processMountList(mountList, keyMap);
                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx fileShare response due to {}",
                    ex.getMessage());
            keyMap.put(VNXFileConstants.FAULT_DESC, ex.getMessage());
            keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_FAILURE);
        } finally {
            result.releaseConnection();
        }
        return;
    }

    /**
     * Process the mountList which are received from XMLAPI server.
     * 
     * @param mountList : List of Mount objects.
     * @param keyMap : keyMap.
     */
    private void processMountList(final List<Object> mountList,
            Map<String, Object> keyMap) throws VNXFilePluginException {
        _logger.info("call processMountList");

        final DbClient dbClient = (DbClient) keyMap.get(VNXFileConstants.DBCLIENT);
        Map<String, Long> fsCapList = (HashMap<String, Long>) keyMap.get(VNXFileConstants.FILE_CAPACITY_MAP);
        Map<String, Map<String, Long>> snapCapFsMap =
                (HashMap<String, Map<String, Long>>) keyMap.get(VNXFileConstants.SNAP_CAPACITY_MAP);
        AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
        // get the storagesystem from db
        StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, profile.getSystemId());

        // this map store DM to VDM list
        Map<String, List<String>> dmToVDM = new HashMap<String, List<String>>();

        List<String> fsList = null;
        Map<String, List<String>> fsMountvNASMap = new HashMap<String, List<String>>();
        Map<String, List<String>> fsMountPhyNASMap = new HashMap<String, List<String>>();

        Iterator<Object> iterator = mountList.iterator();
        if (iterator.hasNext()) {
            Status status = (Status) iterator.next();
            if (status.getMaxSeverity() == Severity.OK) {
                // get the filesystem list on mover
                while (iterator.hasNext()) {
                    Mount mount = (Mount) iterator.next();

                    if (mount.isMoverIdIsVdm() == true) {
                        fsList = fsMountvNASMap.get(mount.getMover());
                        if (null == fsList) {
                            fsList = new ArrayList<String>();
                        }
                        fsList.add(mount.getFileSystem());
                        fsMountvNASMap.put(mount.getMover(), fsList);
                        _logger.info("call processMountList mount {} and is virtual {}", mount.getMover(), "true");

                    } else {
                        fsList = fsMountPhyNASMap.get(mount.getMover());
                        if (null == fsList) {
                            fsList = new ArrayList<String>();
                        }
                        fsList.add(mount.getFileSystem());
                        fsMountPhyNASMap.put(mount.getMover(), fsList);
                        _logger.info("call processMountList mount {} and is virtual {}", mount.getMover(), "false");
                    }

                    _logger.info("mount fs object fsId: {} and Mover: {}", mount.getFileSystem(), mount.getMover());
                    _logger.info("mount fs object fssize: {} and Mover: {}", String.valueOf(fsList.size()), mount.getMover());
                }

                Map<PhysicalNAS, Integer> dmFsCountMap = new HashMap<PhysicalNAS, Integer>();
                // physical nas
                if (!fsMountPhyNASMap.isEmpty()) {
                    PhysicalNAS pNAS = null;
                    _logger.info("physical mover size: {} ", String.valueOf(fsMountPhyNASMap.size()));
                    for (String moverId : fsMountPhyNASMap.keySet()) {
                        // get NAS object from db

                        pNAS = findPhysicalNasByNativeId(storageSystem, dbClient, moverId);
                        URIQueryResultList virtualNASURI = new URIQueryResultList();
                        if (null != pNAS) {

                            dbClient.queryByConstraint(
                                    ContainmentConstraint.Factory.getVirtualNASByParentConstraint(pNAS.getId()), virtualNASURI);

                            Iterator<URI> virtualNASIter = virtualNASURI.iterator();
                            int fsCont = 0;
                            while (virtualNASIter.hasNext()) {
                                URI vNASURI = virtualNASIter.next();
                                VirtualNAS virtualNAS = dbClient.queryObject(VirtualNAS.class, vNASURI);
                                if (virtualNAS != null && !virtualNAS.getInactive()) {
                                    List<String> fileSystemList = fsMountvNASMap.get(virtualNAS.getNativeId());
                                    if (fileSystemList != null && !fileSystemList.isEmpty()) {
                                        fsCont = fsCont + fileSystemList.size();

                                        int snapCount = 0;
                                        for (String fsId : fileSystemList) {

                                            Map<String, Long> snapCapMap = snapCapFsMap.get(fsId);
                                            if (snapCapMap != null && !snapCapMap.isEmpty()) {

                                                snapCount = snapCount + snapCapMap.size();

                                            }

                                            fsCont = fsCont + snapCount;

                                        }
                                    }

                                }

                            }

                            // add total count of phyical Nas too
                            int dmSnapCount = 0;
                            List<String> fileSystemList = fsMountvNASMap.get(pNAS.getNativeId());
                            if (fileSystemList != null && !fileSystemList.isEmpty()) {

                                for (String fsId : fileSystemList) {

                                    Map<String, Long> snapCapMap = snapCapFsMap.get(fsId);
                                    if (snapCapMap != null && !snapCapMap.isEmpty()) {

                                        dmSnapCount = dmSnapCount + snapCapMap.size();

                                    }
                                    fsCont = fsCont + dmSnapCount;

                                }
                                fsCont = fsCont + fileSystemList.size();

                            }

                            dmFsCountMap.put(pNAS, new Integer(fsCont));
                        }
                    }
                }
                // process the filesystems of VDM or DM
                StringMap dbMetricsMap = null;

                if (!fsMountvNASMap.isEmpty()) {
                    VirtualNAS virtualNAS = null;
                    _logger.info("virtual mover size: {} ", String.valueOf(fsMountvNASMap.size()));
                    for (Entry<String, List<String>> entryMount : fsMountvNASMap.entrySet()) {

                        String moverId = entryMount.getKey();

                        // get vNAS object from db
                        virtualNAS = findvNasByNativeId(storageSystem, dbClient, moverId);
                        if (virtualNAS != null) {
                            dbMetricsMap = virtualNAS.getMetrics();
                            if (null == dbMetricsMap) {
                                dbMetricsMap = new StringMap();
                            }
                            // store the metrics in db
                            fsList = entryMount.getValue();
                            prepareDBMetrics(fsList, fsCapList, snapCapFsMap, virtualNAS, dmFsCountMap, dbClient);
                            dbClient.persistObject(virtualNAS);
                        } else {
                            _logger.info("virtual mover not present in ViPR db: {} ", moverId);
                        }

                    }
                }
                // physical nas
                if (!fsMountPhyNASMap.isEmpty()) {
                    PhysicalNAS physicalNAS = null;
                    _logger.info("physical mover size: {} ", String.valueOf(fsMountPhyNASMap.size()));
                    for (Entry<String, List<String>> entryMount : fsMountPhyNASMap.entrySet()) {

                        String moverId = entryMount.getKey();

                        // get NAS object from db
                        physicalNAS = findPhysicalNasByNativeId(storageSystem, dbClient, moverId);
                        if (null != physicalNAS) {
                            dbMetricsMap = physicalNAS.getMetrics();
                            if (null == dbMetricsMap) {
                                dbMetricsMap = new StringMap();
                            }
                            fsList = entryMount.getValue();
                            // store the metrics in db
                            prepareDBMetrics(fsList, fsCapList, snapCapFsMap, physicalNAS, dmFsCountMap, dbClient);
                            dbClient.persistObject(physicalNAS);

                        } else {
                            _logger.info("mover not present in ViPR db: {} ", moverId);
                        }

                    }

                } else {
                    throw new VNXFilePluginException(
                            "Fault response received from XMLAPI Server.",
                            VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
                }
            }
        }
        return;

    }

    /**
     * get the DB metrics for each data mover or VDM
     * 
     * @param fsList
     * @param fsCapList
     * @param snapCapFsMap
     * @param nasServer
     * @param dmFsCountMAP
     * @param dbClient
     */
    private void prepareDBMetrics(final List<String> fsList, final Map<String, Long> fsCapList,
            final Map<String, Map<String, Long>> snapCapFsMap, NASServer nasServer, Map<PhysicalNAS, Integer> dmFsCountMAP,
            DbClient dbClient) {
        // get the DB metrics
        long totalFSCap = 0; // in KB
        long totalSnapCap = 0;
        long fsCount = 0;
        long snapCount = 0;
        StringMap dbMetrics = nasServer.getMetrics();

        Map<String, Long> snapCapMap = null;
        // list of fs system on data mover or vdm
        if (fsList != null && !fsList.isEmpty()) {
            for (String fsId : fsList) {
                // get snaps of fs
                snapCapMap = snapCapFsMap.get(fsId);
                if (snapCapMap != null && !snapCapMap.isEmpty()) {

                    snapCount = snapCount + snapCapMap.size();
                    for (Entry<String, Long> snapCapacity : snapCapMap.entrySet()) {
                        totalSnapCap = totalSnapCap + snapCapacity.getValue();
                    }
                }
                // get file system capacity and add to total capacity
                totalFSCap = totalFSCap + fsCapList.get(fsId);
            }
        }
        // no. of fs on given mover
        fsCount = fsCapList.size();

        // set the values in dbMetrics
        long totalObjects = fsCount + snapCount;
        long loadFactor = 1;

        if (nasServer instanceof VirtualNAS) {
            long totalObjectOnDataMover = 0l;

            totalObjectOnDataMover = dmFsCountMAP.get(nasServer.getId());

            if (totalObjectOnDataMover > 0)
            {
                loadFactor = 1 - (totalObjects / totalObjectOnDataMover);
            }

        } else if (nasServer instanceof PhysicalNAS) {

            int totalObjectOnAllDataMover = 0;
            for (Entry<PhysicalNAS, Integer> mount : dmFsCountMAP.entrySet()) {

                totalObjectOnAllDataMover = totalObjectOnAllDataMover + mount.getValue();

            }
            if (totalObjectOnAllDataMover > 0)
            {
                loadFactor = 1 - (totalObjects / totalObjectOnAllDataMover);
            }

        }

        long totalCap = totalFSCap + totalSnapCap;

        dbMetrics.put(MetricsKeys.storageObjects.name(), String.valueOf(totalObjects));
        dbMetrics.put(MetricsKeys.storageCapacity.name(), String.valueOf(totalCap));
        dbMetrics.put(MetricsKeys.loadFactor.name(), String.valueOf(loadFactor));

        // set the over load metrics
        Long maxCapacity = MetricsKeys.getLong(MetricsKeys.maxStorageCapacity, dbMetrics);
        Long maxObjects = MetricsKeys.getLong(MetricsKeys.maxStorageObjects, dbMetrics);

        if (maxObjects == 0 || maxCapacity == 0) {
            dbMetrics.put(MetricsKeys.overLoaded.name(), "false");

        }
        else if (totalObjects >= maxObjects || totalCap >= maxCapacity) {
            dbMetrics.put(MetricsKeys.overLoaded.name(), "true");
        }
        return;
    }

    /**
     * find vNAS or VDM object from db using nativeId
     * 
     * @param system
     * @param dbClient
     * @param nativeId
     * @return
     */
    private VirtualNAS findvNasByNativeId(final StorageSystem system, DbClient dbClient, String nativeId) {
        URIQueryResultList results = new URIQueryResultList();
        VirtualNAS vNas = null;

        // Set storage port details to vNas
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, nativeId, NativeGUIDGenerator.VIRTUAL_NAS);

        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getVirtualNASByNativeGuidConstraint(nasNativeGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            VirtualNAS tmpVnas = dbClient.queryObject(VirtualNAS.class, iter.next());

            if (tmpVnas != null && !tmpVnas.getInactive()) {
                vNas = tmpVnas;
                _logger.info("found virtual NAS {}", tmpVnas.getNativeGuid() + ":" + tmpVnas.getNasName());
                break;
            }
        }
        return vNas;
    }

    /**
     * find DM or NAS from db using native id
     * 
     * @param system
     * @param dbClient
     * @param nativeId
     * @return
     */
    private PhysicalNAS findPhysicalNasByNativeId(final StorageSystem system, DbClient dbClient, String nativeId) {
        URIQueryResultList results = new URIQueryResultList();
        PhysicalNAS physicalNas = null;

        // Set storage port details to vNas
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, nativeId, NativeGUIDGenerator.PHYSICAL_NAS);

        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getPhysicalNasByNativeGuidConstraint(nasNativeGuid),
                results);

        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            PhysicalNAS tmpNas = dbClient.queryObject(PhysicalNAS.class, iter.next());

            if (tmpNas != null && !tmpNas.getInactive()) {
                physicalNas = tmpNas;
                _logger.info("found physical NAS {}", physicalNas.getNativeGuid() + ":" + physicalNas.getNasName());
                break;
            }
        }
        return physicalNas;
    }

}
