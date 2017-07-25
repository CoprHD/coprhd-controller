/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.FileSystem;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

/**
 *
 */
public class VNXFileSystemIdProcessor extends VNXFileProcessor {

    private final Logger _logger = LoggerFactory.getLogger(VNXFileSystemIdProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj,
            Map<String, Object> keyMap) throws BaseCollectionException {
        _logger.info("Processing VNX File System ID Query response: {}", resultObj);
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

    }

    /**
     * Process the fileSystemList which are received from XMLAPI server.
     * 
     * @param filesystemList : List of FileSystem objects.
     * @param keyMap : keyMap.
     */
    private void processFilesystemList(List<Object> filesystemList,
            Map<String, Object> keyMap) throws VNXFilePluginException {

        String fsName = (String) keyMap.get(VNXFileConstants.FILESYSTEM_NAME);
        String fsId = (String) keyMap.get(VNXFileConstants.FILESYSTEM_ID);
        boolean foundId = false;

        Iterator<Object> iterator = filesystemList.iterator();
        if (iterator.hasNext()) {
            Status status = (Status) iterator.next();
            if (status.getMaxSeverity() == Severity.OK) {
                while (iterator.hasNext()) {
                    FileSystem fileSystem = (FileSystem) iterator.next();

                    if (fileSystem.getName().equals(fsName) ||
                            fileSystem.getFileSystem().equals(fsId)) {
                        String id = fileSystem.getFileSystem();
                        _logger.info("Found matching file system: {}", id);
                        keyMap.put(VNXFileConstants.FILESYSTEM_ID, id);
                        keyMap.put(VNXFileConstants.FILESYSTEM, fileSystem);
                        keyMap.put(VNXFileConstants.IS_FILESYSTEM_AVAILABLE_ON_ARRAY, Boolean.TRUE);
                        foundId = true;
                        break;
                    }
                }

                if (!foundId) {
                    _logger.error("Did not find file system ID for {}", fsName);
                    keyMap.put(VNXFileConstants.IS_FILESYSTEM_AVAILABLE_ON_ARRAY, Boolean.FALSE);
                }

            } else {
                throw new VNXFilePluginException(
                        "Fault response received from XMLAPI Server.",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
        }

    }

    @Override
    protected void setPrerequisiteObjects(List<Object> inputArgs)
            throws BaseCollectionException {

    }

}
