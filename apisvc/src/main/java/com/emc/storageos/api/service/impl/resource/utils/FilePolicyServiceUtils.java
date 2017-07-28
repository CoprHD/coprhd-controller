/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.ArgValidator;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.FilePolicy;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyApplyLevel;
import com.emc.storageos.db.client.model.FilePolicy.FilePolicyType;
import com.emc.storageos.db.client.model.FilePolicy.ScheduleFrequency;
import com.emc.storageos.db.client.model.FilePolicy.SnapshotExpireType;
import com.emc.storageos.db.client.model.FileReplicationTopology;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Task;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.VirtualPool.FileReplicationType;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.fileorchestrationcontroller.FileOrchestrationUtils;
import com.emc.storageos.model.file.policy.FilePolicyScheduleParams;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyExpireParam;
import com.emc.storageos.model.file.policy.FileSnapshotPolicyParam;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * @author jainm15
 */
public class FilePolicyServiceUtils {
    private static final Logger _log = LoggerFactory.getLogger(FilePolicyServiceUtils.class);
    private static final int MIN_SNAPSHOT_EXPIRE_TIME = 2;
    private static final int MAX_SNAPSHOT_EXPIRE_TIME = 10;
    private static final long MIN_SNAPSHOT_EXPIRE_SECONDS = 7200;
    private static final long MAX_SNAPSHOT_EXPIRE_SECONDS = 10 * 365 * 24 * 3600;

    private FilePolicyServiceUtils() {

    }

