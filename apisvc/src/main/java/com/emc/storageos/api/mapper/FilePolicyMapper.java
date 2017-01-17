/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.SchedulePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.ReplicationSettingsRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.ScheduleRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.SnapshotSettingsRestRep;

public final class FilePolicyMapper {

    private FilePolicyMapper() {

    }

    public static FilePolicyRestRep map(FilePolicy from, DbClient dbClient) {

        FilePolicyRestRep resp = new FilePolicyRestRep();

        DbObjectMapper.mapDataObjectFields(from, resp);

        resp.setName(from.getFilePolicyName());
        resp.setDescription(from.getFilePolicyDescription());

        ScheduleRestRep schedule = new ScheduleRestRep();
        String dayOfWeek = from.getScheduleDayOfWeek();
        if (NullColumnValueGetter.isNotNullValue(dayOfWeek)) {
            schedule.setDayOfWeek(dayOfWeek);
        }

        schedule.setDayOfMonth(from.getScheduleDayOfMonth());
        schedule.setFrequency(from.getScheduleFrequency());
        schedule.setRepeat(from.getScheduleRepeat());
        schedule.setTime(from.getScheduleTime());

        resp.setSchedule(schedule);

        URI vpoolURI = from.getFilePolicyVpool();
        if (!NullColumnValueGetter.isNullURI(vpoolURI)) {
            VirtualPool vpool = dbClient.queryObject(VirtualPool.class, vpoolURI);
            resp.setVpool(DbObjectMapper.toNamedRelatedResource(ResourceTypeEnum.FILE_VPOOL,
                    vpoolURI, vpool.getLabel()));
        }

        String appliedAt = from.getApplyAt();
        if (NullColumnValueGetter.isNotNullValue(appliedAt)) {
            resp.setAppliedAt(appliedAt);
            StringSet assignedResources = from.getAssignedResources();
            if (assignedResources != null && !assignedResources.isEmpty()) {
                List<URI> resourceURIs = new ArrayList<>();
                for (Iterator<String> iterator = assignedResources.iterator(); iterator.hasNext();) {
                    String resourceId = iterator.next();
                    resourceURIs.add(URI.create(resourceId));
                }
                if (!resourceURIs.isEmpty()) {

                    FilePolicyApplyLevel level = FilePolicyApplyLevel.valueOf(appliedAt);
                    Class<? extends DataObject> clazz = null;
                    switch (level) {
                        case file_system:
                            clazz = FileShare.class;
                            break;
                        case vpool:
                            clazz = VirtualPool.class;
                            break;
                        case project:
                            clazz = Project.class;
                            break;
                        default:
                            break;
                    }

                    if (clazz != null) {
                        Iterator<? extends DataObject> resourceIterator = dbClient.queryIterativeObjects(clazz, resourceURIs);
                        while (resourceIterator.hasNext()) {
                            DataObject dataObject = resourceIterator.next();
                            if (FilePolicyApplyLevel.vpool == level) {
                                resp.addAssignedResource(DbObjectMapper.toNamedRelatedResource(ResourceTypeEnum.FILE_VPOOL,
                                        dataObject.getId(), dataObject.getLabel()));
                            } else {
                                resp.addAssignedResource(DbObjectMapper.toNamedRelatedResource(dataObject));
                            }
                        }
                    }
                }
            }
        }

        if (from.getPriority() != null) {
            resp.setPriority(from.getPriority());
        }

        if (from.getApplyOnTargetSite() != null) {
            resp.setApplyOnTargetSite(from.getApplyOnTargetSite());
        }

        String policyType = from.getFilePolicyType();
        resp.setType(policyType);
        if (FilePolicyType.file_replication.name().equals(policyType)) {
            ReplicationSettingsRestRep replicationSettings = new ReplicationSettingsRestRep();
            replicationSettings.setMode(from.getFileReplicationCopyMode());
            replicationSettings.setType(from.getFileReplicationType());
            resp.setReplicationSettings(replicationSettings);
        }

        if (FilePolicyType.file_snapshot.name().equals(policyType)) {
            SnapshotSettingsRestRep snapshotSettings = new SnapshotSettingsRestRep();
            String expiryType = from.getSnapshotExpireType();
            snapshotSettings.setExpiryType(expiryType);
            if (!SnapshotExpireType.NEVER.name().equalsIgnoreCase(expiryType)) {
                snapshotSettings.setExpiryTime(from.getSnapshotExpireTime());
            }
            if (from.getSnapshotNamePattern() != null) {
                snapshotSettings.setSnapshotNamePattern(from.getSnapshotNamePattern());
            }
            resp.setSnapshotSettings(snapshotSettings);
        }
        return resp;

    }
}
