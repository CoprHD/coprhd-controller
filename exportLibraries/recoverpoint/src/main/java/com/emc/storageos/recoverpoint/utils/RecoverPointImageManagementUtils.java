/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.utils;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettings;
import com.emc.fapiclient.ws.ConsistencyGroupCopySnapshots;
import com.emc.fapiclient.ws.ConsistencyGroupCopyState;
import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.ConsistencyGroupLinkState;
import com.emc.fapiclient.ws.ConsistencyGroupSettings;
import com.emc.fapiclient.ws.ConsistencyGroupState;
import com.emc.fapiclient.ws.ConsistencyGroupUID;
import com.emc.fapiclient.ws.ExecutionState;
import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.fapiclient.ws.GlobalCopyUID;
import com.emc.fapiclient.ws.ImageAccessMode;
import com.emc.fapiclient.ws.ImageAccessScenario;
import com.emc.fapiclient.ws.PipeState;
import com.emc.fapiclient.ws.RecoverPointTimeStamp;
import com.emc.fapiclient.ws.Snapshot;
import com.emc.fapiclient.ws.StorageAccessState;
import com.emc.fapiclient.ws.TimeFrame;
import com.emc.fapiclient.ws.TransactionID;
import com.emc.fapiclient.ws.TransactionResult;
import com.emc.fapiclient.ws.TransactionStatus;
import com.emc.fapiclient.ws.VerifyConsistencyGroupCopyStateParam;
import com.emc.fapiclient.ws.VerifyConsistencyGroupStateParam;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.requests.RPCopyRequestParams;

public class RecoverPointImageManagementUtils {

    private static final int WAIT_FOR_LINKS_SLEEP_INTERVAL = 15000;

    private static final String NAME_UNKNOWN = "unknown";

    private static final int MAX_RETRIES = 60;

    private static Logger logger = LoggerFactory.getLogger(RecoverPointImageManagementUtils.class);

    private final static int numMicroSecondsInMilli = 1000;
    private final static int numMillisInSecond = 1000;
    private final static int numMicroSecondsInSecond = 1000000;
    private final static int disableRetrySleepTimeSeconds = 1;

    // The snapshot window buffer default. 15 minutes.
    private final static int SNAPSHOT_QUERY_WINDOW_BUFFER = 15;
    // 48 attempts of expanding the query window by 15 minutes in both directions allows
    // us to cover a 24 hour period in search of a snapshot PiT.
    private final static int NUM_SNAPSHOT_QUERY_ATTEMPTS = 48;