    /**
     * validates whether the schedule policy parameters are valid or not
     * 
     * @param policyScheduleparams - schedule policy parameters
     * @param schedulePolicy - schedulePolicy object to set schedule values
     * @param errorMsg - error message
     * @return true/false
     */
    public static boolean validateAndUpdatePolicyScheduleParam(FilePolicyScheduleParams policyScheduleparams, FilePolicy schedulePolicy,
            StringBuilder errorMsg) {

        if (policyScheduleparams != null) {

            // check schedule frequency is valid or not
            if (policyScheduleparams.getScheduleFrequency() == null) {
                errorMsg.append("required parameter schedule_frequency is missing");
                return false;
            }
            ArgValidator.checkFieldValueFromEnum(policyScheduleparams.getScheduleFrequency().toUpperCase(), "schedule_frequency",
                    EnumSet.allOf(FilePolicy.ScheduleFrequency.class));

            // validating schedule repeat period
            if (policyScheduleparams.getScheduleRepeat() == null || policyScheduleparams.getScheduleRepeat() < 1) {
                errorMsg.append("required parameter schedule_repeat is missing or value: " + policyScheduleparams.getScheduleRepeat()
                        + " is invalid");
                return false;
            }
            if (policyScheduleparams.getScheduleTime() == null) {
                errorMsg.append("required parameter schedule_time is missing");
                return false;
            }

            // Convert time from 24 Hours to 12 Hours using SimpleDateFormat class
            String time = policyScheduleparams.getScheduleTime();
            DateFormat sdf24 = new SimpleDateFormat("HH:mm");
            DateFormat sdf12 = new SimpleDateFormat("hh:mm a");
            try {
                Date date = sdf24.parse(time);
                String time12 = sdf12.format(date);
                policyScheduleparams.setScheduleTime(time12);
            } catch (ParseException e) {
                errorMsg.append("Schedule time: " + time + " is invalid");
                return false;
            }

            ScheduleFrequency scheduleFreq = ScheduleFrequency.valueOf(policyScheduleparams.getScheduleFrequency().toUpperCase());
            switch (scheduleFreq) {

                case MINUTES:
                case HOURS:
                case DAYS:
                    schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                    schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime());
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
                    schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime());
                    if (schedulePolicy.getScheduleDayOfMonth() != null) {
                        schedulePolicy.setScheduleDayOfMonth(0L);
                    }
                    break;
                case MONTHS:
                    if (policyScheduleparams.getScheduleDayOfMonth() != null
                            && policyScheduleparams.getScheduleDayOfMonth() > 0 && policyScheduleparams.getScheduleDayOfMonth() <= 31) {
                        schedulePolicy.setScheduleDayOfMonth((long) policyScheduleparams.getScheduleDayOfMonth());
                        schedulePolicy.setScheduleRepeat((long) policyScheduleparams.getScheduleRepeat());
                        schedulePolicy.setScheduleTime(policyScheduleparams.getScheduleTime());
                        if (schedulePolicy.getScheduleDayOfWeek() != null) {
                            schedulePolicy.setScheduleDayOfWeek(NullColumnValueGetter.getNullStr());
                        }
                    } else {
                        errorMsg.append("Required parameter schedule_day_of_month is missing or value is invalid");
                        return false;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public static void validateSnapshotPolicyExpireParam(FileSnapshotPolicyParam param) {
        boolean isValidSnapshotExpire = false;
        if (param.getSnapshotExpireParams().getExpireType() != null) {
            // check snapshot expire type is valid or not
            ArgValidator.checkFieldValueFromEnum(param.getSnapshotExpireParams().getExpireType().toUpperCase(), "expire_type",
                    EnumSet.allOf(FilePolicy.SnapshotExpireType.class));

            isValidSnapshotExpire = validateSnapshotExpireParam(param.getSnapshotExpireParams());
        }
        if (!isValidSnapshotExpire) {
            int expireTime = param.getSnapshotExpireParams().getExpireValue();
            _log.error("Invalid schedule snapshot expire time {}. Try an expire time between {} hours to {} years",
                    expireTime, MIN_SNAPSHOT_EXPIRE_TIME, MAX_SNAPSHOT_EXPIRE_TIME);
            throw APIException.badRequests.invalidScheduleSnapshotExpireValue(expireTime, MIN_SNAPSHOT_EXPIRE_TIME,
                    MAX_SNAPSHOT_EXPIRE_TIME);
        }
    }

    /**
     * Validates whether the snapshot or replication schedule policy parameters are valid or not
     * 
     * @param policyScheduleparams - schedule policy parameters
     * @param schedulePolicy - schedulePolicy object to set schedule values
     * @param errorMsg - error message
     * @return true/false
     */
    public static boolean validatePolicyScheduleParam(FilePolicyScheduleParams policyScheduleparams, FilePolicy schedulePolicy,
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
            int hour;
            int minute;
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
                case MINUTES:
                    break;
                case HOURS:
                    break;
                case DAYS:
                    break;
                case WEEKS:
                    if (policyScheduleparams.getScheduleDayOfWeek() != null && !policyScheduleparams.getScheduleDayOfWeek().isEmpty()) {
                        List<String> weeks = Arrays.asList("monday", "tuesday", "wednesday", "thursday", "friday",
                                "saturday", "sunday");
                        if (!weeks.contains(policyScheduleparams.getScheduleDayOfWeek().toLowerCase())) {
                            errorMsg.append("Schedule day of week: " + policyScheduleparams.getScheduleDayOfWeek() + " is invalid");
                            return false;
                        }
                    } else {
                        errorMsg.append("required parameter schedule_day_of_week is missing or empty");
                        return false;
                    }
                    break;
                case MONTHS:
                    if (policyScheduleparams.getScheduleDayOfMonth() != null
                            && policyScheduleparams.getScheduleDayOfMonth() <= 0 || policyScheduleparams.getScheduleDayOfMonth() > 31) {
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

    /**
     * validates whether the snapshot expire parameters are valid or not
     * 
     * @param expireParam - snapshot expire parameters
     * @return true/false
     */
    private static boolean validateSnapshotExpireParam(FileSnapshotPolicyExpireParam expireParam) {
        long seconds;
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
    public static void validateVpoolSupportPolicyType(FilePolicy filepolicy, VirtualPool virtualPool) {
        FilePolicyType policyType = FilePolicyType.valueOf(filepolicy.getFilePolicyType());
        StringBuilder errorMsg = new StringBuilder();
        switch (policyType) {
            case file_snapshot:
                if (virtualPool.getScheduleSnapshots()) {
                    break;
                } else {
                    errorMsg.append("Provided vpool :" + virtualPool.getLabel() + " doesn't support file snapshot policy.");
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            case file_replication:
                if (virtualPool.getFileReplicationSupported()) {
                    break;
                } else {
                    errorMsg.append("Provided vpool :" + virtualPool.getLabel() + " doesn't support file replication policy");
                    _log.error(errorMsg.toString());
                    throw APIException.badRequests.invalidFilePolicyAssignParam(filepolicy.getFilePolicyName(), errorMsg.toString());
                }
            default:
                return;
        }
    }

    /**
     * update the replication type into capabilities list from applicable replication policy
     * 
     * @param dbClient
     * @param vPool
     * @param project
     * @param fs
     * @param capabilities
     * @param errorMsg
     * @return true/false
     */
    public static void updateReplicationTypeCapabilities(DbClient dbClient, VirtualPool vPool, Project project,
            FileShare fs, VirtualPoolCapabilityValuesWrapper capabilities, StringBuilder errorMsg) {

        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, FileReplicationType.NONE.name());
        List<FilePolicy> eligiblePolicies = FileOrchestrationUtils.getReplicationPolices(dbClient, vPool, project, fs);
        if (eligiblePolicies != null && !eligiblePolicies.isEmpty()) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, eligiblePolicies.get(0).getFileReplicationType());
        }
        return;
    }

    /**
     * update the replication policy capabilities into capabilities list from applicable replication policy
     * only a single replication policy across vpool/prject/fs levels
     * otherwise throw appropriate exception.
     * 
     * 
     * @param dbClient
     * @param currVArray
     * @param vPool
     * @param project
     * @param fs
     * @param capabilities
     * @param errorMsg
     * @return true/false
     */
    public static boolean updatePolicyCapabilities(DbClient dbClient, VirtualArray currVArray, VirtualPool vPool, Project project,
            FileShare fs,
            VirtualPoolCapabilityValuesWrapper capabilities, StringBuilder errorMsg) {

        List<FilePolicy> eligiblePolicies = FileOrchestrationUtils.getReplicationPolices(dbClient, vPool, project, fs);
        if (eligiblePolicies != null && !eligiblePolicies.isEmpty()) {
            if (eligiblePolicies.size() > 1) {
                if (errorMsg == null) {
                    errorMsg = new StringBuilder();
                }
                // Single replication policy across vpool/project/fs
                errorMsg.append("More than one replication policy could not be applied accross vpool/project/fs");
                return false;
            }

            FilePolicy policy = eligiblePolicies.get(0);
            // Update replication policy capabilities!!
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, policy.getFileReplicationType());
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_COPY_MODE, policy.getFileReplicationCopyMode());
            if (vPool.getFrRpoType() != null) { // rpo type can be DAYS or HOURS
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_RPO_TYPE, vPool.getFrRpoType());
            }
            if (vPool.getFrRpoValue() != null) {
                capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_RPO_VALUE, vPool.getFrRpoValue());
            }
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_APPLIED_AT, policy.getApplyAt());

