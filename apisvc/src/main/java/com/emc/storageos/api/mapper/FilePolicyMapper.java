/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.mapper;

import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.SchedulePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.file.policy.FilePolicyRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.ReplicationSettingsRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.ScheduleRestRep;
import com.emc.storageos.model.file.policy.FilePolicyRestRep.SnapshotSettingsRestRep;

public class FilePolicyMapper {

    public static FilePolicyRestRep map(FilePolicy from) {

        FilePolicyRestRep resp = new FilePolicyRestRep();

        DbObjectMapper.mapDataObjectFields(from, resp);

        resp.setId(from.getId());
        resp.setName(from.getFilePolicyName());
        resp.setDescription(from.getFilePolicyDescription());

        ScheduleRestRep schedule = new ScheduleRestRep();
        String dayOfWeek = from.getScheduleDayOfWeek();
        if (!NullColumnValueGetter.isNullValue(dayOfWeek)) {
            schedule.setDayOfWeek(dayOfWeek);
        }

        schedule.setDayOfMonth(from.getScheduleDayOfMonth());
        schedule.setFrequency(from.getScheduleFrequency());
        schedule.setRepeat(from.getScheduleRepeat());

        resp.setSchedule(schedule);

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

            resp.setSnapshotSettings(snapshotSettings);
        }
        return resp;

    }
}