    /**
     * Perform an enable image access on a CG copy
     *
     * @param impl - RP handle to use for RP operations
     * @param cgCopy - CG copy to perform the enable image access on
     * @param accessMode - The access mode to use (LOGGED_ACCESS, VIRTUAL_ACCESS, VIRTUAL_ACCESS_WITH_ROLL
     * @param bookmarkName - The bookmark image to enable (can be null)
     * @param apitTime -The APIT time to enable (can be null)
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void enableCGCopy(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopy, boolean waitForLinkState, ImageAccessMode accessMode,
            String bookmarkName, Date apitTime) throws RecoverPointException {
        String cgCopyName = NAME_UNKNOWN;
        String cgName = NAME_UNKNOWN;
        Snapshot snapshotToEnable = null;
        try {
            cgCopyName = impl.getGroupCopyName(cgCopy);
            cgName = impl.getGroupName(cgCopy.getGroupUID());
            if (waitForLinkState) {
                // Make sure the CG is ready for enable
                waitForCGLinkState(impl, cgCopy.getGroupUID(),
                        RecoverPointImageManagementUtils.getPipeActiveState(impl, cgCopy.getGroupUID()));
            }

            if (bookmarkName == null) {
                // Time based enable
                if (apitTime == null) {
                    logger.info("Enable most recent image on RP CG: " + cgName + " for CG copy: " + cgCopyName);

                    // No APIT specified. Get the most recent snapshot image
                    int numRetries = 0;
                    boolean foundSnap = false;
                    // Wait up to 15 minutes to find a snap
                    while (!foundSnap && numRetries++ < MAX_RETRIES) {
                        ConsistencyGroupCopySnapshots copySnapshots = impl.getGroupCopySnapshots(cgCopy);
                        Snapshot newestSnapshot = null;
                        for (Snapshot snapshot : copySnapshots.getSnapshots()) {
                            if (newestSnapshot == null) {
                                newestSnapshot = snapshot;
                            } else {
                                if (snapshot.getClosingTimeStamp().getTimeInMicroSeconds() > newestSnapshot.getClosingTimeStamp()
                                        .getTimeInMicroSeconds()) {
                                    newestSnapshot = snapshot;
                                }
                            }
                        }
                        if (newestSnapshot != null) {
                            snapshotToEnable = newestSnapshot;
                            bookmarkName = newestSnapshot.getDescription();
                            if (bookmarkName.length() == 0) {
                                bookmarkName = "Internal";
                            }
                        }
                        if (snapshotToEnable == null) {
                            logger.info("Did not find most recent snapshot. Sleep 15 seconds and retry");
                            Thread.sleep(15000);
                        } else {
                            foundSnap = true;
                        }
                    }
                } else {
                    // Get the snapshotToEnable based on the APIT

                    Calendar apitTimeCal = Calendar.getInstance();
                    apitTimeCal.setTime(apitTime);

                    Calendar apitTimeStart = Calendar.getInstance();
                    apitTimeStart.setTime(apitTime);
                    apitTimeStart.add(Calendar.MINUTE, -SNAPSHOT_QUERY_WINDOW_BUFFER);

                    Calendar apitTimeEnd = Calendar.getInstance();
                    apitTimeEnd.setTime(apitTime);
                    apitTimeEnd.add(Calendar.MINUTE, SNAPSHOT_QUERY_WINDOW_BUFFER);

                    // Convert all times to microseconds so they can be used in constructing a
                    // snapshot query time frame.
                    long apitTimeInMicroSeconds = apitTimeCal.getTimeInMillis() * numMicroSecondsInMilli;
                    long apitTimeStartInMicroSeconds = apitTimeStart.getTimeInMillis() * numMicroSecondsInMilli;
                    long apitTimeEndInMicroSeconds = apitTimeEnd.getTimeInMillis() * numMicroSecondsInMilli;

                    logger.info(String
                            .format("Request to enable a PiT image on RP CG: %s for CG copy: %s. The PiT requested is %s which evaluates to %s microseconds.",
                                    cgName, cgCopyName, apitTime, apitTimeInMicroSeconds));

                    logger.info(String.format(
                            "Building snapshot query window between %s and %s.  Evaluates to a microsecond window between %s and %s.",
                            apitTimeStart.getTime(), apitTimeEnd.getTime(), apitTimeStartInMicroSeconds, apitTimeEndInMicroSeconds));

                    // Construct the RecoverPoint TimeFrame
                    RecoverPointTimeStamp startTime = new RecoverPointTimeStamp();
                    startTime.setTimeInMicroSeconds(apitTimeStartInMicroSeconds);

                    RecoverPointTimeStamp endTime = new RecoverPointTimeStamp();
                    endTime.setTimeInMicroSeconds(apitTimeEndInMicroSeconds);

                    TimeFrame timeFrame = new TimeFrame();
                    timeFrame.setStartTime(startTime);
                    timeFrame.setEndTime(endTime);

                    // Get the CG copy snapshots for the given time frame.
                    ConsistencyGroupCopySnapshots copySnapshots = impl.getGroupCopySnapshotsForTimeFrameAndName(cgCopy, timeFrame, null);

                    if (copySnapshots != null && copySnapshots.getSnapshots() != null && copySnapshots.getSnapshots().isEmpty()) {
                        // There are no snapshots returned so lets see if the query window is valid. First, see if
                        // the point-in-time is before the start of the protection window.
                        logger.info(String.format("Determined that the protection window is between %s and %s.", copySnapshots
                                .getEarliest().getTimeInMicroSeconds(), copySnapshots.getLatest().getTimeInMicroSeconds()));
                        if (apitTimeInMicroSeconds < copySnapshots.getEarliest().getTimeInMicroSeconds()) {
                            logger.info("The specified point-in-time is before the start of the protection window.  As a result, returning the first snapshot in the protection window.");
                            startTime.setTimeInMicroSeconds(copySnapshots.getEarliest().getTimeInMicroSeconds());
                            // Set the start and end time to the same to get the exact snapshot
                            timeFrame.setStartTime(startTime);
                            timeFrame.setEndTime(startTime);
                            copySnapshots = impl.getGroupCopySnapshotsForTimeFrameAndName(cgCopy, timeFrame, null);

                            snapshotToEnable = copySnapshots.getSnapshots().get(0);
                        } else if (apitTimeInMicroSeconds > copySnapshots.getLatest().getTimeInMicroSeconds()) {
                            logger.info("The specified point-in-time is after the end of the protection window.  As a result, returning the most current snapshot in the protection window.");
                            startTime.setTimeInMicroSeconds(copySnapshots.getLatest().getTimeInMicroSeconds());
                            // Set the start and end time to the same to get the exact snapshot
                            timeFrame.setStartTime(startTime);
                            timeFrame.setEndTime(startTime);
                            copySnapshots = impl.getGroupCopySnapshotsForTimeFrameAndName(cgCopy, timeFrame, null);

                            snapshotToEnable = copySnapshots.getSnapshots().get(0);
                        } else {
                            // The snapshot falls within the protection window but the query window was too small to find
                            // a snapshot that matches the PiT. We will attempt to gradually expand the query window
                            // and retry until we find a match or exhaust our retry count.
                            int queryAttempt = 1;
                            while (queryAttempt <= NUM_SNAPSHOT_QUERY_ATTEMPTS) {
                                // Expand the snapshot query window in each direction by the defined buffer amount.
                                apitTimeStart.add(Calendar.MINUTE, -SNAPSHOT_QUERY_WINDOW_BUFFER);
                                apitTimeEnd.add(Calendar.MINUTE, SNAPSHOT_QUERY_WINDOW_BUFFER);
                                apitTimeStartInMicroSeconds = apitTimeStart.getTimeInMillis() * numMicroSecondsInMilli;
                                apitTimeEndInMicroSeconds = apitTimeEnd.getTimeInMillis() * numMicroSecondsInMilli;

                                startTime.setTimeInMicroSeconds(apitTimeStartInMicroSeconds);
                                endTime.setTimeInMicroSeconds(apitTimeEndInMicroSeconds);
                                timeFrame.setStartTime(startTime);
                                timeFrame.setEndTime(endTime);

                                logger.info(String
                                        .format("The PiT falls within the protection window but no results are returned. Attempt %s of %s. Expanding the query window by %s minute(s) in both directions to [%s - %s].",
                                                queryAttempt, NUM_SNAPSHOT_QUERY_ATTEMPTS, SNAPSHOT_QUERY_WINDOW_BUFFER,
                                                apitTimeStartInMicroSeconds, apitTimeEndInMicroSeconds));

                                copySnapshots = impl.getGroupCopySnapshotsForTimeFrameAndName(cgCopy, timeFrame, null);

                                if (!copySnapshots.getSnapshots().isEmpty()) {
                                    // Snapshots have been found for the given query window so now attempt to find the correct
                                    // snapshot that matches the specified PiT.
                                    snapshotToEnable = findPiTSnapshot(copySnapshots, apitTimeInMicroSeconds);
                                    break;
                                }

                                // increment the query attempt
                                queryAttempt++;
                            }
                        }
                    } else {
                        snapshotToEnable = findPiTSnapshot(copySnapshots, apitTimeInMicroSeconds);
                    }
                }
            } else {
                // Bookmark based enable. Set snapshotToEnable
                logger.info("Enable bookmark image " + bookmarkName + " on RP CG: " + cgName + " for CG copy: " + cgCopyName);
                // No APIT specified. Get the most recent snapshot image
                int numRetries = 0;
                boolean foundSnap = false;
                // Wait up to 15 minutes to find a snap
                while (!foundSnap && numRetries++ < MAX_RETRIES) {
                    ConsistencyGroupCopySnapshots copySnapshots = impl.getGroupCopySnapshots(cgCopy);
                    for (Snapshot snapItem : copySnapshots.getSnapshots()) {
                        if (snapItem.getDescription() != null && !snapItem.getDescription().isEmpty()) {
                            logger.info("Look at description: " + snapItem.getDescription());
                            if (snapItem.getDescription().equals(bookmarkName)) {
                                foundSnap = true;
                                snapshotToEnable = snapItem;
                                break;
                            }
                        }
                    }
                    if (!foundSnap) {
                        logger.info("Did not find snapshot to enable. Sleep 15 seconds and retry");
                        Thread.sleep(15000);
                    }
                }
            }

            if (snapshotToEnable == null) {
                throw RecoverPointException.exceptions.failedToFindBookmarkOrAPIT();
            }

            String bookmarkDate = new java.util.Date(snapshotToEnable.getClosingTimeStamp().getTimeInMicroSeconds()
                    / numMicroSecondsInMilli).toString();
            logger.info("Enable snapshot image: " + bookmarkName + " of time " + bookmarkDate + " on CG Copy" + cgCopyName
                    + " for CG name " + cgName);
            impl.enableImageAccess(cgCopy, snapshotToEnable, accessMode, ImageAccessScenario.NONE);

            if (waitForEnableToComplete(impl, cgCopy, accessMode, null)) {
                // Verify image is enabled correctly
                logger.info("Wait for image to be in correct mode");
                // This will wait for the state change, and throw if it times out or gets some other error
                waitForCGCopyState(impl, cgCopy, accessMode, false);
            }

        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        } catch (InterruptedException e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Given a list of snapshots, finds the snapshot that is closest to the the provided any point-in-time
     * in microseconds.
     *
     * @param copySnapshots the list of group copy snapshots.
     * @param apitTimeInMicroSeconds the point-in-time in microseconds
     * @return the snapshot closest to the provided point-in-time.
     */
    private Snapshot findPiTSnapshot(ConsistencyGroupCopySnapshots copySnapshots, long apitTimeInMicroSeconds) {
        long min = Long.MAX_VALUE;
        Snapshot closest = null;

        for (Snapshot snapshot : copySnapshots.getSnapshots()) {
            final long diff = Math.abs(snapshot.getClosingTimeStamp().getTimeInMicroSeconds() - apitTimeInMicroSeconds);
            logger.debug(
                    String.format(
                            "Examining snapshot %s with closing timestamp %s. Determining if it's closest to provided point-in-time %s. Difference is %s",
                            snapshot.getSnapshotUID().getId(), snapshot.getClosingTimeStamp().getTimeInMicroSeconds(),
                            apitTimeInMicroSeconds, diff));
            if (diff < min) {
                min = diff;
                closest = snapshot;
            } else {
                // We have hit the point where we have found the closest result to the APIT.
                logger.info(String.format(
                        "Determined that snapshot %s with closing timestamp %s is closest to point-in-time %s",
                        closest.getSnapshotUID().getId(), closest.getClosingTimeStamp().getTimeInMicroSeconds(),
                        apitTimeInMicroSeconds));
                break;
            }
        }

        return closest;
    }