            // Update target varrys for file placement!!
            Set<String> targetVArrys = new HashSet<String>();
            if (policy.getFileReplicationType().equalsIgnoreCase(FileReplicationType.REMOTE.name())) {
                if (policy.getReplicationTopologies() != null && !policy.getReplicationTopologies().isEmpty()) {

                    for (String strTopology : policy.getReplicationTopologies()) {
                        FileReplicationTopology dbTopology = dbClient.queryObject(FileReplicationTopology.class,
                                URI.create(strTopology));
                        if (currVArray.getId().toString().equalsIgnoreCase(dbTopology.getSourceVArray().toString())) {
                            targetVArrys.addAll(dbTopology.getTargetVArrays());
                            break;
                        }
                    }
                    if (targetVArrys.isEmpty()) {
                        errorMsg.append("Target VArry is not defined in replication topology for source varry "
                                + currVArray.getId().toString());
                        return false;
                    }
                    capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET_VARRAYS,
                            targetVArrys);

                    capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TARGET_VPOOL,
                            vPool.getId());

                } else {
                    errorMsg.append("Replication Topology is not defined for policy " + policy.getFilePolicyName());
                    return false;
                }
            } else {
                targetVArrys.add(currVArray.getId().toString());
            }
            return true;

        } else if (vPool.getFileReplicationSupported()) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, FileReplicationType.NONE.name());
            errorMsg.append("No replication policy assigned at any level for virtual pool ").append(vPool.getLabel());
            _log.warn(errorMsg.toString());
            return true;
        }
        return false;
    }

    /**
     * Verifies the vpool supports replication and has replication policy
     * 
     * 
     * @param dbClient
     * @param vPool
     * @param fs
     * @return true/false
     */
    public static boolean vPoolSpecifiesFileReplication(FileShare fs, VirtualPool vPool, DbClient dbClient) {
        if (vPool == null) {
            vPool = dbClient.queryObject(VirtualPool.class, fs.getVirtualPool());
        }
        Project project = dbClient.queryObject(Project.class, fs.getProject().getURI());
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.FILE_REPLICATION_TYPE, FileReplicationType.NONE.name());
        StringBuilder errorMsg = new StringBuilder();
        if (vPool.getFileReplicationSupported()) {
            updateReplicationTypeCapabilities(dbClient, vPool, project, fs, capabilities, errorMsg);
        }
        return vPoolSpecifiesFileReplication(capabilities);
    }

    /**
     * Verifies the vpool supports replication capability
     * 
     * 
     * @param capabilities
     * @return true/false
     */
    public static boolean vPoolSpecifiesFileReplication(VirtualPoolCapabilityValuesWrapper capabilities) {
        return (capabilities.getFileReplicationType() != null && FileReplicationType.validFileReplication(capabilities
                .getFileReplicationType()));
    }

    /**
     * Verifies the vpool assigned policies support replication capability
     * 
     * 
     * @param dbClient
     * @param vpoolURI
     * @return true/false
     */
    public static boolean vPoolHasReplicationPolicy(DbClient dbClient, URI vpoolURI) {
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        if (vPool != null && vPool.getFilePolicies() != null && !vPool.getFilePolicies().isEmpty()) {
            for (String strPolicy : vPool.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (policy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())) {
                    _log.info("Replication policy found for vpool {} ", vPool.getLabel());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifies the project assigned policies in combination with virtual pool support replication capability
     * 
     * 
     * @param dbClient
     * @param vpoolURI
     * @param projectURI
     * @return true/false
     */
    public static boolean projectHasReplicationPolicy(DbClient dbClient, URI projectURI, URI vpoolURI) {
        // vpool has replication policy!!!
        if (vPoolHasReplicationPolicy(dbClient, vpoolURI)) {
            return true;
        }
        Project project = dbClient.queryObject(Project.class, projectURI);
        if (project != null && project.getFilePolicies() != null && !project.getFilePolicies().isEmpty()) {
            for (String strPolicy : project.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (policy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())
                        && !NullColumnValueGetter.isNullURI(policy.getFilePolicyVpool()) && vpoolURI != null
                        && policy.getFilePolicyVpool().toString().equalsIgnoreCase(vpoolURI.toString())) {
                    _log.info("Replication policy found for vpool {} and project {}", vpoolURI, project.getLabel());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifies the file system assigned policies in combination with virtual pool/project support replication capability
     * 
     * 
     * @param dbClient
     * @param vpoolURI
     * @param projectURI
     * @param fsUri
     * @return true/false
     */
    public static boolean fsHasReplicationPolicy(DbClient dbClient, URI vpoolURI, URI projectURI, URI fsUri) {
        // vpool/project has replication policy!!!
        if (vPoolHasReplicationPolicy(dbClient, vpoolURI) || projectHasReplicationPolicy(dbClient, projectURI, vpoolURI)) {
            return true;
        }
        // file system has replication policy!!
        FileShare fs = dbClient.queryObject(FileShare.class, fsUri);
        if (fs != null && fs.getFilePolicies() != null && !fs.getFilePolicies().isEmpty()) {
            for (String strPolicy : fs.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (policy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_replication.name())) {
                    _log.info("Replication policy {} found at fs {} ", policy.getFilePolicyName(), fs.getLabel());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Verifies the vpool assigned policies with similar schedule as given policy
     * 
     * 
     * @param dbClient
     * @param vpoolURI
     * @param newPolicy
     * @return true/false
     */
    public static boolean vPoolHasSnapshotPolicyWithSameSchedule(DbClient dbClient, URI vpoolURI, FilePolicy newPolicy) {
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vpoolURI);
        if (vPool != null && vPool.getFilePolicies() != null && !vPool.getFilePolicies().isEmpty()) {
            for (String strPolicy : vPool.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (policy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_snapshot.name())) {
                    if (policy.getScheduleFrequency().equalsIgnoreCase(newPolicy.getScheduleFrequency())
                            && policy.getScheduleRepeat().longValue() == newPolicy.getScheduleRepeat().longValue()) {
                        _log.info("Snapshot policy found for vpool {} with similar schedule ", vPool.getLabel());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Verifies the project assigned policies with similar schedule as given policy
     * 
     * 
     * @param dbClient
     * @param vpoolURI
     * @param projectURI
     * @param newPolicy
     * @return true/false
     */
    public static boolean projectHasSnapshotPolicyWithSameSchedule(DbClient dbClient, URI projectURI, URI vpoolURI, FilePolicy newPolicy) {
        Project project = dbClient.queryObject(Project.class, projectURI);
        if (project != null && project.getFilePolicies() != null && !project.getFilePolicies().isEmpty()) {
            for (String strPolicy : project.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (policy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_snapshot.name())
                        && !NullColumnValueGetter.isNullURI(policy.getFilePolicyVpool()) && vpoolURI != null
                        && policy.getFilePolicyVpool().toString().equalsIgnoreCase(vpoolURI.toString())) {
                    if (policy.getScheduleFrequency().equalsIgnoreCase(newPolicy.getScheduleFrequency())
                            && policy.getScheduleRepeat().longValue() == newPolicy.getScheduleRepeat().longValue()) {
                        _log.info("Snapshot policy found for project {} with similar schedule ", project.getLabel());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Verifies the fs assigned policies with similar schedule as given policy
     * 
     * 
     * @param dbClient
     * @param vpoolURI
     * @param newPolicy
     * @return true/false
     */
    public static boolean fsHasSnapshotPolicyWithSameSchedule(DbClient dbClient, URI fsUri, FilePolicy newPolicy) {
        FileShare fs = dbClient.queryObject(FileShare.class, fsUri);
        if (fs != null && fs.getFilePolicies() != null && !fs.getFilePolicies().isEmpty()) {
            for (String strPolicy : fs.getFilePolicies()) {
                FilePolicy policy = dbClient.queryObject(FilePolicy.class, URI.create(strPolicy));
                if (policy.getFilePolicyType().equalsIgnoreCase(FilePolicyType.file_snapshot.name())) {
                    if (policy.getScheduleFrequency().equalsIgnoreCase(newPolicy.getScheduleFrequency())
                            && policy.getScheduleRepeat().longValue() == newPolicy.getScheduleRepeat().longValue()) {
                        _log.info("Snapshot policy found for fs {} with similar schedule ", fs.getLabel());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static void updateTaskTenant(DbClient dbClient, FilePolicy policy, String policyAction, Task task,
            URI tenantId) {
        if (task != null) {
            if (policyAction != null && policyAction.equalsIgnoreCase("assign") || policyAction.equalsIgnoreCase("unassign")) {
                if (policy.getApplyAt() != null && FilePolicyApplyLevel.vpool.name().equalsIgnoreCase(policy.getApplyAt())) {
                    task.setTenant(TenantOrg.SYSTEM_TENANT);
                } else {
                    task.setTenant(tenantId);
                }
            } else {
                task.setTenant(TenantOrg.SYSTEM_TENANT);
            }
            dbClient.updateObject(task);
        }
    }

    /**
     * Resets the filesystem relation due to replication policy assigned at higher level
     * Only to be used when delete FS is FULL type
     * 
     * @param _dbClient
     * @param fileshare
     */
    public static void resetReplicationFileSystemsRelation(DbClient _dbClient, FileShare fileshare) {
        List<FileShare> modifiedFileshares = new ArrayList<>();
        if (fileshare.getPersonality() != null) {
            fileshare.setMirrorStatus(NullColumnValueGetter.getNullStr());
            fileshare.setAccessState(NullColumnValueGetter.getNullStr());
            fileshare.setPersonality(NullColumnValueGetter.getNullStr());
            if (fileshare.getMirrorfsTargets() != null && !fileshare.getMirrorfsTargets().isEmpty()) {
                StringSet targets = fileshare.getMirrorfsTargets();
                for (String strTargetFs : targets) {
                    FileShare targetFs = _dbClient.queryObject(FileShare.class, URI.create(strTargetFs));
                    targetFs.setMirrorStatus(NullColumnValueGetter.getNullStr());
                    targetFs.setAccessState(NullColumnValueGetter.getNullStr());
                    targetFs.setParentFileShare(NullColumnValueGetter.getNullNamedURI());
                    targetFs.setPersonality(NullColumnValueGetter.getNullStr());
                    modifiedFileshares.add(targetFs);
                }
                targets.clear();
                fileshare.setMirrorfsTargets(targets);
            }
        }
        modifiedFileshares.add(fileshare);
        if (!modifiedFileshares.isEmpty()) {
            _dbClient.updateObject(modifiedFileshares);
        }
    }
}
