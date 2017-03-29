/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;


import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.FileSystem;
import com.emc.nas.vnxfile.xmlapi.FileSystemCapacityInfo;
import com.emc.nas.vnxfile.xmlapi.FileSystemAutoExtInfo;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.vnx.xmlapi.VNXFileSystem;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 *
 */
public class VNXFileSystemsProcessor extends VNXFileProcessor {

    private final Logger _logger = LoggerFactory.getLogger(VNXFileSystemsProcessor.class);
    private final long MBTOBYTECONVERTER = 1024 * 1024;

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        final PostMethod result = (PostMethod) resultObj;
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller.unmarshal(result
                    .getResponseBodyAsStream());
            // Extract session information from the response header.
            Header[] headers = result.getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.CELERRA_SESSION, headers[0].getValue());
                _logger.info("Received celerra session info from the Server.");
            }

            Status status = null;
            if (null != responsePacket.getPacketFault()) {
                status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                List<Object> filesystemList = getQueryResponse(responsePacket);
                List<VNXFileSystem> processedFileSystems = processFilesystemList(filesystemList, keyMap);
                keyMap.put(VNXFileConstants.FILESYSTEMS, processedFileSystems);
                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx fileShare response due to {}",
                    ex.getMessage());
            keyMap.put(com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants.FAULT_DESC, ex.getMessage());
            keyMap.put(com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants.CMD_RESULT,
                    com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants.CMD_FAILURE);
        } finally {
            result.releaseConnection();
        }

    }

    /**
     * Process the fileSystemList which are received from XMLAPI server.
     * 
     * @param filesystemList : List of FileSystem objects.
     * @param keyMap : keyMap.
     */
    private List<VNXFileSystem> processFilesystemList(List<Object> filesystemList,
            Map<String, Object> keyMap) throws VNXFilePluginException {
        Iterator<Object> iterator = filesystemList.iterator();
        List<VNXFileSystem> processedFileSystem = new ArrayList<VNXFileSystem>();

        if (iterator.hasNext()) {
            Status status = (Status) iterator.next();
            if ((status.getMaxSeverity() == Severity.OK) ||
                    (status.getMaxSeverity() == Severity.WARNING)) {
                Map<String, FileSystem> fileSystems = new HashMap<String, FileSystem>();
                Map<String, FileSystemCapacityInfo> fileSysCapacityInfos = new HashMap<String, FileSystemCapacityInfo>();
                while (iterator.hasNext()) {
                    Object obj = iterator.next();
                    if (obj instanceof FileSystem) {
                        FileSystem fs = (FileSystem) obj;
                        if (fs.isInternalUse() == false) {
                            fileSystems.put(fs.getFileSystem(), fs);
                        }
                    } else if (obj instanceof FileSystemCapacityInfo) {
                        FileSystemCapacityInfo fsCapacityInfo = (FileSystemCapacityInfo) obj;
                        fileSysCapacityInfos.put(fsCapacityInfo.getFileSystem(), fsCapacityInfo);
                    }
                }
                Iterator it = fileSystems.keySet().iterator();
                while (it.hasNext()) {
                    String fsId = (String) it.next();
                    FileSystem fileSystem = fileSystems.get(fsId);

                    FileSystemCapacityInfo fsCapacity = fileSysCapacityInfos.get(fsId);
                    List<String> pools = new ArrayList<String>();
                    if (null != fileSystem.getStoragePools()) {
                        pools = fileSystem.getStoragePools();
                    }
                    String poolId = "";
                    if (null != pools) {
                        poolId = pools.get(0);
                    }
                    StringBuffer debugInfo = new StringBuffer();
                    debugInfo.append("VNXFileSystem : " + fileSystem.getName());
                    debugInfo.append(" Pool : " + poolId);
                    long totalCapacity = 0;
                    VNXFileSystem vnxFS = new VNXFileSystem(fileSystem.getName(), Integer.valueOf(fileSystem.getFileSystem()));
                    if (fileSystem.isVirtualProvisioning()) {
                        vnxFS.setType(UnManagedDiscoveredObject.SupportedProvisioningType.THIN.name());
                        FileSystemAutoExtInfo autoExtInfo = fileSystem.getFileSystemAutoExtInfo();
                        if (null != autoExtInfo) {
                            totalCapacity = autoExtInfo.getAutoExtensionMaxSize();
                            totalCapacity = totalCapacity * MBTOBYTECONVERTER;
                        } else {
                            totalCapacity = fsCapacity.getVolumeSize() * MBTOBYTECONVERTER;
                        }
                    } else {
                        vnxFS.setType(UnManagedDiscoveredObject.SupportedProvisioningType.THICK.name());
                        totalCapacity = fsCapacity.getVolumeSize() * MBTOBYTECONVERTER;
                    }
                    vnxFS.setStoragePool(poolId);
                    debugInfo.append(" fsCapacity.getVolumeSize() : " + fsCapacity.getVolumeSize());

                    vnxFS.setTotalCapacity(totalCapacity + "");
                    vnxFS.setUsedCapcity("0");
                    if (null != fsCapacity.getResourceUsage()) {
                        // We can get Used capacity
                        // Size is in MB, convert to Bytes
                        debugInfo.append(" fsCapacity.getUsedCapacity:" + fsCapacity.getResourceUsage().getSpaceUsed());
                        long usedCapacity = fsCapacity.getResourceUsage().getSpaceUsed() * MBTOBYTECONVERTER;
                        vnxFS.setUsedCapcity(usedCapacity + "");
                    }
                    debugInfo.append(" Total Capacity :" + vnxFS.getTotalCapacity());
                    debugInfo.append(" Used Capacity :" + vnxFS.getUsedCapacity());
                    _logger.debug(debugInfo.toString());
                    processedFileSystem.add(vnxFS);
                }
            } else {
                throw new com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException(
                        "Fault response received from XMLAPI Server.",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
        }

        return processedFileSystem;
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs) throws BaseCollectionException {

    }

}
