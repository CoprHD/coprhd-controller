package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

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

import com.emc.nas.vnxfile.xmlapi.FileSystem;
import com.emc.nas.vnxfile.xmlapi.Mount;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Severity;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFilePluginException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

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
                //process the mount list
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
        Map<String, Long> fsCapList = (HashMap<String, Long>)keyMap.get(VNXFileConstants.FILE_CAPACITY_MAP);
        Map<String, Map<String, Long>> snapCapFsMap =  
                (HashMap<String, Map<String, Long>> )keyMap.get(VNXFileConstants.SNAP_CAPACITY_MAP);
        
        List<String> fsList = null;
        Map<String, List<String>> fsMountvNASMap = new HashMap<String, List<String>>();
        Map<String, List<String>> fsMountPhyNASMap = new HashMap<String, List<String>>();
        
        Iterator<Object> iterator = mountList.iterator();
        if (iterator.hasNext()) {
            Status status = (Status) iterator.next();
            if (status.getMaxSeverity() == Severity.OK) {
                //get the filesystem list on mover
                while (iterator.hasNext()) {
                    Mount mount = (Mount) iterator.next();
                    
                    if( mount.isMoverIdIsVdm() == true) {
                        fsList = fsMountvNASMap.get(mount.getMover());
                        if(null == fsList) {
                            fsList = new ArrayList<String>();
                        }
                        fsList.add(mount.getFileSystem());
                        fsMountvNASMap.put(mount.getMover(), fsList);
                        _logger.info("call processMountList mount {} and is virtual {}", mount.getMover(), "true");
                        
                    } else {
                        fsList = fsMountPhyNASMap.get(mount.getMover());
                        if(null == fsList) {
                            fsList = new ArrayList<String>();
                        }
                        fsList.add(mount.getFileSystem());
                        fsMountPhyNASMap.put(mount.getMover(), fsList);
                        _logger.info("call processMountList mount {} and is virtual {}", mount.getMover(), "false");
                    }
                    
                    
                    _logger.info("mount fs object fs: {} and Mover: {}", mount.getFileSystem(), mount.getMover());
                    _logger.info("mount fs object fssize: {} and Mover: {}", String.valueOf(fsList.size()), mount.getMover());
                }
                
               
                //process the filesystems of VDM or DM
                StringMap mStringMap = null;
                
                if(!fsMountvNASMap.isEmpty()) {
                    VirtualNAS virtualNAS = null;
                    _logger.info("virtual mover size: {} ", String.valueOf(fsMountvNASMap.size()));
                    for (Entry<String, List<String>> entry : fsMountvNASMap.entrySet()) {
                        
                        String moverId = entry.getKey();
                        
//                        mStringMap = virtualNAS.getMetrics();
//                        if(null == mStringMap) {
//                            mStringMap = new StringMap();
//                        }
//                        prepareDBMetrics(fsList, fsCapList, snapCapFsMap, mStringMap);
//                        dbClient.persistObject(virtualNAS);
                        
                    }
                }
                //physical nas
                if(!fsMountPhyNASMap.isEmpty()) {
                    PhysicalNAS physicalNAS = null;
                    _logger.info("virtual mover size: {} ", String.valueOf(fsMountPhyNASMap.size()));
                    PhysicalNAS phyNAS = null;
                    for (Entry<String, List<String>> entry : fsMountPhyNASMap.entrySet()) {
                        
                        String moverId = entry.getKey();
                        
//                        mStringMap = physicalNAS.getMetrics();
//                        if(null == mStringMap) {
//                            mStringMap = new StringMap();
//                        }
//                        prepareDBMetrics(fsList, fsCapList, snapCapFsMap, mStringMap);
//                        dbClient.persistObject(physicalNAS);
                    }
                }
                
            } else {
                throw new VNXFilePluginException(
                        "Fault response received from XMLAPI Server.",
                        VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
            }
        }

    }
    /**
     * get the db metrics for each data mover or VDM
     * @param fsList
     * @param fsCapList
     * @param snapCapFsMap
     * @param dbMetrics
     */
    private void prepareDBMetrics(List<String> fsList, 
            final Map<String, Long> fsCapList, 
            final Map<String, Map<String, Long>> snapCapFsMap, StringMap dbMetrics) {
        
        //get the demetrics
        Long totalFSCap = 0L; //in KB
        Long totalSnapCap = 0L;
        long fsCount = 0;
        long snapCount = 0;
        
        Map<String, Long> snapCapMap = null;
        //list of fs system on data mover or vdm
        if(fsList != null && !fsList.isEmpty()) {
            for(String fsId: fsList) {
                //no of snapshot
                snapCapMap = (Map<String, Long>)snapCapFsMap.get(fsId);
                if(snapCapMap  != null && !snapCapMap.isEmpty()) {
                    snapCount = snapCount + snapCapMap.size();
                    for (Entry<String, Long> snapCapacity : snapCapMap.entrySet()) {
                        totalSnapCap = totalSnapCap + snapCapacity.getValue();
                    }
                }
                //file system capacity
                totalFSCap = totalFSCap + (Long)fsCapList.get(fsId);
            }
        }
        //no of fs on given mover
        fsCount = fsCapList.size();
        
        // set the values dbMetrics
        long totalObjects = fsCount + snapCount;
        long totalCap = totalFSCap + totalSnapCap;
        
    
        return;
    }
  
}
