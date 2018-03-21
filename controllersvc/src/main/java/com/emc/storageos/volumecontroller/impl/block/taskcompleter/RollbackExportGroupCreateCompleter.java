/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;

public class RollbackExportGroupCreateCompleter extends ExportTaskCompleter {

    private static final long serialVersionUID = -8307408975210762419L;
    private static final Logger _log = LoggerFactory.getLogger(RollbackExportGroupCreateCompleter.class);

    public RollbackExportGroupCreateCompleter(URI egUri, URI emUri, String task) {
        super(ExportGroup.class, egUri, emUri, task);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded) throws DeviceControllerException {
        try {
            ExportGroup exportGroup = dbClient.queryObject(ExportGroup.class, getId());
            ExportMask exportMask = (getMask() != null) ? dbClient.queryObject(ExportMask.class, getMask()) : null;

            if ((status == Operation.Status.error) && (coded instanceof ServiceError)) {
                ServiceError error = (ServiceError) coded;
                String originalMessage = error.getMessage();
                StorageSystem storageSystem = exportMask != null ? dbClient.queryObject(StorageSystem.class, exportMask.getStorageDevice())
                        : null;
                String additionMessage = String.format(
                        "Rollback encountered problems cleaning up export mask %s on storage system %s and may require manual clean up",
                        exportMask.getMaskName(), storageSystem != null ? storageSystem.forDisplay() : "Unknown");
                String updatedMessage = String.format("%s\n%s", originalMessage, additionMessage);
                error.setMessage(updatedMessage);
            }

            if (exportMask != null) {
			    URI pgURI = exportMask.getPortGroup();
				// clean up export group
                exportGroup.removeExportMask(exportMask.getId());
				dbClient.updateObject(exportGroup);
				List<URI> boURIs = new ArrayList<>();
				// clean up export mask, if there are volumes added that are
				// part of export group then we should remove that volumes
				for (String emUri : exportGroup.getExportMasks()) {
					if (URIUtil.isValid(emUri) && !emUri.equals(exportMask.getId())) {
						ExportMask em = dbClient.queryObject(ExportMask.class, URI.create(emUri));
						if (em != null) {
							for (String boURI : em.getUserAddedVolumes().values()) {
								if (URIUtil.isValid(boURI)) {
									boURIs.add(URI.create(boURI));
								}
							}
						}
					}
				}
				exportMask.removeFromUserAddedVolumesByURI(boURIs);
                exportMask.removeVolumes(boURIs);
				dbClient.updateObject(exportMask);
				// if its not used anywhere and is system created delete it
				if (ExportMaskUtils.getExportGroups(dbClient, exportMask.getId()).isEmpty()
						&& exportMask.getCreatedBySystem() && !exportMask.hasAnyVolumes()) {
					dbClient.markForDeletion(exportMask);
				}
                updatePortGroupVolumeCount(pgURI, dbClient);
            }
            _log.info(String.format("Done RollbackExportGroupCreate - Id: %s, OpId: %s, status: %s",
                    getId().toString(), getOpId(), status.name()));
        } catch (Exception e) {
            _log.error(String.format("Failed updating status for RollbackExportGroupCreate - Id: %s, OpId: %s",
                    getId().toString(), getOpId()), e);
        } finally {
            super.complete(dbClient, status, coded);
        }
    }

}
