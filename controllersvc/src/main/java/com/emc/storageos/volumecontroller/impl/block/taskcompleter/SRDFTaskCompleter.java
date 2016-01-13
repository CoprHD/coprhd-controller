/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import static java.util.Arrays.asList;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.smis.SRDFOperations.Mode;

public class SRDFTaskCompleter extends TaskCompleter {

    /**
     * Reference to logger
     */
    private static final Logger _logger = LoggerFactory
            .getLogger(SRDFTaskCompleter.class);

    private DbClient dbClient;

    protected List<Volume> volumeCache;

    /**
     * Constructor for specifying a combination of source and multiple target ID's.
     * 
     * @param ids
     * @param opId
     */
    public SRDFTaskCompleter(List<URI> ids, String opId) {
        super(Volume.class, ids, opId);
    }

    public SRDFTaskCompleter(URI sourceURI, URI targetURI, String opId) {
        super(Volume.class, asList(sourceURI, targetURI), opId);
    }

    @Override
    protected void complete(DbClient dbClient, Operation.Status status, ServiceCoded coded)
            throws DeviceControllerException {
        setStatus(dbClient, status, coded);
        updateWorkflowStatus(status, coded);
        updateVolumeStatus(dbClient, status);
    }

    protected void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    protected DbClient getDbClient() {
        return dbClient;
    }

    protected Volume getTargetVolume() {
        for (Volume v : getVolumes()) {
            if (!NullColumnValueGetter.isNullNamedURI(v.getSrdfParent()) && !v.getSrdfParent().getURI().toString().equalsIgnoreCase("null")) {
                return v;
            }
        }
        throw new IllegalStateException("Expected a target volume with an non-null SRDF parent");
    }

    protected Volume getSourceVolume() {
        for (Volume v : getVolumes()) {
            if (v.getSrdfTargets() != null) {
                return v;
            }
        }
        throw new IllegalStateException("Expected a source volume with an non-null SRDF parent");
    }

    protected List<Volume> getVolumes() {
        if (volumeCache == null) {
            volumeCache = getDbClient().queryObject(Volume.class, getIds());
        }
        return volumeCache;
    }

    protected Set<String> getVolumeIds() {
        Set<String> volumeIds = new HashSet<String>();
        for (Volume volume : volumeCache) {
            volumeIds.add(volume.getNativeGuid());
        }
        return volumeIds;
    }

    /**
     * 
     * @param dbClient
     * @param evtType
     * @param status
     * @param desc
     * @throws Exception
     */
    public void recordBourneSRDFEvent(DbClient dbClient, URI volumeUri,
            String evtType,
            Operation.Status status, String desc)
            throws Exception {

        RecordableEventManager eventManager = new RecordableEventManager();
        eventManager.setDbClient(dbClient);

        Volume volObj = dbClient.queryObject(Volume.class, volumeUri);
        RecordableBourneEvent event = ControllerUtils
                .convertToRecordableBourneEvent(volObj, evtType,
                        desc, "", dbClient,
                        ControllerUtils.BLOCK_EVENT_SERVICE,
                        RecordType.Event.name(),
                        ControllerUtils.BLOCK_EVENT_SOURCE);

        try {
            eventManager.recordEvents(event);
            _logger.info("Bourne {} event recorded", evtType);
        } catch (Exception ex) {
            _logger.error(
                    "Failed to record event. Event description: {}. Error: ",
                    evtType, ex);
        }
    }