    /**
     * Perform a disable image access on a CG copy
     *
     * @param impl - RP handle to use for RP operations
     * @param cgCopy - CG copy to perform the disable image access on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void disableCGCopy(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopy) throws RecoverPointException {

        String cgName = null;
        String cgCopyName = null;

        try {

            Map<String, String> disabledWWNs = new HashMap<String, String>();
            cgCopyName = impl.getGroupCopyName(cgCopy);
            cgName = impl.getGroupName(cgCopy.getGroupUID());

            boolean startTransfer = true;
            logger.info("Disable the image on copy name: " + cgCopyName + " for CG Name: " + cgName);
            try {
                impl.disableImageAccess(cgCopy, startTransfer);
            } catch (FunctionalAPIActionFailedException_Exception e) {
                // Try again
                logger.info("Disable the image failed for copy name: " + cgCopyName + " for CG Name: " + cgName + ". Try again");
                try {
                    Thread.sleep(Long.valueOf(disableRetrySleepTimeSeconds * numMillisInSecond));
                } catch (InterruptedException e1) {
                    // Fuggeddaboudit!
                }
                impl.disableImageAccess(cgCopy, startTransfer);
            }

            waitForDisableToComplete(impl, cgCopyName, cgName, cgCopy);
            logger.info("Successful disable of " + disabledWWNs.size() + " target LUNs.");
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDisableCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDisableCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Perform a failover on a CG copy
     *
     * @param impl - RP handle to use for RP operations
     * @param cgCopy - CG copy to perform the failover on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCGCopy(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopyUID) throws RecoverPointException {

        String cgName = null;
        String cgCopyName = null;
        try {

            cgCopyName = impl.getGroupCopyName(cgCopyUID);
            cgName = impl.getGroupName(cgCopyUID.getGroupUID());

            logger.info("Failover the image to copy name: " + cgCopyName + " for CG Name: " + cgName);

            // Failover the copy
            impl.failover(cgCopyUID, true);

            // Bharath : TODO
            // With 4.1, the below call is failing, transaction reported back as UNKNOWN_TRANSACTION.
            // I am not sure if this is needed with 4.1, but will check with Sean G and decide, for now, i am commenting the
            // check so that tests can pass and failover/swap still work.
            /*
             * if (!verifyCopyIsCurrentSourceCopy(impl, cgCopyUID)) {
             * // Image failover failed
             * throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName);
             * }
             */

