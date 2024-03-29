/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.FileSystem;
import com.emc.nas.vnxfile.xmlapi.MoverOrVdmRef;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

/**
 * VNXFileSystemMoverInfoProcessor is responsible to process the result received from XML API
 * Server after getting the one VNX FileSystem Information it process the mover associated to a file system
 */
public class VNXFileSystemMoverInfoProcessor extends VNXFileProcessor {

    /**
     * Logger instance.
     */
    private final Logger _logger = LoggerFactory
            .getLogger(VNXFileSystemMoverInfoProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
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
                List<Object> filesystemList = getQueryResponse(responsePacket);
                processFilesystemList(filesystemList, keyMap);
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the vnx fileShare response due to {}",
                    ex.getMessage());
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
    private void processFilesystemList(List<Object> filesystemList,
            Map<String, Object> keyMap) throws VNXFilePluginException {
        Iterator<Object> iterator = filesystemList.iterator();
        Set<String> moverIds = new HashSet<String>();
        if (iterator.hasNext()) {
            Status status = (Status) iterator.next();
            if (status.getMaxSeverity() == Severity.OK) {
                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
                while (iterator.hasNext()) {
                    FileSystem fileSystem = (FileSystem) iterator.next();
                    _logger.info("Processing result for file system {}", fileSystem.getName());
                    List<MoverOrVdmRef> roFileSysHosts = fileSystem.getRoFileSystemHosts();
                    if (null != roFileSysHosts) {
                        Iterator<MoverOrVdmRef> roFileSysHostItr = roFileSysHosts.iterator();
                        while (roFileSysHostItr.hasNext()) {
                            MoverOrVdmRef mover = roFileSysHostItr.next();
                            moverIds.add(mover.getMover());
                            _logger.debug("Mover id for read only host is {}", mover.getMover());
                        }
                    }
                    List<MoverOrVdmRef> rwFileSysHosts = fileSystem.getRwFileSystemHosts();
                    if (null != rwFileSysHosts) {
                        Iterator<MoverOrVdmRef> rwFileSysHostItr = rwFileSysHosts.iterator();
                        while (rwFileSysHostItr.hasNext()) {
                            MoverOrVdmRef mover = rwFileSysHostItr.next();
                            moverIds.add(mover.getMover());
                            _logger.debug("Mover id for read write host is {}", mover.getMover());
                        }
                    }
                }
            } else {
                keyMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_FAILURE);
                throw new VNXFilePluginException(
                        "Fault response received from XMLAPI Server.",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
        }
        keyMap.put(VNXFileConstants.MOVERLIST, moverIds);
    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }

}