    /**
     * Record block volume related event and audit
     * 
     * @param dbClient db client
     * @param opType operation type
     * @param status operation status
     * @param extParam parameters array from which we could generate detail audit message
     */
    public void recordSRDFOperation(DbClient dbClient, OperationTypeEnum opType, Operation.Status status, Object... extParam) {
        try {
            boolean opStatus = (Operation.Status.ready == status) ? true : false;
            String evType;
            evType = opType.getEvType(opStatus);
            String evDesc = opType.getDescription();
            String opStage = AuditLogManager.AUDITOP_END;
            _logger.info("opType: {} detail: {}", opType.toString(), evType.toString() + ':' + evDesc);

            recordBourneSRDFEvent(dbClient, getId(), evType, status, evDesc);

            String id = (String) extParam[0];
            switch (opType) {
                case CREATE_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case SUSPEND_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case DETACH_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case PAUSE_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case RESUME_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case FAILOVER_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case SWAP_SRDF_VOLUME:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case STOP_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                case SYNC_SRDF_LINK:
                    AuditBlockUtil.auditBlock(dbClient, opType, opStatus, opStage, extParam);
                    break;
                default:
                    _logger.error("unrecognized SRDF operation type");
            }
        } catch (Exception e) {
            _logger.error("Failed to record SRDF operation {}, err: {}", opType.toString(), e);
        }
    }

    protected Volume.LinkStatus getVolumeSRDFLinkStatusForSuccess() {
        return Volume.LinkStatus.OTHER;
    }

    /**
     * Setting access state is based on the personality and failover state of the volume, regardless of operation.
     * 
     * @param v a volume impacted by this SRDF operation
     * @return volume access state for that volume.
     */
    protected Volume.VolumeAccessState getVolumeAccessStateForSuccess(Volume v) {
        // If this volume is a source and exported to a host, the volume is write-disabled. Otherwise it is readwrite.
        if (v.getPersonality().equals(Volume.PersonalityTypes.SOURCE.toString())
                && v.getLinkStatus().equals(Volume.LinkStatus.FAILED_OVER.name())) {
            // Check to see if it's exported
            URIQueryResultList exportGroups = new URIQueryResultList();
            getDbClient().queryByConstraint(ContainmentConstraint.
                    Factory.getBlockObjectExportGroupConstraint(v.getId()), exportGroups);
            if (exportGroups != null && exportGroups.iterator().hasNext()) {
                // A source volume that is in an export group is write-disabled or not-ready.
                return Volume.VolumeAccessState.NOT_READY;
            } else {
                return Volume.VolumeAccessState.READWRITE;
            }
        } else if (v.getPersonality().equals(Volume.PersonalityTypes.TARGET.toString())
                && !v.getLinkStatus().equals(Volume.LinkStatus.FAILED_OVER.name())) {
            if (Mode.ACTIVE.equals(Mode.valueOf(v.getSrdfCopyMode()))) {
                // For Active mode target access state is always updated from the provider
                // after each operation so just use that.
                return Volume.VolumeAccessState.getVolumeAccessState(v.getAccessState());
            } else {
                // A target volume in any state other than FAILED_OVER is write-disabled or not-ready.
                return Volume.VolumeAccessState.NOT_READY;
            }
        }

        // Any other state is READWRITE
        return Volume.VolumeAccessState.READWRITE;
    }

    protected void updateVolumeStatus(DbClient dbClient, Operation.Status status) {
        try {
            if (Operation.Status.ready.equals(status)) {
                List<Volume> volumes = dbClient.queryObject(Volume.class, getIds());
                for (Volume v : volumes) {
                    v.setLinkStatus(getVolumeSRDFLinkStatusForSuccess().name());
                    v.setAccessState(getVolumeAccessStateForSuccess(v).name());
                    if (v.getSrdfTargets() != null) {
                        List<URI> targetVolumeURIs = new ArrayList<URI>();
                        for (String targetId : v.getSrdfTargets()) {
                            targetVolumeURIs.add(URI.create(targetId));
                        }
                        List<Volume> targetVolumes = dbClient.queryObject(Volume.class, targetVolumeURIs);
                        for (Volume targetVolume : targetVolumes) {
                            targetVolume.setLinkStatus(getVolumeSRDFLinkStatusForSuccess().name());
                            targetVolume.setAccessState(getVolumeAccessStateForSuccess(targetVolume).name());
                        }
                        dbClient.updateAndReindexObject(targetVolumes);
                    }
                }
                dbClient.persistObject(volumes);
                _logger.info("Updated SRDF link status for volumes: {}", getIds());
            }
        } catch (Exception e) {
            _logger.info("Not updating volume SRDF link status for volumes: {}", getIds(), e);
        }
    }
}
