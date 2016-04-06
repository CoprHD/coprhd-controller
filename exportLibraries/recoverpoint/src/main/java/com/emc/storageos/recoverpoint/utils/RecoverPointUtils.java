/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.utils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.AccountState;
import com.emc.fapiclient.ws.ClusterConfiguration;
import com.emc.fapiclient.ws.ClusterSANVolumes;
import com.emc.fapiclient.ws.ClusterSplittersSettings;
import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConnectionOutThroughput;
import com.emc.fapiclient.ws.ConsistencyGroupCopyRole;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettings;
import com.emc.fapiclient.ws.ConsistencyGroupCopyState;
import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.ConsistencyGroupSettings;
import com.emc.fapiclient.ws.ConsistencyGroupState;
import com.emc.fapiclient.ws.ConsistencyGroupUID;
import com.emc.fapiclient.ws.DeviceUID;
import com.emc.fapiclient.ws.FullRecoverPointSettings;
import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.fapiclient.ws.FunctionalAPIValidationException_Exception;
import com.emc.fapiclient.ws.GlobalCopyUID;
import com.emc.fapiclient.ws.InOutThroughputStatistics;
import com.emc.fapiclient.ws.LicenseState;
import com.emc.fapiclient.ws.LicenseStatus;
import com.emc.fapiclient.ws.ReplicationSetSettings;
import com.emc.fapiclient.ws.RpaStatistics;
import com.emc.fapiclient.ws.RpaUID;
import com.emc.fapiclient.ws.SetVolumeParam;
import com.emc.fapiclient.ws.SplitterSettings;
import com.emc.fapiclient.ws.SplitterUID;
import com.emc.fapiclient.ws.SystemStatistics;
import com.emc.fapiclient.ws.TrafficStatistics;
import com.emc.fapiclient.ws.UserVolumeSettings;
import com.emc.fapiclient.ws.VolumeInformation;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.requests.CreateCopyParams;
import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;

public class RecoverPointUtils {

    private static Logger logger = LoggerFactory.getLogger(RecoverPointClient.class);

    /**
     * @param buffer
     * @param useSeparator
     * @return String - GUID representation in a string format.
     */
    public static String getGuidBufferAsString(List<Byte> buffer, boolean useSeparator) {
        StringBuffer sb = new StringBuffer();
        boolean isFirst = true;
        Iterator<Byte> itr = buffer.iterator();
        while (itr.hasNext()) {
            if (useSeparator) {
                if (isFirst) {
                    isFirst = false;
                } else {
                    sb.append(',');
                }
            }
            int i = itr.next().intValue();
            if (i < 0) {
                i += 256; // byte is signed, u8 isn't.
            }
            sb.append(getHex(i));
        }
        return sb.toString();
    }

    public static String getHex(int i) {
        String res = Integer.toHexString(i);
        return res.length() > 1 ? res : "0" + res;
    }

