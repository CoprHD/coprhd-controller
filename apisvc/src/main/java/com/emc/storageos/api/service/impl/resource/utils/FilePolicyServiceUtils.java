/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.api.service.impl.resource.FilePolicyService;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.AssignToResource;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.ScheduleFrequency;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * @author jainm15
 */
public class FilePolicyServiceUtils {
    private static final Logger _log = LoggerFactory.getLogger(FilePolicyService.class);
    private static final int MIN_SNAPSHOT_EXPIRE_TIME = 2;
    private static final int MAX_SNAPSHOT_EXPIRE_TIME = 10;
    private static final long MIN_SNAPSHOT_EXPIRE_SECONDS = 7200;
    private static final long MAX_SNAPSHOT_EXPIRE_SECONDS = 10 * 365 * 24 * 3600;

    /**
     * validates whether the schedule policy parameters are valid or not
     * 
     * @param policyScheduleparams - schedule policy parameters
     * @param schedulePolicy - schedulePolicy object to set schedule values
     * @param errorMsg - error message
     * @return true/false
     */
    public static boolean validatePolicySchdeuleParam(FilePolicyScheduleParams policyScheduleparams, FilePolicy schedulePolicy,
            StringBuilder errorMsg) {

        if (policyScheduleparams != null) {

            // check schedule frequency is valid or not
            ArgValidator.checkFieldValueFromEnum(policyScheduleparams.getScheduleFrequency().toUpperCase(), "schedule_frequency",
                    EnumSet.allOf(FilePolicy.ScheduleFrequency.class));

            // validating schedule repeat period
            if (policyScheduleparams.getScheduleRepeat() < 1) {
                errorMsg.append("required parameter schedule_repeat is missing or value: " + policyScheduleparams.getScheduleRepeat()
                        + " is invalid");
                return false;
            }

            // validating schedule time
            String period = " PM";
            int hour = 0, minute = 0;
            boolean isValid = true;
            if (policyScheduleparams.getScheduleTime().contains(":")) {
                String splitTime[] = policyScheduleparams.getScheduleTime().split(":");
                hour = Integer.parseInt(splitTime[0]);
                minute = Integer.parseInt(splitTime[1]);
                if (splitTime[0].startsWith("-") || splitTime[1].startsWith("-")) {
                    isValid = false;
                }
            } else {
                hour = Integer.parseInt(policyScheduleparams.getScheduleTime());
                minute = 0;
            }
            if (isValid && (hour >= 0 && hour < 24) && (minute >= 0 && minute < 60)) {
                if (hour < 12) {
                    period = " AM";
                }
            } else {
                errorMsg.append("Schedule time: " + policyScheduleparams.getScheduleTime() + " is invalid");
                return false;
            }

            ScheduleFrequency scheduleFreq = ScheduleFrequency.valueOf(policyScheduleparams.getScheduleFrequency().toUpperCase());
            switch (scheduleFreq) {

                case DAYS:
                    schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                    schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime() + period);
                    if (schedulePolicy.getScheduleDayOfWeek() != null && !schedulePolicy.getScheduleDayOfWeek().isEmpty()) {
                        schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                    }
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case WEEKS:
                    schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                    if (policyScheduleparams.getScheduleDayOfWeek() != null && !policyScheduleparams.getScheduleDayOfWeek().isEmpty()) {
                        List<String> weeks = Arrays.asList("monday", "tuesday", "wednesday", "thursday", "friday",
                                "saturday", "sunday");
                        if (weeks.contains(policyScheduleparams.getScheduleDayOfWeek().toLowerCase())) {
                            schedulePolicy.setScheduleDayOfWeek(policyScheduleparams.getScheduleDayOfWeek().toLowerCase());
                        } else {
                            errorMsg.append("Schedule day of week: " + policyScheduleparams.getScheduleDayOfWeek() + " is invalid");
                            return false;
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_week is missing or empty");
                        return false;
                    }
                    schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime() + period);
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case MONTHS:
                    if (policyScheduleparams.getScheduleDayOfMonth() != null
                            && policyScheduleparams.getScheduleDayOfMonth() > 0 && policyScheduleparams.getScheduleDayOfMonth() <= 31) {
                        schedulePolicy.setScheduleDayOfMonth((long) policyScheduleparams.getScheduleDayOfMonth());
                        schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                        schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime() + period);
                        if (schedulePolicy.getScheduleDayOfWeek() != null) {
                            schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_month is missing or value is invalid");
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public static void validateSnapshotPolicyParam(FileSnapshotPolicyParam param) {
        boolean isValidSnapshotExpire = false;

        // check snapshot expire type is valid or not
        ArgValidator.checkFieldValueFromEnum(param.getSnapshotExpireParams().getExpireType().toUpperCase(), "expire_type",
                EnumSet.allOf(FilePolicy.SnapshotExpireType.class));

        isValidSnapshotExpire = validateSnapshotExpireParam(param.getSnapshotExpireParams());
        if (!isValidSnapshotExpire) {
            int expireTime = param.getSnapshotExpireParams().getExpireValue();
            _log.error("Invalid schedule snapshot expire time {}. Try an expire time between {} hours to {} years",
                    expireTime, MIN_SNAPSHOT_EXPIRE_TIME, MAX_SNAPSHOT_EXPIRE_TIME);
            throw APIException.badRequests.invalidScheduleSnapshotExpireValue(expireTime, MIN_SNAPSHOT_EXPIRE_TIME,
                    MAX_SNAPSHOT_EXPIRE_TIME);
        }
    }

    /**
     * validates whether the snapshot expire parameters are valid or not
     * 
     * @param expireParam - snapshot expire parameters
     * @return true/false
     */
    private static boolean validateSnapshotExpireParam(FileSnapshotPolicyExpireParam expireParam) {
        long seconds = 0;
        int expireValue = expireParam.getExpireValue();
        SnapshotExpireType expireType = SnapshotExpireType.valueOf(expireParam.getExpireType().toUpperCase());
        switch (expireType) {
            case HOURS:
                seconds = TimeUnit.HOURS.toSeconds(expireValue);
                break;
            case DAYS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue);
                break;
            case WEEKS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue * 7);
                break;
            case MONTHS:
                seconds = TimeUnit.DAYS.toSeconds(expireValue * 30);
                break;
            case NEVER:
                return true;
            default:
                return false;
        }
        if (seconds >= MIN_SNAPSHOT_EXPIRE_SECONDS && seconds <= MAX_SNAPSHOT_EXPIRE_SECONDS) {
            return true;
        }
        return false;
    }

    /**
     * Check if the vpool supports provided policy type
     * 
     * @param filepolicy
     * @param virtualPool
     * @return
     */
    public static boolean validateVpoolSupportPolicyType(FilePolicy filepolicy, VirtualPool virtualPool) {
        FilePolicyType policyType = FilePolicyType.valueOf(filepolicy.getFilePolicyType());
        switch (policyType) {
            case file_snapshot:
                if (virtualPool.getScheduleSnapshots()) {
                    return true;
                }
            case file_replication:
                if (virtualPool.getFileReplicationSupported()) {
                    return true;
                }
            default:
                return false;
        }
    }

    public static List<FilePolicy> getAllApplicablePolices(DbClient dbClient, URI vpool, URI project) {
        List<FilePolicy> filePolicies = new ArrayList<FilePolicy>();
        List<URI> policyIds = dbClient.queryByType(FilePolicy.class, true);
        List<FilePolicy> filepolicies = dbClient.queryObject(FilePolicy.class, policyIds);

        for (FilePolicy filePolicy : filepolicies) {
            if (filePolicy.getApplyAt() != null) {
                FilePolicyApplyLevel appliedLevel = FilePolicyApplyLevel.valueOf(filePolicy.getApplyAt());

                switch (appliedLevel) {
                    case vpool:
                        if (filePolicy.getAssignedResources() != null && filePolicy.getAssignedResources().contains(vpool.toString())) {
                            filePolicies.add(filePolicy);
                        }
                        break;
                    case project:
                        if (filePolicy.getAssignedResources() != null && filePolicy.getAssignedResources().contains(project.toString())
                                && filePolicy.getFilePolicyVpool().toString().equals(vpool.toString())) {
                            filePolicies.add(filePolicy);
                        }
                        break;
                    case file_system:
                        // TODO Here logic has to be changed..
                        if (AssignToResource.all.name().equalsIgnoreCase(filePolicy.getApplyToFS())
                                && filePolicy.getFilePolicyVpool().toString().equals(vpool.toString())) {
                            filePolicies.add(filePolicy);
                        }
                        break;
                    default:
                        return null;
                }
            }
        }
        return filePolicies;
    }

    public static boolean updateReplicationTypeCapabilities(DbClient dbClient, VirtualPool vPool, Project project,
            FileShare fs, VirtualPoolCapabilityValuesWrapper capabilities, StringBuilder errorMsg) {

        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, FileReplicationType.NONE.name());
        List<FilePolicy> eligiblePolicies = getAllApplicablePolices(dbClient, vPool.getId(), project.getId());
        for (FilePolicy policy : eligiblePolicies) {
            if (FilePolicyType.file_replication.name().equalsIgnoreCase(policy.getFilePolicyType())) {
                // Update replication policy capabilities!!
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, policy.getFileReplicationType());
            }
        }
        return true;
    }

    public static boolean updatePolicyCapabilities(DbClient dbClient, VirtualArray currVArray, VirtualPool vPool, Project project,
            FileShare fs,
            VirtualPoolCapabilityValuesWrapper capabilities, StringBuilder errorMsg) {

        Boolean replicationSupported = false;
        List<FilePolicy> eligiblePolicies = getAllApplicablePolices(dbClient, vPool.getId(), project.getId());
        for (FilePolicy policy : eligiblePolicies) {
            if (FilePolicyType.file_replication.name().equalsIgnoreCase(policy.getFilePolicyType())) {
                if (replicationSupported) {
                    if (errorMsg == null) {
                        errorMsg = new StringBuilder();
                    }
                    // Single replication policy across vpool/project/fs
                    errorMsg.append("More than one replication policy could not be applied accross vpool/project/fs");
                    return false;

                } else {
                    // Update replication policy capabilities!!
                    capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, policy.getFileReplicationType());
                    capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_COPY_MODE, policy.getFileReplicationCopyMode());
                    if (vPool.getFrRpoType() != null) { // rpo type can be DAYS or HOURS
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_RPO_TYPE, vPool.getFrRpoType());
                    }
                    if (vPool.getFrRpoValue() != null) {
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_RPO_VALUE, vPool.getFrRpoValue());
                    }

                    // Update target varrys for file placement!!
                    if (policy.getReplicationTopologies() != null && !policy.getReplicationTopologies().isEmpty()) {
                        Set<String> targetVArrys = new HashSet<String>();
                        for (String strTopology : policy.getReplicationTopologies()) {
                            FileReplicationTopology dbTopology = dbClient.queryObject(FileReplicationTopology.class,
                                    URI.create(strTopology));
                            if (currVArray.getId().toString().equalsIgnoreCase(dbTopology.getSourceVArray().toString())) {
                                targetVArrys.addAll(dbTopology.getTargetVArrays());
                            }
                        }
                        if (targetVArrys.isEmpty()) {
                            errorMsg.append("Target VArry is not defined in replication topology for source varry "
                                    + currVArray.getId().toString());
                            return false;
                        }
                        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET_VARRAYS,
                                targetVArrys);

                    } else {
                        errorMsg.append("Replication Topology is not defined for policy " + policy.getFilePolicyName());
                        return false;
                    }
                    capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET_VPOOL, vPool.getId());
                }
                replicationSupported = true;
            }
        }

        if (vPool.getFileReplicationSupported() != replicationSupported) {
            errorMsg.append("No replication policy assigned at any level for virtual pool ").append(vPool.getLabel());
        }

        return false;
    }

    public static boolean vPoolSpecifiesFileReplication(FileShare fs, VirtualPool vPool, DbClient dbClient) {
        if (vPool == null) {
            vPool = dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        }
        Project project = dbClient.queryObject(Project.class, fs.getProject().getURI());
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        StringBuilder errorMsg = new StringBuilder();
        if (vPool.getFileReplicationSupported()
                && FilePolicyServiceUtils.updateReplicationTypeCapabilities(dbClient, vPool, project, fs, capabilities, errorMsg)) {
        } else {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, VirtualPool.FileReplicationType.NONE.name());
        }
        return vPoolSpecifiesFileReplication(capabilities);
    }

    public static boolean vPoolSpecifiesFileReplication(VirtualPoolCapabilityValuesWrapper capabilities) {
        return (capabilities.getFileReplicationType() != null && FileReplicationType.validFileReplication(capabilities
                .getFileReplicationType()));
    }
}
