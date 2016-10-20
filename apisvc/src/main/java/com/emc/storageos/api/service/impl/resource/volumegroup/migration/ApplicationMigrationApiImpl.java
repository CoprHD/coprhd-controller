/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.volumegroup.migration;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.migrationcontroller.MigrationController;
import com.emc.storageos.model.application.ApplicationMigrationParam;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;
import com.google.common.base.Joiner;

public class ApplicationMigrationApiImpl extends AbstractMigrationServiceApiImpl {

	private static final Logger logger = LoggerFactory.getLogger(ApplicationMigrationApiImpl.class);		
    
    public ApplicationMigrationApiImpl() {
		super(null);
		// TODO Auto-generated constructor stub
	}
    
    //Migration related methods
	@Override
	public void migrationCreate(URI applicationId, ApplicationMigrationParam param) {
		logger.info("Migration : Create");
		
		VirtualArray tgtVarray = dbClient.queryObject(VirtualArray.class, param.getTargetVirtualArray());
		VirtualPool tgtVpool = dbClient.queryObject(VirtualPool.class, param.getTargetVirtualPool());
		Integer minPathsRequested = tgtVpool.getMinPaths();		
		StoragePortsAllocator allocator = new StoragePortsAllocator();

		Map<URI, List<StoragePort>> networkPortMapForVarray = new HashMap<URI, List<StoragePort>>();
		
		// get varray networks
        List<Network> networks = CustomQueryUtility.queryActiveResourcesByRelation(
        							dbClient, tgtVarray.getId(), Network.class,"connectedVirtualArrays");
		
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        
        // get varray storageports
        dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getVirtualArrayStoragePortsConstraint(tgtVarray.getId().toString()),
                storagePortURIs);
        
        // build map of network to list of storage ports in that network
        for(Network network : networks) {
        	StringMap networkEndpointsMap = network.getEndpointsMap();
        	List<StoragePort> portList = new ArrayList<StoragePort>();
        	for(URI portUri : storagePortURIs) {
        		StoragePort port = dbClient.queryObject(StoragePort.class, portUri);
        		if (networkEndpointsMap.containsKey(port.getPortNetworkId())) {
        			portList.add(port);
        		}
        	}
        	networkPortMapForVarray.put(network.getId(), portList);        	        
        }        
        
        // port allocation using the usage map
        List<StoragePort> portsAllocated = new ArrayList<StoragePort>();        
        for (Map.Entry<URI, List<StoragePort>> entry : networkPortMapForVarray.entrySet() ) {            	
        if (!entry.getValue().isEmpty()) {
	        Map<StoragePort, Long> sportMap = blockScheduler
	                .computeStoragePortUsage(entry.getValue());
	        NetworkLite nwlite = NetworkUtil.getNetworkLite(entry.getKey(), dbClient);
	        portsAllocated = allocator.selectStoragePorts(dbClient, sportMap,
	                nwlite, tgtVarray.getId(), minPathsRequested, null, false);        
		        if (!portsAllocated.isEmpty() && portsAllocated.size() >= minPathsRequested) {
		        	//success
		        	logger.info("Allocated Ports:");
		        	for(StoragePort portAllocated : portsAllocated) {
		        		logger.info(portAllocated.getNativeId() + "," );
		        	}
		        	break;
		        }
	        }
        }
        
        // TODO: what sort of validation checks do we need here.
        		
		getController(MigrationController.class, "application").migrationCreate();
	}

	@Override
	public void migrationMigrate() {
		logger.info("Migration : Migrate");
	}

	@Override
	public void migrationCommit() {
		logger.info("Migration : Commit");
	}

	@Override
	public void migrationCancel(boolean removeEnvironment) {
		logger.info("Migration : Cancel");
	}

	@Override
	public void migrationRefresh() {
		logger.info("Migration : Refresh");
	}

	@Override
	public void migrationRecover() {
		logger.info("Migration : Recover");
	}

	@Override
	public void migrationRemoveEnv() {
		logger.info("Migration : Remove Environment");
	}

	@Override
	public void migrationSyncStart() {
		logger.info("Migration : Sync Start");
	}

	@Override
	public void migrationSyncStop() {
		logger.info("Migration : Sync Stop");
	}
}