    /**
     * @param impl - RP handle to use for RP operations
     * @param cgUID - Consistency Group UID
     * @param localCopyUID
     * @param remoteCopiesUID
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     * @throws RecoverPointException
     * @throws FunctionalAPIValidationException_Exception
     */
    public static void enableNewConsistencyGroup(FunctionalAPIImpl impl,
            ConsistencyGroupUID cgUID, List<ConsistencyGroupCopyUID> localCopiesUID,
            List<ConsistencyGroupCopyUID> remoteCopiesUID)
            throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception, RecoverPointException, FunctionalAPIValidationException_Exception {
        logger.info("Start enableNewConsistencyGroup...");
        if (remoteCopiesUID != null && !remoteCopiesUID.isEmpty()) {
            for (ConsistencyGroupCopyUID remoteCopyUID : remoteCopiesUID) {
                try {
                    logger.info("Validate Remote copy...");
                    // CG validation warnings will be caught, logged, and
                    // processing will continue
                    impl.validateEnableConsistencyGroupCopy(remoteCopyUID, true);
                } catch (FunctionalAPIValidationException_Exception e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
        if (localCopiesUID != null && !localCopiesUID.isEmpty()) {
            for (ConsistencyGroupCopyUID localCopyUID : localCopiesUID) {
                try {
                    logger.info("Validate Local copy...");
                    // CG validation warnings will be caught, logged, and
                    // processing will continue
                    impl.validateEnableConsistencyGroupCopy(localCopyUID, true);
                } catch (FunctionalAPIValidationException_Exception e) {
                    logger.warn(e.getMessage(), e);
                }
            }
        }
        // Last thing to do is enable the CG
        logger.info("Enable Consistency Group and all copies");
        try {
            // CG validation warnings will be caught, logged, and
            // processing will continue
            impl.validateEnableConsistencyGroup(cgUID, true);
        } catch (FunctionalAPIValidationException_Exception e) {
            logger.warn(e.getMessage(), e);
        }
        impl.enableConsistencyGroup(cgUID, true);
        // Make sure the CG is ready
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.waitForCGLinkState(impl, cgUID, RecoverPointImageManagementUtils.getPipeActiveState(impl, cgUID));
        logger.info("End enableNewConsistencyGroup.");
    }

    /**
     * Validates that the production and/or local/remote copies are not null
     *
     * @param prodCopy
     * @param localCopy
     * @param remoteCopies
     * @throws RecoverPointException
     */
    public static void validateCopiesNotNull(List<CreateCopyParams> prodCopies,
            List<CreateCopyParams> localCopies, List<CreateCopyParams> remoteCopies)
            throws RecoverPointException {
        if (prodCopies == null) {
            throw RecoverPointException.exceptions.didNotFindProductionCopyWWNs();
        }

        if (localCopies == null && remoteCopies == null) {
            throw RecoverPointException.exceptions.didNotFindLocalRemoteCopyWWNs();
        }
    }

    /**
     * Removes a consistency group
     *
     * @param impl - RP handle to use for RP operations
     * @param cgUID - UID of the consistency group which needs to be removed.
     * @throws RecoverPointException
     */
    public static void cleanupCG(FunctionalAPIImpl impl, ConsistencyGroupUID cgUID) throws RecoverPointException {

        if (impl != null && cgUID != null) {
            try {
                impl.removeConsistencyGroup(cgUID);
            } catch (FunctionalAPIActionFailedException_Exception e1) {
                throw RecoverPointException.exceptions.cannotCleanupCG(e1);
            } catch (FunctionalAPIInternalError_Exception e1) {
                throw RecoverPointException.exceptions.cannotCleanupCG(e1);
            }
        }
    }

    /**
     * @param WWN
     * @param useSeparator
     * @return - WWN as string
     */
    public static String getWWNAsString(long WWN, boolean useSeparator) {
        String hexString = Long.toHexString(WWN);
        if (useSeparator) {
            String formattedWWN = new String();
            for (int i = 0; i < hexString.length(); i += 2) {
                formattedWWN += hexString.substring(i, i + 2);
                if (i < hexString.length() - 2) {
                    formattedWWN += ":";
                }
            }
            return formattedWWN;
        }
        return hexString;
    }

    /**
     * verify that the volumes in a consistency group are connected to a splitter
     *
     * @param impl - handle for FAPI
     * @param groupUID - consistency group to examine volumes on
     * @throws - RecoverPointException
     */
    public static void verifyCGVolumesAttachedToSplitter(FunctionalAPIImpl impl, ConsistencyGroupUID groupUID)
            throws RecoverPointException {
        ConsistencyGroupSettings groupSettings;
        try {
            groupSettings = impl.getGroupSettings(groupUID);
            // Get the consistency group settings
            for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
                // Run over all replication sets
                for (UserVolumeSettings userVolume : replicationSet.getVolumes()) {
                    logger.info("Volume : "
                            + RecoverPointUtils.getGuidBufferAsString(userVolume.getVolumeInfo().getRawUids(), false)
                            + " is of type " + userVolume.getVolumeType());
                    if (userVolume.getAttachedSplitters().isEmpty()) {
                        String volumeWWN = RecoverPointUtils.getGuidBufferAsString(userVolume.getVolumeInfo().getRawUids(), false);
                        logger.warn("Volume " + volumeWWN + " is not attached to any splitters");
                        Set<SplitterUID> splittersToAttachTo = getSplittersToAttachToForVolume(impl, userVolume.getClusterUID(), userVolume
                                .getVolumeInfo().getVolumeID());
                        for (SplitterUID splitterUID : splittersToAttachTo) {
                            SetVolumeParam volumeParam = new SetVolumeParam();
                            volumeParam.setShouldAttachAsClean(false);
                            volumeParam.setVolumeID(userVolume.getVolumeInfo().getVolumeID());
                            logger.info("Attaching volume " + volumeWWN + " to splitter" + impl.getSplitterName(splitterUID));
                            impl.attachVolumeToSplitter(splitterUID, volumeParam);
                        }
                    } else {
                        for (SplitterUID splitterUID : userVolume.getAttachedSplitters()) {
                            logger.info("Volume " + RecoverPointUtils.getGuidBufferAsString(userVolume.getVolumeInfo().getRawUids(), false)
                                    + " is attached to splitter " + impl.getSplitterName(splitterUID));
                        }
                    }
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingSettingsCG(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingSettingsCG(e);
        }
    }

    /**
     * Get all of the arrays associated with a cluster
     *
     * @param impl endpoint interface
     * @param clusterUID cluster ID
     * @return set of storage systems
     * @throws RecoverPointException
     */
    public static Set<String> getArraysForCluster(FunctionalAPIImpl impl, ClusterUID clusterUID) throws RecoverPointException {
        Set<String> returnArrays = new HashSet<>();
        try {
            logger.info("Finding arrays associated with RP cluster: " + clusterUID.getId());

            // Get the arrays that are configured as splitters.
            ClusterSplittersSettings splitterSettings = impl.getSplittersSettingsFromCluster(clusterUID);
            if (splitterSettings != null && splitterSettings.getSplittersSettings() != null) {
                for (SplitterSettings splitterSetting : splitterSettings.getSplittersSettings()) {

                    // The splitter name will arrive as:
                    // VPLEX: FNM0123456789
                    // VNX: APM0123467890-A
                    // VMAX: SYMM-01947656483
                    // In all cases, we need to distill that into a serial number.
                    //
                    // NOTE: What would be ideal is to take the SplitterSetting.getArrayID() and get back
                    // native array information. I could not find that method, so I had to resort to
                    // this for now. It does work, but it's not ideal. WJEIV
                    Pattern myPattern = Pattern.compile("[A-Z,a-z,0-9]*");
                    Matcher m = myPattern.matcher(splitterSetting.getSplitterName());
                    while (m.find()) {
                        String s = m.group(0);
                        // VMAX is a special case; they put SYMM- at the beginning.
                        // We get around this by finding the third group in the pattern.
                        if (s.equals("SYMM")) {
                            // Iterate to the "-"
                            m.find();
                            // Iterate to the serial number
                            m.find();
                            s = m.group(0);
                        }
                        returnArrays.add(s);
                        logger.info("Found array name: " + s);
                        break;
                    }
                }
            }
            return returnArrays;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingArrays(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingArrays(e);
        }
    }

    /**
     * Finds the splitter(s) to attach a volume to (if any need to be attached).
     *
     * @param impl - handle for FAPI
     * @param ClusterUID - site for the volume
     * @param volume - volume ID we are looking for
     * @return - Set<SplitterUID>
     */
    private static Set<SplitterUID> getSplittersToAttachToForVolume(FunctionalAPIImpl impl, ClusterUID ClusterUID, DeviceUID volume)
            throws RecoverPointException {
        Set<SplitterUID> returnSplitters = new HashSet<SplitterUID>();
        try {
            logger.info("Finding splitters with unattached volumes");
            List<SplitterUID> splittersWithUnattachedVols = impl.getAvailableSplittersToAttachToVolume(ClusterUID, volume);
            for (SplitterUID splitterUID : splittersWithUnattachedVols) {
                SplitterSettings splitterSettings = impl.getSplitterSettings(splitterUID);
                List<DeviceUID> unattachedVolumes = impl.getAvailableVolumesToAttachToSplitter(splitterUID, true);
                if (!unattachedVolumes.isEmpty()) {
                    for (DeviceUID unattachedVolume : unattachedVolumes) {
                        if (unattachedVolume.getId() == volume.getId()) {
                            returnSplitters.add(splitterSettings.getSplitterUID());
                        }
                    }
                }
            }
            return returnSplitters;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingSplittersVolume(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingSplittersVolume(e);
        }
    }

    /**
     * Validates if the two CG's copies passed in are the same, but w/o the CG itself specified.
     *
     * @param globalCopyUID
     * @param globalCopyUID2
     * @return - boolean
     */
    public static boolean cgCopyEqual(GlobalCopyUID globalCopyUID, GlobalCopyUID globalCopyUID2) {
        if ((globalCopyUID.getCopyUID() == globalCopyUID2.getCopyUID()) &&
                (globalCopyUID.getClusterUID().getId() == globalCopyUID2.getClusterUID().getId())) {
            return true;
        }
        return false;
    }

    /**
     * Validates if the two CG's passed in are the same.
     *
     * @param globalCopyUID
     * @param globalCopyUID2
     * @return - boolean
     */
    public static boolean cgCopyEqual(ConsistencyGroupCopyUID copyUID, ConsistencyGroupCopyUID copyUID2) {
        if ((copyUID.getGroupUID().getId() == copyUID2.getGroupUID().getId()) &&
                (copyUID.getGlobalCopyUID().getCopyUID() == copyUID2.getGlobalCopyUID().getCopyUID()) &&
                (copyUID.getGlobalCopyUID().getClusterUID().getId() == copyUID2.getGlobalCopyUID().getClusterUID().getId())) {
            return true;
        }
        return false;
    }

    /**
     * Determine if the CG passed in is a production copy. In the case of metropoint, there are more than one production copy, active and
     * stand-by,
     * and they have different CG Copy UID.
     *
     * @param copyUID
     * @param productionCopiesUIDs
     * @return
     */
    public static boolean isProductionCopy(ConsistencyGroupCopyUID copyUID, List<ConsistencyGroupCopyUID> productionCopiesUIDs) {
        // check if the passed in cgCopyUID is in the list of production copies UID
        for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
            if ((productionCopyUID.getGroupUID().getId() == copyUID.getGroupUID().getId()) &&
                    (productionCopyUID.getGlobalCopyUID().getCopyUID() == copyUID.getGlobalCopyUID().getCopyUID()) &&
                    (productionCopyUID.getGlobalCopyUID().getClusterUID().getId() == copyUID.getGlobalCopyUID().getClusterUID().getId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if a consistency group copy is the standby production copy.
     *
     * @param copyUID the consistency group copy to check.
     * @param state the consistency group state.
     * @param productionCopiesUIDs the list of production copies in the consistency group.
     * @return true if the copy is a standby production copy, false otherwise.
     */
    public static boolean isStandbyProductionCopy(ConsistencyGroupCopyUID copyUID, ConsistencyGroupState state,
            List<ConsistencyGroupCopyUID> productionCopiesUIDs) {
        if (RecoverPointUtils.isProductionCopy(copyUID, productionCopiesUIDs)) {
            for (ConsistencyGroupCopyState copyState : state.getGroupCopiesStates()) {
                // If the state of this production copy is not active, it is the standby production copy
                if (RecoverPointUtils.cgCopyEqual(copyUID, copyState.getCopyUID())
                        && !copyState.isActive()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the standby production copy if one exists. This will only be the case in MetroPoint
     * configurations.
     *
     * @param cgSettings the consistency group settings.
     * @param state the consistency group state.
     * @return the standby copy if one exists, null otherwise.
     */
    public static ConsistencyGroupCopyUID getStandbyProductionCopy(ConsistencyGroupSettings cgSettings, ConsistencyGroupState state) {
        if (cgSettings != null && cgSettings.getGroupCopiesSettings() != null) {
            List<ConsistencyGroupCopyUID> productionCopies = cgSettings.getProductionCopiesUIDs();

            // If there are 2 production copies, we are dealing with a MetroPoint configuration. There will
            // be an active and a standby production copy.
            if (productionCopies != null && productionCopies.size() == 2) {
                for (ConsistencyGroupCopySettings copySetting : cgSettings.getGroupCopiesSettings()) {
                    if (RecoverPointUtils.isProductionCopy(copySetting.getCopyUID(), cgSettings.getProductionCopiesUIDs())
                            && ConsistencyGroupCopyRole.ACTIVE.equals(copySetting.getRoleInfo().getRole())) {
                        for (ConsistencyGroupCopyState copyState : state.getGroupCopiesStates()) {
                            if (RecoverPointUtils.cgCopyEqual(copySetting.getCopyUID(), copyState.getCopyUID())
                                    && !copyState.isActive()) {
                                return copySetting.getCopyUID();
                            }
                        }
                    }
                }

            }
        }
        return null;
    }

    /**
     * @param rpSites
     * @param wwnString
     * @return
     */
    public static DeviceUID getDeviceID(Collection<RPSite> rpSites, String wwnString) {
        for (RPSite rpSite : rpSites) {
            ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
            for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                String siteVolUID = RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false);
                if (siteVolUID.equalsIgnoreCase(wwnString)) {
                    return volume.getVolumeID();
                }
            }
        }
        return null;
    }

    /**
     * @param rpSites
     * @param wwnString
     * @return
     */
    public static DeviceUID getDeviceID(Collection<RPSite> rpSites, String volInternalSiteName, String wwnString) {
        for (RPSite rpSite : rpSites) {
            if (volInternalSiteName.equals(rpSite.getInternalSiteName())) {
                ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                    String siteVolUID = RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false);
                    if (siteVolUID.equalsIgnoreCase(wwnString)) {
                        logger.info("Found volume " + wwnString + " on site " + rpSite.getInternalSiteName() + " as volume ID: "
                                + volume.getVolumeID().getId());
                        return volume.getVolumeID();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Find the transient site ID, given the permanent/unchanging unique internal site name.
     * Needed for some internal operations, like creating cgs.
     *
     * @param impl the established connection to an appliance
     * @param internalSiteName internal site name, never changes
     * @return ClusterUID corresponding to the site that has that internal site name.
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    public static ClusterUID getRPSiteID(FunctionalAPIImpl impl, String internalSiteName)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        FullRecoverPointSettings fullRecoverPointSettings = impl.getFullRecoverPointSettings();
        for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration()
                .getClustersConfigurations()) {
            if (siteSettings.getInternalClusterName().equals(internalSiteName)) {
                return siteSettings.getCluster();
            }
        }
        return null;
    }

    /**
     * Find the internal site name, given the cluster UID
     *
     * @param impl the established connection to an appliance
     * @param ClusterUID corresponding to the site that has that internal site name.
     * @return string internal site name
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    public static String getInternalSiteName(FunctionalAPIImpl impl, ClusterUID clusterID)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        FullRecoverPointSettings fullRecoverPointSettings = impl.getFullRecoverPointSettings();
        for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration()
                .getClustersConfigurations()) {
            if (siteSettings.getCluster().getId() == clusterID.getId()) {
                return siteSettings.getInternalClusterName();
            }
        }
        return null;
    }

    /**
     * @param rpProtectionInfo
     * @return
     */
    public static ConsistencyGroupCopyUID mapRPVolumeProtectionInfoToCGCopyUID(RecoverPointVolumeProtectionInfo rpProtectionInfo) {
        ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
        cgUID.setId(rpProtectionInfo.getRpVolumeGroupID());
        ConsistencyGroupCopyUID cgCopyUID = new ConsistencyGroupCopyUID();
        cgCopyUID.setGlobalCopyUID(new GlobalCopyUID());
        cgCopyUID.getGlobalCopyUID().setCopyUID(rpProtectionInfo.getRpVolumeGroupCopyID());
        cgCopyUID.setGroupUID(cgUID);
        ClusterUID ClusterUID = new ClusterUID();
        ClusterUID.setId(rpProtectionInfo.getRpVolumeSiteID());
        cgCopyUID.getGlobalCopyUID().setClusterUID(ClusterUID);
        return cgCopyUID;
    }

    /**
     * Returns true if the specified RecoverPoint site is licensed.
     *
     * @param impl
     * @return boolean
     * @throws Exception
     */
    public static boolean isSiteLicensed(FunctionalAPIImpl impl) throws Exception
    {
        // A bad license will be caught hear 99% of the time.
        try {
            AccountState accountState = impl.getAccountState();

            List<LicenseState> licenseStates = accountState.getLicensesStates();
            for (LicenseState licenseState : licenseStates) {
                if (licenseState.getLicenseStatus().equals(LicenseStatus.ACTIVE)) {
                    logger.info("Found an active license");
                    return true;
                }
            }

            logger.error("RecoverPoint licenses do not exist, are invalid, or have expired.  Check your RP configuration");
        } catch (FunctionalAPIActionFailedException_Exception e) {
            return false;
        } catch (FunctionalAPIInternalError_Exception e) {
            ;
            return false;
        } catch (Exception f) {
            throw f;
        }

        return false;
    }

    /**
     * Returns the preferred RPA number to use as the primary RPA for a new
     * consistency group. The preferred RPA is determined by examining the
     * current throughput for all of the passed in cluster's RPAs and selecting
     * the RPA that is currently handling the least amount of throughput.
     *
     * @param impl - the established connection to an appliance
     * @param clusterUID - corresponding to the site that we are trying to determine the preferred RPA for.
     * @return RpaUID - object with the preferred RPA number
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     *
     */
    public static RpaUID getPreferredRPAForNewCG(FunctionalAPIImpl impl, ClusterUID clusterUID)
            throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception
    {
        RpaUID preferredRPA = new RpaUID();
        // set the cluster UID to the value being passed
        preferredRPA.setClusterUID(clusterUID);
        // default the rpa to 1
        preferredRPA.setRpaNumber(1);
        try {
            SystemStatistics systemStatistics = impl.getSystemStatistics();
            List<RpaStatistics> rpaStatisticsList = systemStatistics.getRpaStatistics();
            Long smallestThroughput = Long.valueOf(0);
            Long currenThroughput = Long.valueOf(0);
            for (RpaStatistics rpaStatistics : rpaStatisticsList) {
                if (rpaStatistics.getRpaUID().getClusterUID().getId() == clusterUID.getId()) {
                    currenThroughput = Long.valueOf(0);
                    logger.info("Looking at current throughput for RPA " + rpaStatistics.getRpaUID().getRpaNumber() + " on cluster "
                            + rpaStatistics.getRpaUID().getClusterUID().getId());
                    TrafficStatistics trafficStatistics = rpaStatistics.getTraffic();
                    InOutThroughputStatistics throughput = trafficStatistics.getApplicationThroughputStatistics();
                    List<ConnectionOutThroughput> out = throughput.getConnectionsOutThroughputs();
                    for (ConnectionOutThroughput a : out) {
                        logger.info("RPA " + rpaStatistics.getRpaUID().getRpaNumber() + " throughput to cluster " + a.getCluster().getId()
                                + ": " + a.getOutThroughput() + " bytes/sec");
                        currenThroughput += a.getOutThroughput();
                    }
                    if (smallestThroughput.longValue() == 0) {
                        smallestThroughput = currenThroughput;
                    }
                    logger.info("Total throughput for RPA " + rpaStatistics.getRpaUID().getRpaNumber() + ": " + currenThroughput.toString()
                            + " bytes/sec");
                    if (currenThroughput.compareTo(smallestThroughput) <= 0) {
                        smallestThroughput = currenThroughput;
                        preferredRPA.setRpaNumber(rpaStatistics.getRpaUID().getRpaNumber());
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
            logger.info("Encountered error determining the preferred RPA, defaulting to RPA 1");
            preferredRPA.setRpaNumber(1);
            return preferredRPA;
        }
        logger.info("Selected RPA " + preferredRPA.getRpaNumber() + " of cluster " + preferredRPA.getClusterUID().getId()
                + " for the new consistency group");
        return preferredRPA;
    }

    /**
     * Determine the NAA identifier of the xtremio volume in the format RP requires
     *
     * @param nativeGuid string
     * @return the NAA identifier for the xtremio volume
     */
    public static String getXioNativeGuid(String nativeGuid) {
        // nativeGuid coming in XTREMIO+APM00142114518+VOLUME+ea85e053e92a4076bc7c6b76935e14a2
        // we want the final value after the third + sign
        return nativeGuid.split("\\+")[3];
    }

    /**
     * Determines if the volume is an xtremio volume
     *
     * @param nativeGuid string
     * @return boolean indicating if this is an xtremio volume
     */
    public static boolean isXioVolume(String nativeGuid) {
        // nativeGuid coming in XTREMIO+APM00142114518+VOLUME+ea85e053e92a4076bc7c6b76935e14a2
        return nativeGuid.contains("XTREMIO");
    }

}
