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
 * it get the filesystem and snapshot and their capacity data strcuture from fsusageoperation and snapshotoperation
 * and final it calculate capacity and no. storage object for each mover and VDM.
 * result will be stored in db.
 */
public class VNXFileSystemStaticLoadProcessor extends VNXFileProcessor {
    private final Logger _logger = LoggerFactory.getLogger(VNXFileSystemStaticLoadProcessor.class);

    /**
     * Process the result got from data sources.
     */
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
    	_logger.info("Processing file system mount response....");

    	final DbClient dbClient = (DbClient) keyMap.get(VNXFileConstants.DBCLIENT);
    	// step -1 get the filesystem capacity map < filesystemid, size>
    	Map<String, Long> fsCapList = (HashMap<String, Long>) keyMap.get(VNXFileConstants.FILE_CAPACITY_MAP);
    	Map<String, Map<String, Long>> snapCapFsMap =
    			(HashMap<String, Map<String, Long>>) keyMap.get(VNXFileConstants.SNAP_CAPACITY_MAP);
    	// step-2 get the snapshot checkpoint size for give filesystem and it is map of filesystem and map <snapshot, checkpointsize>>
    	AccessProfile profile = (AccessProfile) keyMap.get(Constants.ACCESSPROFILE);
    	// get the storagesystem from db
    	StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class, profile.getSystemId());

    	List<String> fsList = null;
    	Map<String, List<String>> fsMountvNASMap = new HashMap<String, List<String>>();
    	Map<String, List<String>> fsMountPhyNASMap = new HashMap<String, List<String>>();
    	// step -3 we will get filesystem on VDM or DM
    	Iterator<Object> iterator = mountList.iterator();
    	if (iterator.hasNext()) {
    		Status status = (Status) iterator.next();
    		if (status.getMaxSeverity() == Severity.OK) {
    			// step -4 get the filesystem list for each mover or VDM in Map
    			while (iterator.hasNext()) {
    				Mount mount = (Mount) iterator.next();

    				if (mount.isMoverIdIsVdm() == true) {
    					fsList = fsMountvNASMap.get(mount.getMover());
    					if (null == fsList) {
    						fsList = new ArrayList<String>();
    					}
    					fsList.add(mount.getFileSystem());
    					fsMountvNASMap.put(mount.getMover(), fsList);// get filesystem list for VDM or vNAS
    					_logger.debug("Filestem or Snapshot {} mounted on vdm {} ", 
    							mount.getFileSystem(), mount.getMover() );

    				} else {
    					fsList = fsMountPhyNASMap.get(mount.getMover());
    					if (null == fsList) {
    						fsList = new ArrayList<String>();
    					}
    					fsList.add(mount.getFileSystem());
    					fsMountPhyNASMap.put(mount.getMover(), fsList); // get filesystem list for DM or mover
    					_logger.debug("Filestem or Snapshot {} mounted on data mover {} ", 
    							mount.getFileSystem(), mount.getMover() );
    				}
    			}

    			// Log the number of objects mounted on each data mover and virtual data mover!!!
    			for (Entry<String, List<String>> eachVNas  :fsMountvNASMap.entrySet()){
    				_logger.info(" Virtual data mover {} has Filestem or Snapshot mounts {} ", 
    						eachVNas.getKey(), eachVNas.getValue().size() );
    			}

    			for (Entry<String, List<String>> eachNas  :fsMountPhyNASMap.entrySet()){
    				_logger.info(" Data mover {} has Filestem or Snapshot mounts {} ", 
    						eachNas.getKey(), eachNas.getValue().size() );
    			}


    			Map<String, Long> vdmCapacityMap = new HashMap<String, Long>();
    			Map<String, Long> dmCapacityMap = new HashMap<String, Long>();

    			vdmCapacityMap = computeMoverCapacity(fsMountvNASMap, fsCapList, snapCapFsMap);
    			dmCapacityMap = computeMoverCapacity(fsMountPhyNASMap, fsCapList, snapCapFsMap);

    			prepareDBMetrics(storageSystem, dbClient, fsMountPhyNASMap,
    					dmCapacityMap, fsMountvNASMap, vdmCapacityMap);
    		} else {
    			throw new VNXFilePluginException(
    					"Fault response received from XMLAPI Server.",
    					VNXFilePluginException.ERRORCODE_INVALID_RESPONSE);
    		}
    	}

    }
    
    /**
     * computeMoverCapacity - computes the total capacity of data mover/vdm
     * based on file systems capacity and snapshots capacity
     * 
     * @param nasFsMountMap
     * @param fsCapMap
     * @param snapCapFsMap
     * @param snapCapFsMap
     * 
     */

    private Map<String, Long> computeMoverCapacity( Map<String, List<String>> nasFsMountMap,
    		Map<String, Long> fsCapMap, Map<String, Map<String, Long>> snapCapFsMap ) {
    	
    	Map<String, Long> moverCapacityMap = new HashMap<String, Long>();
       
        
        // Compute the total capacity of mover !!!
        
        for (Entry<String, List<String>> eachNas  :nasFsMountMap.entrySet()) {
        	_logger.info(" mover {} has Filestem or Snapshot mounts {} ", 
        			eachNas.getKey(), eachNas.getValue().size() );
        	
        	// Get File system capacity
        	Long moverTotalCapacity = 0L;
        	
        	for (String fsNativeId :eachNas.getValue() ) {
        		moverTotalCapacity = moverTotalCapacity + ( fsCapMap.get(fsNativeId) != null?
        				fsCapMap.get(fsNativeId) : 0);
        		
        		// Include snap shot capacity on the fs too !!!
        		Long fsSnapTotalCapacity = 0L;
        		Map<String, Long> fsSnapCapMap = snapCapFsMap.get(fsNativeId);
                if (fsSnapCapMap != null && !fsSnapCapMap.isEmpty()) {
                	for (Entry<String, Long> eachFsSnap  :fsSnapCapMap.entrySet()) {
                		fsSnapTotalCapacity = fsSnapTotalCapacity + eachFsSnap.getValue();
                	}
                }
                
                // Add snap capacity
                moverTotalCapacity  = moverTotalCapacity + fsSnapTotalCapacity;
        		
        	}
        	moverCapacityMap.put(eachNas.getKey(), moverTotalCapacity);
        }
        return moverCapacityMap;
    }
    
    /**
     * get the DB metrics for each data mover or VDM
     * 
     * @param fsList
     * @param fsCapList
     * @param snapCapFsMap
     * @param nasServer
     * @param dmFsCountMAP
     */
    private void prepareDBMetrics(StorageSystem storageSystem, DbClient dbClient, 
    		final Map<String, List<String>> dmFsMountMap, final Map<String, Long> dmCapacityMap,
    		final Map<String, List<String>> vdmFsMountMap, final Map<String, Long> vdmCapacityMap) {
    	
    	List<VirtualNAS> modifiedVNas = new ArrayList<VirtualNAS>();
    	List<PhysicalNAS> modifiedPNas = new ArrayList<PhysicalNAS>();
        
        for (Entry<String, List<String>> eachNas  :dmFsMountMap.entrySet()) {
        	_logger.info(" Computing metrics for data mover {}  ", eachNas.getKey());
        	// Get Physical NAS from db!!
        	PhysicalNAS pNAS = findPhysicalNasByNativeId(storageSystem, dbClient, eachNas.getKey());
        	
        	List<VirtualNAS> vNasList = new ArrayList<VirtualNAS>();
        	
        	if (null != pNAS) {
        		URIQueryResultList virtualNASUris = new URIQueryResultList();
        		dbClient.queryByConstraint(
        				ContainmentConstraint.Factory.getVirtualNASByParentConstraint(pNAS.getId()), virtualNASUris);

        		Long totalDmObjects = 0L;
        		Long totalDmCapacity = 0L;

        		Iterator<URI> virtualNASIter = virtualNASUris.iterator();
        		while (virtualNASIter.hasNext()) {
        			// Get Each vNAS on Physical NAS
        			VirtualNAS virtualNAS = dbClient.queryObject(VirtualNAS.class, virtualNASIter.next());
        			if (virtualNAS != null && !virtualNAS.getInactive()) {
        				
        				vNasList.add(virtualNAS);
        				int vNasObjects = 0;
        				
        				if( vdmFsMountMap.get(virtualNAS.getNativeId()) != null){
        					vNasObjects = vdmFsMountMap.get(virtualNAS.getNativeId()).size();
        					totalDmObjects = totalDmObjects + vNasObjects;
        				}

        				Long vNasCapacity = 0L;
        				if( vdmCapacityMap.get(virtualNAS.getNativeId()) != null){
        					vNasCapacity = vdmCapacityMap.get(virtualNAS.getNativeId());
        					totalDmCapacity = totalDmCapacity + vNasCapacity;
        				}

        				// Update dbMetrics for vNAS!!
        				StringMap vNasDbMetrics = virtualNAS.getMetrics();
						vNasDbMetrics.put(MetricsKeys.storageObjects.name(), String.valueOf(vNasObjects));
						vNasDbMetrics.put(MetricsKeys.storageCapacity.name(), String.valueOf(vNasCapacity));
						
						modifiedVNas.add(virtualNAS);

        			} 
        		}

        		if( dmFsMountMap.get(pNAS.getNativeId()) != null){

        			totalDmObjects = totalDmObjects + vdmFsMountMap.get(pNAS.getNativeId()).size();
        		}

        		if( vdmCapacityMap.get(pNAS.getNativeId()) != null){
        			totalDmCapacity = totalDmCapacity + vdmCapacityMap.get(pNAS.getNativeId());
        		}

        		StringMap pNasDbMetrics = pNAS.getMetrics();
        		pNasDbMetrics.put(MetricsKeys.storageObjects.name(), String.valueOf(totalDmObjects));
        		pNasDbMetrics.put(MetricsKeys.storageCapacity.name(), String.valueOf(totalDmCapacity));

        		Long maxObjects = MetricsKeys.getLong(MetricsKeys.maxStorageObjects, pNasDbMetrics);
        		Long maxCapacity = MetricsKeys.getLong(MetricsKeys.maxStorageCapacity, pNasDbMetrics);

        		if (totalDmObjects >= maxObjects || totalDmCapacity >= maxCapacity) {
        			pNasDbMetrics.put(MetricsKeys.overLoaded.name(), "true");
        			// All vNas under should be updated!!!
        			for (VirtualNAS vNas: vNasList) {
        				// Update dbMetrics for vNAS!!
        				StringMap vNasDbMetrics = vNas.getMetrics();
        				vNasDbMetrics.put(MetricsKeys.overLoaded.name(), "true");
        			}
        		}else {
        			pNasDbMetrics.put(MetricsKeys.overLoaded.name(), "false");
        			// All vNas under should be updated!!!
        			for (VirtualNAS vNas: vNasList) {
        				// Update dbMetrics for vNAS!!
        				StringMap vNasDbMetrics = vNas.getMetrics();
        				vNasDbMetrics.put(MetricsKeys.overLoaded.name(), "false");
        			}
        		}
        		modifiedPNas.add(pNAS);
        	}
        	
        	// Update the db
        	if(!modifiedVNas.isEmpty()) {
        		dbClient.persistObject(modifiedVNas);
        	}
        	
        	if(!modifiedPNas.isEmpty()) {
        		dbClient.persistObject(modifiedPNas);
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
     */
    private void prepareDBMetrics(final List<String> fsList, final Map<String, Long> fsCapList,
            final Map<String, Map<String, Long>> snapCapFsMap, NASServer nasServer, Map<URI, Integer> dmFsCountMAP) {
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
        double totalObjects = fsCount + snapCount;
        double loadFactor = 1.0;

        if (nasServer instanceof VirtualNAS) {
            double totalObjectOnDataMover = 0.0;
            URI pNaURIs = ((VirtualNAS) nasServer).getParentNasUri();
            if (dmFsCountMAP.containsKey(pNaURIs)) {
                totalObjectOnDataMover = dmFsCountMAP.get(pNaURIs);
            }

            if (totalObjectOnDataMover > 0)
            {
                loadFactor = (totalObjects / totalObjectOnDataMover);
            }

        } else if (nasServer instanceof PhysicalNAS) {

            double totalObjectOnAllDataMover = 0.0;
            for (Integer dmValue : dmFsCountMAP.values()) {

                totalObjectOnAllDataMover = totalObjectOnAllDataMover + dmValue;

            }
            if (totalObjectOnAllDataMover > 0 && dmFsCountMAP.containsKey(nasServer.getId())) {
                totalObjects = dmFsCountMAP.get(nasServer.getId());

                loadFactor = totalObjects / totalObjectOnAllDataMover;
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