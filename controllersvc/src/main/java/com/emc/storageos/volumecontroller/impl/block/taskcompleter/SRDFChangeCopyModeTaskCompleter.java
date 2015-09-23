/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation.Status;
import com.emc.storageos.db.client.model.RemoteDirectorGroup;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;

public class SRDFChangeCopyModeTaskCompleter extends SRDFTaskCompleter{

	/**
	 * serialVesionUID
	 */
	private static final long serialVersionUID = -5479591144305569199L;
	private static final Logger log = LoggerFactory.getLogger(SRDFChangeCopyModeTaskCompleter.class);
    private Collection<Volume> tgtVolumes;
    private String newCopyMode;

	public SRDFChangeCopyModeTaskCompleter(List<URI> ids, String opId, String copyMode) {
		super(ids, opId);
		this.newCopyMode = copyMode;
	}
	
	@Override
	protected void complete(DbClient dbClient, Status status, ServiceCoded coded)
			throws DeviceControllerException {
        try {
            setDbClient(dbClient);
          
            switch (status) {
            case ready:
            	RemoteDirectorGroup rdfGrp =null;
            	for (Volume target : tgtVolumes) {
            		target.setSrdfCopyMode(newCopyMode);
            		dbClient.persistObject(target);
            		log.info(String.format("SRDF Device source %s and target %s copy mode got changed into %s",
            				target.getSrdfParent().toString(), target.getId().toString(), newCopyMode));
            		if(rdfGrp == null){
            			rdfGrp = dbClient.queryObject(RemoteDirectorGroup.class, target.getSrdfGroup());
            		}
            	}
            	if(rdfGrp != null){
            		rdfGrp.setSupportedCopyMode(newCopyMode);
            		dbClient.persistObject(rdfGrp);
            		log.info("RDF Group {} copy mode got changed into : {}", rdfGrp.getId(), newCopyMode);
            	}
            	break;
            default:
            	log.info("Unable to handle SRDF Link Change Copy Mode Operational status: {}", status);
            }

        } catch (Exception e) {
            log.error("Failed change copy mode. SRDFMirror {}, for task " + getOpId(), getId(), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

	public Collection<Volume> getTgtVolumes() {
		return tgtVolumes;
	}

	public void setTgtVolumes(Collection<Volume> tgtVolumes) {
		this.tgtVolumes = tgtVolumes;
	}

	public String getNewCopyMode() {
		return newCopyMode;
	}

	public void setNewCopyMode(String newCopyMode) {
		this.newCopyMode = newCopyMode;
	}

}
