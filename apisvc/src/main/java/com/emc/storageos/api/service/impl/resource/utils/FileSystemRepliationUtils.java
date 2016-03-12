package com.emc.storageos.api.service.impl.resource.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.impl.resource.FileService;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.FileShare.MirrorStatus;
import com.emc.storageos.db.client.model.FileShare.PersonalityTypes;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.volumecontroller.FileControllerConstants;

public class FileSystemRepliationUtils {

    private static final Logger _log = LoggerFactory.getLogger(FileService.class);

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param currentVpool
     *            the source virtual pool
     * @param newVpool
     *            the target virtual pool
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * 
     */
    public static boolean isSupportedFileReplicationCreate(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff) {
        _log.info(String.format("Checking isSupportedFileReplicationCreate for Fs [%s] with vpool [%s]...", fs.getLabel(),
                currentVpool.getLabel()));

        if (!doBasicMirrorValidation(fs, currentVpool, notSuppReasonBuff)) {
            return false;
        }
        // File system should not be the active source file system!!
        if (fs.getPersonality() != null
                && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.name())
                && !MirrorStatus.DETACHED.name().equalsIgnoreCase(fs.getMirrorStatus())) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request is an active source file system %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }

        // File system should not have any active mirror copies!!
        if (fs.getMirrorfsTargets() != null
                && !fs.getMirrorfsTargets().isEmpty()) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request has active target file system %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param currentVpool
     *            the source virtual pool
     * @param newVpool
     *            the target virtual pool
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    public static boolean validateDeleteMirrorCopies(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff) {
        _log.info(String.format("Checking validateDeleteMirrorCopies for Fs [%s] ", fs.getLabel()));

        if (!doBasicMirrorValidation(fs, currentVpool, notSuppReasonBuff)) {
            return false;
        }
        // File system should not be the failover state
        // Failover state, the mirror copy would be in production!!!
        if (fs.getPersonality() != null
                && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.SOURCE.name())
                && (MirrorStatus.FAILED_OVER.name().equalsIgnoreCase(fs.getMirrorStatus())
                        || MirrorStatus.SUSPENDED.name().equalsIgnoreCase(fs.getMirrorStatus()))) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request is in active or failover state %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }

        // File system should not have any active mirror copies!!
        if (fs.getMirrorfsTargets() == null
                || fs.getMirrorfsTargets().isEmpty()) {
            notSuppReasonBuff
                    .append(String
                            .format("File system given in request has no active target file system %s.",
                                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Checks to see if the file replication operation is supported.
     * 
     * @param fs file share object
     * @param currentVpool source virtual pool
     * @param notSuppReasonBuff the not supported reason string buffer
     * @param operation mirror operation to be checked
     */
    public static boolean validateMirrorOperationSupported(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff,
            String operation) {

        _log.info("Checking if mirror operation {} is supported for file system {} ", operation, fs.getLabel());

        if (!doBasicMirrorValidation(fs, currentVpool, notSuppReasonBuff)) {
            return false;
        }
        String currentMirrorStatus = fs.getMirrorStatus();
        boolean isSupported = false;

        switch (operation) {

            // Refresh operation can be performed without any check.
            case "refresh":
                isSupported = true;
                break;

            // START operation can be performed only if Mirror status is UNKNOWN or DETACHED
            case "start":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.UNKNOWN.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.DETACHED.toString()))
                    isSupported = true;
                break;

            // STOP operation can be performed only if Mirror status is SYNCHRONIZED or IN_SYNC
            case "stop":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.SYNCHRONIZED.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.IN_SYNC.toString()))
                    isSupported = true;
                break;

            // PAUSE operation can be performed only if Mirror status is SYNCHRONIZED or IN_SYNC
            case "pause":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.SYNCHRONIZED.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.IN_SYNC.toString()))
                    isSupported = true;
                break;

            // RESUME operation can be performed only if Mirror status is SUSPENDED.
            case "resume":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.SUSPENDED.toString()))
                    isSupported = true;
                break;

            // Fail over can be performed if Mirror status is NOT UNKNOWN or FAILED_OVER.
            case "failover":
                if (!(currentMirrorStatus.equalsIgnoreCase(MirrorStatus.UNKNOWN.toString())
                        || currentMirrorStatus.equalsIgnoreCase(MirrorStatus.FAILED_OVER.toString())))
                    isSupported = true;
                break;

            // Fail back can be performed only if Mirror status is FAILED_OVER.
            case "failback":
                if (currentMirrorStatus.equalsIgnoreCase(MirrorStatus.FAILED_OVER.toString()))
                    isSupported = true;
                break;
        }
        notSuppReasonBuff.append(String.format(" : file system %s is in %s state", fs.getLabel(), currentMirrorStatus.toUpperCase()));
        return isSupported;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param fs
     * @param currentVpool
     *            the source virtual pool
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    public static boolean doBasicMirrorValidation(FileShare fs, VirtualPool currentVpool, StringBuffer notSuppReasonBuff) {

        // file system virtual pool must be enabled with replication..
        if (!VirtualPool.vPoolSpecifiesFileReplication(currentVpool)) {
            notSuppReasonBuff.append(String.format("File replication is not enabled in virtual pool - %s"
                    + " of the requested file system -%s ", currentVpool.getLabel(), fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }

        // File system should not be the target file system..
        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            notSuppReasonBuff.append(String.format("File system - %s given in request is an active Target file system.",
                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return false;
        }
        return true;
    }

    /**
     * Checks to see if the file replication change is supported.
     * 
     * @param fs
     * @param notSuppReasonBuff
     *            the not supported reason string buffer
     * @return
     */
    public static boolean filesystemHasActiveReplication(FileShare fs, StringBuffer notSuppReasonBuff,
            String deleteType, boolean forceDelete) {

        // File system should not be the target file system..
        if (fs.getPersonality() != null && fs.getPersonality().equalsIgnoreCase(PersonalityTypes.TARGET.name())) {
            notSuppReasonBuff.append(String.format("File system - %s given in request is an active Target file system.",
                    fs.getLabel()));
            _log.info(notSuppReasonBuff.toString());
            return true;
        }

        // File system should not have active replication targets!!
        // For resource delete (forceDelete=false)
        // For VIPR_ONLY type, till we support ingestion of replication file systems
        // avoid deleting file systems if it has active mirrors!!
        if (forceDelete == false || FileControllerConstants.DeleteTypeEnum.VIPR_ONLY.toString().equalsIgnoreCase(deleteType)) {
            if (fs.getMirrorfsTargets() != null
                    && !fs.getMirrorfsTargets().isEmpty()) {
                notSuppReasonBuff
                        .append(String
                                .format("File system %s given in request has active target file systems.",
                                        fs.getLabel()));
                _log.info(notSuppReasonBuff.toString());
                return true;

            }
        }

        return false;
    }

}
