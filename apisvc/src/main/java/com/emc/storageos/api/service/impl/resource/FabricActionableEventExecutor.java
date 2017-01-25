/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource;

import static com.emc.storageos.api.mapper.TaskMapper.toTask;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.networkcontroller.NetworkController;
import com.emc.storageos.networkcontroller.impl.TransportZoneReconciler;

public class FabricActionableEventExecutor  implements EventExecutor {
    
    private DbClient _dbClient;
    private NetworkController networkController;
    private boolean isAutoApproved = false;

    public FabricActionableEventExecutor(DbClient dbClient, NetworkController nwController) {
        this._dbClient = dbClient;
        this.networkController = nwController;
    }
    
    public FabricActionableEventExecutor(DbClient dbClient, NetworkController nwController, boolean isAutoApproved) {
        this(dbClient, nwController);
        this.isAutoApproved = isAutoApproved;
    }
    
    public TaskResourceRep fabricNameChange(URI networkURI, String existingName, String newName, String networkWWN) {
        
        Network network = _dbClient.queryObject(Network.class, networkURI);
        String taskId = UUID.randomUUID().toString();

        Operation op = _dbClient.createTaskOpStatus(Network.class, networkURI, taskId,
                ResourceOperationTypeEnum.UPDATE_NETWORK);
        
        //Consider the fabric name we have is the latest
        String latestFabricName = newName;
        if(!isAutoApproved) {
          //If isAutoApproved - then only fetch the latest details from the network system
            Map<String, String> mapFabricWWNvsId = networkController.getFabricIdsMap(networkURI);
            latestFabricName = mapFabricWWNvsId.get(networkWWN);
            
        }
        
        //Fetch all dependent FCZoneReferences for update
        URIQueryResultList fcZoneRefURIS = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFCZoneReferenceByFabricIdConstraint(existingName), fcZoneRefURIS);
        Iterator<FCZoneReference> fcZoneRefIterator = _dbClient.queryIterativeObjects(FCZoneReference.class, fcZoneRefURIS);
        
        //Update FCZoneReferences with the new fabricId
        List<FCZoneReference> updatedFcZoneRefs = new ArrayList<>(fcZoneRefURIS.size());
        while (fcZoneRefIterator.hasNext()) {
            FCZoneReference ref = fcZoneRefIterator.next();
            ref.setFabricId(latestFabricName);
            updatedFcZoneRefs.add(ref);
        }
        
        //Bulk update of FCZonereferences
        _dbClient.updateObject(updatedFcZoneRefs);
        
        //update the native id of the network
        network.setNativeId(latestFabricName);
        
        //update the network label
        String currentLabel = network.getLabel();
        String newLabel = null;
        int startIndex = currentLabel.lastIndexOf(TransportZoneReconciler.PREFIX_FABRIC);
        if(startIndex == -1){
            newLabel = TransportZoneReconciler.PREFIX_VSAN + latestFabricName;
        } else {
            newLabel = TransportZoneReconciler.PREFIX_FABRIC + latestFabricName;
        }
        network.setLabel(newLabel);
        _dbClient.updateObject(network);        
        
        _dbClient.ready(Network.class, networkURI, taskId);
        return toTask(network, taskId, op);
        
        
    }

}
