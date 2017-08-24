/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.workflow.WorkflowService;

public class ExportMaskRemoveInitiatorCompleter extends ExportTaskCompleter {
    private static final Logger _log = LoggerFactory.getLogger(ExportMaskRemoveInitiatorCompleter.class);

    private List<URI> _initiatorURIs;

    public ExportMaskRemoveInitiatorCompleter(URI egUri, URI emUri, List<URI> initiatorURIs,
            String task) {
        super(ExportGroup.class, egUri, emUri, task);
        _initiatorURIs = new ArrayList<URI>();
        _initiatorURIs.addAll(initiatorURIs);
    }

    @Override
    public void ready(DbClient dbClient) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            if (exportMask != null) {
                List<Initiator> initiators =
                        dbClient.queryObject(Initiator.class, _initiatorURIs);
                List<URI> targetPorts = ExportUtils.getRemoveInitiatorStoragePorts(exportMask, initiators, dbClient);
                exportMask.removeInitiators(initiators);
                exportMask.removeFromUserCreatedInitiators(initiators);
                if (exportMask.getInitiators() == null ||
                        exportMask.getInitiators().isEmpty()) {
                    exportGroup.removeExportMask(exportMask.getId());
                    dbClient.markForDeletion(exportMask);
                    dbClient.updateObject(exportGroup);
                } else {
                    if (targetPorts != null && !targetPorts.isEmpty()) {
                        for (URI targetPort : targetPorts) {
                            exportMask.removeTarget(targetPort);
                        }
                    }
                    removeUnusedTargets(exportMask);
                    dbClient.updateObject(exportMask);
                }
                _log.info(String.format(
                        "Done ExportMaskRemoveInitiator - Id: %s, OpId: %s, status: %s",
                        getId().toString(), getOpId(), Operation.Status.ready.name()));
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveInitiator - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.ready(dbClient);
        }
    }
    
    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
    	try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ?
                    dbClient.queryObject(ExportMask.class, getMask()) : null;
            boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(getOpId());
            if ((status == Operation.Status.error) && (isRollback) && (coded instanceof ServiceError)) {
                ServiceError error = (ServiceError) coded;
                String originalMessage = error.getMessage();
                StorageSystem storageSystem = exportMask != null ? dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice())
                        : null;
                List<Initiator> initiators = dbClient.queryObject(Initiator.class, _initiatorURIs);
                StringBuffer initiatorsJoined = new StringBuffer();
                if (initiators != null && !initiators.isEmpty()) {
                    Iterator<Initiator> initIter = initiators.iterator();
                    while (initIter.hasNext()) {
                        Initiator initiator = initIter.next();
                        initiatorsJoined.append(initiator.forDisplay());
                        if (initIter.hasNext()) {
                            initiatorsJoined.append(",");
                        }
                    }
                }
                String additionMessage = String.format(
                        "Rollback encountered problems removing initiator(s) %s from export mask %s on storage system %s and may require manual clean up",
                        initiatorsJoined.toString(), exportMask.getMaskName(),
                        storageSystem != null ? storageSystem.forDisplay() : "Unknown");
                String updatedMessage = String.format("%s\n%s", originalMessage, additionMessage);
                error.setMessage(updatedMessage);
            }

            if (exportMask != null && (status == Operation.Status.ready || isRollback)) {
                List<Initiator> initiators =
                        dbClient.queryObject(Initiator.class, _initiatorURIs);
                List<URI> targetPorts = ExportUtils.getRemoveInitiatorStoragePorts(exportMask, initiators, dbClient);
                exportMask.removeInitiators(initiators);
                exportMask.removeFromUserCreatedInitiators(initiators);
                if (exportMask.getExistingInitiators() != null &&
                        exportMask.getExistingInitiators().isEmpty()){
                	exportMask.setExistingInitiators(null);
                }
				if (exportMask.getInitiators() == null || exportMask.getInitiators().isEmpty()) {
					exportGroup.removeExportMask(exportMask.getId());
					dbClient.removeObject(exportMask);
					dbClient.updateObject(exportGroup);
				} else {
					if (targetPorts != null && !targetPorts.isEmpty()) {
						for (URI targetPort : targetPorts) {
							exportMask.removeTarget(targetPort);
						}
					}
					removeUnusedTargets(exportMask);
					dbClient.updateObject(exportMask);
				}
                
                _log.info(String.format(
                        "Done ExportMaskRemoveInitiator - Id: %s, OpId: %s, status: %s",
                        getId().toString(), getOpId(), status.name()));
            }
        } catch (Exception e) {
            _log.error(String.format(
                    "Failed updating status for ExportMaskRemoveInitiator - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
        	super.complete(dbClient, status, coded);
        }
    }
    
    /*
     * Remove unused storage Ports from export mask
     */
	private void removeUnusedTargets(ExportMask exportMask) {
		StringSet initiators = exportMask.getInitiators();
		StringSetMap zoningMap = exportMask.getZoningMap();
		Set<String> zonedTarget = new HashSet<String>();
        boolean isRollback = WorkflowService.getInstance().isStepInRollbackState(getOpId());
		for (String initiator : initiators) {
		    StringSet zoneEntry = zoningMap.get(initiator);
		    if (zoneEntry != null) {
		        zonedTarget.addAll(zoneEntry);
		    } else {
		        _log.warn(String.format(
                        "removeUnusedTargets() - tried looking up initiator [%s] in zoningMap for "
                        + "Export Mask [%s - %s], but no entry found - continue...", initiator, 
                        exportMask.getMaskName(), exportMask.getId())); 
		    }
		}

        Set<String> targets = new HashSet<String>();
        if (exportMask.getStoragePorts() != null) {
            targets.addAll(exportMask.getStoragePorts());
        }
        
        if (isRollback) {
            StringSet emStoragePorts = new StringSet();

            if (exportMask.getStoragePorts() != null) {
                emStoragePorts = exportMask.getStoragePorts();
            } else {
                _log.warn(String.format(
                        "removeUnusedTargets() - could not find any storage ports for"
                                + "Export Mask [%s - %s].",
                        exportMask.getMaskName(), exportMask.getId()));
            }

            targets = new HashSet<String>(emStoragePorts);
		}

		if (!targets.removeAll(zonedTarget)) {
			for (String zonedPort : zonedTarget) {
				exportMask.addTarget(URIUtil.uri(zonedPort));
			}
		}
		
		for (String targetPort : targets) {
			exportMask.removeTarget(URIUtil.uri(targetPort));
		}
	}

	public boolean removeInitiator(URI initiator) {
        return _initiatorURIs.remove(initiator);
    }
}
