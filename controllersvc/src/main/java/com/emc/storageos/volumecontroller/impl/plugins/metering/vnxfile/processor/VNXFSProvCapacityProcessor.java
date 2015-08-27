package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.PostMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.FileSystemCapacityInfo;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXFSProvCapacityProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXFSProvCapacityProcessor.class);

    @Override
    public void processResult(Operation operation, Object resultObj, Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub
        final PostMethod result = (PostMethod) resultObj;
        _logger.info("processing filesystem capacity response" + resultObj);

        try {
            String moverId = (String) keyMap.get(VNXFileConstants.MOVER_ID);
            Map<String, Long> fileSystemCapacityMap = new HashMap<String, Long>();

            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            List<Object> queryResponse = getQueryResponse(responsePacket);
            Iterator<Object> queryRespItr = queryResponse.iterator();

            while (queryRespItr.hasNext()) {
                Object responseObj = queryRespItr.next();
                if (responseObj instanceof FileSystemCapacityInfo) {
                    FileSystemCapacityInfo fsCapacityInfo = (FileSystemCapacityInfo) responseObj;
                    fileSystemCapacityMap.put(fsCapacityInfo.getFileSystem(), fsCapacityInfo.getVolumeSize());
                }
            }
            _logger.info("Number of FileSystem found {} on Data mover- : {}", fileSystemCapacityMap.size(), moverId);
            keyMap.put(VNXFileConstants.FILE_CAPACITY_MAP, fileSystemCapacityMap);
            // Extract session information from the response header.
            Header[] headers = result
                    .getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
            if (null != headers && headers.length > 0) {
                keyMap.put(VNXFileConstants.CELERRA_SESSION,
                        headers[0].getValue());
                _logger.info("Recieved celerra session information from the Server.");
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the filesystem capacity response due to {}",
                    ex.getMessage());
        } finally {
            result.releaseConnection();
        }

    }

}
