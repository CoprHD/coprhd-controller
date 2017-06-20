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

import com.emc.nas.vnxfile.xmlapi.FileSystemCapacityInfo;
import com.emc.nas.vnxfile.xmlapi.FileSystemCapacityInfo.ResourceUsage;
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
public class VNXFileSystemUsedSpaceProcessor extends VNXFileProcessor {

    private final Logger _logger = LoggerFactory.getLogger(VNXFileSystemUsedSpaceProcessor.class);

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

        Iterator<Object> iterator = filesystemList.iterator();
        while (iterator.hasNext()) {
            Status status = (Status) filesystemList.get(0);
            if (status.getMaxSeverity() == Severity.OK) {
                if (iterator.hasNext()) {
                    // This will give file system info we need to iterate once more
                    iterator.next();
                    if (iterator.hasNext()) {
                        FileSystemCapacityInfo info = (FileSystemCapacityInfo) iterator.next();
                        ResourceUsage resourceUsage = info.getResourceUsage();

                        if (resourceUsage != null) {

                            _logger.info("Size of file system: is  {}", resourceUsage.getSpaceUsed());

                            keyMap.put(VNXFileConstants.FILESYSTEM_USED_SPACE, resourceUsage.getSpaceUsed());
                            break;
                        }
                    }
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
