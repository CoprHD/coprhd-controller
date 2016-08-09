/*
 * Copyright (c) 2015 EMC Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.block;

import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COPYMODE;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_COUNT;
import static com.emc.sa.service.ServiceParams.LINKED_SNAPSHOT_NAME;
import static com.emc.sa.service.ServiceParams.NAME;
import static com.emc.sa.service.ServiceParams.READ_ONLY;
import static com.emc.sa.service.ServiceParams.STORAGE_TYPE;
import static com.emc.sa.service.ServiceParams.TYPE;
import static com.emc.sa.service.ServiceParams.VOLUMES;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import com.emc.sa.asset.providers.BlockProvider;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.sa.engine.bind.Param;
import com.emc.sa.engine.service.Service;
import com.emc.sa.model.dao.ModelClient;
import com.emc.sa.service.vipr.ViPRService;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshot;
import com.emc.sa.service.vipr.block.tasks.CreateBlockSnapshotSession;
import com.emc.sa.service.vipr.block.tasks.DeactivateBlockSnapshot;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList.NamedElement;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.uimodels.ScheduledEvent;
import com.emc.storageos.db.client.model.uimodels.RetainedResource;
import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.emc.storageos.model.block.VolumeDeleteTypeEnum;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.core.util.ResourceUtils;

@Service("CreateBlockSnapshot")
public class CreateBlockSnapshotService extends ViPRService {

    @Param(value = STORAGE_TYPE, required = false)
    protected String storageType;

    @Param(VOLUMES)
    protected List<String> volumeIds;

    @Param(NAME)
    protected String nameParam;

    @Param(value = TYPE, required = false)
    protected String type;

    @Param(value = READ_ONLY, required = false)
    protected Boolean readOnly;
    
    @Param(value = LINKED_SNAPSHOT_NAME, required = false)
    protected String linkedSnapshotName;
    
    @Param(value = LINKED_SNAPSHOT_COUNT, required = false)
    protected Integer linkedSnapshotCount;
        
    @Param(value = LINKED_SNAPSHOT_COPYMODE, required = false)
    protected String linkedSnapshotCopyMode;

    private List<BlockObjectRestRep> volumes;

    @Override
    public void precheck() {
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            volumes = new ArrayList<>();
            volumes = BlockStorageUtils.getBlockResources(uris(volumeIds));
        }
            
        if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)
                || BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {               
            if (linkedSnapshotName != null && !linkedSnapshotName.isEmpty()) {
                // If trying to create a Snapshot Session and the optional linkedSnapshotName 
                // is populated, make sure that linkedSnapshotCount > 0.
                if (linkedSnapshotCount == null || linkedSnapshotCount.intValue() <= 0) {
                    ExecutionUtils.fail("failTask.CreateBlockSnapshot.linkedSnapshotCount.precheck", new Object[] {}, new Object[] {});
                }
                // Ensure that copy mode is selected
                if (linkedSnapshotCopyMode == null
                        || !(BlockProvider.LINKED_SNAPSHOT_COPYMODE_VALUE.equals(linkedSnapshotCopyMode)
                                || BlockProvider.LINKED_SNAPSHOT_NOCOPYMODE_VALUE.equals(linkedSnapshotCopyMode))) {
                    ExecutionUtils.fail("failTask.CreateBlockSnapshot.linkedSnapshotCopyMode.precheck", new Object[] {}, new Object[] {});
                }
            }
        }
    }

    @Override
    public void execute() {
    	Tasks<? extends DataObjectRestRep> tasks = null;
        if (ConsistencyUtils.isVolumeStorageType(storageType)) {
            for (BlockObjectRestRep volume : volumes) {
                checkRetainedSnapshots(volume.getId().toString());
                
                if (BlockProvider.SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                    tasks = execute(new CreateBlockSnapshotSession(volume.getId(), nameParam, 
                                                                    linkedSnapshotName, linkedSnapshotCount, linkedSnapshotCopyMode));
                } else {
                    tasks = execute(new CreateBlockSnapshot(volume.getId(), type, nameParam, readOnly));
                    
                }
                addAffectedResources(tasks);
                addRetainedResources(volume.getId().toString(), tasks);
            }
        } else {
            for (String consistencyGroupId : volumeIds) {
                checkRetainedSnapshots(consistencyGroupId);
                
                if (BlockProvider.CG_SNAPSHOT_SESSION_TYPE_VALUE.equals(type)) {
                    tasks = ConsistencyUtils.createSnapshotSession(uri(consistencyGroupId), nameParam, 
                                                                    linkedSnapshotName, linkedSnapshotCount, linkedSnapshotCopyMode);
                } else {
                    tasks = ConsistencyUtils.createSnapshot(uri(consistencyGroupId), nameParam, readOnly);
                }
                addAffectedResources(tasks);
                addRetainedResources(consistencyGroupId, tasks);
            }
        }
    }

    protected boolean isRetentionRequired() {
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        if (event == null) {
            return false;
        }
        Integer maxNumOfCopies = event.getMaxNumOfRetainedCopies();
        if (maxNumOfCopies == null) {
            return false;
        }
        return true;
    }
        
    private void addRetainedResources(String sourceResourceId, Tasks<? extends DataObjectRestRep> tasks) {
        if (!isRetentionRequired()) {
            return;
        }
        if (tasks == null) {
            return;
        }
        
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        RetainedResource retention = new RetainedResource();
        retention.setScheduledEventId(event.getId());
        retention.setResourceId(uri(sourceResourceId));
        StringSet retainedResource = new StringSet();
        retention.setRetainedResourceIds(retainedResource);
        
    	for (Task<? extends DataObjectRestRep> task : tasks.getTasks()) {
    	    if (task.getAssociatedResources() != null
                    && !task.getAssociatedResources().isEmpty()) {
                for (URI id : ResourceUtils.refIds(task.getAssociatedResources())) {
                    retainedResource.add(id.toString());
                    info("Add task associated resource %s to scheduled event %s", id, event.getId());
                }
            } else {
    	        URI resourceId = task.getResourceId();
                if (resourceId != null) {
                	retainedResource.add(resourceId.toString());
                	info("Add task resource %s to scheduled event %s", resourceId, event.getId());
                }
            }
        }
    	
    	getModelClient().save(retention);
    }
    
    private void checkRetainedSnapshots(String resourceId) {
        if (!isRetentionRequired()) {
            return;
        }
        RetainedResource retainedResource = findObsoleteResource(resourceId);
    	info("Deactivating block snapshot %s since it exceeds max snapshots allowed", retainedResource.getId());
    	for (String obsoleteResourceId : retainedResource.getRetainedResourceIds()) {
        	if (ConsistencyUtils.isVolumeStorageType(storageType)) {
        	    execute(new DeactivateBlockSnapshot(uri(obsoleteResourceId), VolumeDeleteTypeEnum.FULL));
        	} else {
        	    ConsistencyUtils.removeSnapshot(uri(resourceId), uri(obsoleteResourceId));
        	}
    	}
    	getModelClient().delete(retainedResource);
        info("Remove block snapshot %s from retention list of scheduled event", retainedResource.getId());
    }
    
    private RetainedResource findObsoleteResource(String resourceId) {
        ScheduledEvent event = ExecutionUtils.currentContext().getScheduledEvent();
        Integer maxNumOfCopies = event.getMaxNumOfRetainedCopies(); 
        
        ModelClient modelClient = getModelClient();
        List<NamedElement> retentionList = modelClient.findBy(RetainedResource.class, "scheduledEventId", event.getId());
        
        List<RetainedResource> retentions = new ArrayList<RetainedResource>();
        for (NamedElement uri : retentionList) {
            RetainedResource retention = modelClient.findById(RetainedResource.class, uri.getId());
            if (retention.getResourceId().toString().equals(resourceId)) {
                retentions.add(retention);
            }
        }
        if (retentions.size() >= maxNumOfCopies) {
            // find the oldest one to remove
            Calendar oldest = Calendar.getInstance();
            RetainedResource oldestResource = null;
            for(RetainedResource resource : retentions) {
                Calendar creationTime = resource.getCreationTime();
                if (creationTime.before(oldest)) {
                    oldestResource = resource;
                    oldest = creationTime;
                }
            }
            return oldestResource;
        }
        return null;
    }
}
