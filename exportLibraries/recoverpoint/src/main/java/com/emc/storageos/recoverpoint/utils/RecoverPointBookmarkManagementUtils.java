/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.utils;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.BookmarkConsolidationPolicy;
import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettings;
import com.emc.fapiclient.ws.ConsistencyGroupCopySnapshots;
import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.ConsistencyGroupSettings;
import com.emc.fapiclient.ws.ConsistencyGroupUID;
import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.fapiclient.ws.ReplicationSetSettings;
import com.emc.fapiclient.ws.Snapshot;
import com.emc.fapiclient.ws.SnapshotConsistencyType;
import com.emc.fapiclient.ws.UserVolumeSettings;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient;
import com.emc.storageos.recoverpoint.impl.RecoverPointClient.RecoverPointReturnCode;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.objectmodel.RPConsistencyGroup;
import com.emc.storageos.recoverpoint.objectmodel.RPCopy;
import com.emc.storageos.recoverpoint.requests.CreateBookmarkRequestParams;
import com.emc.storageos.recoverpoint.responses.CreateBookmarkResponse;

public class RecoverPointBookmarkManagementUtils {
    private static Logger logger = LoggerFactory.getLogger(RecoverPointClient.class);
    private final static int numMicroSecondsInMilli = 1000;
    private final static int numMillisInSecond = 1000;

    public enum RecoverPointCopyType {
        CDP_PROTECTION,
        CRR_PROTECTION,
        CLR_PROTECTION,
        MULTISITE_PROTECTION,
        UNKNOWN_PROTECTION
    }

