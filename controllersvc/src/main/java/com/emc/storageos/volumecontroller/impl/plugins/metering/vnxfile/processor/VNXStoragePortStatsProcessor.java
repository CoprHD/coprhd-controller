package com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.processor;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.net.URI;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.MoverNetStats;
import com.emc.nas.vnxfile.xmlapi.ResponsePacket;
import com.emc.nas.vnxfile.xmlapi.Status;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.domainmodel.Operation;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileProcessor;

public class VNXStoragePortStatsProcessor extends VNXFileProcessor{
    
    private final Logger _logger = LoggerFactory.getLogger(VNXStoragePortStatsProcessor.class);
    private PortMetricsProcessor portMetricsProcessor;
    @Override
    public void processResult(Operation operation, Object resultObj, 
            Map<String, Object> keyMap) throws BaseCollectionException {
        // TODO Auto-generated method stub
        
        final PostMethod result = (PostMethod) resultObj;
        _logger.info("processing PortMetrics response" + resultObj);
        try {
            ResponsePacket responsePacket = (ResponsePacket) _unmarshaller
                    .unmarshal(result.getResponseBodyAsStream());
            
            if (null != responsePacket.getPacketFault()) {
                Status status = responsePacket.getPacketFault();
                processErrorStatus(status, keyMap);
            } else {
                //get the key values
                AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
                final DbClient dbClient = (DbClient) keyMap.get(VNXFileConstants.DBCLIENT);

                List<Stat> statsList = (List<Stat>) keyMap.get(VNXFileConstants.STATS);
                Map<String, Map<String, List<String>>> moverInterMap = 
                        (Map<String, Map<String, List<String>>>)keyMap.get(VNXFileConstants.INTREFACE_PORT_MAP); 
                StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, profile.getSystemId());
                
                List<Stat> newstatsList = null;
                
                //process response
                List<Object> queryResponse = getQueryResponse(responsePacket);
                Iterator<Object> queryRespItr = queryResponse.iterator();
                List<MoverNetStats> moverNetStatses = new ArrayList<MoverNetStats>();
                
                
                Map<String, List<String>> interPortMap = null;
                while (queryRespItr.hasNext()) {
                    Object responseObj = queryRespItr.next();
                    if (responseObj instanceof MoverNetStats) {
                        MoverNetStats moverNetStats = (MoverNetStats) responseObj;
                                               
                        //get the stats info
                        List<MoverNetStats.Sample> sampleList = moverNetStats.getSample();
                        
                        Map<String, BigInteger> stringMapPortIOs = new HashMap<String, BigInteger>();
                        //process Moverstats per port and storage in map 
                        getPortI0Traffic(sampleList, stringMapPortIOs);
                        
                        //process mover stats per data mover
                        String moverId = moverNetStats.getMover();
                        interPortMap = moverInterMap.get(moverId);
                        
                        //stats sample time
                        long sampleTime = sampleList.get(0).getTime();
                        
                        //process the port metrics
                        newstatsList = processPortStatsInfo(interPortMap, stringMapPortIOs, 
                                                    storageSystem, dbClient, sampleTime);
                        
                        //finally add to stat object
                        statsList.addAll(newstatsList);
 
                    }
                }
                
                Header[] headers = result
                        .getResponseHeaders(VNXFileConstants.CELERRA_SESSION);
                if (null != headers && headers.length > 0) {
                    keyMap.put(VNXFileConstants.CELERRA_SESSION,
                            headers[0].getValue());
                    _logger.info("Recieved celerra Port Metrics information from the Server.");
                }
            }
        } catch (final Exception ex) {
            _logger.error(
                    "Exception occurred while processing the Port Metrics response due to {}",
                    ex.getMessage());
        } finally {
            result.releaseConnection();
        }
        
    }
    
    private List<Stat> processPortStatsInfo(Map<String, List<String>> interPortMap, 
                                        Map<String, BigInteger> stringMapPortIOs, 
                                        StorageSystem storageSystem, DbClient dbClient,
                                        Long sampleTime) {
        
        //get the interfaces and corresponding port
        List<Stat> stat = new ArrayList<Stat>();
        Stat fePortStat = null;
        for (Entry<String, List<String>> entry : interPortMap.entrySet()) {
            String interfaceIP = entry.getKey();
            List<String> portList = entry.getValue();
            
            // get the port information
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    storageSystem, interfaceIP, NativeGUIDGenerator.PORT);
            StoragePort storagePort = findExistingPort(portNativeGuid, dbClient);
            
            _logger.info(
                    "interface {} and port details {}", interfaceIP, storagePort.getPortName() );
            
            //calcuate traffic per interface- total traffic all ports/no of ports 
            BigInteger iovalue = new BigInteger("0");
            for(String physicalName : portList) {
                iovalue = iovalue.add((BigInteger)stringMapPortIOs.get(physicalName));
            }
            Long iopes = iovalue.longValue()/portList.size();
            
            _logger.info(
                    "processFEPortMetrics input data iops{} and time details {} iopes", 
                    iopes.toString(), sampleTime.toString());
            
            //send port metrics processor to store the content
            portMetricsProcessor.processFEPortMetrics(0L, iopes, storagePort, sampleTime);
            
            //finally add above value to stat object
            fePortStat =preparePortStatInfo(portNativeGuid, storagePort.getId(), iopes, sampleTime);
            stat.add(fePortStat);
        }
        
        return stat;
    }
    
    /* get IO traffic(in + out) from sample list */
    private Map<String, BigInteger>  getPortI0Traffic(List<MoverNetStats.Sample> sampleList , 
                                                        Map<String, BigInteger> stringMapPortIOs)
    {
        //Mover stats
        for(MoverNetStats.Sample sample: sampleList) {
            
            //get device traffic stats
            List<MoverNetStats.Sample.DeviceTraffic> deviceTrafficList = sample.getDeviceTraffic();
            Iterator<MoverNetStats.Sample.DeviceTraffic> deviceTrafficIterator= deviceTrafficList.iterator();
            while (deviceTrafficIterator.hasNext()) {
                MoverNetStats.Sample.DeviceTraffic deviceTraffic = deviceTrafficIterator.next();
                //add in + out io traffic
                BigInteger totalIOs = deviceTraffic.getIn().add(deviceTraffic.getOut());
    
                stringMapPortIOs.put(deviceTraffic.getDevice(), totalIOs);
            }
        }
            return stringMapPortIOs;
    }
    
    /* find the port for given portGuid */
    private StoragePort findExistingPort(String portGuid, DbClient dbClient) {
        URIQueryResultList results = new URIQueryResultList();
        StoragePort port = null;

        dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            StoragePort tmpPort = dbClient.queryObject(StoragePort.class, iter.next());

            if (tmpPort!=null && !tmpPort.getInactive()){
                port = tmpPort;
                _logger.info("found port {}", tmpPort.getNativeGuid() + ":" + tmpPort.getPortName());
                break;
            }
        }
        return port;
    }
    
    /* prepare the port stat info*/
    private Stat preparePortStatInfo(String nativeId, URI resourceId, long iops, long timeSample) {
        Stat fePortStat = new Stat();
        fePortStat.setServiceType(Constants._File);
        fePortStat.setTimeCollected(timeSample);
        fePortStat.setResourceId(resourceId);
        fePortStat.setNativeGuid(nativeId);
        fePortStat.setTotalIOs(iops);
        
        return fePortStat;
        
    }
    
    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }
}