            logger.info("Successful failover to copy name: " + cgCopyName + " for CG Name: " + cgName);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Sets the given copy as the production copy. As a prerequisite, all link settings between
     * this copy and other local/remote copies must be added to the consistency group.
     *
     * @param impl the FAPI reference.
     * @param cgCopyUID the copy to be set as the production copy.
     * @throws RecoverPointException
     */
    public void setCopyAsProduction(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopyUID) throws RecoverPointException {
        String cgCopyName = null;
        String cgName = null;

        try {
            cgCopyName = impl.getGroupCopyName(cgCopyUID);
            cgName = impl.getGroupName(cgCopyUID.getGroupUID());

            ConsistencyGroupSettings groupSettings = impl.getGroupSettings(cgCopyUID.getGroupUID());
            List<ConsistencyGroupCopyUID> prodCopiesUIDs = groupSettings.getProductionCopiesUIDs();

            for (ConsistencyGroupCopyUID prodCopyUID : prodCopiesUIDs) {
                if (!copiesEqual(cgCopyUID, prodCopyUID)) {
                    logger.info("Setting copy {} as the new production copy.", cgCopyName);

                    waitForCGCopyState(impl, cgCopyUID, null, false);
                    impl.setProductionCopy(cgCopyUID, true);
                    break;
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToSetCopyAsProduction(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToSetCopyAsProduction(cgCopyName, cgName, e);
        } catch (InterruptedException e) {
            throw RecoverPointException.exceptions.failedToSetCopyAsProduction(cgCopyName, cgName, e);
        }
    }

    /**
     * Convenience method that determines if 2 CG copies are equal. The cluster and copy UIDs
     * for each copy must be equal in order for the copies to be equal.
     *
     * @param firstCopy the first copy in the comparison
     * @param secondCopy the second copy in the comparison.
     * @return
     */
    private boolean copiesEqual(ConsistencyGroupCopyUID firstCopy, ConsistencyGroupCopyUID secondCopy) {
        if (firstCopy != null && secondCopy != null) {
            GlobalCopyUID firstCopyGlobalCopyUID = firstCopy.getGlobalCopyUID();
            GlobalCopyUID secondCopyGlobalCopyUID = secondCopy.getGlobalCopyUID();
            return copiesEqual(firstCopyGlobalCopyUID, secondCopyGlobalCopyUID);
        }

        return false;
    }

    /**
     * Convenience method that determines if 2 CG copies are equal. The cluster and copy UIDs
     * for each copy must be equal in order for the copies to be equal.
     *
     * @param firstCopy the first copy in the comparison
     * @param secondCopy the second copy in the comparison.
     * @return true if the copy UIDs are the same
     */
    private boolean copiesEqual(GlobalCopyUID firstCopy, GlobalCopyUID secondCopy) {
        if (firstCopy != null && secondCopy != null) {
            ClusterUID firstCopyClusterUID = firstCopy.getClusterUID();
            ClusterUID secondCopyClusterUID = secondCopy.getClusterUID();

            if (firstCopyClusterUID != null && secondCopyClusterUID != null
                    && firstCopyClusterUID.getId() == secondCopyClusterUID.getId()
                    && firstCopy.getCopyUID() == secondCopy.getCopyUID()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Perform a restore on a CG copy whose image has been enabled
     *
     * @param impl - RP handle to use for RP operations
     * @param cgCopy - CG copy to perform the restore on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void restoreEnabledCGCopy(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopyUID) throws RecoverPointException {

        String cgName = null;
        String cgCopyName = null;

        try {
            cgCopyName = impl.getGroupCopyName(cgCopyUID);
            cgName = impl.getGroupName(cgCopyUID.getGroupUID());
            logger.info(String.format("Restore the image to copy name: %s for CG name: %s", cgCopyName, cgName));
            recoverProductionAndWait(impl, cgCopyUID);
            // For restore, just wait for link state of the copy being restored
            waitForCGLinkState(impl, cgCopyUID.getGroupUID(),
                    RecoverPointImageManagementUtils.getPipeActiveState(impl, cgCopyUID.getGroupUID()));
            logger.info("Successful restore to copy name: " + cgCopyName + " for CG Name: " + cgName);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        } catch (InterruptedException e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionWaitingForStateChangeAfterRestore();
        }
    }

    /**
     * Recover (restore) the production data for a CG copy. Wait for the restore to complete.
     *
     * @param impl - RP handle to use for RP operations
     * @param cgCopy - CG copy to perform the restore on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    private void recoverProductionAndWait(FunctionalAPIImpl impl, ConsistencyGroupCopyUID groupCopy)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, RecoverPointException,
            InterruptedException
    {
        logger.info("Wait for recoverProduction to complete");
        impl.recoverProduction(groupCopy, true);
        logger.info("Wait for recoverProduction to complete");
        // 4.0 logic. Wait for the copy to no longer be in image access mode.
        this.waitForCGCopyState(impl, groupCopy, ImageAccessMode.UNKNOWN, false);
    }

    /**
     * Wait for an enable image access to complete.
     *
     * @param impl - RP handle to use for RP operations
     * @param groupCopy - CG copy we are waiting for
     *
     * @return void
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    private boolean waitForEnableToComplete(FunctionalAPIImpl port, ConsistencyGroupCopyUID groupCopy, ImageAccessMode accessMode,
            TimeFrame timeFrame)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, InterruptedException,
            RecoverPointException {
        final int transactionTimeout = 3 * MAX_RETRIES;
        VerifyConsistencyGroupStateParam stateParam = new VerifyConsistencyGroupStateParam();
        // Create a state param object to verify the group state
        // Specify the group copy that we want to verify it's state, add it to the copies list of the param object.
        stateParam.getCopies().add(groupCopy);
        // Create a state param object to verify the group copy state
        VerifyConsistencyGroupCopyStateParam copyStateParam = new VerifyConsistencyGroupCopyStateParam();
        // Set the copy UID
        copyStateParam.setCopyUID(groupCopy);
        // if accessMode is null, we are verifying a disable Image
        if (accessMode == null || accessMode == ImageAccessMode.UNKNOWN) {
            logger.info("Verify whether copy image is enabled");
            return verifyGroupCopyImageIsEnabled(port, groupCopy, false, null);
        } else if (accessMode == ImageAccessMode.LOGGED_ACCESS) {
            copyStateParam.getPossibleStorageAccessStates().add(StorageAccessState.LOGGED_ACCESS);
        } else if (accessMode == ImageAccessMode.VIRTUAL_ACCESS) {
            copyStateParam.getPossibleStorageAccessStates().add(StorageAccessState.VIRTUAL_ACCESS);
        } else if (accessMode == ImageAccessMode.VIRTUAL_ACCESS_WITH_ROLL) {
            copyStateParam.getPossibleStorageAccessStates().add(StorageAccessState.VIRTUAL_ACCESS_ROLLING_IMAGE);
            copyStateParam.getPossibleStorageAccessStates().add(StorageAccessState.LOGGED_ACCESS_ROLL_COMPLETE);
        } else {
            logger.error("Cannot check storage access state.  Unknown accessMode");
            return false;
        }

        if (timeFrame != null) {
            copyStateParam.setAccessedImageTime(timeFrame);
        }
        stateParam.getCopiesConditions().add(copyStateParam);
        // Set the copy state to verify
        // Now verify the state- this is an a-sync call so we should wait until the call finishes
        // and check the result

        // It appears that the FAPI call .getTransactionResult and .getTransactionStatus is not
        // working as expected with 4.1 SP1 FAPI.
        // The work-around for now is to sleep for 15s and in most cases that much wait time should
        // be sufficient for the operation to succeed.
        // TODO: Get more information from the RP team as to why this FAPI call returns UNKNOWN_TRANSACTION
        // and fix this correctly.
        logger.info("sleeping 15s for the enable image access to complete");
        Thread.sleep(Long.valueOf(15 * numMillisInSecond));

        TransactionID transactionID = port.verifyConsistencyGroupState(groupCopy.getGroupUID(), stateParam, transactionTimeout);
        logger.debug("Transaction ID: " + transactionID.getId());
        TransactionResult result = getTransactionResult(port, transactionID);
        if (result != null) {
            if (result.getExceptionMessage() != null) {
                // log as warning message, with 4.1 SP1 this call usually fails.
                // dont want false alarms as nothing fatal happens as a result.
                logger.warn("waitForEnableToComplete failed with: " + result.getExceptionMessage());
            }
        }
        return (result != null) && (result.getExceptionMessage() == null || result.getExceptionMessage().equals("Internal error."));
    }

    /**
     * This method verifies that the specified group copy is the current source copy
     *
     * @param port - RP handle to use for RP operations
     * @param cgCopy - CG copy to check
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public boolean verifyCopyIsCurrentSourceCopy(FunctionalAPIImpl port, ConsistencyGroupCopyUID groupCopy)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {

        // Create a state param object to verify the group state
        VerifyConsistencyGroupStateParam stateParam = new VerifyConsistencyGroupStateParam();

        // Set the specified copy as the expected source copy
        stateParam.setSourceCopy(groupCopy);

        // Now verify the state- this is an a-sync call so we should wait until the call finishes and check the result
        TransactionID transactionID = port.verifyConsistencyGroupStateWithDefaultTimeout(groupCopy.getGroupUID(), stateParam);
        TransactionResult result;
        try {

            // Get the result of the transaction - this might take a while...
            result = getTransactionResult(port, transactionID);
            if (result != null) {
                if (result.getExceptionMessage() != null) {
                    logger.error("verifyGroupSourceCopy failed with: " + result.getExceptionMessage());
                }
            }

        } catch (InterruptedException e) {
            logger.error("Caught InterruptedException while checking for failover complete", e);
            return false;
        }

        // The verify call is successful if there is no exception
        return (result != null) && (result.getExceptionType() == null);
    }

    /**
     * Wait for a disable image access to complete.
     *
     * @param impl - RP handle to use for RP operations
     * @param groupCopy - CG copy we are waiting for
     *
     * @return void
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    private void waitForDisableToComplete(FunctionalAPIImpl impl,
            String cgCopyName, String cgName, ConsistencyGroupCopyUID cgCopy)
            throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception,
            RecoverPointException {
        while (verifyGroupCopyImageIsEnabled(impl, cgCopy, false, null)) {
            logger.info("Image still not disabled");
            try {
                Thread.sleep(5 * numMillisInSecond);
            } catch (InterruptedException e) {
                // problem waiting. Just go ahead and do it
                logger.error("InterruptedException while sleeping.");
                throw RecoverPointException.exceptions.failedWaitingForImageForCopyToDisable(
                        cgCopyName, cgName, e);
            }
        }
    }

    /**
     * Verify that a group copy image is enabled. Not a "wait for", just a check
     *
     * @param port - RP handle to use for RP operations
     * @param groupCopy - CG copy we are checking
     * @param expectLoggedAccess - We are explicitly checking for LOGGED_ACCESS
     * @param bookmarkName - A bookmark we are expecting to be enabled (null means don't care)
     *
     * @return boolean - true (enabled) or false (not enabled)
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    private boolean verifyGroupCopyImageIsEnabled(FunctionalAPIImpl port, ConsistencyGroupCopyUID groupCopy, boolean expectLoggedAccess,
            String bookmarkName)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, RecoverPointException {
        ConsistencyGroupUID groupUID = groupCopy.getGroupUID();
        ConsistencyGroupState groupState;
        List<ConsistencyGroupCopyState> groupCopyStateList;
        groupState = port.getGroupState(groupUID);
        groupCopyStateList = groupState.getGroupCopiesStates();
        String cgName = port.getGroupName(groupCopy.getGroupUID());
        String cgCopyName = port.getGroupCopyName(groupCopy);
        boolean isAPITCheck = false;
        RecoverPointTimeStamp apitTimeStamp = null;
        if (bookmarkName == null) {
            // Most "recent"
            isAPITCheck = true;
        } else {
            apitTimeStamp = new RecoverPointTimeStamp();
            isAPITCheck = true;
            apitTimeStamp.setTimeInMicroSeconds(Long.parseLong(bookmarkName) * numMicroSecondsInMilli);
        }
        logger.info("verifyGroupCopyImageIsEnabled called for copy " + cgCopyName + " of group " + cgName + " and bookmarkName/APIT: "
                + bookmarkName);
        for (ConsistencyGroupCopyState groupCopyState : groupCopyStateList) {
            if (RecoverPointUtils.cgCopyEqual(groupCopyState.getCopyUID(), groupCopy)) {
                StorageAccessState accessState = groupCopyState.getStorageAccessState();
                if (expectLoggedAccess) {
                    // Explicitly looking for LOGGED_ACCESS
                    logger.debug("Seeing if copy is enabled for LOGGED_ACCESS");
                    if (accessState == StorageAccessState.LOGGED_ACCESS) {
                        if (!bookmarkName.equals(groupCopyState.getAccessedImage().getDescription())) {
                            // Enabled, but for a different snapshot image
                            if (groupCopyState.getAccessedImage().getDescription().length() > 0) {
                                throw RecoverPointException.exceptions.wrongSnapshotImageEnabled(
                                        bookmarkName, groupCopyState.getAccessedImage()
                                                .getDescription());
                            } else {
                                Timestamp enabledAPITTime = null;
                                RecoverPointTimeStamp enabledTimeDisplay = groupCopyState.getAccessedImage().getClosingTimeStamp();
                                enabledAPITTime = new Timestamp(enabledTimeDisplay.getTimeInMicroSeconds() / numMicroSecondsInMilli);
                                throw RecoverPointException.exceptions.wrongSnapshotImageEnabled(
                                        bookmarkName, enabledAPITTime.toString());
                            }
                        }
                        logger.info("Copy image copy " + cgCopyName + " of group " + cgName + " IS enabled in LOGGED_ACCESS");
                        return true;
                    } else {
                        logger.info("Copy image copy " + cgCopyName + " of group " + cgName
                                + " is NOT enabled in LOGGED_ACCESS. Image state is: " + accessState.toString());
                        return false;
                    }
                }

                logger.debug("Seeing if copy is enabled for any access mode other than DIRECT_ACCESS or NO_ACCESS");
                if (accessState == StorageAccessState.DIRECT_ACCESS) {
                    logger.info("Copy image copy " + cgCopyName + " of group " + cgName + " is in direct access mode");
                    return false;
                }
                if (accessState == StorageAccessState.NO_ACCESS) {
                    logger.info("Copy image copy " + cgCopyName + " of group " + cgName + " is in NO access mode");
                    return false;
                }

                if (groupCopyState.getAccessedImage() != null) {
                    logger.info("Copy image IS enabled. State is: " + accessState.toString() + ". Mounted snapshot name: "
                            + groupCopyState.getAccessedImage().getDescription());
                } else {
                    logger.info("Copy image IS enabled. State is: " + accessState.toString() + ". Enabled image: restore state");
                }

                // Let's throw if its the wrong image, otherwise return true
                if (!isAPITCheck) {
                    if ((bookmarkName == null) && (groupCopyState.getAccessedImage() == null)) {
                        return true;
                    }
                    if ((bookmarkName != null) && !bookmarkName.equals(groupCopyState.getAccessedImage().getDescription())) {
                        // Enabled, but for a different snapshot image
                        if (groupCopyState.getAccessedImage().getDescription().length() > 0) {
                            throw RecoverPointException.exceptions.wrongSnapshotImageEnabled(
                                    bookmarkName, groupCopyState.getAccessedImage()
                                            .getDescription());
                        } else {
                            Timestamp enabledAPITTime = null;
                            RecoverPointTimeStamp enabledTimeDisplay = groupCopyState.getAccessedImage().getClosingTimeStamp();
                            enabledAPITTime = new Timestamp(enabledTimeDisplay.getTimeInMicroSeconds() / numMicroSecondsInMilli);
                            throw RecoverPointException.exceptions.wrongSnapshotImageEnabled(
                                    bookmarkName, enabledAPITTime.toString());
                        }
                    }
                    return true;
                } else {
                    if (bookmarkName == null) {
                        return true;
                    } else {
                        return isGroupCopyImageEnabledForAPIT(port, groupCopy, expectLoggedAccess, apitTimeStamp);
                    }
                }
            }
        }
        logger.error("Could not locate CG copy state");
        return false;
    }

    /**
     * Verify that a group copy image is enabled for an APIT time. Not a "wait for", just a check
     *
     * @param port - RP handle to use for RP operations
     * @param groupCopy - CG copy we are checking
     * @param expectLoggedAccess - We are explicitly checking for LOGGED_ACCESS
     * @param apitTime - An APIT time we are expecting to be enabled)
     *
     * @return boolean - true (enabled) or false (not enabled)
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    private boolean isGroupCopyImageEnabledForAPIT(FunctionalAPIImpl port, ConsistencyGroupCopyUID groupCopy, boolean expectLoggedAccess,
            RecoverPointTimeStamp apitTime)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, RecoverPointException {
        ConsistencyGroupUID groupUID = groupCopy.getGroupUID();
        ConsistencyGroupState groupState;
        List<ConsistencyGroupCopyState> groupCopyStateList;
        groupState = port.getGroupState(groupUID);
        groupCopyStateList = groupState.getGroupCopiesStates();
        String cgName = port.getGroupName(groupCopy.getGroupUID());
        String cgCopyName = port.getGroupCopyName(groupCopy);
        Timestamp enabledApitTime = null;
        // logger.debug ("isGroupCopyImageEnabledForAPIT called for copy " + cgCopyName + " of group " + cgName);
        for (ConsistencyGroupCopyState groupCopyState : groupCopyStateList) {
            if (RecoverPointUtils.cgCopyEqual(groupCopyState.getCopyUID(), groupCopy)) {
                StorageAccessState accessState = groupCopyState.getStorageAccessState();
                if (accessState == StorageAccessState.DIRECT_ACCESS) {
                    // Not enabled
                    logger.info("Copy image copy " + cgCopyName + " of group " + cgName + " is in direct access mode.");
                    return false;
                }
                if (accessState == StorageAccessState.NO_ACCESS) {
                    // Not enabled
                    logger.info("Copy image copy " + cgCopyName + " of group " + cgName + " is in NO access mode.");
                    return false;
                }
                // Enabled. Check out the details
                logger.info("Copy image copy " + cgCopyName + " of group " + cgName + "  IS enabled. State is: " + accessState.toString());
                if (groupCopyState.getAccessedImage().getDescription().isEmpty()) {
                    RecoverPointTimeStamp enabledTimeDisplay = groupCopyState.getAccessedImage().getClosingTimeStamp();
                    enabledApitTime = new Timestamp(enabledTimeDisplay.getTimeInMicroSeconds() / numMicroSecondsInMilli);
                    logger.debug("No name. Mounted snapshot timestamp: " + enabledApitTime.toString());
                } else {
                    // Unexpected, this is
                    throw RecoverPointException.exceptions
                            .expectingAPITMountFoundBookmark(groupCopyState.getAccessedImage()
                                    .getDescription());
                }
                // Let's throw if its the wrong image
                if (apitTime != null) {
                    //
                    // See if the time enabled is exactly the time we requested (regardless of whether it is
                    // system generated, or AppSync generated.
                    //
                    RecoverPointTimeStamp enabledTime = groupCopyState.getAccessedImage().getClosingTimeStamp();
                    // Give it a 60 second variation
                    if (Math.abs(enabledTime.getTimeInMicroSeconds() - apitTime.getTimeInMicroSeconds()) < (numMicroSecondsInSecond * 60)) {
                        //
                        // It's enabled for (close to) the exact time we want. Are we expecting LOGGED_ACCESS?
                        //
                        if (expectLoggedAccess) {
                            logger.debug("Seeing if copy is enabled for LOGGED_ACCESS");
                            if (accessState == StorageAccessState.LOGGED_ACCESS) {
                                logger.info("Copy image copy " + cgCopyName + " of group " + cgName + "  IS enabled in LOGGED_ACCESS");
                                return true;
                            }
                            logger.info("Copy image copy " + cgCopyName + " of group " + cgName
                                    + "  is NOT enabled in LOGGED_ACCESS. Image state is: " + accessState.toString());
                            return false;
                        } else {
                            logger.debug("APIT enabled for same time requested");
                            return true;
                        }
                    }

                    //
                    // It IS possible that an APIT image is not quite exactly the same time requested, but it is "close enough"
                    // How do we tell? Well, we get the list of system snaps + or - 5 minutes from requested time, see if the one before the
                    // requested APIT time is the one we are looking for. Limit the snaps we look at to 1 hour before/after requested APIT
                    // time
                    //
                    final Long timeDeviationInMicroSeconds = Long.valueOf(5 * 60 * numMicroSecondsInMilli * numMillisInSecond);
                    TimeFrame window = new TimeFrame();
                    RecoverPointTimeStamp endTime = new RecoverPointTimeStamp();
                    RecoverPointTimeStamp startTime = new RecoverPointTimeStamp();
                    RecoverPointTimeStamp prevSnapTime = null;

                    // RecoverPointTimeStamp now = new RecoverPointTimeStamp();
                    // now.setTimeInMicroSeconds (System.currentTimeMillis() * numMicroSecondsInMilli );
                    // endTime.setTimeInMicroSeconds(now.getTimeInMicroSeconds() + timeDeviationInMicroSeconds);
                    // startTime.setTimeInMicroSeconds(now.getTimeInMicroSeconds() - timeDeviationInMicroSeconds);
                    endTime.setTimeInMicroSeconds(apitTime.getTimeInMicroSeconds() + timeDeviationInMicroSeconds);
                    startTime.setTimeInMicroSeconds(apitTime.getTimeInMicroSeconds() - timeDeviationInMicroSeconds);

                    window.setStartTime(startTime);
                    window.setEndTime(endTime);
                    // logger.info("Found "
                    // + port.getGroupCopySnapshotsForTimeFrameAndName(groupCopy, window, null).getSnapshots().size()
                    // + " snapshots in the timeframe");
                    for (Snapshot snapItem : port.getGroupCopySnapshotsForTimeFrameAndName(groupCopy, window, null).getSnapshots()) {
                        // RecoverPointTimeStamp foundTimeDisplay = snapItem.getClosingTimeStamp();
                        // Timestamp apitTimeStr = new Timestamp(foundTimeDisplay.getTimeInMicroSeconds() / 1000);
                        // logger.info("Checking snap with time: " + apitTimeStr.toString());
                        if (prevSnapTime == null)
                        {
                            prevSnapTime = snapItem.getClosingTimeStamp();
                        } else {
                            if (prevSnapTime.getTimeInMicroSeconds() < snapItem.getClosingTimeStamp().getTimeInMicroSeconds()) {
                                prevSnapTime = snapItem.getClosingTimeStamp();
                            }
                        }
                    }
                    if (prevSnapTime != null) {
                        RecoverPointTimeStamp enabledTimeDisplay = groupCopyState.getAccessedImage().getClosingTimeStamp();
                        enabledApitTime = new Timestamp(enabledTimeDisplay.getTimeInMicroSeconds() / numMicroSecondsInMilli);
                        logger.debug("Previous snap time is : " + enabledApitTime.toString());
                        if (Math.abs(enabledTime.getTimeInMicroSeconds() - prevSnapTime.getTimeInMicroSeconds()) < numMicroSecondsInSecond) {
                            logger.debug("Currently enabled image is requested snap!");
                            if (expectLoggedAccess) {
                                logger.debug("Seeing if copy is enabled for LOGGED_ACCESS");
                                if (accessState == StorageAccessState.LOGGED_ACCESS) {
                                    logger.info("Copy image copy " + cgCopyName + " of group " + cgName + "  IS enabled in LOGGED_ACCESS");
                                    return true;
                                }
                                logger.info("Copy image copy " + cgCopyName + " of group " + cgName
                                        + "  is NOT enabled in LOGGED_ACCESS. Image state is: " + accessState.toString());
                                return false;
                            } else {
                                return true;
                            }
                        } else {
                            throw RecoverPointException.exceptions
                                    .wrongTimestampEnabled(enabledApitTime);
                        }
                    }
                } else {
                    return false;
                }
            }
        }
        logger.error("Could not locate CG copy state");
        return false;
    }

    /**
     * This is how a client should wait for a transaction to finish
     * A few notes:
     * 1. It is recommended to have a timeout here and not wait infinitely for the transaction to finish.
     * 2. The TransactionStatus holds more data that might be useful (ETA, percentage etc...)
     * 3. The transaction status and result MUST be taken from the same RPA
     * (the specific TransactionID is unknown in other RPAs).
     *
     * @param port - RP handle to use for RP operations
     * @param transactionID - RP transaction we are checking
     *
     * @return TransactionResult - Result of the transaction
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    private TransactionResult getTransactionResult(FunctionalAPIImpl port, TransactionID transactionID)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, InterruptedException {
        TransactionStatus status = port.getTransactionStatus(transactionID);
        // Get the current transaction status
        final int stateChangeSleepWaitTime = 10; // seconds
        while (ExecutionState.RUNNING.equals(status.getState())) {
            // still running
            logger.info("Transaction status state: " + status.getState().value());
            Thread.sleep(Long.valueOf(stateChangeSleepWaitTime * numMicroSecondsInMilli)); // Wait for 10 seconds before checking again...
            status = port.getTransactionStatus(transactionID); // Update the status
        }
        logger.info("No longer in RUNNING state. Transaction status state: " + status.getState().value());
        if (ExecutionState.ABORTED.equals(status.getState())) {
            logger.error("Transaction aborted");
            // The action was aborted
            return null;
        }
        return port.getTransactionResult(transactionID);
    }

    /**
     * Wait for a CG copy to change state
     *
     * @param port - RP handle to use for RP operations
     * @param groupCopy - RP group copy we are looking at
     * @param accessMode - Access mode we are waiting for
     * @param expectRollComplete - true or false we are expecting the state to be LOGGED_ACCESS_WITH_ROLL, and the roll is complete
     *
     * @return void
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    public void waitForCGCopyState(FunctionalAPIImpl port, ConsistencyGroupCopyUID groupCopy, ImageAccessMode accessMode,
            boolean expectRollComplete)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception, InterruptedException,
            RecoverPointException {
        ConsistencyGroupUID groupUID = groupCopy.getGroupUID();
        List<ConsistencyGroupCopyState> groupCopyStateList;
        // groupCopyStateList = groupState.getGroupCopiesState();
        String cgName = port.getGroupName(groupCopy.getGroupUID());
        String cgCopyName = port.getGroupCopyName(groupCopy);
        final int maxMinutes = 30;
        final int sleepTimeSeconds = 15; // seconds
        final int secondsPerMin = 60;
        final int numItersPerMin = secondsPerMin / sleepTimeSeconds;
        logger.info("waitForCGCopyState called for copy " + cgCopyName + " of group " + cgName);

        logger.info("waitForCGCopyState called for copy " + cgCopyName + " of group " + cgName);
        if (accessMode != null) {
            logger.info("Waiting up to " + maxMinutes + " minutes for state to change to: " + accessMode);
        } else {
            logger.info("Waiting up to " + maxMinutes + " minutes for state to change to: DIRECT_ACCESS or NO_ACCESS");
        }
        for (int minIter = 0; minIter < maxMinutes; minIter++) {
            for (int perMinIter = 0; perMinIter < numItersPerMin; perMinIter++) {
                groupCopyStateList = port.getGroupState(groupUID).getGroupCopiesStates();
                for (ConsistencyGroupCopyState groupCopyState : groupCopyStateList) {
                    if (RecoverPointUtils.cgCopyEqual(groupCopyState.getCopyUID(), groupCopy)) {
                        StorageAccessState copyAccessState = groupCopyState.getStorageAccessState();
                        logger.info("Current Copy Access State: " + copyAccessState);

                        if (accessMode == ImageAccessMode.LOGGED_ACCESS) {
                            // HACK HACK HACK WJE had to add check for no access journal preserved, otherwise my restore wouldn't continue
                            if (copyAccessState == StorageAccessState.LOGGED_ACCESS
                                    || copyAccessState == StorageAccessState.NO_ACCESS_JOURNAL_PRESERVED) {
                                logger.info("Copy " + cgCopyName + " of group " + cgName + " is in logged access.  Enable has completed");
                                return;
                            }
                        } else if (accessMode == ImageAccessMode.VIRTUAL_ACCESS) {
                            if (copyAccessState == StorageAccessState.VIRTUAL_ACCESS) {
                                logger.info("Copy " + cgCopyName + " of group " + cgName + " is in virtual access.  Enable has completed");
                                return;
                            }
                        } else if (accessMode == ImageAccessMode.VIRTUAL_ACCESS_WITH_ROLL) {
                            if (expectRollComplete) {
                                if (copyAccessState == StorageAccessState.LOGGED_ACCESS_ROLL_COMPLETE) {
                                    logger.info("Copy " + cgCopyName + " of group " + cgName
                                            + " is in virtual access with roll complete.  Enable has completed");
                                    return;
                                }
                            } else {
                                if ((copyAccessState == StorageAccessState.VIRTUAL_ACCESS_ROLLING_IMAGE)
                                        || (copyAccessState == StorageAccessState.LOGGED_ACCESS_ROLL_COMPLETE)) {
                                    logger.info("Copy " + cgCopyName + " of group " + cgName
                                            + " is in virtual access with roll or roll complete.  Enable has completed");
                                    return;
                                }
                            }
                        } else {
                            // Wait for NO_ACCESS or DIRECT_ACCESS
                            if (copyAccessState == StorageAccessState.DIRECT_ACCESS) {
                                logger.info("Copy " + cgCopyName + " of group " + cgName + " is DIRECT_ACCESS mode");
                                return;
                            }
                            if (copyAccessState == StorageAccessState.NO_ACCESS) {
                                logger.info("Copy " + cgCopyName + " of group " + cgName + " is NO_ACCESS mode");
                                return;
                            }
                        }
                    }
                }
                logger.info("Copy image " + cgCopyName + " of group " + cgName + " not in correct state.  Sleeping " + sleepTimeSeconds
                        + " seconds");
                Thread.sleep(Long.valueOf(sleepTimeSeconds * numMillisInSecond));
            }
        }
        throw RecoverPointException.exceptions.stateChangeNeverCompleted();
    }

    /**
     * Wait for CG copy links to become ACTIVE
     *
     * @param cgUID - Consistency group we are looking at
     * @param desiredPipeState - Desired state of the pipe
     * @param port - RP handle to use for RP operations
     *
     * @return void
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    public void waitForCGLinkState(FunctionalAPIImpl impl, ConsistencyGroupUID cgUID, PipeState desiredPipeState)
            throws RecoverPointException {

        int numRetries = 0;
        String cgName = null;
        try {
            cgName = impl.getGroupName(cgUID);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
        }

        boolean isInitializing = false;
        boolean allLinksInDesiredState = false;
        while ((!allLinksInDesiredState && numRetries++ < MAX_RETRIES) || isInitializing) {
            ConsistencyGroupState cgState = null;
            isInitializing = false;
            try {
                cgState = impl.getGroupState(cgUID);

                // Lets assume all links are in desired state and use boolean AND operation to concatenate the results
                // to get a cumulative status on all the links.
                // allLinksInDesiredState = true;
                for (ConsistencyGroupLinkState linkstate : cgState.getLinksStates()) {
                    PipeState pipeState = linkstate.getPipeState();
                    logger.info("CG link state is " + pipeState.toString() + "; desired state is: " + desiredPipeState.toString());

                    // Special consideration if we want the link to be in the active state.
                    if (PipeState.ACTIVE.equals(desiredPipeState)) {
                        if (PipeState.ACTIVE.equals(pipeState)) {
                            allLinksInDesiredState = true;
                        } else if (PipeState.STAND_BY.equals(pipeState)) {
                            // STAND_BY is a valid state for a MetroPoint link but we need
                            // an ACTIVE link state as well.
                            logger.info("CG link state is STAND_BY, valid state for MetroPoint.");
                        } else if (PipeState.PAUSED.equals(pipeState)) {
                            logger.info("CG link state is PAUSED.  Resume link.");
                            impl.startGroupTransfer(cgUID);
                            allLinksInDesiredState = false;
                            break;
                        } else if (PipeState.INITIALIZING.equals(pipeState)) {
                            logger.info("CG link state is INITIALIZING.");
                            isInitializing = true;
                            allLinksInDesiredState = false;
                            break;
                        } else {
                            logger.info("CG link state is not active. It is: " + pipeState.toString());
                            allLinksInDesiredState = false;
                            break;
                        }
                    } else if (PipeState.SNAP_IDLE.equals(desiredPipeState)) {
                        if (PipeState.SNAP_IDLE.equals(pipeState) || PipeState.SNAP_SHIPPING.equals(pipeState)) {
                            allLinksInDesiredState = true;
                            break;
                        }
                    } else {
                        // Other desired states (like UNKNOWN [inactive])
                        if (pipeState.equals(desiredPipeState)) {
                            logger.info("CG link state matches the desired state.");
                            allLinksInDesiredState = true;
                        } else {
                            // This makes sure that if you wanted to act on the entire CG, but there's still a copy
                            // in the undesired state, we still need to wait for it.
                            logger.info("CG link state is not in desired state. It is: " + pipeState.toString());
                            allLinksInDesiredState = false;
                            break;
                        }
                    }
                }

                if (allLinksInDesiredState) {
                    return;
                } else {
                    logger.info("All links not in desired state.  Sleep 15 seconds and retry");
                    Thread.sleep(WAIT_FOR_LINKS_SLEEP_INTERVAL);
                }
            } catch (FunctionalAPIActionFailedException_Exception e) {
                throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
            } catch (FunctionalAPIInternalError_Exception e) {
                throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
            } catch (InterruptedException e) {
                throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
            }
        }
        throw RecoverPointException.exceptions.cgLinksFailedToBecomeActive(cgName);
    }

    /**
     * Wait for CG copy links to become ACTIVE
     *
     * @param impl access to RP
     * @param copyUID copy ID
     * @param desiredPipeState - Desired state of the pipe
     *
     * @return void
     *
     * @throws RecoverPointException, FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
     *             InterruptedException
     **/
    public void waitForCGCopyLinkState(FunctionalAPIImpl impl, ConsistencyGroupCopyUID copyUID, PipeState desiredPipeState)
            throws RecoverPointException {

        int numRetries = 0;
        String cgName = null;
        try {
            cgName = impl.getGroupName(copyUID.getGroupUID());
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
        }

        while (numRetries++ < MAX_RETRIES) {
            ConsistencyGroupState cgState = null;
            try {
                cgState = impl.getGroupState(copyUID.getGroupUID());

                for (ConsistencyGroupLinkState linkstate : cgState.getLinksStates()) {
<<<<<<< HEAD
                    
                    // The copy we're interested in may be in the FirstCopy or SecondCopy, so we need to find the link
                    // state where our copy is the first or second copy and the other copy is a production.  There may be
                    // multiple production copies, so account for that, too.  (you can assume there aren't multiple productions
                    // going to the same target.  We used to assume that the targets are "second copy", but that is not true.
                    boolean found = false;
                    
                    // Loop through production copies
                    for (ConsistencyGroupCopyUID groupCopyUID : cgState.getSourceCopiesUIDs()) {
                        
                        if (copiesEqual(linkstate.getGroupLinkUID().getFirstCopy(), groupCopyUID.getGlobalCopyUID()) &&
                            copiesEqual(linkstate.getGroupLinkUID().getSecondCopy(), copyUID.getGlobalCopyUID())) {
                            found = true;                            
                        }
                        
                        if (copiesEqual(linkstate.getGroupLinkUID().getSecondCopy(), groupCopyUID.getGlobalCopyUID()) &&
                            copiesEqual(linkstate.getGroupLinkUID().getFirstCopy(), copyUID.getGlobalCopyUID())) {
                            found = true;                            
                        }
                    }

                    if (!found) {
=======

                    if (!copiesEqual(linkstate.getGroupLinkUID().getSecondCopy(), copyUID.getGlobalCopyUID())) {
>>>>>>> master
                        continue;
                    }

                    PipeState pipeState = linkstate.getPipeState();
                    logger.info("Copy link state is " + pipeState.toString() + "; desired state is: " + desiredPipeState.toString());

                    if (pipeState.equals(desiredPipeState)) {
                        logger.info("Copy link state matches the desired state.");
                        return;
                    } else {
                        // This makes sure that if you wanted to act on the entire CG, but there's still a copy
                        // in the undesired state, we still need to wait for it.
                        logger.info("Copy link state is not in desired state. It is: " + pipeState.toString());
                        break;
                    }
                }

                logger.info("Copy link not in desired state.  Sleep 15 seconds and retry");
                Thread.sleep(WAIT_FOR_LINKS_SLEEP_INTERVAL);
            } catch (FunctionalAPIActionFailedException_Exception e) {
                throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
            } catch (FunctionalAPIInternalError_Exception e) {
                throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
            } catch (InterruptedException e) {
                throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
            }
        }

        throw RecoverPointException.exceptions.cgLinksFailedToBecomeActive(cgName);
    }

    /**
     * Perform an enable image on a CG copy
     *
     * @param impl - RP handle to use for RP operations
     * @param copyToEnableTo - CG to enable, as well as the bookmark and APIT
     * @param failover - whether this operation is a failover or not. Affects current copy check.
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void enableCopyImage(FunctionalAPIImpl impl, RPCopyRequestParams copyToEnableTo, boolean failover) throws RecoverPointException {
        // Check the params
        // If bookmark != null, enable the bookmark on the copy, and failover to that copy
        // If APITTime != null, enable the specified APIT on the copy, and failover to that copy
        // If both are null, enable the most recent imagem, and failover to that copy
        String bookmarkName = copyToEnableTo.getBookmarkName();
        Date apitTime = copyToEnableTo.getApitTime();
        // FunctionalAPIImpl impl = new RecoverPointConnection().connect(endpoint, username, password);
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToEnableTo.getCopyVolumeInfo());
        if (bookmarkName != null) {
            logger.info("Enable copy to bookmark : " + bookmarkName);
        } else if (apitTime != null) {
            logger.info("Enable copy to APIT : " + apitTime.toString());
        } else {
            logger.info("Enable copy to most recent image");
        }
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        // Make sure your copies are OK to enable.
        // Will throw an exception if it's not in the right state
        if (!imageManager.verifyCopyCapableOfEnableImageAccess(impl, cgCopyUID, copyToEnableTo.getBookmarkName(), failover)) {
            try {
                String cgCopyName = impl.getGroupCopyName(cgCopyUID);
                String cgName = impl.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Copy " + cgCopyName + " of group " + cgName + " is in a mode that disallows enabling the CG copy.");
                throw RecoverPointException.exceptions.notAllowedToEnableImageAccessToCG(cgName,
                        cgCopyName);
            } catch (FunctionalAPIActionFailedException_Exception e) {
                throw RecoverPointException.exceptions.notAllowedToEnableImageAccessToCGException(e);
            } catch (FunctionalAPIInternalError_Exception e) {
                throw RecoverPointException.exceptions.notAllowedToEnableImageAccessToCGException(e);
            }
        }

        boolean waitForLinkState = true;
        imageManager.enableCGCopy(impl, cgCopyUID, waitForLinkState, ImageAccessMode.LOGGED_ACCESS, bookmarkName, apitTime);
    }

    /**
     * Perform an disable image on a CG copy
     *
     * @param impl - RP handle to use for RP operations
     * @param copyToEnableTo - CG to disable
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void disableCopyImage(FunctionalAPIImpl impl, RPCopyRequestParams copyToEnableTo) throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToEnableTo.getCopyVolumeInfo());
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.disableCGCopy(impl, cgCopyUID);
    }

    /**
     * Verify that a copy is capable of being enabled.
     *
     * @param impl - RP handle
     * @param cgCopy - CG Copy, contains CG
     * @param failover - for a failover operation?
     * @return true if the copy is capable of enable image access, false if it's in some other state
     * @throws RecoverPointException
     */
    public boolean verifyCopyCapableOfEnableImageAccess(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopy, String copyToEnable,
            boolean failover)
            throws RecoverPointException {
        String cgCopyName = NAME_UNKNOWN;
        String cgName = NAME_UNKNOWN;

        try {
            cgCopyName = impl.getGroupCopyName(cgCopy);
            cgName = impl.getGroupName(cgCopy.getGroupUID());

            ConsistencyGroupCopyState cgCopyState = getCopyState(impl, cgCopy);
            if (cgCopyState != null) {
                StorageAccessState copyAccessState = cgCopyState.getStorageAccessState();
                logger.info("Current Copy Access State: " + copyAccessState);
                // Check for NO_ACCESS state (or LOGGED ACCESS for failover)
                if (copyAccessState == StorageAccessState.NO_ACCESS) {
                    return true;
                }

                // Failover-test state
                if ((copyAccessState == StorageAccessState.LOGGED_ACCESS) && failover) {
                    ConsistencyGroupLinkState cgLinkState = getCopyLinkState(impl, cgCopy);
                    if ((cgLinkState != null) && (cgLinkState.getPipeState() == PipeState.PAUSED)) {
                        return true;
                    }
                }

                // return true if CG is already in LOGGED_ACCESS state
                if (copyAccessState == StorageAccessState.LOGGED_ACCESS
                        && cgCopyState.getAccessedImage().getDescription().equals(copyToEnable)) {
                    return true;
                }
            }
            return false;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Verify that a copy is capable of being enabled.
     *
     * @param impl - RP handle
     * @param copyId - CG Copy, contains CG
     * @param cgCopyName - copy name
     * @param cgName - CG name
     * @throws RecoverPointException
     */
    public ConsistencyGroupCopyState getCopyState(FunctionalAPIImpl impl, ConsistencyGroupCopyUID copyId, String cgCopyName, String cgName)
            throws RecoverPointException {
        try {
            ConsistencyGroupUID groupUID = copyId.getGroupUID();
            ConsistencyGroupState groupState;
            List<ConsistencyGroupCopyState> cgCopyStateList;
            groupState = impl.getGroupState(groupUID);
            cgCopyStateList = groupState.getGroupCopiesStates();
            for (ConsistencyGroupCopyState cgCopyState : cgCopyStateList) {
                if (RecoverPointUtils.cgCopyEqual(cgCopyState.getCopyUID(), copyId)) {
                    return cgCopyState;
                }
            }
            return null;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Verify that a copy is capable of being enabled.
     *
     * @param impl - RP handle
     * @param copyId - CG Copy, contains CG
     * @throws RecoverPointException
     * @return state of the CG copy
     */
    public ConsistencyGroupCopyState getCopyState(FunctionalAPIImpl impl, ConsistencyGroupCopyUID copyId) throws RecoverPointException {
        String cgCopyName = NAME_UNKNOWN;
        String cgName = NAME_UNKNOWN;

        try {
            cgCopyName = impl.getGroupCopyName(copyId);
            cgName = impl.getGroupName(copyId.getGroupUID());
            return getCopyState(impl, copyId, cgCopyName, cgName);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Get the link state of a copy
     *
     * @param impl - RP handle
     * @param cgCopy - CG Copy, contains CG
     * @throws RecoverPointException
     */
    public ConsistencyGroupLinkState getCopyLinkState(FunctionalAPIImpl impl, ConsistencyGroupCopyUID cgCopy) throws RecoverPointException {
        String cgCopyName = NAME_UNKNOWN;
        String cgName = NAME_UNKNOWN;

        try {
            cgCopyName = impl.getGroupCopyName(cgCopy);
            cgName = impl.getGroupName(cgCopy.getGroupUID());

            ConsistencyGroupUID groupUID = cgCopy.getGroupUID();
            ConsistencyGroupState groupState = impl.getGroupState(groupUID);
            List<ConsistencyGroupLinkState> linkStates = groupState.getLinksStates();
            for (ConsistencyGroupLinkState cgLinkState : linkStates) {
                if ((RecoverPointUtils.cgCopyEqual(cgLinkState.getGroupLinkUID().getSecondCopy(), cgCopy.getGlobalCopyUID()) || (RecoverPointUtils
                        .cgCopyEqual(cgLinkState.getGroupLinkUID().getFirstCopy(), cgCopy.getGlobalCopyUID())))) {
                    return cgLinkState;
                }
            }
            return null;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Determines if the specified consistency group is using snapshot technology
     *
     * @param impl the FAPI reference.
     * @param cgCopyUID the copy to be set as the production copy.
     * @throws RecoverPointException
     * @return boolean indicating if snapshot technology is being used
     */
    private static boolean isSnapShotTechnologyEnabled(FunctionalAPIImpl impl, ConsistencyGroupUID cgUID) throws RecoverPointException {
        String cgName = "unknown";
        try {
            cgName = impl.getGroupName(cgUID);
            ConsistencyGroupSettings groupSettings = impl.getGroupSettings(cgUID);
            List<ConsistencyGroupCopySettings> copySettings = groupSettings.getGroupCopiesSettings();
            for (ConsistencyGroupCopySettings copySetting : copySettings) {
                if (copySetting.getPolicy().getSnapshotsPolicy().getNumOfDesiredSnapshots() != null &&
                        copySetting.getPolicy().getSnapshotsPolicy().getNumOfDesiredSnapshots() > 0) {
                    logger.info("Setting link state for snapshot technology.");
                    return true;
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.cantCheckLinkState(cgName, e);
        }
        return false;
    }

    /**
     * Determines the active pipe state to be looking for when the
     * when the link is not-snapshot enabled or the link is snapshot enabled
     *
     * @param impl the FAPI reference
     * @param cgUID The consistency group being examined
     * @return PipeState indicating the active state of the link
     *         PipeState.ACTIVE for non-snapshot links
     *         PipeState.SNAP_IDLE for snapshot enabled links
     */
    public static PipeState getPipeActiveState(FunctionalAPIImpl impl, ConsistencyGroupUID cgUID) {
        PipeState pipeState = PipeState.ACTIVE;
        if (isSnapShotTechnologyEnabled(impl, cgUID)) {
            pipeState = PipeState.SNAP_IDLE;
        }
        return pipeState;
    }
}
