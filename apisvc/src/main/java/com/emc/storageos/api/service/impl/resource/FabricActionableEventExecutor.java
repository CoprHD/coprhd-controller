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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ActionableEvent;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.networkcontroller.NetworkController;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.TransportZoneReconciler;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public class FabricActionableEventExecutor  implements EventExecutor {
    
    private static final Logger _log = LoggerFactory.getLogger(FabricActionableEventExecutor.class);
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
    
    /**
     * Approval action method implementation for the fabric rename event
     * 
     * It updates the following
     * - network's nativeId and label
     * - All dependent FCZoneReferences using the nativeId of the network/fabric
     *  
     * @param networkURI - URI of the network to be updated
     * @param existingName - Current nativeid of the network
     * @param newName - New nativeid of the network
     * @return
     */
    public TaskResourceRep fabricNameChange(URI networkURI, String existingName, String newName, URI eventId) {
        
        String networkWWN = "";
        Network network = _dbClient.queryObject(Network.class, networkURI);
        if(null != network) {
            networkWWN = network.getNativeGuid().split("\\+")[2];
        }
        
        String taskId = UUID.randomUUID().toString();
        Operation op = _dbClient.createTaskOpStatus(Network.class, networkURI, taskId,
                ResourceOperationTypeEnum.UPDATE_NETWORK);
        
        try {
            
            //Consider the fabric name we have is the latest
            String latestFabricName = newName;
            if(!isAutoApproved) {
              //If isAutoApproved - then only fetch the latest details from the network system
                _log.info("isAutoApproved is false, fetching the fabric ids map");
                Map<String, String> mapFabricWWNvsId = networkController.getFabricIdsMap(networkURI);
                latestFabricName = mapFabricWWNvsId.get(networkWWN);
                _log.info("New native id of the fabric is:"+latestFabricName);
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
            
            
            //NETWORK ( Fabric or Vsan ) update
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
            
        } catch(NetworkDeviceControllerException ndce) {
            _log.error(ndce.getLocalizedMessage(), ndce);
            markEventFailed(eventId);
            _dbClient.error(Network.class, networkURI, taskId, ndce);
            
        } catch (Exception ex) {
            String message = "Update fabric name caught an exception and failed";
            _log.error(message, ex);
            InternalServerErrorException serviceError = APIException.internalServerErrors.updateObjectError(networkURI.toString(), ex);
            markEventFailed(eventId);
            _dbClient.error(Network.class, networkURI, taskId, serviceError);
        }

        return toTask(network, taskId, op);
        
        
    }
    
    public void markEventFailed(URI eventId) {
        ActionableEvent event = _dbClient.queryObject(ActionableEvent.class, eventId);
        if (event != null) {
            event.setEventStatus(ActionableEvent.Status.failed.name());
            _dbClient.updateObject(event);
        }
    }
    
    /**
     * Decline method for fabric rename event
     * 
     * @param network
     * @param eventId
     * @return
     */
    public TaskResourceRep fabricNameChangeDecline(URI network, URI eventId) {
        return null;
    }

}