    /**
     * Take a list of WWNs for a RecoverPoint appliance and return consistency group information for the WWNs
     *
     * @param impl - RP handle to perform RP operations
     * @param request - Input request of WWNs
     * @param unmappedWWNs - WWNs that could not be mapped to a consistency group
     *
     * @return WWN to consistency group mappings
     *
     * @throws RecoverPointException
     **/
    public Map<String, RPConsistencyGroup> mapCGsForWWNs(FunctionalAPIImpl impl, CreateBookmarkRequestParams request,
            Set<String> unmappedWWNs) throws RecoverPointException {
        try {

            Set<String> wwnList = request.getVolumeWWNSet();
            if (wwnList.isEmpty()) {
                logger.error("Input WWN list size is 0");
                return null;
            }

            Map<String, RPConsistencyGroup> returnMap = new HashMap<String, RPConsistencyGroup>();
            Set<String> wwnListCopy = new HashSet<String>();

            for (String wwn : wwnList) {
                wwnListCopy.add(wwn.toLowerCase(Locale.ENGLISH));
                logger.info("Mapping source WWN " + wwn.toLowerCase(Locale.ENGLISH) + " to RecoverPoint CG");
            }

            List<ConsistencyGroupSettings> cgSettings = impl.getAllGroupsSettings();
            RPConsistencyGroup rpCG = null;
            for (ConsistencyGroupSettings cgSetting : cgSettings) {
                for (ReplicationSetSettings rsSetting : cgSetting.getReplicationSetsSettings()) {
                    // Only get the unique volumes from a replication set. In MetroPoint, a replication set will list the source volume
                    // twice. This is because in MetroPoint each VPLEX leg is considered a copy but the WWN/volume is the same.
                    Set<UserVolumeSettings> uvSettings = new HashSet<UserVolumeSettings>();
                    uvSettings.addAll(rsSetting.getVolumes());
                    for (UserVolumeSettings uvSetting : uvSettings) {
                        String volUID = RecoverPointUtils.getGuidBufferAsString(uvSetting.getVolumeInfo().getRawUids(), false);
                        if (wwnListCopy.contains(volUID.toLowerCase(Locale.ENGLISH))) {
                            // Remove the volUID from the list
                            wwnListCopy.remove(volUID.toLowerCase(Locale.ENGLISH));

                            // We are getting the index of the first production copy because we only need to
                            // get the cluster ID of the source. All source copies in a CG, across different Rsets, are on the same cluster.
                            // Hence we are ok fetching the first one and getting its cluster id and using it.
                            ConsistencyGroupCopyUID productionCopyUID = cgSetting.getProductionCopiesUIDs().get(0);

                            // Get the RecoverPoint CG name and ID
                            String cgName = cgSetting.getName();
                            ConsistencyGroupUID cgUID = cgSetting.getGroupUID();

                            // Get the Copy information
                            RPCopy rpCopy = new RPCopy();
                            rpCopy.setCGGroupCopyUID(uvSetting.getGroupCopyUID());
                            Set<RPCopy> copies = new HashSet<RPCopy>();
                            copies.add(rpCopy);

                            logger.info("Source WWN: " + volUID + " is on RecoverPoint CG " + cgName + " with RecoverPoint CGID "
                                    + cgUID.getId());
                            rpCG = new RPConsistencyGroup();
                            rpCG.setName(cgName);
                            rpCG.setCGUID(cgUID);
                            rpCG.setClusterUID(productionCopyUID.getGlobalCopyUID().getClusterUID());
                            rpCG.setSiteToArrayIDsMap(mapCGToStorageArraysNoConnection(cgSetting));
                            rpCG.setCopies(copies);
                            returnMap.put(volUID, rpCG);

                            break;
                        }
                    }
                }

                if (wwnListCopy.isEmpty()) {
                    break;
                }
            }

            for (String wwnMissing : wwnListCopy) {
                logger.error("Could not map WWN: " + wwnMissing);
                unmappedWWNs.add(wwnMissing);
            }

            return returnMap;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage());
            return null;
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage());
            return null;
        }
    }

    /**
     * Find the arrays for a CG
     *
     * @param groupSettings - A groupSettings object which contains, among other things, the array information for the CG
     *
     * @return The site to array mappings for the CG
     *
     * @throws RecoverPointException
     **/
    public Map<ClusterUID, Set<String>> mapCGToStorageArraysNoConnection(ConsistencyGroupSettings groupSettings)
            throws RecoverPointException {
        Set<String> siteArraySet = null;
        Map<ClusterUID, Set<String>> returnMap = new HashMap<ClusterUID, Set<String>>();
        Set<ClusterUID> siteSet = new HashSet<ClusterUID>();

        // First find out the sites involved in the CG
        ClusterUID ClusterUID = null;
        boolean foundSite = false;
        for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
            for (UserVolumeSettings userVolume : replicationSet.getVolumes()) {
                ClusterUID = userVolume.getClusterUID();
                foundSite = false;
                for (ClusterUID mappedSite : siteSet) {
                    if (ClusterUID.getId() == mappedSite.getId())
                    {
                        foundSite = true;
                        break;
                    }
                }
                if (!foundSite) {
                    siteSet.add(ClusterUID);
                }
            }
        }
        for (ClusterUID mappedSite : siteSet) {
            siteArraySet = new HashSet<String>();
            for (ReplicationSetSettings replicationSet1 : groupSettings.getReplicationSetsSettings()) {
                for (UserVolumeSettings userVolume : replicationSet1.getVolumes()) {
                    ClusterUID = userVolume.getClusterUID();
                    if (ClusterUID.getId() == mappedSite.getId()) {
                        if (userVolume.getVolumeInfo().getVendorName().equalsIgnoreCase("DGC")) {
                            siteArraySet.add(userVolume.getVolumeInfo().getArraySerialNumber());
                        }
                    }
                }
            }
            if (!siteArraySet.isEmpty()) {
                returnMap.put(mappedSite, siteArraySet);
            }
        }
        if (!returnMap.isEmpty()) {
            return returnMap;
        } else {
            return null;
        }
    }

    /**
     * Create bookmarks for a CG
     *
     * @param impl - RP handle to use for RP operations
     * @param rpCGMap - The mapping of RP CGs to WWNs. Used to create a list of CGs to bookmark
     * @param request - Information about the bookmark to request
     *
     * @return CreateBookmarkResponse - Results of the create bookmark.
     *         TODO: Return bookmark information (date/time)
     *
     * @throws RecoverPointException
     **/
    public CreateBookmarkResponse createCGBookmarks(FunctionalAPIImpl impl, Map<String,
            RPConsistencyGroup> rpCGMap,
            CreateBookmarkRequestParams request) throws RecoverPointException {
        Set<ConsistencyGroupUID> uniqueCGUIDSet = new HashSet<ConsistencyGroupUID>();
        List<ConsistencyGroupUID> uniqueCGUIDlist = new LinkedList<ConsistencyGroupUID>();
        Set<RPConsistencyGroup> rpCGSet = new HashSet<RPConsistencyGroup>();
        CreateBookmarkResponse response = new CreateBookmarkResponse();

        for (String volume : rpCGMap.keySet()) {
            RPConsistencyGroup rpCG = rpCGMap.get(volume);
            if (rpCG.getCGUID() != null) {
                boolean foundCGUID = false;
                ConsistencyGroupUID cguid = rpCG.getCGUID();
                for (ConsistencyGroupUID cguidunique : uniqueCGUIDSet) {
                    if (cguidunique.getId() == cguid.getId()) {
                        foundCGUID = true;
                        break;
                    }
                }
                if (!foundCGUID) {
                    logger.info("Adding CG: " + rpCG.getName() + " with ID " + rpCG.getCGUID().getId() + " to unique CGUID list");
                    uniqueCGUIDSet.add(cguid);
                    uniqueCGUIDlist.add(cguid);
                    rpCGSet.add(rpCG);
                }
            }
        }

        // Make sure the CG is in a good state before we make bookmarks
        // RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        // for (ConsistencyGroupUID cgID : uniqueCGUIDlist) {
        // Make sure the CG is ready for enable
        // imageManager.waitForCGLinkState(impl, cgID, RecoverPointImageManagementUtils.getPipeActiveState(impl, cgID));
        // }

        try {
            impl.createBookmark(uniqueCGUIDlist, request.getBookmark(),
                    BookmarkConsolidationPolicy.NEVER_CONSOLIDATE, SnapshotConsistencyType.APPLICATION_CONSISTENT);
            logger.info(String.format("Created RP Bookmark successfully: %s", request.getBookmark()));
            response.setCgBookmarkMap(findRPBookmarks(impl, rpCGSet, request));
            response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToCreateBookmarkOnRecoverPoint(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToCreateBookmarkOnRecoverPoint(e);
        }

        return response;
    }

    /**
     * Find the bookmarks that were created for a CG
     *
     * @param impl - RP handle to use for RP operations
     * @param rpCGset - List of CGs that had this bookmark created
     * @param request - Information about the bookmark that was created on the CGs
     *
     * @return Map of CGs with the bookmark information (CDP and/or CRR)
     *
     * @throws RecoverPointException
     **/
    public Map<RPConsistencyGroup, Set<RPBookmark>> findRPBookmarks(FunctionalAPIImpl impl, Set<RPConsistencyGroup> rpCGSet,
            CreateBookmarkRequestParams request)
            throws RecoverPointException {

        Map<RPConsistencyGroup, Set<RPBookmark>> returnMap = new HashMap<RPConsistencyGroup, Set<RPBookmark>>();
        final int numRetries = 6;
        final int secondsToWaitForRetry = 5;
        RecoverPointCopyType rpCopyType = RecoverPointCopyType.UNKNOWN_PROTECTION;
        boolean wantCDP = false;
        boolean wantCRR = false;
        boolean acceptAnyCopy = false;	// If rpCopyType not specified

        // TODO: acceptAnyCopy will always be set to true, this is because no RP copy type is being specified. This will be taken care of
        // later.
        if (rpCopyType == null || rpCopyType == RecoverPointCopyType.UNKNOWN_PROTECTION) {
            acceptAnyCopy = true;
        }
        else {
            if (rpCopyType == RecoverPointCopyType.CDP_PROTECTION) {
                wantCDP = true;
            } else if (rpCopyType == RecoverPointCopyType.CRR_PROTECTION) {
                wantCRR = true;
            } else if (rpCopyType == RecoverPointCopyType.CRR_PROTECTION) {
                wantCRR = true;
                wantCDP = true;
            }
        }
        boolean tooManyRetries = false;
        for (RPConsistencyGroup rpCG : rpCGSet) {
            if (tooManyRetries) {
                // Stop trying
                break;
            }
            for (int i = 0; i < numRetries; i++) {
                logger.info(String.format("Getting event markers for CG: %s.  Attempt number %d.  Copy type: %s",
                        rpCG.getName() != null ? rpCG.getName() : rpCG.getCGUID().getId(),
                        i,
                        rpCopyType.toString()));
                Set<RPBookmark> rpEventMarkersForCG = getBookmarksForMostRecentBookmarkName(impl, request, rpCG.getCGUID());
                if (rpEventMarkersForCG != null) {
                    if (acceptAnyCopy && (!rpEventMarkersForCG.isEmpty())) {
                        // We will take anything, and we found at least one event marker
                        returnMap.put(rpCG, rpEventMarkersForCG);
                        break; // Go to the next CG
                    } else if ((wantCDP && wantCRR) && rpEventMarkersForCG.size() > 1) {
                        // Need 2 event markers for CLR
                        returnMap.put(rpCG, rpEventMarkersForCG);
                        break; // Go to the next CG
                    } else if ((wantCDP && wantCRR) && rpEventMarkersForCG.size() < 2) {
                        logger.error("Didn't find enough bookmarks for CG: " + rpCG.getName() + ". Going to sleep and retry.");
                    } else if (!rpEventMarkersForCG.isEmpty()) {
                        // Either want CDP or CRR and we found at least 1
                        returnMap.put(rpCG, rpEventMarkersForCG);
                        break; // Go to the next CG
                    } else {
                        logger.error("Didn't find enough bookmarks for CG: " + rpCG.getName() + ". Going to sleep and retry.");
                    }
                } else {
                    // Didn't get what we wanted
                    logger.error("Didn't find any bookmarks for CG: " + rpCG.getName() + ". Going to sleep and retry.");
                }
                try {
                    Thread.sleep(Long.valueOf((secondsToWaitForRetry * numMillisInSecond)));
                } catch (InterruptedException e) { // NOSONAR
                    // It's ok to ignore this
                }
            }
        }

        if (returnMap.size() != rpCGSet.size()) {
            throw RecoverPointException.exceptions.failedToFindExpectedBookmarks();
        }

        return returnMap;
    }

    /**
     * Find the most recent bookmarks that were created for a CG with a given name
     *
     * @param impl - RP handle to use for RP operations
     * @param request - Information about the bookmark that was created on the CGs
     * @param cgUID - The CG to look for bookmarks
     *
     * @return A set of RP bookmarks found on the CG
     *
     * @throws RecoverPointException
     **/
    private Set<RPBookmark> getBookmarksForMostRecentBookmarkName(FunctionalAPIImpl impl,
            CreateBookmarkRequestParams request, ConsistencyGroupUID cgUID) throws RecoverPointException {
        Set<RPBookmark> returnBookmarkSet = null;
        try {
            String bookmarkName = request.getBookmark();
            Set<RPBookmark> bookmarkSet = new HashSet<RPBookmark>();

            ConsistencyGroupSettings cgSettings = impl.getGroupSettings(cgUID);
            List<ConsistencyGroupCopySettings> cgCopySettings = cgSettings.getGroupCopiesSettings();
            ConsistencyGroupCopyUID prodCopyUID = cgSettings.getLatestSourceCopyUID();
            for (ConsistencyGroupCopySettings cgcopysetting : cgCopySettings) {
                RPBookmark newEM = new RPBookmark();
                newEM.setCGGroupCopyUID(cgcopysetting.getCopyUID());
                newEM.setBookmarkName(bookmarkName);
                newEM.setBookmarkTime(null);
                bookmarkSet.add(newEM);
            }

            logger.debug("Getting list of snapshots with event marker name: " + bookmarkName);
            List<ConsistencyGroupCopySnapshots> cgCopySnapList = impl.getGroupSnapshots(cgUID).getCopiesSnapshots();

            for (ConsistencyGroupCopySnapshots cgCopySnap : cgCopySnapList) {
                ConsistencyGroupCopyUID copyUID = cgCopySnap.getCopyUID();
                logger.debug("Found " + cgCopySnap.getSnapshots().size() + " snapshots on copy: " + copyUID.getGlobalCopyUID().getCopyUID());
                for (Snapshot snapItem : cgCopySnap.getSnapshots()) {
                    if (snapItem.getDescription().equals(bookmarkName)) {
                        for (RPBookmark rpBookmark : bookmarkSet) {
                            ConsistencyGroupCopyUID rpBookmarkCopyCG = rpBookmark.getCGGroupCopyUID();
                            if (RecoverPointUtils.cgCopyEqual(copyUID, rpBookmarkCopyCG)) {
                                // Update record with bookmark time, and add back
                                rpBookmark.setBookmarkTime(snapItem.getClosingTimeStamp());
                                Timestamp protectionTimeStr = new Timestamp(snapItem.getClosingTimeStamp().getTimeInMicroSeconds()
                                        / numMicroSecondsInMilli);
                                // Remove it, and add it back
                                RPBookmark updatedBookmark = new RPBookmark();
                                updatedBookmark.setBookmarkTime(snapItem.getClosingTimeStamp());
                                updatedBookmark.setCGGroupCopyUID(rpBookmark.getCGGroupCopyUID());
                                logger.info("Found our bookmark with time: " + protectionTimeStr.toString() + " and group copy ID: " +
                                        rpBookmark.getCGGroupCopyUID().getGlobalCopyUID().getCopyUID());
                                updatedBookmark.setBookmarkName(rpBookmark.getBookmarkName());
                                updatedBookmark.setProductionCopyUID(prodCopyUID);
                                if (returnBookmarkSet == null) {
                                    returnBookmarkSet = new HashSet<RPBookmark>();
                                }

                                // TODO: logic is suspect, need to revisit. Why are we removing and adding the same object??
                                returnBookmarkSet.remove(updatedBookmark);
                                returnBookmarkSet.add(updatedBookmark);

                            }
                        }
                    }
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.exceptionLookingForBookmarks(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.exceptionLookingForBookmarks(e);
        }

        logger.debug("Return set has " + ((returnBookmarkSet != null) ? returnBookmarkSet.size() : 0) + " items");
        return ((returnBookmarkSet != null) ? returnBookmarkSet : null);

    }

    /**
     * Find the bookmarks associated with a consistency group
     *
     * @param impl - RP handle to use for RP operations
     * @param cgUID - The CG to look for bookmarks
     *
     * @return A set of RP bookmarks found on the CG
     *
     * @throws RecoverPointException
     **/
    public List<RPBookmark> getRPBookmarksForCG(FunctionalAPIImpl impl, ConsistencyGroupUID cgUID) throws RecoverPointException {
        List<RPBookmark> returnBookmarkSet = null;
        try {
            logger.debug("Getting list of snapshots for CG: " + cgUID.getId());
            List<ConsistencyGroupCopySnapshots> cgCopySnapList = impl.getGroupSnapshots(cgUID).getCopiesSnapshots();
            for (ConsistencyGroupCopySnapshots cgCopySnap : cgCopySnapList) {
                ConsistencyGroupCopyUID copyUID = cgCopySnap.getCopyUID();
                logger.debug("Found " + cgCopySnap.getSnapshots().size() + " snapshots on copy: " + copyUID.getGlobalCopyUID().getCopyUID());
                for (Snapshot snapItem : cgCopySnap.getSnapshots()) {
                    // We're not interested in bookmarks without names
                    if (snapItem.getDescription() != null && !snapItem.getDescription().isEmpty()) {
                        RPBookmark bookmark = new RPBookmark();
                        bookmark.setBookmarkTime(snapItem.getClosingTimeStamp());
                        bookmark.setCGGroupCopyUID(cgCopySnap.getCopyUID());
                        bookmark.setBookmarkName(snapItem.getDescription());
                        if (returnBookmarkSet == null) {
                            returnBookmarkSet = new ArrayList<RPBookmark>();
                        }

                        returnBookmarkSet.add(bookmark);
                        logger.debug("Recording bookmark: " + bookmark.getBookmarkName());
                    }
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.exceptionLookingForBookmarks(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.exceptionLookingForBookmarks(e);
        }

        logger.debug("Return set has " + ((returnBookmarkSet != null) ? returnBookmarkSet.size() : 0) + " items");
        return ((returnBookmarkSet != null) ? returnBookmarkSet : null);

    }
}
