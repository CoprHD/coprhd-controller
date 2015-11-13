/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.impl;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.fapiclient.ws.ActivationSettingsChangesParams;
import com.emc.fapiclient.ws.ClusterConfiguration;
import com.emc.fapiclient.ws.ClusterRPAsState;
import com.emc.fapiclient.ws.ClusterSANVolumes;
import com.emc.fapiclient.ws.ClusterSettings;
import com.emc.fapiclient.ws.ClusterUID;
import com.emc.fapiclient.ws.ConnectionOutThroughput;
import com.emc.fapiclient.ws.ConsistencyGroupCopyJournal;
import com.emc.fapiclient.ws.ConsistencyGroupCopyRole;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettings;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettingsChangesParam;
import com.emc.fapiclient.ws.ConsistencyGroupCopySettingsParam;
import com.emc.fapiclient.ws.ConsistencyGroupCopyState;
import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.ConsistencyGroupLinkPolicy;
import com.emc.fapiclient.ws.ConsistencyGroupLinkSettings;
import com.emc.fapiclient.ws.ConsistencyGroupLinkState;
import com.emc.fapiclient.ws.ConsistencyGroupLinkUID;
import com.emc.fapiclient.ws.ConsistencyGroupSettings;
import com.emc.fapiclient.ws.ConsistencyGroupSettingsChangesParam;
import com.emc.fapiclient.ws.ConsistencyGroupState;
import com.emc.fapiclient.ws.ConsistencyGroupUID;
import com.emc.fapiclient.ws.DeviceUID;
import com.emc.fapiclient.ws.FiberChannelInitiatorInformation;
import com.emc.fapiclient.ws.FullConsistencyGroupCopyPolicy;
import com.emc.fapiclient.ws.FullConsistencyGroupLinkPolicy;
import com.emc.fapiclient.ws.FullConsistencyGroupPolicy;
import com.emc.fapiclient.ws.FullRecoverPointSettings;
import com.emc.fapiclient.ws.FunctionalAPIActionFailedException_Exception;
import com.emc.fapiclient.ws.FunctionalAPIImpl;
import com.emc.fapiclient.ws.FunctionalAPIInternalError_Exception;
import com.emc.fapiclient.ws.FunctionalAPIValidationException_Exception;
import com.emc.fapiclient.ws.GlobalCopyUID;
import com.emc.fapiclient.ws.ImageAccessMode;
import com.emc.fapiclient.ws.InitiatorInformation;
import com.emc.fapiclient.ws.JournalVolumeSettings;
import com.emc.fapiclient.ws.LinkAdvancedPolicy;
import com.emc.fapiclient.ws.LinkProtectionPolicy;
import com.emc.fapiclient.ws.MonitoredParameter;
import com.emc.fapiclient.ws.MonitoredParametersStatus;
import com.emc.fapiclient.ws.PipeState;
import com.emc.fapiclient.ws.ProtectionMode;
import com.emc.fapiclient.ws.Quantity;
import com.emc.fapiclient.ws.QuantityType;
import com.emc.fapiclient.ws.RemoteClusterConnectionInformation;
import com.emc.fapiclient.ws.ReplicationSetSettings;
import com.emc.fapiclient.ws.ReplicationSetSettingsChangesParam;
import com.emc.fapiclient.ws.ReplicationSetUID;
import com.emc.fapiclient.ws.RpaConfiguration;
import com.emc.fapiclient.ws.RpaState;
import com.emc.fapiclient.ws.RpaStatistics;
import com.emc.fapiclient.ws.RpaUID;
import com.emc.fapiclient.ws.RpoMinimizationType;
import com.emc.fapiclient.ws.RpoPolicy;
import com.emc.fapiclient.ws.SnapshotGranularity;
import com.emc.fapiclient.ws.SnapshotShippingMode;
import com.emc.fapiclient.ws.SnapshotShippingPolicy;
import com.emc.fapiclient.ws.SyncReplicationThreshold;
import com.emc.fapiclient.ws.SystemStatistics;
import com.emc.fapiclient.ws.UserVolumeSettings;
import com.emc.fapiclient.ws.UserVolumeSettingsChangesParam;
import com.emc.fapiclient.ws.VolumeInformation;
import com.emc.fapiclient.ws.WanCompression;
import com.emc.storageos.recoverpoint.exceptions.RecoverPointException;
import com.emc.storageos.recoverpoint.objectmodel.RPBookmark;
import com.emc.storageos.recoverpoint.objectmodel.RPConsistencyGroup;
import com.emc.storageos.recoverpoint.objectmodel.RPCopy;
import com.emc.storageos.recoverpoint.objectmodel.RPSite;
import com.emc.storageos.recoverpoint.requests.CGRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateBookmarkRequestParams;
import com.emc.storageos.recoverpoint.requests.CreateCopyParams;
import com.emc.storageos.recoverpoint.requests.CreateRSetParams;
import com.emc.storageos.recoverpoint.requests.CreateVolumeParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyDisableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyEnableImageRequestParams;
import com.emc.storageos.recoverpoint.requests.MultiCopyRestoreImageRequestParams;
import com.emc.storageos.recoverpoint.requests.RPCopyRequestParams;
import com.emc.storageos.recoverpoint.requests.RecreateReplicationSetRequestParams;
import com.emc.storageos.recoverpoint.requests.RecreateReplicationSetRequestParams.CreateRSetVolumeParams;
import com.emc.storageos.recoverpoint.responses.CreateBookmarkResponse;
import com.emc.storageos.recoverpoint.responses.GetBookmarksResponse;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse;
import com.emc.storageos.recoverpoint.responses.GetCGsResponse.GetCGStateResponse;
import com.emc.storageos.recoverpoint.responses.GetCopyResponse;
import com.emc.storageos.recoverpoint.responses.GetRSetResponse;
import com.emc.storageos.recoverpoint.responses.GetVolumeResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyDisableImageResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyEnableImageResponse;
import com.emc.storageos.recoverpoint.responses.MultiCopyRestoreImageResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointCGResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse;
import com.emc.storageos.recoverpoint.responses.RecoverPointStatisticsResponse.ProtectionSystemParameters;
import com.emc.storageos.recoverpoint.responses.RecoverPointVolumeProtectionInfo;
import com.emc.storageos.recoverpoint.utils.RecoverPointBookmarkManagementUtils;
import com.emc.storageos.recoverpoint.utils.RecoverPointConnection;
import com.emc.storageos.recoverpoint.utils.RecoverPointImageManagementUtils;
import com.emc.storageos.recoverpoint.utils.RecoverPointUtils;
import com.emc.storageos.recoverpoint.utils.WwnUtils;

/**
 * Client implementation of the RecoverPoint controller
 *
 */

public class RecoverPointClient {

    // 10s, for RP between RP operations that adds/sets things on the RP. in RP 4.1 SP1 we started encountering an issue which resulted in
    // conflicts
    // between the RPAs when things ran too quickly.
    private static final int RP_OPERATION_WAIT_TIME = 10000;
    
    // Number of times to wait/check RP for a delete attempt before
    // ViPR gives up.
    private static final int MAX_WAIT_FOR_RP_DELETE_ATTEMPTS = 10;

    FunctionalAPIImpl functionalAPI;

    public enum RecoverPointReturnCode {
        FAIL,
        SUCCESS,
        PARTIAL_FAIL
    }

    public enum RecoverPointCGState {
        READY,	// All CG copies ready
        PAUSED,	// All CG copies paused
        STOPPED,        // All CG copies stopped
        MIXED,	// CG copies are in different states
        GONE	// CG no longer exists
    }

    public enum RecoverPointCGCopyState {
        READY,
        PAUSED,
        STOPPED,
        IMAGE_ENABLED,
    }

    public enum RecoverPointCGCopyType {
        PRODUCTION(0, "production"),
        LOCAL(1, "local"),
        REMOTE(0, "remote");

        // copy number is the number of the copy that goes into the GlobalCopyUID
        private final int copyNumber;
        private final String asString;

        private RecoverPointCGCopyType(int copyNumber, String str) {
            this.copyNumber = copyNumber;
            this.asString = str;
        }

        public int getCopyNumber() {
            return copyNumber;
        }

        public boolean isRemote() {
            return this.equals(RecoverPointCGCopyType.REMOTE);
        }

        @Override
        public String toString() {
            return asString;
        }

        /**
         * @return
         */
        public boolean isProduction() {
            return this.equals(RecoverPointCGCopyType.PRODUCTION);
        }
    }

    private static Logger logger = LoggerFactory.getLogger(RecoverPointClient.class);

    private URI _endpoint;
    private String _username;
    private String _password;

    /**
     * Default constructor.
     */
    public RecoverPointClient() {
    }

    public RecoverPointClient(URI endpoint, String username, String password) {
        this._endpoint = endpoint;
        this.setUsername(username);
        this.setPassword(password);
    }

    public void setFunctionalAPI(FunctionalAPIImpl functionalAPI) {
        this.functionalAPI = functionalAPI;
    }

    public URI getEndpoint() {
        return _endpoint;
    }

    /**
     * tests credentials to ensure they are correct, and that the RP site is up and running
     *
     * @return 0 for success
     * @throws RecoverPointException
     **/
    public int ping() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        try {
            logger.info("RecoverPoint service: Checking RP access for endpoint: " + _endpoint.toASCIIString());
            functionalAPI.getAccountSettings();
            logger.info("Successful ping for Mgmt IP: " + mgmtIPAddress);
            return 0;
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, getCause(e));
        }
    }
    
    /**
     * Method to refresh the connection of this RPClient to the Recover Point System
     * via FAPI. Used just in case the connection has become stale. 
     */
    public void reconnect() {
        logger.info(String.format("Attempt to refresh connection to RecoverPoint at %s", this.getEndpoint()));
        try {
            // Remove existing FAPI reference
            this.setFunctionalAPI(null);
            
            // Create the connection
            FunctionalAPIImpl impl = new RecoverPointConnection().connect(this.getEndpoint(), this.getUsername(), this.getPassword());

            // Add the new FAPI instance to the RecoverPointClient
            this.setFunctionalAPI(impl);

            // We just connected but to be safe, lets do a quick ping to confirm that
            // we can reach the new RecoverPoint client
            this.ping();
            
            logger.info("Connection refreshed.");
        } catch (Exception e) {
            logger.error("Received " + e.toString() + ". Failed to refresh RP connection: " + this.getEndpoint().toString() +
                    ", Cause: " + RecoverPointClient.getCause(e));           
            throw RecoverPointException.exceptions.failedToPingMgmtIP(this.getEndpoint().toString(), RecoverPointClient.getCause(e));            
        }
    }

    public Set<String> getClusterTopology() throws RecoverPointException {
        Set<String> clusterTopology = new HashSet<String>();
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        try {
            Map<Long, String> clusterSiteNameMap = new HashMap<Long, String>();
            for (ClusterConfiguration clusterInfo : functionalAPI.getFullRecoverPointSettings().getSystemSettings()
                    .getGlobalSystemConfiguration().getClustersConfigurations()) {
                clusterSiteNameMap.put(clusterInfo.getCluster().getId(), clusterInfo.getInternalClusterName());
            }
            logger.info("RecoverPoint service: Returning all RP Sites associated with endpoint: " + _endpoint);
            for (ClusterConfiguration clusterInfo : functionalAPI.getFullRecoverPointSettings().getSystemSettings()
                    .getGlobalSystemConfiguration().getClustersConfigurations()) {
                for (RemoteClusterConnectionInformation connectionInfo : clusterInfo.getRemoteClustersConnectionInformations()) {
                    // Find the internal site name associated with the cluster name
                    clusterTopology.add(clusterInfo.getInternalClusterName() + " "
                            + clusterSiteNameMap.get(connectionInfo.getCluster().getId()) + " "
                            + connectionInfo.getConnectionType().toString());
                }
            }
            return clusterTopology;
        } catch (RecoverPointException e) {
            throw e;
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, getCause(e));
        }
    }

    /**
     * Returns a list of sites associated with any given site (or RPA). The user can/should enter a site mgmt IP addr, but they can also
     * support the mgmt IP addr of an RPA. This method will return sites, not RPAs
     *
     * @return set of discovered RP sites
     *
     * @throws RecoverPointException
     **/
    public Set<RPSite> getAssociatedRPSites() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        try {
            logger.info("RecoverPoint service: Returning all RP Sites associated with endpoint: " + _endpoint);
            Set<RPSite> returnSiteSet = new HashSet<RPSite>();
            RPSite discoveredSite = null;
            ClusterUID localClusterUID = functionalAPI.getLocalCluster();
            String localSiteName = "unknown";
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            SortedSet<String> siteNames = new TreeSet<String>();

            for (ClusterSettings siteSettings : fullRecoverPointSettings.getSystemSettings().getClustersSettings()) {
                String siteName = siteSettings.getClusterName();
                siteNames.add(siteName);
            }

            Iterator<String> iter = siteNames.iterator();
            String installationId = "";
            while (iter.hasNext()) {
                installationId += iter.next();
                if (iter.hasNext()) {
                    installationId += "_";
                }
            }

            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration()
                    .getClustersConfigurations()) {
                // TODO: Support multiple management IPs per site
                String siteIP = siteSettings.getManagementIPs().get(0).getIp();
                String siteName = siteSettings.getClusterName();
                if (siteIP == null) {
                    throw RecoverPointException.exceptions.cannotDetermineMgmtIPSite(siteName);
                }

                List<RpaConfiguration> rpaList = siteSettings.getRpasConfigurations();
                discoveredSite = new RPSite();
                discoveredSite.setSiteName(siteName);
                discoveredSite.setSiteManagementIPv4(siteIP);
                discoveredSite.setSiteVersion(functionalAPI.getRecoverPointVersion().getVersion());
                discoveredSite.setSiteVolumes(functionalAPI.getClusterSANVolumes(siteSettings.getCluster(), true));
                discoveredSite.setInternalSiteName(siteSettings.getInternalClusterName());
                discoveredSite.setSiteUID(siteSettings.getCluster().getId());
                if (localClusterUID.getId() == siteSettings.getCluster().getId()) {
                    localSiteName = siteName;
                }
                discoveredSite.setNumRPAs(rpaList.size());

                String siteGUID = installationId + ":" + siteSettings.getCluster().getId();
                logger.info("SITE GUID:  " + siteGUID);
                discoveredSite.setSiteGUID(siteGUID);
                if (localClusterUID.getId() == siteSettings.getCluster().getId()) {
                    logger.info("Discovered local site name: " + siteName + ", site IP: " + siteIP + ", RP version: "
                            + discoveredSite.getSiteVersion() + ", num RPAs: "
                            + discoveredSite.getNumRPAs());

                } else {
                    logger.info("Discovered non-local site name: " + siteName + ", site IP: " + siteIP + ", RP version: "
                            + discoveredSite.getSiteVersion()
                            + ", num RPAs: " + discoveredSite.getNumRPAs());
                }

                returnSiteSet.add(discoveredSite);
            }

            // 99% of unlicensed RP system errors will be caught here
            if (!RecoverPointUtils.isSiteLicensed(functionalAPI)) {
                throw RecoverPointException.exceptions.siteNotLicensed(localSiteName);
            }

            return returnSiteSet;

        } catch (RecoverPointException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw RecoverPointException.exceptions.failedToPingMgmtIP(mgmtIPAddress, getCause(e));
        }
    }

    /**
     * Checks to see if the given CG exists.
     *
     * @param cgName the consistency group name
     * @return true if the consistency group exists, false otherwise
     * @throws RecoverPointException
     */
    public boolean doesCgExist(String cgName) throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        try {
            // Make sure the CG name is unique.
            List<ConsistencyGroupUID> allCgs = functionalAPI.getAllConsistencyGroups();
            for (ConsistencyGroupUID cg : allCgs) {
                ConsistencyGroupSettings settings = functionalAPI.getGroupSettings(cg);
                if (settings.getName().toString().equalsIgnoreCase(cgName)) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.failedToLookupConsistencyGroup(cgName, getCause(e));
        }

        return false;
    }

    /**
     * Returns all CGs, policies, and volumes within the CG.
     *
     * @return a set of RP consistency group objects
     * @throws RecoverPointException
     */
    public Set<GetCGsResponse> getAllCGs() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        
        Set<GetCGsResponse> cgs = new HashSet<GetCGsResponse>();
        try {
            // Quickly get a map of cluster/sitenames
            Map<Long, String> clusterIdToInternalSiteNameMap = new HashMap<Long, String>();
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration()
                    .getClustersConfigurations()) {
                clusterIdToInternalSiteNameMap.put(siteSettings.getCluster().getId(), siteSettings.getInternalClusterName());
            }
            
            // Make sure the CG name is unique.
            List<ConsistencyGroupUID> allCgs = functionalAPI.getAllConsistencyGroups();
            for (ConsistencyGroupUID cg : allCgs) {
                ConsistencyGroupSettings settings = functionalAPI.getGroupSettings(cg);
                logger.info("Processing CG found on RecoverPoint system: " + settings.getName());

                // First storage attributes about the top-level CG
                GetCGsResponse ncg = new GetCGsResponse();
                ncg.setCgName(settings.getName());
                ncg.setCgId(cg.getId());
                ncg.cgPolicy = new GetCGsResponse.GetPolicyResponse();
                
                // Find and store the policy information
                if (settings.getActiveLinksSettings() != null) {
                    for (ConsistencyGroupLinkSettings cgls : settings.getActiveLinksSettings()) {
                        if (cgls.getLinkPolicy() != null && cgls.getLinkPolicy().getProtectionPolicy() != null) {
                            if (cgls.getLinkPolicy().getProtectionPolicy().getProtectionType() != null) {
                                if (cgls.getLinkPolicy().getProtectionPolicy().getProtectionType().toString().equalsIgnoreCase(ProtectionMode.SYNCHRONOUS.toString())) {
                                    ncg.cgPolicy.synchronous = true;
                                } else {
                                    ncg.cgPolicy.synchronous = false;
                                }
                            }
                            
                            if (cgls.getLinkPolicy().getProtectionPolicy().getRpoPolicy() != null &&
                                cgls.getLinkPolicy().getProtectionPolicy().getRpoPolicy().getMaximumAllowedLag() != null) {
                                ncg.cgPolicy.rpoType = cgls.getLinkPolicy().getProtectionPolicy().getRpoPolicy().getMaximumAllowedLag().getType().name();
                                ncg.cgPolicy.rpoValue = cgls.getLinkPolicy().getProtectionPolicy().getRpoPolicy().getMaximumAllowedLag().getValue(); 
                            }
                        }
                    }
                }
                
                // We assume CG health until we see something that indicates otherwise.
                ncg.cgState = GetCGsResponse.GetCGStateResponse.HEALTHY;
                RecoverPointCGState cgState = this.getCGState(cg);
                if (cgState.equals(RecoverPointCGState.GONE)) {
                    ncg.cgState = GetCGStateResponse.UNHEALTHY_ERROR;
                } else if (cgState.equals(RecoverPointCGState.MIXED)) {
                    ncg.cgState = GetCGStateResponse.UNHEALTHY_PAUSED_OR_DISABLED;
                } else if (cgState.equals(RecoverPointCGState.PAUSED)) {
                    ncg.cgState = GetCGStateResponse.UNHEALTHY_PAUSED_OR_DISABLED;
                } else if (cgState.equals(RecoverPointCGState.STOPPED)) {
                    ncg.cgState = GetCGStateResponse.UNHEALTHY_PAUSED_OR_DISABLED;
                }
                
                // Fill in the Copy information
                if (settings.getGroupCopiesSettings() == null) {
                    continue;
                }

                Map<String, String> copyUIDToNameMap = new HashMap<String, String>();
                // used to set the copy uid on the rset volume when adding rsets
                Set<String> productionCopiesUID = new HashSet<String>();

                // Retrieve all RP copies for this CG
                for (ConsistencyGroupCopySettings copySettings : settings.getGroupCopiesSettings()) {
                    GetCopyResponse copy = new GetCopyResponse();
                    copy.setName(copySettings.getName());
                    String copyID = copySettings.getCopyUID().getGlobalCopyUID().getClusterUID().getId() + "-" + 
                            copySettings.getCopyUID().getGlobalCopyUID().getCopyUID();
                    copyUIDToNameMap.put(copyID,  copySettings.getName());
                    
                    GlobalCopyUID globalCopyUID = copySettings.getCopyUID().getGlobalCopyUID();
                    if (ConsistencyGroupCopyRole.ACTIVE.equals(copySettings.getRoleInfo().getRole()) ||
                            ConsistencyGroupCopyRole.TEMPORARY_ACTIVE.equals(copySettings.getRoleInfo().getRole())) {
                        productionCopiesUID.add(copyID);
                        copy.setProduction(true);
                    } else {
                        copy.setProduction(false);
                    }

                    if (copySettings.getJournal() == null || copySettings.getJournal().getJournalVolumes() == null) {
                        continue;
                    }
                    
                    for (JournalVolumeSettings journal : copySettings.getJournal().getJournalVolumes()) {
                        GetVolumeResponse volume = new GetVolumeResponse();
                  
                        volume.setRpCopyName(copySettings.getName());
                        volume.setInternalSiteName(clusterIdToInternalSiteNameMap.get(journal.getClusterUID().getId()));
                        
                        // Need to extract the rawUids to format: 600601608D20370089260942815CE511
                        volume.setWwn(RecoverPointUtils.getGuidBufferAsString(journal.getVolumeInfo().getRawUids(), false).toUpperCase(Locale.ENGLISH));
                        if (copy.getJournals() == null) {
                            copy.setJournals(new ArrayList<GetVolumeResponse>());
                        }
                        
                        copy.getJournals().add(volume);
                    }
                    
                    if (ncg.getCopies() == null) {
                        ncg.setCopies(new ArrayList<GetCopyResponse>());
                    }
                    
                    ncg.getCopies().add(copy);
                }

                // Retrieve all replication sets for this CG
                for (ReplicationSetSettings rsetSettings : settings.getReplicationSetsSettings()) {
                    GetRSetResponse rset = new GetRSetResponse();
                    rset.setName(rsetSettings.getReplicationSetName());
                    
                    if (rsetSettings.getVolumes() == null) {
                        continue;
                    }
                    
                    for (UserVolumeSettings volume : rsetSettings.getVolumes()) {
                        GetVolumeResponse nvolume = new GetVolumeResponse();
                        
                        // Get the RP copy name, needed to match up sources to targets
                        String copyID = volume.getGroupCopyUID().getGlobalCopyUID().getClusterUID().getId() + "-" + 
                                volume.getGroupCopyUID().getGlobalCopyUID().getCopyUID();
                        nvolume.setRpCopyName(copyUIDToNameMap.get(copyID));
                        nvolume.setInternalSiteName(clusterIdToInternalSiteNameMap.get(volume.getClusterUID().getId()));
                        
                        if (productionCopiesUID.contains(copyID)) {
                            nvolume.setProduction(true);
                        } else {
                            nvolume.setProduction(false);
                        }                        
                        
                        // Need to extract the rawUids to format: 600601608D20370089260942815CE511
                        nvolume.setWwn(RecoverPointUtils.getGuidBufferAsString(volume.getVolumeInfo().getRawUids(), false).toUpperCase(Locale.ENGLISH));
                        
                        if (rset.getVolumes() == null) {
                            rset.setVolumes(new ArrayList<GetVolumeResponse>());
                        }
                        
                        // added this check because the simulator was returning the same volume over and over.
                        boolean found = false;
                        for (GetVolumeResponse vol : rset.getVolumes()) {
                            if (vol.getWwn().equalsIgnoreCase(nvolume.getWwn())) {
                                found = true;
                            }
                        }
                        
                        if (!found) {
                            rset.getVolumes().add(nvolume);
                        }
                    }
                    
                    if (ncg.getRsets() == null) {
                        ncg.setRsets(new ArrayList<GetRSetResponse>());
                    }
                    
                    ncg.getRsets().add(rset);
                   
                }
                
                cgs.add(ncg);
                
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.failedToLookupConsistencyGroups(getCause(e));
        }

        return cgs;
    }

    /**
     * scans all sites until all volumes involved in the Recoverpoint protection are visible
     *
     * @param request
     */
    public void waitForVolumesToBeVisible(CGRequestParams request) {
        scan(request.getCopies(), request.getRsets());
    }

    /**
     * Updates an existing CG by adding new replication sets.
     *
     * @param request - contains all the information required to create the consistency group
     * 
     * @param attachAsClean attach as clean can be true if source and target are guaranteed to be the same (as in create
     *            new volume). for change vpool, attach as clean should be false
     *
     * @return RecoverPointCGResponse - response as to success or fail of creating the consistency group
     *
     * @throws RecoverPointException
     **/
    public RecoverPointCGResponse addReplicationSetsToCG(CGRequestParams request, boolean metropoint, boolean attachAsClean)
            throws RecoverPointException {

        if (null == _endpoint.toASCIIString()) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        List<RecoverPointVolumeProtectionInfo> replicationSetsRollback = new ArrayList<RecoverPointVolumeProtectionInfo>();
        RecoverPointCGResponse response = new RecoverPointCGResponse();
        List<ConsistencyGroupCopySettings> groupCopySettings = null;
        ConsistencyGroupUID cgUID = null;

        try {

            // Make sure the CG name is unique.
            List<ConsistencyGroupUID> allCgs = functionalAPI.getAllConsistencyGroups();
            for (ConsistencyGroupUID cg : allCgs) {
                ConsistencyGroupSettings settings = functionalAPI.getGroupSettings(cg);
                if (settings.getName().toString().equalsIgnoreCase(request.getCgName())) {
                    cgUID = settings.getGroupUID();
                    groupCopySettings = settings.getGroupCopiesSettings();
                    break;
                }
            }

            if (cgUID == null) {
                // The CG does not exist so we cannot add replication sets
                throw RecoverPointException.exceptions.failedToAddReplicationSetCgDoesNotExist(request.getCgName());
            }

            response.setCgId(cgUID.getId());

            // caches site names to cluster id's to reduce calls to fapi for the same information
            Map<String, ClusterUID> clusterIdCache = new HashMap<String, ClusterUID>();
            // prodSites is used for logging and to determine if a non-production copy is local or remote
            List<ClusterUID> prodSites = new ArrayList<ClusterUID>();

            // used to set the copy uid on the rset volume when adding rsets
            Map<Long, ConsistencyGroupCopyUID> productionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();

            for (ConsistencyGroupCopySettings copySettings : groupCopySettings) {
                GlobalCopyUID globalCopyUID = copySettings.getCopyUID().getGlobalCopyUID();
                if (ConsistencyGroupCopyRole.ACTIVE.equals(copySettings.getRoleInfo().getRole()) ||
                        ConsistencyGroupCopyRole.TEMPORARY_ACTIVE.equals(copySettings.getRoleInfo().getRole())) {
                    productionCopiesUID.put(Long.valueOf(globalCopyUID.getClusterUID().getId()), copySettings.getCopyUID());
                    prodSites.add(globalCopyUID.getClusterUID());
                } else {
                    nonProductionCopiesUID.put(Long.valueOf(globalCopyUID.getClusterUID().getId()), copySettings.getCopyUID());
                }
            }

            StringBuffer sb = new StringBuffer();
            for (ClusterUID prodSite : prodSites) {
                sb.append(prodSite.getId());
                sb.append(" ");
            }

            logger.info("RecoverPointClient: Adding replication set(s) to consistency group " + request.getCgName() + " for endpoint: "
                    + _endpoint.toASCIIString() + " and production sites: " + sb.toString());

            ConsistencyGroupSettingsChangesParam cgSettingsParam = configureCGSettingsChangeParams(request, cgUID, prodSites,
                    clusterIdCache,
                    productionCopiesUID, nonProductionCopiesUID, attachAsClean);

            logger.info("Adding journals and rsets for CG " + request.getCgName());
            functionalAPI.setConsistencyGroupSettings(cgSettingsParam);

            // Sometimes the CG is still active when we start polling for link state and then
            // starts initializing some time afterwards. Adding this sleep to make sure the CG
            // starts initializing before we check the link states
            waitForRpOperation();
            
            RecoverPointImageManagementUtils rpiMgmt = new RecoverPointImageManagementUtils();            
            logger.info("Waiting for links to become active for CG " + request.getCgName());
            
            rpiMgmt.waitForCGLinkState(functionalAPI, cgUID, RecoverPointImageManagementUtils.getPipeActiveState(functionalAPI, cgUID));
            logger.info(String.format("Replication sets have been added to consistency group %s.", request.getCgName()));
                   
            response.setReturnCode(RecoverPointReturnCode.SUCCESS);
            return response;
        } catch (Exception e) {
            for (CreateRSetParams rsetParam : request.getRsets()) {
            	for(CreateVolumeParams volumeParam : rsetParam.getVolumes()) {
            		RecoverPointVolumeProtectionInfo volProtectionInfo = this.getProtectionInfoForVolume(volumeParam.getWwn());
            		replicationSetsRollback.add(volProtectionInfo);
            	}                
            }
            deleteReplicationSets(replicationSetsRollback);
            throw RecoverPointException.exceptions.failedToAddReplicationSetToConsistencyGroup(request.getCgName(), getCause(e));
        }
    }

    /**
     * Creates a consistency group
     *
     * @param request - contains all the information required to create the consistency group
     * 
     * @param attachAsClean attach as clean can be true if source and target are guaranteed to be the same (as in create
     *            new volume). for change vpool, attach as clean should be false
     *
     * @return CreateCGResponse - response as to success or fail of creating the consistency group
     *
     * @throws RecoverPointException
     **/
    public RecoverPointCGResponse createCG(CGRequestParams request, boolean metropoint, boolean attachAsClean) throws RecoverPointException {

        if (null == _endpoint.toASCIIString()) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        RecoverPointCGResponse response = new RecoverPointCGResponse();
        ConsistencyGroupUID cgUID = null;
        try {

            // Make sure the CG name is unique.
            int cgSuffix = 0;
            String cgName = request.getCgName();
            while (doesCgExist(request.getCgName())) {
                request.setCgName(String.format("%s-%d", cgName, ++cgSuffix));
            }

            // caches site names to cluster id's to reduce calls to fapi for the same information
            Map<String, ClusterUID> clusterIdCache = new HashMap<String, ClusterUID>();

            // prodSites is used for logging and to determine if a non-production copy is local or remote
            List<ClusterUID> prodSites = getProdSites(request, clusterIdCache);
            StringBuffer sb = new StringBuffer();
            for (ClusterUID prodSite : prodSites) {
                sb.append(prodSite.getId());
                sb.append(" ");
            }

            logger.info("RecoverPointClient: Creating recoverPoint consistency group " + request.getCgName() + " for endpoint: "
                    + _endpoint.toASCIIString() + " and production sites: " + sb.toString());

            // used to set the copy uid on the rset volume when adding rsets
            Map<Long, ConsistencyGroupCopyUID> productionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID = new HashMap<Long, ConsistencyGroupCopyUID>();

            FullConsistencyGroupPolicy fullConsistencyGroupPolicy = configureCGPolicy(request, prodSites, clusterIdCache,
                    productionCopiesUID, nonProductionCopiesUID);

            // create the CG with copies
            logger.info("Adding cg, copies and links for CG: " + request.getCgName());
            functionalAPI.validateAddConsistencyGroupAndCopies(fullConsistencyGroupPolicy);
            cgUID = functionalAPI.addConsistencyGroupAndCopies(fullConsistencyGroupPolicy);
            response.setCgId(cgUID.getId());

            ConsistencyGroupSettingsChangesParam cgSettingsParam = configureCGSettingsChangeParams(request, cgUID, prodSites,
                    clusterIdCache,
                    productionCopiesUID, nonProductionCopiesUID, attachAsClean);

            logger.info("Adding journals and rsets for CG " + request.getCgName());
            functionalAPI.setConsistencyGroupSettings(cgSettingsParam);

            RecoverPointImageManagementUtils rpiMgmt = new RecoverPointImageManagementUtils();                
            
            logger.info("Waiting for links to become active for CG " + request.getCgName());
            rpiMgmt.waitForCGLinkState(functionalAPI, cgUID, RecoverPointImageManagementUtils.getPipeActiveState(functionalAPI, cgUID));
            logger.info(String.format("Consistency group %s has been created.", request.getCgName()));

            response.setReturnCode(RecoverPointReturnCode.SUCCESS);

            return response;
        } catch (Exception e) {
            if (cgUID != null) {
                try {
                    RecoverPointUtils.cleanupCG(functionalAPI, cgUID);
                } catch (Exception e1) {
                    logger.error("Error removing CG " + request.getCgName() + " after create CG failure");
                    logger.error(e1.getMessage(), e1);
                }
            }
            throw RecoverPointException.exceptions.failedToCreateConsistencyGroup(request.getCgName(), getCause(e));
        }

    }

    /**
     * Operation to add journal volumes to an existing recoverpoint consistency group
     * 
     * @param request - contains both the consistency group
     *                  and the journals to add to the consistency group
     * @param copyType - indicates whether the copy is production, local or remote
     * @return boolean indicating the result of the operation
     *
     */
    public boolean addJournalVolumesToCG(CGRequestParams request, int copyType) {
    	// Make sure the CG name is unique.
    	ConsistencyGroupUID cgUID = null;
    	List<ConsistencyGroupUID> allCgs;
    	String copyName = "not determined";
    	Map<ConsistencyGroupCopyUID, DeviceUID> addedJournalVolumes = new HashMap<ConsistencyGroupCopyUID, DeviceUID>();
    	try {
    		allCgs = functionalAPI.getAllConsistencyGroups();
    		for (ConsistencyGroupUID cg : allCgs) {
    			ConsistencyGroupSettings settings = functionalAPI.getGroupSettings(cg);
    			if (settings.getName().toString().equalsIgnoreCase(request.getCgName())) {
    				cgUID = settings.getGroupUID();
    				break;
    			}
    		}
    		if (cgUID == null) {
    			// The CG does not exist so we cannot add replication sets
    			throw RecoverPointException.exceptions.failedToAddReplicationSetCgDoesNotExist(request.getCgName());    			
    		}    		
    		
    		List<CreateCopyParams> copyParams = request.getCopies();
    		
    		// determine if the volumes are visible to the recoverpoint appliance
    		Set<RPSite> allSites = scan(copyParams, null);    		    		
    		
    		for (CreateCopyParams copyParam : copyParams) {    		
    			for (CreateVolumeParams journalVolume: copyParam.getJournals()) {
    				copyName = journalVolume.getRpCopyName();
    				ClusterUID clusterId = RecoverPointUtils.getRPSiteID(functionalAPI, journalVolume.getInternalSiteName()); 
    				ConsistencyGroupCopyUID copyUID = getCGCopyUid(clusterId, getCopyType(copyType), cgUID);    				   				
    				DeviceUID journalDevice = RecoverPointUtils.getDeviceID(allSites, journalVolume.getWwn());
    				addedJournalVolumes.put(copyUID, journalDevice);
    				functionalAPI.addJournalVolume(copyUID, journalDevice);        		
    			}    			
    		}
    	}
    	catch (FunctionalAPIActionFailedException_Exception e) {
    		if (!addedJournalVolumes.isEmpty()) {
    			try {
    				for (Map.Entry<ConsistencyGroupCopyUID, DeviceUID> journalVolume : addedJournalVolumes.entrySet()) {
    					functionalAPI.removeJournalVolume(journalVolume.getKey(), journalVolume.getValue()); 			   
    				}
    			} catch (Exception e1) {
                  logger.error("Error removing journal volume from consistency group");
                  logger.error(e1.getMessage(), e1);
    			}
    		}    		
    		logger.error("Error in attempting to add a journal volume to the recoverpoint consistency group");
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.failedToAddJournalVolumeToConsistencyGroup(copyName, getCause(e));
    	} catch (FunctionalAPIInternalError_Exception e) {
    		if (!addedJournalVolumes.isEmpty()) {
    			try {
    				for (Map.Entry<ConsistencyGroupCopyUID, DeviceUID> journalVolume : addedJournalVolumes.entrySet()) {
    					functionalAPI.removeJournalVolume(journalVolume.getKey(), journalVolume.getValue()); 			   
    				}
    			} catch (Exception e1) {
                  logger.error("Error removing journal volume from consistency group");
                  logger.error(e1.getMessage(), e1);
    			}
    		}  
    		logger.error("Error in attempting to add a journal volume to the recoverpoint consistency group");
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.failedToCreateConsistencyGroup(copyName, getCause(e));
    	}
    	return true;        
    }
    
    /**
     * @param request
     * @param clusterIdCache
     * @return
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private List<ClusterUID> getProdSites(CGRequestParams request, Map<String, ClusterUID> clusterIdCache)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        List<ClusterUID> prodSites = new ArrayList<ClusterUID>();
        for (CreateVolumeParams volume : request.getRsets().get(0).getVolumes()) {
            if (volume.isProduction()) {
                ClusterUID prodSite = getRPSiteID(volume.getInternalSiteName(), clusterIdCache);
                prodSites.add(prodSite);
            }
        }
        return prodSites;
    }

    /**
     * returns cluster uid for a copy
     *
     * @param copyParam
     * @param clusterIdCache
     * @return
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private ClusterUID getClusterUid(CreateCopyParams copyParam, Map<String, ClusterUID> clusterIdCache)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        if (copyParam.getJournals() != null && !copyParam.getJournals().isEmpty()) {
            return getRPSiteID(copyParam.getJournals().iterator().next().getInternalSiteName(), clusterIdCache);
        }
        return null;
    }

    /**
     * get copy type (local, remote, production)
     *
     * @param copyParam
     * @param prodSites
     * @param clusterUID
     * @return
     */
    private RecoverPointCGCopyType getCopyType(CreateCopyParams copyParam, List<ClusterUID> prodSites, ClusterUID clusterUID) {
        if (copyParam.getJournals() != null && !copyParam.getJournals().isEmpty()) {
            CreateVolumeParams volume = copyParam.getJournals().iterator().next();
            if (volume.isProduction()) {
                return RecoverPointCGCopyType.PRODUCTION;
            } else {
                if (isLocalCopy(prodSites, clusterUID)) {
                    return RecoverPointCGCopyType.LOCAL;
                } else {
                    return RecoverPointCGCopyType.REMOTE;
                }
            }
        }
        return null;
    }
    
    private int getMaxNumberOfSnapShots(CreateCopyParams copyParam) {
        if (copyParam.getJournals() != null && !copyParam.getJournals().isEmpty()) {
            return copyParam.getJournals().iterator().next().getMaxNumberOfSnapShots();
        }
        return 0;
    }
    
    private boolean usingSnapShotTechnology(CGRequestParams request) {
    	 if (getMaxNumberOfSnapShots(request.getCopies().get(0)) > 0) {
    		return true; 
    	 }
    	 return false;
    }

    /**
     * Determines and creates RecoverPointCGCopyType type based on passed int value
     * 
     * @param type - the copy type
     * @return RecoverPointCGCopyType representing the copy type
     */
    private RecoverPointCGCopyType getCopyType(int type) {
    	RecoverPointCGCopyType copyType = RecoverPointCGCopyType.PRODUCTION;
    	if (type == RecoverPointCGCopyType.LOCAL.getCopyNumber()) {
    		copyType = RecoverPointCGCopyType.LOCAL;
    	}
    	if (type == RecoverPointCGCopyType.REMOTE.getCopyNumber()) {
    		copyType = RecoverPointCGCopyType.REMOTE;
    	}
    	return copyType;
    }
    
    /**
     * construct a CG copy UID
     *
     * @param clusterUID
     * @param copyType
     * @param cgUID
     * @return
     */
    private ConsistencyGroupCopyUID getCGCopyUid(ClusterUID clusterUID, RecoverPointCGCopyType copyType, ConsistencyGroupUID cgUID) {
        ConsistencyGroupCopyUID cgCopyUID = new ConsistencyGroupCopyUID();
        GlobalCopyUID globalCopyUID = new GlobalCopyUID();
        globalCopyUID.setClusterUID(clusterUID);
        globalCopyUID.setCopyUID(copyType.getCopyNumber());
        cgCopyUID.setGlobalCopyUID(globalCopyUID);
        cgCopyUID.setGroupUID(cgUID);
        return cgCopyUID;
    }

    /**
     * @param request
     * @param prodSites
     * @param clusterIdCache
     * @param attachAsClean attach as clean can be true if source and target are guaranteed to be the same (as in create
     *            new volume). for change vpool, attach as clean should be false
     * @return
     * @throws FunctionalAPIInternalError_Exception
     * @throws FunctionalAPIActionFailedException_Exception
     */
    private ConsistencyGroupSettingsChangesParam configureCGSettingsChangeParams(CGRequestParams request, ConsistencyGroupUID cgUID,
            List<ClusterUID> prodSites,
            Map<String, ClusterUID> clusterIdCache, Map<Long, ConsistencyGroupCopyUID> productionCopiesUID,
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID, boolean attachAsClean)
            throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception {

        Set<RPSite> allSites = getAssociatedRPSites();

        // used to set journal volumes and RSets after the CG is created
        ConsistencyGroupSettingsChangesParam cgSettingsParam = new ConsistencyGroupSettingsChangesParam();
        ActivationSettingsChangesParams cgActivationSettings = new ActivationSettingsChangesParams();
        cgActivationSettings.setEnable(true);
        cgActivationSettings.setStartTransfer(true);
        cgSettingsParam.setActivationParams(cgActivationSettings);
        cgSettingsParam.setGroupUID(cgUID);

        for (CreateCopyParams copyParam : request.getCopies()) {

            ClusterUID clusterUID = getClusterUid(copyParam, clusterIdCache);
            if (clusterUID != null) {

                RecoverPointCGCopyType copyType = getCopyType(copyParam, prodSites, clusterUID);

                if (copyType != null) {

                    ConsistencyGroupCopyUID cgCopyUID = getCGCopyUid(clusterUID, copyType, cgUID);

                    // set up journal params
                    ConsistencyGroupCopySettingsChangesParam copySettingsParam = new ConsistencyGroupCopySettingsChangesParam();
                    copySettingsParam.setCopyUID(cgCopyUID);

                    ActivationSettingsChangesParams copyActivationSettings = new ActivationSettingsChangesParams();
                    copyActivationSettings.setEnable(true);
                    copyActivationSettings.setStartTransfer(true);
                    copySettingsParam.setActivationParams(copyActivationSettings);

                    for (CreateVolumeParams journalVolume : copyParam.getJournals()) {
                        logger.info("Configuring Journal : \n" + journalVolume.toString() + "\n for copy: " + copyParam.getName() +
                                "; CG " + request.getCgName());                                                                                                
                        copySettingsParam.getNewJournalVolumes().add(RecoverPointUtils.getDeviceID(allSites, journalVolume.getWwn()));
                    }

                    cgSettingsParam.getCopiesChanges().add(copySettingsParam);

                } else {
                    logger.warn("No journal volumes specified for CG: " + copyParam.getName());
                }
            } else {
                logger.warn("No journal volumes specified for CG: " + copyParam.getName());
            }
        }

        String previousProdCopyName = null;

        // configure replication sets
        for (CreateRSetParams rsetParam : request.getRsets()) {

            logger.info("Configuring replication set: " + rsetParam.toString() + " for cg " + request.getCgName());

            ReplicationSetSettingsChangesParam repSetSettings = new ReplicationSetSettingsChangesParam();
            repSetSettings.setName(rsetParam.getName());
            repSetSettings.setShouldAttachAsClean(attachAsClean);

            Set<String> sourceWWNsInRset = new HashSet<String>();
            for (CreateVolumeParams volume : rsetParam.getVolumes()) {            	
            	
                UserVolumeSettingsChangesParam volSettings = new UserVolumeSettingsChangesParam();
                volSettings.setNewVolumeID(RecoverPointUtils.getDeviceID(allSites, volume.getWwn()));

                ClusterUID volSiteId = getRPSiteID(volume.getInternalSiteName(), clusterIdCache);

                if (volume.isProduction()) {                	                	
                	// for metropoint, the same production volume will appear twice; we only want to add it once
                    if (sourceWWNsInRset.contains(volume.getWwn())) {
                        continue;
                    }
                    if (previousProdCopyName == null) {
                        previousProdCopyName = volume.getRpCopyName();
                    } else if (!previousProdCopyName.equals(volume.getRpCopyName())) {
                        logger.info(String
                                .format("will not add rset for volume %s to prod copy %s because another rset has already been added to prod copy %s",
                                        rsetParam.getName(), volume.getRpCopyName(), previousProdCopyName));
                        continue;
                    }
                    sourceWWNsInRset.add(volume.getWwn());
                    logger.info("Configuring production copy volume : \n" + volume.toString());
                    ConsistencyGroupCopyUID copyUID = productionCopiesUID.get(Long.valueOf(volSiteId.getId()));
                    volSettings.setCopyUID(copyUID);
                } else {
                    logger.info("Configuring non-production copy volume : \n" + volume.toString());
                    ConsistencyGroupCopyUID copyUID = nonProductionCopiesUID.get(Long.valueOf(volSiteId.getId()));
                    volSettings.setCopyUID(copyUID);
                }
                volSettings.getCopyUID().setGroupUID(cgUID);
                repSetSettings.getVolumesChanges().add(volSettings);
            }
            cgSettingsParam.getReplicationSetsChanges().add(repSetSettings);
        }

        return cgSettingsParam;
    }

    /**
     * @param request
     * @return
     * @throws FunctionalAPIInternalError_Exception
     * @throws FunctionalAPIActionFailedException_Exception
     */
    private FullConsistencyGroupPolicy configureCGPolicy(CGRequestParams request, List<ClusterUID> prodSites,
            Map<String, ClusterUID> clusterIdCache, Map<Long, ConsistencyGroupCopyUID> productionCopiesUID,
            Map<Long, ConsistencyGroupCopyUID> nonProductionCopiesUID) throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception {

        logger.info("Requesting preferred RPA for cluster " + prodSites.get(0).getId());
        RpaUID preferredRPA = RecoverPointUtils.getPreferredRPAForNewCG(functionalAPI, prodSites.get(0));
        logger.info("Preferred RPA for cluster " + preferredRPA.getClusterUID().getId() + " is RPA " + preferredRPA.getRpaNumber());

        // used to create the CG; contains CG settings, copies and links
        FullConsistencyGroupPolicy fullConsistencyGroupPolicy = new FullConsistencyGroupPolicy();
        fullConsistencyGroupPolicy.setGroupName(request.getCgName());
        fullConsistencyGroupPolicy.setGroupPolicy(functionalAPI.getDefaultConsistencyGroupPolicy());
        fullConsistencyGroupPolicy.getGroupPolicy().setPrimaryRPANumber(preferredRPA.getRpaNumber());

        for (CreateCopyParams copyParam : request.getCopies()) {

            ClusterUID clusterUID = getClusterUid(copyParam, clusterIdCache);
            if (clusterUID != null) {

                RecoverPointCGCopyType copyType = getCopyType(copyParam, prodSites, clusterUID);

                if (copyType != null) {

                    logger.info(String.format("Configuring %s copy %s for CG %s", copyType.toString(), copyParam.getName(),
                            request.getCgName()));

                    ConsistencyGroupCopyUID cgCopyUID = getCGCopyUid(clusterUID, copyType, null);

                    FullConsistencyGroupCopyPolicy copyPolicy = new FullConsistencyGroupCopyPolicy();
                    copyPolicy.setCopyName(copyParam.getName());
                    copyPolicy.setCopyPolicy(functionalAPI.getDefaultConsistencyGroupCopyPolicy());
                    copyPolicy.setCopyUID(cgCopyUID);
                    
                    if (getMaxNumberOfSnapShots(copyParam) > 0) {
                    	copyPolicy.getCopyPolicy().getSnapshotsPolicy().setNumOfDesiredSnapshots(getMaxNumberOfSnapShots(copyParam));
                    }

                    fullConsistencyGroupPolicy.getCopiesPolicies().add(copyPolicy);

                    if (copyType.isProduction()) {
                        fullConsistencyGroupPolicy.getProductionCopies().add(copyPolicy.getCopyUID());
                        productionCopiesUID.put(Long.valueOf(clusterUID.getId()), copyPolicy.getCopyUID());
                    } else {
                        nonProductionCopiesUID.put(Long.valueOf(clusterUID.getId()), copyPolicy.getCopyUID());
                    }

                } else {
                    logger.error("No journal volumes specified for create CG: " + copyParam.getName());
                }
            } else {
                logger.error("No journal volumes specified for create CG: " + copyParam.getName());
            }
        }

        // set links between production and remote/local copies
        configureLinkPolicies(fullConsistencyGroupPolicy, request);

        return fullConsistencyGroupPolicy;
    }

    /**
     * configure links between each production and each local and/or remote copy in a new CG
     * configured links are added to fullConsistencyGroupPolicy
     *
     * @param fullConsistencyGroupPolicy cg policy with copies populated
     * @param copyType prod, local or remote
     * @param request create cg request used for copy mode and rpo
     */
    private void configureLinkPolicies(FullConsistencyGroupPolicy fullConsistencyGroupPolicy, CGRequestParams request) {
        for (FullConsistencyGroupCopyPolicy copyPolicy : fullConsistencyGroupPolicy.getCopiesPolicies()) {

            if (!fullConsistencyGroupPolicy.getProductionCopies().contains(copyPolicy.getCopyUID())) {

                for (ConsistencyGroupCopyUID productionCopyUID : fullConsistencyGroupPolicy.getProductionCopies()) {

                    logger.info("Configuring link policy between production copy and local or remote copy on cluster(id) : "
                            + copyPolicy.getCopyUID().getGlobalCopyUID().getClusterUID().getId());

                    ConsistencyGroupLinkUID linkUid = new ConsistencyGroupLinkUID();
                    linkUid.setFirstCopy(productionCopyUID.getGlobalCopyUID());
                    linkUid.setSecondCopy(copyPolicy.getCopyUID().getGlobalCopyUID());

                    boolean isLocal = productionCopyUID.getGlobalCopyUID().getClusterUID()
                            .equals(copyPolicy.getCopyUID().getGlobalCopyUID().getClusterUID());
                    RecoverPointCGCopyType copyType = isLocal ? RecoverPointCGCopyType.LOCAL : RecoverPointCGCopyType.REMOTE;

                    ConsistencyGroupLinkPolicy linkPolicy = createLinkPolicy(copyType, request.cgPolicy.copyMode, request.cgPolicy.rpoType,
                            request.cgPolicy.rpoValue);
                    
                    if (copyPolicy.getCopyPolicy().getSnapshotsPolicy().getNumOfDesiredSnapshots() != null &&
                    		copyPolicy.getCopyPolicy().getSnapshotsPolicy().getNumOfDesiredSnapshots()	> 0) {
                    	SnapshotShippingPolicy snapPolicy = new SnapshotShippingPolicy();
                    	snapPolicy.setIntervaInMinutes(1L);
                    	snapPolicy.setMode(SnapshotShippingMode.PERIODICALLY);
                    	linkPolicy.setSnapshotShippingPolicy(snapPolicy);
                    }
                                                            
                    ConsistencyGroupLinkSettings linkSettings = new ConsistencyGroupLinkSettings();
                    linkSettings.setGroupLinkUID(linkUid);
                    linkSettings.setLinkPolicy(linkPolicy);
                    linkSettings.setLocalLink(isLocal);
                    linkSettings.setTransferEnabled(false);

                    FullConsistencyGroupLinkPolicy fullLinkPolicy = new FullConsistencyGroupLinkPolicy();
                    fullLinkPolicy.setLinkPolicy(linkPolicy);
                    fullLinkPolicy.setLinkUID(linkUid);

                    fullConsistencyGroupPolicy.getLinksPolicies().add(fullLinkPolicy);
                }
            }
        }

    }

    /**
     * @param prodSites - List of production sites
     * @param siteID - current site that is being validated for if its on the same site as a production copy
     * @return boolean
     */
    private boolean isLocalCopy(List<ClusterUID> prodSites, ClusterUID siteID) {
        for (ClusterUID prodSite : prodSites) {
            if (prodSite.getId() == siteID.getId()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the root cause of the failure, if available
     *
     * @param e the base exception
     * @return the exception with valuable information in it
     */
    public static Throwable getCause(Exception e) {
        if (e.getCause() != null) {
            return e.getCause();
        }
        return e;
    }

    /**
     * Walk through the journals and source/target volumes to see where the WWNS lie.
     *
     * @param copies
     * @param rSets
     * @return set of discovered RP sites
     */
    private Set<RPSite> scan(List<CreateCopyParams> copies, List<CreateRSetParams> rSets) {
        // Setting the MAX_SCAN_WAIT_TOTAL_TRIES = 240
        // so that we loop for a max of 1 hour (240 * 15000 = 1 hour)
        final int MAX_SCAN_WAIT_TOTAL_TRIES = 240;
        final int MAX_SCAN_WAIT_RETRY_MILLISECONDS = 15000;

        int rescanTries = MAX_SCAN_WAIT_TOTAL_TRIES;
        boolean needsScan = true; // set to true to stay in the loop

        Set<RPSite> allSites = null;

        while (needsScan && rescanTries-- > 0) {
            // Reset scan flag. If something goes wrong, it'll get set to true.
            needsScan = false;

            if ((MAX_SCAN_WAIT_TOTAL_TRIES - rescanTries) != 1) {
                logger.info("RecoverPointClient: Briefly sleeping to accommodate export group latencies (Attempt #{} / {})",
                        MAX_SCAN_WAIT_TOTAL_TRIES - rescanTries, MAX_SCAN_WAIT_TOTAL_TRIES);
                
                try {
                    Thread.sleep(MAX_SCAN_WAIT_RETRY_MILLISECONDS);
                } catch (InterruptedException e1) {
                    Thread.currentThread().interrupt();
                }
            }

            // Rescan the san
            logger.info("RecoverPointClient: Rescanning san volumes for endpoint: " + _endpoint.toASCIIString());
            try {
                functionalAPI.rescanSANVolumesInAllClusters(true);
            } catch (FunctionalAPIActionFailedException_Exception e) {
                logger.warn("Exception in call to rescanSANVolumesInAllSites");
            } catch (FunctionalAPIInternalError_Exception e) {
                logger.warn("Exception in call to rescanSANVolumesInAllSites");
            }

            // Get all of the volumes
            allSites = getAssociatedRPSites();

            //
            // Walk through the journals volumes to see where our WWNs lie
            //
            for (CreateCopyParams copy : copies) {
                for (CreateVolumeParams volumeParam : copy.getJournals()) {                	                	                	
                    boolean found = false;
                    for (RPSite rpSite : allSites) {
                        ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                        for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {                        	                        	
                        	String siteVolUID = RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false);                            
                            if (siteVolUID.equalsIgnoreCase(volumeParam.getWwn())) {
                                logger.info("Found site and volume ID for journal: " + volumeParam.getWwn() + " for copy: "
                                        + copy.getName());
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                    if (!found) {
                        needsScan = true; // set that we still need to scan.

                        if (rescanTries <= 0) {
                            for (RPSite rpSite : allSites) {
                                logger.error(String.format("Could not find volume %s on any RP site", volumeParam.getWwn()));
                                ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                                for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                                    logger.info(String.format("RP Site: %s; volume from RP: %s", rpSite.getSiteName(),
                                            RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false)));
                                }
                            }
                            throw RecoverPointException.exceptions
                                    .couldNotFindSiteAndVolumeIDForJournal(volumeParam.getWwn(), copy.getName(),
                                            volumeParam.getInternalSiteName());
                        }
                    }
                }
            }

            // When adding new journal volumes only no need to look at source and target volumes
            if (rSets == null || rSets.isEmpty()) {
            	continue;
            }
            //
            // Walk through the source/target volumes to see where our WWNs lie
            //
            for (CreateRSetParams rset : rSets) {
                for (CreateVolumeParams volumeParam : rset.getVolumes()) {
                    boolean found = false;
                    for (RPSite rpSite : allSites) {
                        ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                        for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                            String siteVolUID = RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false);                            
                            if (siteVolUID.equalsIgnoreCase(volumeParam.getWwn())) {
                                logger.info(String.format(
                                        "Found site and volume ID for volume: %s for replication set: %s on site: %s (%s)",
                                        volumeParam.getWwn(), rset.getName(), rpSite.getSiteName(), volumeParam.getInternalSiteName()));
                                found = true;
                                break;
                            }
                        }
                        if (found) {
                            break;
                        }
                    }
                    if (!found) {
                        needsScan = true; // set that we still need to scan

                        if (rescanTries <= 0) {
                            for (RPSite rpSite : allSites) {
                                logger.error(String.format("Could not find voume %s on any RP site", volumeParam.getWwn()));
                                ClusterSANVolumes siteSANVolumes = rpSite.getSiteVolumes();
                                for (VolumeInformation volume : siteSANVolumes.getVolumesInformations()) {
                                    logger.info(String.format("RP Site: %s; volume from RP: %s", rpSite.getSiteName(),
                                            RecoverPointUtils.getGuidBufferAsString(volume.getRawUids(), false)));
                                }
                            }
                            throw RecoverPointException.exceptions
                                    .couldNotFindSiteAndVolumeIDForVolume(volumeParam.getWwn(), rset.getName(),
                                            volumeParam.getInternalSiteName());
                        }
                    }
                }
            }
        }

        return allSites;
    }

    /**
     * Convenience method to set the link policy.
     *
     * @param remote whether to set the "protect over wan" to true
     * @param prodCopyUID production copy id
     * @param targetCopyUID target copy id
     * @param cgUID cg id
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private void setLinkPolicy(boolean remote, ConsistencyGroupCopyUID prodCopyUID,
            ConsistencyGroupCopyUID targetCopyUID, ConsistencyGroupUID cgUID) throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception {
        ConsistencyGroupLinkUID groupLink = new ConsistencyGroupLinkUID();

        ConsistencyGroupLinkPolicy linkPolicy = new ConsistencyGroupLinkPolicy();
        linkPolicy.setAdvancedPolicy(new LinkAdvancedPolicy());
        linkPolicy.getAdvancedPolicy().setPerformLongInitialization(true);
        linkPolicy.getAdvancedPolicy().setSnapshotGranularity(SnapshotGranularity.FIXED_PER_SECOND);
        linkPolicy.setProtectionPolicy(new LinkProtectionPolicy());
        linkPolicy.getProtectionPolicy().setBandwidthLimit(0.0);
        linkPolicy.getProtectionPolicy().setCompression(WanCompression.NONE);
        linkPolicy.getProtectionPolicy().setDeduplication(false);
        linkPolicy.getProtectionPolicy().setMeasureLagToTargetRPA(true);
        linkPolicy.getProtectionPolicy().setProtectionType(ProtectionMode.ASYNCHRONOUS);
        linkPolicy.getProtectionPolicy().setReplicatingOverWAN(remote);
        linkPolicy.getProtectionPolicy().setRpoPolicy(new RpoPolicy());
        linkPolicy.getProtectionPolicy().getRpoPolicy().setAllowRegulation(false);
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMaximumAllowedLag(getQuantity(QuantityType.MICROSECONDS, 25000000));
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMinimizationType(RpoMinimizationType.IRRELEVANT);
        linkPolicy.getProtectionPolicy().setSyncReplicationLatencyThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds()
                .setResumeSyncReplicationBelow(getQuantity(QuantityType.MICROSECONDS, 3000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds()
                .setStartAsyncReplicationAbove(getQuantity(QuantityType.MICROSECONDS, 5000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setSyncReplicationThroughputThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds()
                .setResumeSyncReplicationBelow(getQuantity(QuantityType.KB, 35000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds()
                .setStartAsyncReplicationAbove(getQuantity(QuantityType.KB, 45000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setWeight(1);
        groupLink.setFirstCopy(prodCopyUID.getGlobalCopyUID());
        groupLink.setSecondCopy(targetCopyUID.getGlobalCopyUID());
        groupLink.setGroupUID(cgUID);
        functionalAPI.addConsistencyGroupLink(groupLink, linkPolicy);
        waitForRpOperation();
    }

    /**
     * Convenience method for creating a link policy object
     *
     * @param remote whether to set the "protect over wan" to true
     */
    private ConsistencyGroupLinkPolicy createLinkPolicy(RecoverPointCGCopyType copyType, String copyMode, String rpoType, Long rpoValue) {

        ConsistencyGroupLinkPolicy linkPolicy = new ConsistencyGroupLinkPolicy();
        linkPolicy.setAdvancedPolicy(new LinkAdvancedPolicy());
        linkPolicy.getAdvancedPolicy().setPerformLongInitialization(true);
        linkPolicy.getAdvancedPolicy().setSnapshotGranularity(SnapshotGranularity.FIXED_PER_SECOND);
        linkPolicy.setProtectionPolicy(new LinkProtectionPolicy());
        linkPolicy.getProtectionPolicy().setBandwidthLimit(0.0);
        linkPolicy.getProtectionPolicy().setCompression(WanCompression.NONE);
        linkPolicy.getProtectionPolicy().setDeduplication(false);
        linkPolicy.getProtectionPolicy().setMeasureLagToTargetRPA(true);
        linkPolicy.getProtectionPolicy().setProtectionType(ProtectionMode.ASYNCHRONOUS);
        linkPolicy.getProtectionPolicy().setReplicatingOverWAN(copyType.isRemote());
        linkPolicy.getProtectionPolicy().setRpoPolicy(new RpoPolicy());
        linkPolicy.getProtectionPolicy().getRpoPolicy().setAllowRegulation(false);
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMaximumAllowedLag(getQuantity(QuantityType.MICROSECONDS, 25000000));
        linkPolicy.getProtectionPolicy().getRpoPolicy().setMinimizationType(RpoMinimizationType.IRRELEVANT);
        linkPolicy.getProtectionPolicy().setSyncReplicationLatencyThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds()
                .setResumeSyncReplicationBelow(getQuantity(QuantityType.MICROSECONDS, 3000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds()
                .setStartAsyncReplicationAbove(getQuantity(QuantityType.MICROSECONDS, 5000));
        linkPolicy.getProtectionPolicy().getSyncReplicationLatencyThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setSyncReplicationThroughputThresholds(new SyncReplicationThreshold());
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds()
                .setResumeSyncReplicationBelow(getQuantity(QuantityType.KB, 35000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds()
                .setStartAsyncReplicationAbove(getQuantity(QuantityType.KB, 45000));
        linkPolicy.getProtectionPolicy().getSyncReplicationThroughputThresholds().setThresholdEnabled(false);
        linkPolicy.getProtectionPolicy().setWeight(1);

        LinkProtectionPolicy linkProtectionPolicy = linkPolicy.getProtectionPolicy();
        if (copyMode != null) {
            logger.info("Setting CG policy of: " + copyMode);
            ProtectionMode protectionMode = ProtectionMode.valueOf(copyMode);
            if (protectionMode == null) {
                // Default to ASYNCHRONOUS
                protectionMode = ProtectionMode.ASYNCHRONOUS;
            }
            linkProtectionPolicy.setProtectionType(protectionMode);
        }
        RpoPolicy rpoPolicy = linkProtectionPolicy.getRpoPolicy();
        if (rpoValue != null && rpoType != null) {
            logger.info("Setting CG RPO policy of: " + rpoValue.toString() + " " + rpoType);
            Quantity rpoQuantity = new Quantity();
            QuantityType quantityType = QuantityType.valueOf(rpoType);
            rpoQuantity.setType(quantityType);
            rpoQuantity.setValue(rpoValue);
            rpoPolicy.setMaximumAllowedLag(rpoQuantity);
        } else if ((rpoValue == null && rpoType != null) ||
                (rpoValue != null && rpoType == null)) {
            logger.warn("RPO Policy specified only one of value and type, both need to be specified for RPO policy to be applied.  Ignoring RPO policy.");
        }
        linkProtectionPolicy.setRpoPolicy(rpoPolicy);
        linkPolicy.setProtectionPolicy(linkProtectionPolicy);        

        return linkPolicy;

    }

    /**
     * Creates a bookmark against one or more consistency group
     *
     * @param CreateBookmarkRequestParams request - contains the information about which CGs to create bookmarks on
     *
     * @return CreateBookmarkResponse - response as to success or fail of creating the bookmarks
     *
     * @throws RecoverPointException
     **/
    public CreateBookmarkResponse createBookmarks(CreateBookmarkRequestParams request) throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        Set<String> wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }

        Set<String> unmappedWWNs = new HashSet<String>();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, request, unmappedWWNs);

        if (!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }

        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }

        return bookmarkManager.createCGBookmarks(functionalAPI, rpCGMap, request);
    }

    private Quantity getQuantity(QuantityType type, long value) {
        Quantity quantity1 = new Quantity();
        quantity1.setType(type);
        quantity1.setValue(value);
        return quantity1;
    }

    /**
     * Get all RP bookmarks for all CGs specified
     *
     * @param request - set of CG integer IDs
     * @return GetBookmarkResponse - a map of CGs to bookmarks for that CG
     * @throws RecoverPointException
     **/
    public GetBookmarksResponse getRPBookmarks(Set<Integer> request) throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }

        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        GetBookmarksResponse response = new GetBookmarksResponse();
        response.setCgBookmarkMap(new HashMap<Integer, List<RPBookmark>>());
        for (Integer cgID : request) {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(cgID);
            response.getCgBookmarkMap().put(cgID, bookmarkManager.getRPBookmarksForCG(functionalAPI, cgUID));
        }

        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Enables copy images for one or more consistency group copies
     *
     * @param MultiCopyEnableImageRequestParams request - contains the information about which CG copies to enable
     *
     * @return MultiCopyEnableImageResponse - response as to success or fail of enabling the image copies
     *
     * @throws RecoverPointException
     **/
    public MultiCopyEnableImageResponse enableImageCopies(MultiCopyEnableImageRequestParams request) throws RecoverPointException {
        MultiCopyEnableImageResponse response = new MultiCopyEnableImageResponse();
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        Set<String> wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        Set<String> unmappedWWNs = new HashSet<String>();
        CreateBookmarkRequestParams mapRequest = new CreateBookmarkRequestParams();
        mapRequest.setBookmark(request.getBookmark());
        mapRequest.setVolumeWWNSet(wwnSet);
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, mapRequest, unmappedWWNs);
        if (!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        Set<RPConsistencyGroup> cgSetToEnable = new HashSet<RPConsistencyGroup>();
        for (String volume : rpCGMap.keySet()) {
            // logger.info("Get RPCG for volume: " + volume);
            cgSetToEnable.add(rpCGMap.get(volume));
        }

        // Make sure your copies are OK to enable.
        for (RPConsistencyGroup rpcg : cgSetToEnable) {
            Set<RPCopy> copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                try {
                    String cgCopyName = functionalAPI.getGroupCopyName(copy.getCGGroupCopyUID());
                    String cgName = functionalAPI.getGroupName(copy.getCGGroupCopyUID().getGroupUID());
                    if (!imageManager.verifyCopyCapableOfEnableImageAccess(functionalAPI, copy.getCGGroupCopyUID(), request.getBookmark(), false)) {
                        logger.info("Copy " + cgCopyName + " of group " + cgName + " is in a mode that disallows enabling the CG copy.");
                        throw RecoverPointException.exceptions.notAllowedToEnableImageAccessToCG(
                                cgName, cgCopyName);
                    }
                } catch (FunctionalAPIActionFailedException_Exception e) {
                    throw RecoverPointException.exceptions
                            .notAllowedToEnableImageAccessToCGException(e);
                } catch (FunctionalAPIInternalError_Exception e) {
                    throw RecoverPointException.exceptions
                            .notAllowedToEnableImageAccessToCGException(e);
                }
            }
        }
      
	    for (RPConsistencyGroup rpcg : cgSetToEnable) {
	        Set<RPCopy> copies = rpcg.getCopies();
	        for (RPCopy copy : copies) {
	            boolean waitForLinkState = true;
	            imageManager.enableCGCopy(functionalAPI, copy.getCGGroupCopyUID(), waitForLinkState, ImageAccessMode.LOGGED_ACCESS,
	                    request.getBookmark(), request.getAPITTime());
	        }
	    }
    
        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }
    

    /**
     * Disables copy images for one or more consistency group copies
     *
     * @param MultiCopyDisableImageRequestParams request - contains the information about which CG copies to disable
     *
     * @return MultiCopyDisableImageResponse - response as to success or fail of disabling the image copies
     *
     * @throws RecoverPointException
     **/
    public MultiCopyDisableImageResponse disableImageCopies(MultiCopyDisableImageRequestParams request) throws RecoverPointException {
        MultiCopyDisableImageResponse response = new MultiCopyDisableImageResponse();
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        Set<String> wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        Set<String> unmappedWWNs = new HashSet<String>();
        CreateBookmarkRequestParams mapRequest = new CreateBookmarkRequestParams();
        mapRequest.setVolumeWWNSet(wwnSet);
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, mapRequest, unmappedWWNs);
        if (!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        Set<RPConsistencyGroup> cgSetToDisable = new HashSet<RPConsistencyGroup>();
        for (String volume : rpCGMap.keySet()) {
            cgSetToDisable.add(rpCGMap.get(volume));
        }

        for (RPConsistencyGroup rpcg : cgSetToDisable) {
            Set<RPCopy> copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                ConsistencyGroupCopyState copyState = imageManager.getCopyState(functionalAPI, copy.getCGGroupCopyUID());
                if (copyState != null && copyState.getAccessedImage() != null && copyState.getAccessedImage().getDescription() != null &&
                        copyState.getAccessedImage().getDescription().equals(request.getEmName())) {
                    imageManager.disableCGCopy(functionalAPI, copy.getCGGroupCopyUID());
                }
            }
        }

        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Restore copy images for one or more consistency group copies
     *
     * @param MultiCopyRestoreImageRequestParams request - contains the information about which CG copies to restore
     *
     * @return MultiCopyRestoreImageResponse - response as to success or fail of restoring the image copies
     *
     * @throws RecoverPointException
     **/
    public MultiCopyRestoreImageResponse restoreImageCopies(MultiCopyRestoreImageRequestParams request) throws RecoverPointException {
        MultiCopyRestoreImageResponse response = new MultiCopyRestoreImageResponse();
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        RecoverPointBookmarkManagementUtils bookmarkManager = new RecoverPointBookmarkManagementUtils();
        String mgmtIPAddress = _endpoint.toASCIIString();

        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        Set<String> wwnSet = request.getVolumeWWNSet();
        if (wwnSet == null) {
            throw RecoverPointException.exceptions.noWWNsFoundInRequest();
        }
        Set<String> unmappedWWNs = new HashSet<String>();
        CreateBookmarkRequestParams mapRequest = new CreateBookmarkRequestParams();
        mapRequest.setBookmark(request.getBookmark());
        mapRequest.setVolumeWWNSet(wwnSet);
        Map<String, RPConsistencyGroup> rpCGMap = bookmarkManager.mapCGsForWWNs(functionalAPI, mapRequest, unmappedWWNs);
        if (!unmappedWWNs.isEmpty()) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(unmappedWWNs);
        }
        if (rpCGMap == null) {
            throw RecoverPointException.exceptions.couldNotMapWWNsToAGroup(wwnSet);
        }
        Set<RPConsistencyGroup> cgSetToEnable = new HashSet<RPConsistencyGroup>();
        for (String volume : rpCGMap.keySet()) {
            // logger.info("Get RPCG for volume: " + volume);
            cgSetToEnable.add(rpCGMap.get(volume));
        }
        ClusterUID siteToRestore = null;
        // Verify that we are not restoring from different sites
        for (RPConsistencyGroup rpcg : cgSetToEnable) {
            Set<RPCopy> copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                if (siteToRestore == null) {
                    siteToRestore = copy.getCGGroupCopyUID().getGlobalCopyUID().getClusterUID();
                } else if (siteToRestore.getId() != copy.getCGGroupCopyUID().getGlobalCopyUID().getClusterUID().getId()) {
                    throw RecoverPointException.exceptions
                            .cannotRestoreVolumesFromDifferentSites(wwnSet);
                }
                try {
                    List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(
                            copy.getCGGroupCopyUID().getGroupUID()).getProductionCopiesUIDs();
                    for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
                        if (RecoverPointUtils.cgCopyEqual(productionCopyUID, copy.getCGGroupCopyUID())) {
                            throw RecoverPointException.exceptions
                                    .cannotRestoreVolumesInConsistencyGroup(wwnSet);
                        }
                    }
                } catch (FunctionalAPIActionFailedException_Exception e) {
                    logger.error(e.getMessage());
                    logger.error("Received FunctionalAPIActionFailedException_Exception. Get production copy");
                    throw RecoverPointException.exceptions.failureRestoringVolumes();
                } catch (FunctionalAPIInternalError_Exception e) {
                    logger.error(e.getMessage());
                    logger.error("Received FunctionalAPIActionFailedException_Exception. Get production copy");
                    throw RecoverPointException.exceptions.failureRestoringVolumes();
                }
            }
        }
        
        try {
            for (RPConsistencyGroup rpcg : cgSetToEnable) {
                Set<RPCopy> copies = rpcg.getCopies();
                for (RPCopy copy : copies) {
                    // For restore, just wait for link state of the copy being restored                	
                    imageManager.waitForCGLinkState(functionalAPI, copy.getCGGroupCopyUID().getGroupUID(), RecoverPointImageManagementUtils.getPipeActiveState(functionalAPI, rpcg.getCGUID()));
                    boolean waitForLinkState = false;
                    imageManager.enableCGCopy(functionalAPI, copy.getCGGroupCopyUID(), waitForLinkState, ImageAccessMode.LOGGED_ACCESS,
                            request.getBookmark(), request.getAPITTime());
                }
            }
        } catch (RecoverPointException e) {
            logger.error("Caught exception while enabling CG copies for restore.  Return copies to previous state");
            for (RPConsistencyGroup rpcg : cgSetToEnable) {
                Set<RPCopy> copies = rpcg.getCopies();
                for (RPCopy copy : copies) {
                    imageManager.disableCGCopy(functionalAPI, copy.getCGGroupCopyUID());
                }
            }
            throw e;
        }
        for (RPConsistencyGroup rpcg : cgSetToEnable) {
            Set<RPCopy> copies = rpcg.getCopies();
            for (RPCopy copy : copies) {
                imageManager.restoreEnabledCGCopy(functionalAPI, copy.getCGGroupCopyUID());
            }
        }
        response.setReturnCode(RecoverPointReturnCode.SUCCESS);
        return response;
    }

    /**
     * Given an RP site, return a map of all the RP initiator WWNs for each RPA in that site.  
     * @param internalSiteName - RP internal site name
     * @return Map of RPA number to Map with portWWN being the key and nodeWWN the value.
     * @throws RecoverPointException
     */
    public Map<String, Map<String, String>> getInitiatorWWNs(String internalSiteName) throws RecoverPointException {
        Map<String, Map<String, String>> rpaWWNs = new HashMap<String, Map<String, String>>();
        try {
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration()
                    .getClustersConfigurations()) {
                if (!siteSettings.getInternalClusterName().equals(internalSiteName)) {
                    continue;
                }
                
                ClusterRPAsState clusterRPAState = functionalAPI.getRPAsStateFromCluster(siteSettings.getCluster());
                for (RpaState rpaState : clusterRPAState.getRpasStates()) {
                    for (InitiatorInformation rpaPortState : rpaState.getInitiatorsStates()) {
                        if (rpaPortState instanceof FiberChannelInitiatorInformation) {
                            FiberChannelInitiatorInformation initiator = (FiberChannelInitiatorInformation) rpaPortState;
                            String nodeWWN = WwnUtils.convertWWN(initiator.getNodeWWN(), WwnUtils.FORMAT.COLON);
                            String portWWN = WwnUtils.convertWWN(initiator.getPortWWN(), WwnUtils.FORMAT.COLON);                            
                            String rpaId = String.valueOf(rpaState.getRpaUID().getRpaNumber());
                            logger.info(String.format("RPA ID: %s - RPA Port WWN : %s, NodeWWN : %s", rpaId, portWWN, nodeWWN));                          
                            if (!rpaWWNs.containsKey(rpaId)) {
                            	rpaWWNs.put(rpaId, new HashMap<String, String>());
                            }
                            rpaWWNs.get(rpaId).put(portWWN, nodeWWN);                         
                        }
                    }
                }
            }
            return rpaWWNs;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage());
            logger.error("Received FunctionalAPIActionFailedException_Exception. Get port information");
            throw RecoverPointException.exceptions.failureGettingInitiatorWWNs();
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage());
            logger.error("Received FunctionalAPIInternalError_Exception. Get port information");
            throw RecoverPointException.exceptions.failureGettingInitiatorWWNs();
        }
    }

    /**
     * The getProtectionInfoForVolume method takes the WWN, and looks for it in the RP site protection environment.
     * If it finds the WWN as a member of a consistency group, it fills in the information, and returns it to the caller.
     * If it does not find the WWN as a member of a consistency group, it returns null
     *
     * @param String volumeWWN - The WWN being checked for RecoverPoint protection
     *
     * @return RecoverPointVolumeProtectionInfo - description of protection information about the WWN, or null if not protected in CG
     *
     * @throws RecoverPointException
     **/
    public RecoverPointVolumeProtectionInfo getProtectionInfoForVolume(String volumeWWN) throws RecoverPointException {
        RecoverPointVolumeProtectionInfo protectionInfo = null;
        try {
            // logger.info("getProtectionInfoForVolume called for: " + volumeWWN);
            protectionInfo = new RecoverPointVolumeProtectionInfo();
            List<ConsistencyGroupSettings> cgsSettings = functionalAPI.getAllGroupsSettings();
            for (ConsistencyGroupSettings cgSettings : cgsSettings) {
                // See if it is a production source, or an RP target
                for (ReplicationSetSettings rsSettings : cgSettings.getReplicationSetsSettings()) {
                    for (UserVolumeSettings uvSettings : rsSettings.getVolumes()) {
                        String volUID = RecoverPointUtils.getGuidBufferAsString(uvSettings.getVolumeInfo().getRawUids(), false);
                        if (volUID.toLowerCase(Locale.ENGLISH).equalsIgnoreCase(volumeWWN)) {
                            ConsistencyGroupUID cgID = uvSettings.getGroupCopyUID().getGroupUID();
                            List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(cgID)
                                    .getProductionCopiesUIDs();
                            String cgName = cgSettings.getName();
                            String cgCopyName = functionalAPI.getGroupCopyName(uvSettings.getGroupCopyUID());
                            protectionInfo.setRpProtectionName(cgName);
                            protectionInfo.setRpVolumeGroupCopyID(uvSettings.getGroupCopyUID().getGlobalCopyUID().getCopyUID());
                            protectionInfo.setRpVolumeGroupID(cgID.getId());
                            protectionInfo.setRpVolumeSiteID(uvSettings.getClusterUID().getId());
                            protectionInfo.setRpVolumeRSetID(rsSettings.getReplicationSetUID().getId());
                            protectionInfo.setRpVolumeWWN(volumeWWN);
                            if (RecoverPointUtils.isProductionCopy(uvSettings.getGroupCopyUID(), productionCopiesUIDs)) {
                                logger.info("Production volume: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo
                                        .setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE);
                            } else {
                                logger.info("Target volume: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo
                                        .setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_TARGET);
                            }
                            return protectionInfo;
                        }
                    }
                }
                // See if it is a journal volume
                for (ConsistencyGroupCopySettings cgCopySettings : cgSettings.getGroupCopiesSettings()) {
                    ConsistencyGroupCopyJournal cgJournal = cgCopySettings.getJournal();
                    List<JournalVolumeSettings> journalVolumeSettingsList = cgJournal.getJournalVolumes();
                    for (JournalVolumeSettings journalVolumeSettings : journalVolumeSettingsList) {
                        String journalVolUID =
                                RecoverPointUtils.getGuidBufferAsString(journalVolumeSettings.getVolumeInfo().getRawUids(), false);
                        if (journalVolUID.toLowerCase(Locale.ENGLISH).equalsIgnoreCase(volumeWWN)) {
                            ConsistencyGroupUID cgID = journalVolumeSettings.getGroupCopyUID().getGroupUID();
                            List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(cgID)
                                    .getProductionCopiesUIDs();
                            String cgName = cgSettings.getName();
                            String cgCopyName = functionalAPI.getGroupCopyName(journalVolumeSettings.getGroupCopyUID());
                            protectionInfo.setRpProtectionName(cgName);
                            protectionInfo.setRpVolumeGroupCopyID(journalVolumeSettings.getGroupCopyUID().getGlobalCopyUID().getCopyUID());
                            protectionInfo.setRpVolumeGroupID(cgID.getId());
                            protectionInfo.setRpVolumeSiteID(journalVolumeSettings.getClusterUID().getId());
                            protectionInfo.setRpVolumeWWN(volumeWWN);
                            if (RecoverPointUtils.isProductionCopy(journalVolumeSettings.getGroupCopyUID(), productionCopiesUIDs)) {
                                logger.info("Production journal: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo
                                        .setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.SOURCE_JOURNAL);
                            } else {
                                logger.info("Target journal: " + volumeWWN + " is on copy " + cgCopyName + " of CG " + cgName);
                                protectionInfo
                                        .setRpVolumeCurrentProtectionStatus(RecoverPointVolumeProtectionInfo.volumeProtectionStatus.TARGET_JOURNAL);
                            }
                            return protectionInfo;
                        }
                    }

                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failureGettingProtectionInfoForVolume(volumeWWN,
                    e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failureGettingProtectionInfoForVolume(volumeWWN,
                    e);
        }
        throw RecoverPointException.exceptions.failureGettingProtectionInfoForVolume(volumeWWN);
    }

    /**
     * Disable (stop) the consistency group protection specified by the input volume info.
     * If a target volume is specified, disable the copy associated with the target.
     * Disable requires a full sweep when enabled.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to disable
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void disableProtection(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Disable the whole CG
                disableConsistencyGroup(cgUID);
            } else {
                // Disable the CG copy associated with the target.
                ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
                functionalAPI.disableConsistencyGroupCopy(cgCopyUID);
                String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
                String cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Protection disabled on CG copy " + cgCopyName + " on CG " + cgName);

                // Make sure the CG copy is stopped
                RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
                imageManager.waitForCGLinkState(functionalAPI, cgUID, PipeState.UNKNOWN);
                logger.info("Protection disabled on CG copy " + cgCopyName + " on CG " + cgName);
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDisableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDisableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Disables the whole consistency group.
     *
     * @param cgUID the consistency group UID.
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private void disableConsistencyGroup(ConsistencyGroupUID cgUID) throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception {
        functionalAPI.disableConsistencyGroup(cgUID);
        String cgName = functionalAPI.getGroupName(cgUID);
        logger.info("Protection disabled on CG: " + cgName);

        // Make sure the CG is stopped
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.waitForCGLinkState(functionalAPI, cgUID, PipeState.UNKNOWN);
    }

    /**
     * Enable (start) the consistency group protection specified by the input volume info
     * Requires a full sweep.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to enable
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void enableProtection(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            ConsistencyGroupCopyUID cgCopyUID = null;
            cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
            String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
            String cgName = functionalAPI.getGroupName(cgUID);
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Enable the whole CG.
                logger.info("Enabling consistency group " + cgName);
                functionalAPI.enableConsistencyGroup(cgUID, true);
            } else {
                // Enable the CG copy associated with the target
                logger.info("Enabling CG copy " + cgCopyName + " on CG " + cgName);
                functionalAPI.enableConsistencyGroupCopy(cgCopyUID, true);
            }
            // Make sure the CG is ready
            RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();            
            imageManager.waitForCGLinkState(functionalAPI, cgUID, RecoverPointImageManagementUtils.getPipeActiveState(functionalAPI, cgUID));
            logger.info("Protection enabled on CG copy " + cgCopyName + " on CG " + cgName);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToEnableProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Return the state of a consistency group.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to get CG state for
     *
     * @return the state of the CG
     *
     * @throws RecoverPointException
     **/
    public RecoverPointCGState getCGState(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
        cgUID.setId(volumeInfo.getRpVolumeGroupID());
        return getCGState(cgUID);
    }

    /**
     * Return the state of a consistency group.
     *
     * @param cgUID - CG identifier
     *
     * @return the state of the CG
     *
     * @throws RecoverPointException
     **/
    private RecoverPointCGState getCGState(ConsistencyGroupUID cgUID) throws RecoverPointException {
        ConsistencyGroupSettings cgSettings = null;
        ConsistencyGroupState cgState = null;
        try {
            cgSettings = functionalAPI.getGroupSettings(cgUID);
            cgState = functionalAPI.getGroupState(cgUID);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        } catch (FunctionalAPIInternalError_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        }
        if (!cgSettings.isEnabled()) {
            return RecoverPointCGState.STOPPED;
        }
        // First check for disabled copies
        boolean someCopiesEnabled = false;
        boolean someCopiesDisabled = false;
        for (ConsistencyGroupCopyState cgCopyState : cgState.getGroupCopiesStates()) {
            if (cgCopyState.isEnabled()) {
                someCopiesEnabled = true;
            } else {
                someCopiesDisabled = true;
            }
        }
        if (someCopiesDisabled && !someCopiesEnabled) {
            // All copies are disabled
            return RecoverPointCGState.STOPPED;
        }

        // Now check to see if all the copies are paused
        boolean someCopiesPaused = false;
        boolean someCopiesNotPaused = false;
        List<ConsistencyGroupLinkState> cgLinkStateList;
        try {
            cgLinkStateList = functionalAPI.getGroupState(cgUID).getLinksStates();
        } catch (FunctionalAPIActionFailedException_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        } catch (FunctionalAPIInternalError_Exception e) {
            // No longer exists
            return RecoverPointCGState.GONE;
        }

        for (ConsistencyGroupLinkState cgLinkState : cgLinkStateList) {
            // OK, this is our link that we just restored. Check the link state to see if it is active
            if (PipeState.ACTIVE.equals(cgLinkState.getPipeState())) {
                someCopiesNotPaused = true;
            } else {
                someCopiesPaused = true;
            }
        }

        if (someCopiesPaused && !someCopiesNotPaused) {
            // All copies are paused
            return RecoverPointCGState.PAUSED;
        }
        if (someCopiesPaused || someCopiesDisabled) {
            return RecoverPointCGState.MIXED;
        }
        return RecoverPointCGState.READY;
    }

    /**
     * Pause (suspend) the consistency group protection specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to pause
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void pauseTransfer(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Pause the whole CG
                functionalAPI.pauseGroupTransfer(cgUID);
                String cgName = functionalAPI.getGroupName(cgUID);
                logger.info("Protection paused on CG " + cgName);
            } else {
                // Pause the CG copy associated with the target
                ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
                functionalAPI.pauseGroupCopyTransfer(cgCopyUID);
                String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
                String cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Protection paused on CG copy " + cgCopyName + " on CG " + cgName);
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToPauseProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToPauseProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Resume the consistency group protection specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to resume
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void resumeTransfer(RecoverPointVolumeProtectionInfo volumeInfo) throws RecoverPointException {
        try {
            ConsistencyGroupUID cgUID = new ConsistencyGroupUID();
            cgUID.setId(volumeInfo.getRpVolumeGroupID());
            if (volumeInfo.getRpVolumeCurrentProtectionStatus() == RecoverPointVolumeProtectionInfo.volumeProtectionStatus.PROTECTED_SOURCE) {
                // Resume the whole CG
                String cgName = functionalAPI.getGroupName(cgUID);
                logger.info("Protection resumed on CG " + cgName);
                functionalAPI.startGroupTransfer(cgUID);
            } else {
                // Resume the CG copy associated with the target
                ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(volumeInfo);
                functionalAPI.startGroupCopyTransfer(cgCopyUID);
                String cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
                String cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
                logger.info("Protection resumed on CG copy " + cgCopyName + " on CG " + cgName);
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToResumeProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToResumeProtection(
                    volumeInfo.getRpVolumeGroupID(), e);
        }
    }

    /**
     * Perform a failover test to the consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG to perform a failover test to. Also contains bookmark and APIT
     *            info. If no bookmark or APIT specified, failover test to most recent image.
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopyTest(RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        // Check the params
        // If bookmark != null, enable the bookmark on the copy, and failover to that copy
        // If APITTime != null, enable the specified APIT on the copy, and failover to that copy
        // If both are null, enable the most recent imagem, and failover to that copy
        String bookmarkName = copyToFailoverTo.getBookmarkName();
        Date apitTime = copyToFailoverTo.getApitTime();
        if (bookmarkName != null) {
            logger.info("Failver copy to bookmark : " + bookmarkName);
        } else if (apitTime != null) {
            logger.info("Failover copy to APIT : " + apitTime.toString());
        } else {
            logger.info("Failover copy to most recent image");
        }
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.enableCopyImage(functionalAPI, copyToFailoverTo, false);
        // RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToFailoverTo.getCopyVolumeInfo());
        RecoverPointVolumeProtectionInfo failoverCopyInfo = copyToFailoverTo.getCopyVolumeInfo();
        pauseTransfer(failoverCopyInfo);
    }

    /**
     * Cancel a failover test for a consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG that a previous failover test was performed on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopyTestCancel(RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        RecoverPointVolumeProtectionInfo failoverCopyInfo = copyToFailoverTo.getCopyVolumeInfo();
        resumeTransfer(failoverCopyInfo);
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.disableCopyImage(functionalAPI, copyToFailoverTo);
    }

    /**
     * Perform a failover to the consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG to perform a failover to. Also contains bookmark and APIT info.
     *            If no bookmark or APIT specified, failover to most recent image.
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopy(RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        // Check the params
        // If bookmark != null, enable the bookmark on the copy, and failover to that copy
        // If APITTime != null, enable the specified APIT on the copy, and failover to that copy
        // If both are null, enable the most recent imagem, and failover to that copy
        String bookmarkName = copyToFailoverTo.getBookmarkName();
        Date apitTime = copyToFailoverTo.getApitTime();
        if (bookmarkName != null) {
            logger.info("Failver copy to bookmark : " + bookmarkName);
        } else if (apitTime != null) {
            logger.info("Failover copy to APIT : " + apitTime.toString());
        } else {
            logger.info("Failover copy to most recent image");
        }
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.enableCopyImage(functionalAPI, copyToFailoverTo, true);
        // Stop the replication link to this copy
        // ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToFailoverTo.getCopyVolumeInfo());
        // imageManager.disableCGCopy(functionalAPI, cgCopyUID);
    }

    /**
     * Cancel a failover operation, usually a failover after a failover without a swap.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG that a previous failover test was performed on
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void failoverCopyCancel(RPCopyRequestParams copyToFailoverTo) throws RecoverPointException {
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        imageManager.disableCopyImage(functionalAPI, copyToFailoverTo);
    }

    /**
     * Perform a swap to the consistency group copy specified by the input request params.
     *
     * @param RPCopyRequestParams copyToFailoverTo - Volume info for the CG to perform a swap to.
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void swapCopy(RPCopyRequestParams copyParams) throws RecoverPointException {
        logger.info("Swap copy to current or most recent image");
        // Make sure the copy is already enabled or RP will fail the operation. If it isn't enabled, enable it.
        RecoverPointImageManagementUtils imageManager = new RecoverPointImageManagementUtils();
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyParams.getCopyVolumeInfo());
        ConsistencyGroupCopyState copyState = imageManager.getCopyState(functionalAPI, cgCopyUID);

        if (copyState != null && copyState.getAccessedImage() == null) {
            // Enable to the latest image
            failoverCopy(copyParams);
        }

        // Perform the failover
        imageManager.failoverCGCopy(functionalAPI, cgCopyUID);

        // Prepare the link settings for new links
        prepareLinkSettings(cgCopyUID);

        // Set the failover copy as production to resume data flow
        imageManager.setCopyAsProduction(functionalAPI, cgCopyUID);

        // wait for links to become active
        ConsistencyGroupUID cgUID = cgCopyUID.getGroupUID();
        String cgName = null;
        try {
            cgName = functionalAPI.getGroupName(cgUID);
        } catch (FunctionalAPIActionFailedException_Exception | FunctionalAPIInternalError_Exception e) {
            // benign error -- cgName is only used for logging
            logger.error(e.getMessage(), e);
        }

        logger.info("Waiting for links to become active for CG " + (cgName == null ? "unknown CG name" : cgName));
        RecoverPointImageManagementUtils rpiMgmt = new RecoverPointImageManagementUtils();        
        rpiMgmt.waitForCGLinkState(functionalAPI, cgUID, RecoverPointImageManagementUtils.getPipeActiveState(functionalAPI, cgUID));
        logger.info(String.format("Replication sets have been added to consistency group %s.",
                (cgName == null ? "unknown CG name" : cgName)));
    }

    /**
     * Find the link settings corresponding to the given production and target copy identifiers.
     *
     * @param linkSettings the consistency group link settings
     * @param prodCopyUID the production copy
     * @param targetCopyUID the target copy
     * @param prodCopyName the name of the production copy
     * @param targetCopyName the name of the target copy
     * @return the consistency group settings matching the prod/target copy relationship
     */
    private ConsistencyGroupLinkSettings findLinkSettings(List<ConsistencyGroupLinkSettings> cgLinkSettings, GlobalCopyUID prodCopyUID,
            GlobalCopyUID targetCopyUID, String prodCopyName, String targetCopyName) {
        ConsistencyGroupLinkSettings toRet = null;

        if (cgLinkSettings != null && !cgLinkSettings.isEmpty()
                && prodCopyUID != null && targetCopyUID != null) {
            for (ConsistencyGroupLinkSettings linkSetting : cgLinkSettings) {
                if (isMatchingLinkSettings(linkSetting, prodCopyUID, targetCopyUID)) {
                    logger.info("Found existing link settings between {} and {}.", prodCopyName, targetCopyName);
                    toRet = linkSetting;
                    break;
                }
            }
        }

        if (toRet == null) {
            logger.info("Unable to find existing link settings between {} and {}.", prodCopyName, targetCopyName);
        }

        return toRet;
    }

    /**
     * Convenience method used to determine if the provided production copy and target copy
     * are points on the given link settings.
     *
     * @param linkSetting the link settings to examine.
     * @param prodCopyUID the production copy end of the link settings.
     * @param targetCopyUID the target copy end of the link settings.
     * @return
     */
    private boolean isMatchingLinkSettings(ConsistencyGroupLinkSettings linkSettings,
            GlobalCopyUID prodCopyUID, GlobalCopyUID targetCopyUID) {

        GlobalCopyUID firstCopy = null;
        GlobalCopyUID secondCopy = null;

        if (linkSettings.getGroupLinkUID() != null) {
            firstCopy = linkSettings.getGroupLinkUID().getFirstCopy();
            secondCopy = linkSettings.getGroupLinkUID().getSecondCopy();

            // Compare both ends of the link to the provided prod and target copies passed in.
            // A link is a match if the prod and target copy are both found, regardless of which
            // end of the link they belong.
            if ((RecoverPointUtils.cgCopyEqual(firstCopy, prodCopyUID)
                    && RecoverPointUtils.cgCopyEqual(secondCopy, targetCopyUID))
                    || (RecoverPointUtils.cgCopyEqual(firstCopy, targetCopyUID)
                    && RecoverPointUtils.cgCopyEqual(secondCopy, prodCopyUID))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Prepares the link settings between the new production copy and all other copies.
     *
     * @param cgCopyUID the failover/new production copy
     * @throws RecoverPointException
     */
    private void prepareLinkSettings(ConsistencyGroupCopyUID cgCopyUID) throws RecoverPointException {
        String cgCopyName = null;
        String cgName = null;

        logger.info("Preparing link settings between new production copy and local/remote copies after failover.");

        try {
            cgCopyName = functionalAPI.getGroupCopyName(cgCopyUID);
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());

            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgCopyUID.getGroupUID());
            List<ConsistencyGroupLinkSettings> cgLinkSettings = groupSettings.getActiveLinksSettings();
            List<ConsistencyGroupLinkSettings> oldProdCgLinkSettings = groupSettings.getPassiveLinksSettings();
            List<ConsistencyGroupCopyUID> productionCopiesUIDs = groupSettings.getProductionCopiesUIDs();
            for (ConsistencyGroupCopyUID prodCopyUID : productionCopiesUIDs) {

                String prodCopyName = functionalAPI.getGroupCopyName(prodCopyUID);

                List<ConsistencyGroupCopySettings> copySettings = groupSettings.getGroupCopiesSettings();

                ConsistencyGroupLinkSettings linkSettings = null;

                for (ConsistencyGroupCopySettings copySetting : copySettings) {
                    // We need to set the link settings for all orphaned copies. Orphaned copies
                    // are identified by not being the production copy or the current copy.
                    if (!copySetting.getName().equalsIgnoreCase(prodCopyName)
                            && !copySetting.getName().equalsIgnoreCase(cgCopyName)) {

                        String copyName = functionalAPI.getGroupCopyName(copySetting.getCopyUID());
                        // Check to see if a link setting already exists for the link between the 2 copies
                        linkSettings = findLinkSettings(
                                cgLinkSettings, cgCopyUID.getGlobalCopyUID(), copySetting.getCopyUID().getGlobalCopyUID(),
                                cgCopyName, copyName);

                        if (linkSettings == null) {
                            // Link settings for the source/target copies does not exist so we need to create one
                            // Find the corresponding link settings prior to the failover.
                            linkSettings = findLinkSettings(
                                    oldProdCgLinkSettings, prodCopyUID.getGlobalCopyUID(), copySetting.getCopyUID().getGlobalCopyUID(),
                                    prodCopyName, copyName);

                            if (linkSettings != null) {
                                logger.info(String
                                        .format("Generate new link settings between %s and %s based on existing link settings between the current production copy %s and %s.",
                                                cgCopyName, copyName, prodCopyName, copyName));
                                ConsistencyGroupLinkUID cgLinkUID = linkSettings.getGroupLinkUID();
                                // Set the link copies appropriately
                                GlobalCopyUID sourceCopy = cgCopyUID.getGlobalCopyUID();
                                GlobalCopyUID targetCopy = copySetting.getCopyUID().getGlobalCopyUID();

                                cgLinkUID.setFirstCopy(sourceCopy);
                                cgLinkUID.setSecondCopy(targetCopy);

                                ConsistencyGroupLinkPolicy linkPolicy = linkSettings.getLinkPolicy();

                                // Check the copy cluster information to determine if this is a local or remote copy
                                if (sourceCopy.getClusterUID().getId() == targetCopy.getClusterUID().getId()) {
                                    // local copy
                                    logger.info(String.format(
                                            "Creating new local copy link settings between %s and %s, for consistency group %s.",
                                            cgCopyName, copyName, cgName));
                                    linkPolicy.getProtectionPolicy().setReplicatingOverWAN(false);
                                } else {
                                    // remote copy
                                    logger.info(String.format(
                                            "Creating new remote copy link settings between %s and %s, for consistency group %s.",
                                            cgCopyName, copyName, cgName));
                                    linkPolicy.getProtectionPolicy().setReplicatingOverWAN(true);
                                }

                                functionalAPI.addConsistencyGroupLink(cgLinkUID, linkPolicy);
                            }
                        }
                    }
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(cgCopyName, cgName, e);
        }
    }

    /**
     * Delete the consistency group copy specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG copy to delete (can't be production)
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteCopy(RecoverPointVolumeProtectionInfo copyToDelete) throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToDelete);
        String copyName = null;
        String cgName = null;
        try {
            copyName = functionalAPI.getGroupCopyName(cgCopyUID);
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
            List<ConsistencyGroupCopyUID> productionCopiesUIDs = functionalAPI.getGroupSettings(cgCopyUID.getGroupUID())
                    .getProductionCopiesUIDs();
            for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
                if (RecoverPointUtils.cgCopyEqual(productionCopyUID, cgCopyUID)) {
                    // Can't call delete copy using the production CG copy
                    throw RecoverPointException.exceptions.cantCallDeleteCopyUsingProductionVolume(copyName, cgName);
                }
                functionalAPI.removeConsistencyGroupCopy(cgCopyUID);
                logger.info("Deleted copy " + copyName + " for consistency group " + cgName);
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteCopy(copyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteCopy(copyName, cgName, e);
        }
    }

    /**
     * Delete the consistency group specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo volumeInfo - Volume info for the CG to delete
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteCG(RecoverPointVolumeProtectionInfo cgToDelete) throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(cgToDelete);
        String cgName = null;
        try {
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgCopyUID.getGroupUID());
            List<ConsistencyGroupCopyUID> productionCopiesUIDs = groupSettings.getProductionCopiesUIDs();

            for (ConsistencyGroupCopyUID productionCopyUID : productionCopiesUIDs) {
                if (!cgToDelete.isMetroPoint() && !RecoverPointUtils.cgCopyEqual(productionCopyUID, cgCopyUID)) {
                    // Can't call delete CG using anything but the production CG copy
                    throw RecoverPointException.exceptions
                            .cantCallDeleteCGUsingProductionCGCopy(cgName);
                }
            }
            // First disable the CG before removing it, this buys RP a bit of time
            // to clean it up.
            disableConsistencyGroup(cgCopyUID.getGroupUID());
            // Delete the CG, async call to RP
            functionalAPI.removeConsistencyGroup(cgCopyUID.getGroupUID());
            // Verify the CG has been removed
            validateCGRemoved(cgCopyUID.getGroupUID(), cgName);            
            logger.info("Deleted consistency group " + cgName);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, e);
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, e);
        }
    }

    /**
     * Validate that the CG has been removed from the RP system by calling out
     * to get all CGs and ensuring the one we are trying to delete is gone.
     * 
     * If we still see the CG being returned, wait and try again until max attempts is
     * reached.
     * 
     * @param cgToValidate The CG UID to check
     * @param cgName The CG name to check
     * @throws RecoverPointException RP Exception to throw if we hit it
     */        
    private void validateCGRemoved(ConsistencyGroupUID cgToValidate, String cgName) 
            throws RecoverPointException {        
        try {
            logger.info(String.format("Validating that RP CG [%s] (%d) has been removed.", cgName, cgToValidate.getId()));
            int cgDeleteAttempt = 0;
            while (cgDeleteAttempt < MAX_WAIT_FOR_RP_DELETE_ATTEMPTS) {
                boolean cgDeleted = true;
                logger.info(String.format("Validation attempt %d of %d", cgDeleteAttempt + 1, MAX_WAIT_FOR_RP_DELETE_ATTEMPTS));
                // Get all the CGs from RecoverPoint
                List<ConsistencyGroupUID> allCGs = functionalAPI.getAllConsistencyGroups();
                // Check to see that the CG we're looking to remove is gone.
                // If not, wait and check again.
                for (ConsistencyGroupUID cgUID : allCGs) {
                    if (cgToValidate.getId() == cgUID.getId()) {
                        logger.info(String.format("RP CG [%s] (%d) has not been removed yet. Will wait and check again...", 
                                cgName, cgToValidate.getId()));
                        waitForRpOperation();
                        cgDeleteAttempt++;
                        cgDeleted = false;
                        
                        // If we've reached 1/2 the attempts, let's try refreshing the connection
                        // to RecoverPoint to ensure we do not have a stale connection.
                        if (cgDeleteAttempt == (MAX_WAIT_FOR_RP_DELETE_ATTEMPTS / 2)) {
                            this.reconnect();
                        }
                        
                        break;
                    }
                }       
                if (cgDeleted) {
                    // RP CG appears to have been removed from RP
                    logger.info(String.format("RP CG [%s] (%d) has been removed.", cgName, cgToValidate.getId()));
                    break;
                }
            }
            // If we reached max attempts alert the user and continue on with delete operation.
            if (cgDeleteAttempt >= MAX_WAIT_FOR_RP_DELETE_ATTEMPTS) {
                // Allow the cleanup to continue in ViPR but warn the user
                logger.error(String.format("Max attempts reached waiting for RP CG [%s] (%d) to be removed from RP. "
                        + "Please check RP System. Delete operation will continue...", 
                        cgName, cgToValidate.getId()));
                throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, 
                        new Exception("Max attempts reached waiting for RP CG to be removed from RP."));
            }
        } catch (Exception e) {
            logger.error(String.format("Exception hit while waiting for RP CG [%s] to be removed.", cgName));
            throw RecoverPointException.exceptions.failedToDeleteConsistencyGroup(cgName, e);
        }
    }

    /**
     * Small wait to let RP catch up to the calls from ViPR
     */
    private void waitForRpOperation() {
        logger.info("Sleeping for 10s waiting for RP operation");
        try {
            Thread.sleep(RP_OPERATION_WAIT_TIME);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }        
    }

    /**
     * Delete a journal volume (WWN) from the consistency group copy specified by the input volume info.
     *
     * @param RecoverPointVolumeProtectionInfo copyToModify - Volume info for the CG to add a journal volume to
     *
     * @param String journalWWNToDelete - WWN of the journal volume to delete
     *
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteJournalFromCopy(RecoverPointVolumeProtectionInfo copyToModify, String journalWWNToDelete)
            throws RecoverPointException {
        ConsistencyGroupCopyUID cgCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(copyToModify);
        String copyName = null;
        String cgName = null;
        try {
            copyName = functionalAPI.getGroupCopyName(cgCopyUID);
            cgName = functionalAPI.getGroupName(cgCopyUID.getGroupUID());
            logger.info("Request to delete journal " + journalWWNToDelete + " from copy " + copyName + " for consistency group " + cgName);

            Set<RPSite> allSites = getAssociatedRPSites();
            DeviceUID journalDeviceUIDToDelete = RecoverPointUtils.getDeviceID(allSites, journalWWNToDelete);
            if (journalDeviceUIDToDelete == null) {
                throw RecoverPointException.exceptions.cannotFindJournal(journalWWNToDelete);
            }
            functionalAPI.removeJournalVolume(cgCopyUID, journalDeviceUIDToDelete);

        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteJournal(journalWWNToDelete,
                    copyName, cgName, e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteJournal(journalWWNToDelete,
                    copyName, cgName, e);
        }
    }

    /**
     * Delete a replication set based on the volume info sent in.
     *
     * @param RecoverPointVolumeProtectionInfo volume - Volume info for the CG to remove the replication set from
     * @param String volumeWWNToDelete - WWN of the volume to delete the entire replication set for
     * @return void
     *
     * @throws RecoverPointException
     **/
    public void deleteReplicationSet(RecoverPointVolumeProtectionInfo volume) throws RecoverPointException {
        List<RecoverPointVolumeProtectionInfo> wrapper = new ArrayList<RecoverPointVolumeProtectionInfo>();
        wrapper.add(volume);
        deleteReplicationSets(wrapper);
    }

    /**
     * Get RP site statistics for use in collectStatisticsInformation
     *
     * @param RecoverPointVolumeProtectionInfo copyToModify - Volume info for the CG to add a journal volume to
     *
     * @param String journalWWNToDelete - WWN of the journal volume to delete
     *
     * @return RecoverPointStatisticsResponse
     *
     * @throws RecoverPointException
     **/
    public RecoverPointStatisticsResponse getRPSystemStatistics() throws RecoverPointException {
        logger.info("Collecting RecoverPoint System Statistics.");
        RecoverPointStatisticsResponse response = new RecoverPointStatisticsResponse();
        try {
            Map<Long, Double> siteAvgCPUUsageMap = new HashMap<Long, Double>();
            Map<Long, Long> siteInputAvgThroughput = new HashMap<Long, Long>();
            Map<Long, Long> siteOutputAvgThroughput = new HashMap<Long, Long>();
            Map<Long, Long> siteIncomingAvgWrites = new HashMap<Long, Long>();
            SystemStatistics systemStatistics = functionalAPI.getSystemStatistics();
            Set<ClusterUID> ClusterUIDList = new HashSet<ClusterUID>();
            List<RpaStatistics> rpaStatisticsList = systemStatistics.getRpaStatistics();

            for (RpaStatistics rpaStatistics : rpaStatisticsList) {
                ClusterUID siteID = rpaStatistics.getRpaUID().getClusterUID();
                boolean foundSite = false;
                for (ClusterUID siteListUID : ClusterUIDList) {
                    if (siteID.getId() == siteListUID.getId()) {
                        foundSite = true;
                        break;
                    }
                }
                if (!foundSite) {
                    ClusterUIDList.add(siteID);
                }
            }

            for (ClusterUID ClusterUID : ClusterUIDList) {
                List<Double> rpaCPUList = new LinkedList<Double>();
                List<Long> rpaSiteInputAvgThroughputList = new LinkedList<Long>();
                List<Long> rpaSiteOutputAvgThroughputList = new LinkedList<Long>();
                List<Long> rpaSiteInputAvgIncomingWritesList = new LinkedList<Long>();
                for (RpaStatistics rpaStatistics : rpaStatisticsList) {
                    if (rpaStatistics.getRpaUID().getClusterUID().getId() == ClusterUID.getId()) {
                        rpaCPUList.add(Double.valueOf(rpaStatistics.getCpuUsage()));
                        rpaSiteInputAvgThroughputList
                                .add(rpaStatistics.getTraffic().getApplicationThroughputStatistics().getInThroughput());
                        for (ConnectionOutThroughput cot : rpaStatistics.getTraffic().getApplicationThroughputStatistics()
                                .getConnectionsOutThroughputs()) {
                            rpaSiteOutputAvgThroughputList.add(cot.getOutThroughput());
                        }
                        rpaSiteInputAvgIncomingWritesList.add(rpaStatistics.getTraffic().getApplicationIncomingWrites());
                    }
                }
                Double cpuTotalUsage = 0.0;
                Long incomingWritesTotal = Long.valueOf(0);
                Long inputThoughputTotal = Long.valueOf(0);
                Long outputThoughputTotal = Long.valueOf(0);

                for (Double rpaCPUs : rpaCPUList) {
                    cpuTotalUsage += rpaCPUs;
                }
                for (Long siteInputThroughput : rpaSiteInputAvgThroughputList) {
                    inputThoughputTotal += siteInputThroughput;
                }
                for (Long siteOutputThroughput : rpaSiteOutputAvgThroughputList) {
                    outputThoughputTotal += siteOutputThroughput;
                }
                for (Long incomingWrites : rpaSiteInputAvgIncomingWritesList) {
                    incomingWritesTotal += incomingWrites;
                }
                logger.info("Average CPU usage for site: " + ClusterUID.getId() + " is " + cpuTotalUsage / rpaCPUList.size());
                logger.info("Average input throughput for site: " + ClusterUID.getId() + " is " + inputThoughputTotal / rpaCPUList.size()
                        + " kb/s");
                logger.info("Average output throughput for site: " + ClusterUID.getId() + " is " + outputThoughputTotal / rpaCPUList.size()
                        + " kb/s");
                logger.info("Average incoming writes for site: " + ClusterUID.getId() + " is " + incomingWritesTotal / rpaCPUList.size()
                        + " writes/s");

                siteAvgCPUUsageMap.put(ClusterUID.getId(), cpuTotalUsage / rpaCPUList.size());
                siteInputAvgThroughput.put(ClusterUID.getId(), inputThoughputTotal / rpaCPUList.size());
                siteOutputAvgThroughput.put(ClusterUID.getId(), outputThoughputTotal / rpaCPUList.size());
                siteIncomingAvgWrites.put(ClusterUID.getId(), incomingWritesTotal / rpaCPUList.size());

            }
            response.setSiteCPUUsageMap(siteAvgCPUUsageMap);
            response.setSiteInputAvgIncomingWrites(siteIncomingAvgWrites);
            response.setSiteOutputAvgThroughput(siteOutputAvgThroughput);
            response.setSiteInputAvgThroughput(siteInputAvgThroughput);
            List<ProtectionSystemParameters> systemParameterList = new LinkedList<ProtectionSystemParameters>();
            MonitoredParametersStatus monitoredParametersStatus = functionalAPI.getMonitoredParametersStatus();
            List<MonitoredParameter> monitoredParameterList = monitoredParametersStatus.getParameters();

            for (MonitoredParameter monitoredParameter : monitoredParameterList) {
                ProtectionSystemParameters param = response.new ProtectionSystemParameters();
                param.parameterName = monitoredParameter.getKey().getParameterType().value();
                param.parameterLimit = monitoredParameter.getValue().getParameterWaterMarks().getLimit();
                param.currentParameterValue = monitoredParameter.getValue().getValue();

                if (monitoredParameter.getKey().getClusterUID() != null) {
                    param.siteID = monitoredParameter.getKey().getClusterUID().getId();
                }

                systemParameterList.add(param);
            }
            response.setParamList(systemParameterList);

            for (ProtectionSystemParameters monitoredParameter : response.getParamList()) {
                logger.info("Key: " + monitoredParameter.parameterName);
                logger.info("Current Value: " + monitoredParameter.currentParameterValue);
                logger.info("Max Value: " + monitoredParameter.parameterLimit);
            }
            return response;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToGetStatistics(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToGetStatistics(e);
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToGetStatistics(e);
        }
    }

    /**
     * Find the transient site ID, given the permanent/unchanging unique internal site name.
     * Needed for some external operations, like filling in proper copy info in a snapshot.
     *
     * @param internalSiteName internal site name, never changes
     * @param clusterIdCache cache of already discovered cluster ids (can be null)
     * @return ClusterUID corresponding to the site that has that internal site name.
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     */
    private ClusterUID getRPSiteID(String internalSiteName, Map<String, ClusterUID> clusterIdCache)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception {
        if (clusterIdCache != null && clusterIdCache.containsKey(internalSiteName)) {
            return clusterIdCache.get(internalSiteName);
        } else {
            ClusterUID clusterId = RecoverPointUtils.getRPSiteID(functionalAPI, internalSiteName);
            if (clusterIdCache != null) {
                clusterIdCache.put(internalSiteName, clusterId);
            }
            return clusterId;
        }
    }

    /**
     * Find the transient site ID, given the permanent/unchanging unique internal site name.
     * Needed for some external operations, like filling in proper copy info in a snapshot.
     *
     * @param internalSiteName internal site name, never changes
     * @return ClusterUID corresponding to the site that has that internal site name.
     * @throws RecoverPointException
     */
    public ClusterUID getRPSiteID(String internalSiteName) throws RecoverPointException {
        try {
            return RecoverPointUtils.getRPSiteID(functionalAPI, internalSiteName);
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.failedToGetRPSiteID(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.failedToGetRPSiteID(e);
        }
    }

    /**
     * Get the replication set information associated with this volume. This is important when assembling a workflow to
     * recreate the replication set for the purpose of expanding volumes.
     *
     * Steps are as follows:
     * This method: Get the state information associated with the replication set
     * Delete method below: Delete the replication set
     * RP Controller: Expand volumes
     * Recreate method below: Perform a rescan_san
     * Recreate method below: Create the replication set
     *
     * @param RecoverPointVolumeProtectionInfo volume - Volume info for the CG to remove the replication set from
     * @return void
     *
     * @throws RecoverPointException
     **/
    public RecreateReplicationSetRequestParams getReplicationSet(RecoverPointVolumeProtectionInfo volume) throws RecoverPointException {
        ReplicationSetSettings rsetSettings = null;

        try {
            ConsistencyGroupUID cgID = new ConsistencyGroupUID();
            cgID.setId(volume.getRpVolumeGroupID());
            ReplicationSetUID repSetUID = new ReplicationSetUID();
            repSetUID.setId(volume.getRpVolumeRSetID());
            rsetSettings = getReplicationSetSettings(functionalAPI, rsetSettings, cgID, repSetUID);

            if (rsetSettings == null) {
                throw RecoverPointException.exceptions.cannotFindReplicationSet(volume
                        .getRpVolumeWWN());
            }

            RecreateReplicationSetRequestParams response = new RecreateReplicationSetRequestParams();
            response.setCgName(volume.getRpProtectionName());
            response.setName(rsetSettings.getReplicationSetName());
            response.setConsistencyGroupUID(cgID);
            response.setVolumes(new ArrayList<CreateRSetVolumeParams>());
            for (UserVolumeSettings volumeSettings : rsetSettings.getVolumes()) {
                CreateRSetVolumeParams volumeParams = new CreateRSetVolumeParams();
                volumeParams.setDeviceUID(volumeSettings.getVolumeInfo().getVolumeID());
                volumeParams.setConsistencyGroupCopyUID(volumeSettings.getGroupCopyUID());
                response.getVolumes().add(volumeParams);
            }

            return response;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            throw RecoverPointException.exceptions.cannotFindReplicationSet(
                    volume.getRpVolumeWWN(), e);
        } catch (FunctionalAPIInternalError_Exception e) {
            throw RecoverPointException.exceptions.cannotFindReplicationSet(
                    volume.getRpVolumeWWN(), e);
        }
    }

    /**
     * Deletes one-to-many replication sets based on the volume information passed in.
     *
     * @param volumeInfoList the volume information that relates to one or more replication sets.
     * @throws RecoverPointException
     */
    public void deleteReplicationSets(List<RecoverPointVolumeProtectionInfo> volumeInfoList) throws RecoverPointException {
        // Used to capture the volume WWNs associated with each replication set to remove.
        List<String> volumeWWNs = new ArrayList<String>();
        Map<Long, String> rsetNames = new HashMap<Long, String>();
        List<Long> rsetIDsToValidate = new ArrayList<Long>();

        try {
            ConsistencyGroupUID cgID = new ConsistencyGroupUID();
            cgID.setId(volumeInfoList.get(0).getRpVolumeGroupID());

            ConsistencyGroupSettingsChangesParam cgSettingsParam = new ConsistencyGroupSettingsChangesParam();
            cgSettingsParam.setGroupUID(cgID);

            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgID);
            List<ReplicationSetSettings> replicationSetSettings = groupSettings.getReplicationSetsSettings();
            
            for (RecoverPointVolumeProtectionInfo volumeInfo : volumeInfoList) {
                boolean found = false;
                // Validate that the requested replication sets to delete actually exist.
                for (ReplicationSetSettings replicationSet : replicationSetSettings) {
                    if (replicationSet.getReplicationSetUID().getId() == volumeInfo.getRpVolumeRSetID()) {
                        rsetNames.put(volumeInfo.getRpVolumeRSetID(), replicationSet.getReplicationSetName());
                        found = true;                        
                        break;
                    }
                }

                if (!found) {
                    logger.warn(String.format("No matching replication set for volume [%s] with replication set ID [%s] found."
                            + " This will need to be checked on the RP System.", 
                            volumeInfo.getRpVolumeWWN(), volumeInfo.getRpVolumeRSetID()));      
                    continue;
                }

                ReplicationSetUID repSetUID = new ReplicationSetUID();
                repSetUID.setId(volumeInfo.getRpVolumeRSetID());
                repSetUID.setGroupUID(cgID);

                if (!containsRepSetUID(cgSettingsParam.getRemovedReplicationSets(), repSetUID)) {
                	cgSettingsParam.getRemovedReplicationSets().add(repSetUID);
                    rsetIDsToValidate.add(repSetUID.getId());
                }
                volumeWWNs.add(volumeInfo.getRpVolumeWWN());
                
                logger.info(String.format("Adding replication set [%s] (%d) to be removed from RP CG [%s] (%d)", 
                        rsetNames.get(volumeInfo.getRpVolumeRSetID()), volumeInfo.getRpVolumeRSetID(), 
                        groupSettings.getName(), cgID.getId()));
            }

            // Only execute the remove replication sets operation if there are replication sets
            // to remove.
            if (cgSettingsParam.getRemovedReplicationSets() != null &&
                    !cgSettingsParam.getRemovedReplicationSets().isEmpty()) {
                if (replicationSetSettings.size() == cgSettingsParam.getRemovedReplicationSets().size()) {
                    // We are removing all the replication sets in the CG so we need to disable
                    // the entire CG.
                    disableConsistencyGroup(cgID);
                }
                // Remove the replication sets
                functionalAPI.setConsistencyGroupSettings(cgSettingsParam);
                // Validate that the RSets have been removed
                validateRSetsRemoved(rsetIDsToValidate, cgID, volumeWWNs);                
                logger.info("Request to delete replication sets " + rsetNames.toString() + " from RP CG "
                        + groupSettings.getName() + " completed.");
            } else {
                logger.warn(String.format("No replication sets found to be deleted from RP CG [%s] (%d)", 
                        groupSettings.getName(), cgID.getId()));
            }        
        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToDeleteReplicationSet(
                    volumeWWNs.toString(), e);
        }
    }
    
    /**
     * Returns true if repSetUID is already contained in rsetUids list. 
     * @param rsetUids List of ReplicationSet UIDs
     * @param repSetUID ReplicationSet UID to check if it is contained in the rsetUids list.
     * @return
     */
    private boolean containsRepSetUID(List<ReplicationSetUID> rsetUids, ReplicationSetUID repSetUID) {
    	for(ReplicationSetUID rsetUid : rsetUids) {
    		if (rsetUid.getId() == repSetUID.getId()) {
    			return true;    			
    		}
    	}
    	return false;
    }
    
    /**
     * Validate that the RSet(s) has been removed from the RP system by calling out
     * to get all RSets for the CG and ensuring the one(s) we are trying to delete is gone.
     * 
     * If we still see the RSet(s) being returned, wait and try again until max attempts is
     * reached.
     * @param resetIDsToValidate The RSet IDs to check that they have been removed from RP
     * @param cgToValidate The CG UID to check
     * @param volumeWWNs The WWNs of the source volumes to delete, used for exceptions
     * @throws RecoverPointException RP Exception to throw if we hit it
     */
    private void validateRSetsRemoved(List<Long> resetIDsToValidate, ConsistencyGroupUID cgToValidate, List<String> volumeWWNs) 
            throws RecoverPointException {        
        try {            
            String cgName = functionalAPI.getGroupName(cgToValidate);
            logger.info(String.format("Validating that all requested RSets have been removed from RP CG [%s] (%d)", cgName, cgToValidate.getId()));
            int rsetDeleteAttempt = 0;
            while (rsetDeleteAttempt < MAX_WAIT_FOR_RP_DELETE_ATTEMPTS) {
                boolean allRSetsDeleted = true;
                logger.info(String.format("Validation attempt %d of %d", rsetDeleteAttempt + 1, MAX_WAIT_FOR_RP_DELETE_ATTEMPTS));
                // Get the current RSets from the CG
                ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgToValidate);
                List<ReplicationSetSettings> replicationSetSettings = groupSettings.getReplicationSetsSettings();
                // Check to see that all RSets in the request have been removed from the CG.
                // If any are still present, wait and check again.
                for (ReplicationSetSettings rset : replicationSetSettings) {                    
                    if (resetIDsToValidate.contains(rset.getReplicationSetUID().getId())) {
                        logger.info(String.format("RSet [%s] (%d) has not been removed yet. Will wait and check again...", 
                                rset.getReplicationSetName(), rset.getReplicationSetUID().getId()));
                        waitForRpOperation();
                        rsetDeleteAttempt++;
                        allRSetsDeleted = false;                        
                        // If we've reached 1/2 the attempts, let's try refreshing the connection
                        // to RecoverPoint to ensure we do not have a stale connection.
                        if (rsetDeleteAttempt == (MAX_WAIT_FOR_RP_DELETE_ATTEMPTS / 2)) {
                            this.reconnect();
                        }                                
                        break;
                    }
                }
                if (allRSetsDeleted) {
                    // RSets appear to have been removed from RP
                    logger.info(String.format("All requested RSets have been removed from RP CG [%s] (%d).", 
                            cgName, cgToValidate.getId()));
                    break;
                }
            }
            // If we reached max attempts alert the user and continue on with delete operation.
            if (rsetDeleteAttempt >= MAX_WAIT_FOR_RP_DELETE_ATTEMPTS) {
                // Allow the cleanup to continue in ViPR but warn the user
                logger.error(String.format("Max attempts reached waiting for requested RSets to be removed from RP CG. "
                        + "Please check RP System."));     
                throw RecoverPointException.exceptions.failedToDeleteReplicationSet(
                        volumeWWNs.toString(), new Exception("Max attempts reached waiting for requested RSets to be removed from RP CG. "
                        + "Please check RP System."));
            } 
        } catch (Exception e) {
            logger.error(String.format("Exception hit while waiting for all requested RSets to be removed from RP CG."));
            throw RecoverPointException.exceptions.failedToDeleteReplicationSet(
                    volumeWWNs.toString(), e);
        }
    }

    /**
     * Perform Step 2 of expanding a volume, Recreate a replication set that was previously removed.
     *
     * @param volume volume base of replication set
     * @param rsetSettings replication set information used to create replication set
     * @throws RecoverPointException
     */
    public void recreateReplicationSets(Map<String, RecreateReplicationSetRequestParams> rsetParams) throws RecoverPointException {
        if (rsetParams != null && !rsetParams.isEmpty()) {
            // Used to capture the volume WWNs associated with each replication set to recreate.
            List<String> volumeWWNs = new ArrayList<String>();

            try {
                // Get the CG ID from the first element in the list. All elements will share
                // the same CG ID.
                RecreateReplicationSetRequestParams param = rsetParams.values().iterator().next();
                ConsistencyGroupUID cgID = param.getConsistencyGroupUID();

                // Rescan the SAN
                functionalAPI.rescanSANVolumesInAllClusters(true);

                ConsistencyGroupSettingsChangesParam cgSettingsParam = new ConsistencyGroupSettingsChangesParam();
                ActivationSettingsChangesParams cgActivationSettings = new ActivationSettingsChangesParams();
                cgActivationSettings.setEnable(true);
                cgActivationSettings.setStartTransfer(true);
                cgSettingsParam.setActivationParams(cgActivationSettings);
                cgSettingsParam.setGroupUID(cgID);

                for (Entry<String, RecreateReplicationSetRequestParams> entry : rsetParams.entrySet()) {
                    RecreateReplicationSetRequestParams rsetParam = entry.getValue();
                    // Create replication set
                    logger.info("Adding replication set: " + rsetParam.getName());
                    ReplicationSetSettingsChangesParam repSetSettings = new ReplicationSetSettingsChangesParam();
                    repSetSettings.setName(rsetParam.getName());
                    repSetSettings.setShouldAttachAsClean(false);

                    for (CreateRSetVolumeParams volume : rsetParam.getVolumes()) {
                        UserVolumeSettingsChangesParam volSettings = new UserVolumeSettingsChangesParam();
                        volSettings.setNewVolumeID(volume.getDeviceUID());
                        volSettings.setCopyUID(volume.getConsistencyGroupCopyUID());
                        volSettings.getCopyUID().setGroupUID(cgID);

                        repSetSettings.getVolumesChanges().add(volSettings);
                    }
                    cgSettingsParam.getReplicationSetsChanges().add(repSetSettings);
                    volumeWWNs.add(entry.getKey());
                }

                // Add the replication set
                functionalAPI.setConsistencyGroupSettings(cgSettingsParam);

                logger.info("Checking for volumes unattached to splitters");
                RecoverPointUtils.verifyCGVolumesAttachedToSplitter(functionalAPI, cgID);
            } catch (FunctionalAPIActionFailedException_Exception e) {
                throw RecoverPointException.exceptions.failedToRecreateReplicationSet(volumeWWNs.toString(), e);
            } catch (FunctionalAPIInternalError_Exception e) {
                throw RecoverPointException.exceptions.failedToRecreateReplicationSet(volumeWWNs.toString(), e);
            }
        }
    }

    private ReplicationSetSettings getReplicationSetSettings(FunctionalAPIImpl impl,
            ReplicationSetSettings rsetSettings,
            ConsistencyGroupUID cgID, ReplicationSetUID repSetUID)
            throws FunctionalAPIActionFailedException_Exception,
            FunctionalAPIInternalError_Exception {
        ConsistencyGroupSettings groupSettings = impl.getGroupSettings(cgID);
        for (ReplicationSetSettings replicationSet : groupSettings.getReplicationSetsSettings()) {
            if (replicationSet.getReplicationSetUID().getId() == repSetUID.getId()) {
                rsetSettings = replicationSet;
                break;
            }
        }
        return rsetSettings;
    }

    /**
     * Returns the array serial numbers associated with each RP Cluster.
     * That is, all arrays that have "visibility" according to the RP Cluster.
     *
     * @return a Map of RP Cluster ID -> a Set of array serial numbers
     * @throws RecoverPointException
     */
    public Map<String, Set<String>> getArraysForClusters() throws RecoverPointException {
        String mgmtIPAddress = _endpoint.toASCIIString();
        if (null == mgmtIPAddress) {
            throw RecoverPointException.exceptions.noRecoverPointEndpoint();
        }
        try {
            logger.info("RecoverPoint service: Returning all RP Clusters associated with endpoint: " + _endpoint);
            FullRecoverPointSettings fullRecoverPointSettings = functionalAPI.getFullRecoverPointSettings();
            Map<String, Set<String>> clusterStorageSystems = new HashMap<String, Set<String>>();

            for (ClusterConfiguration siteSettings : fullRecoverPointSettings.getSystemSettings().getGlobalSystemConfiguration()
                    .getClustersConfigurations()) {
                String siteName = siteSettings.getInternalClusterName();
                clusterStorageSystems.put(siteName, RecoverPointUtils.getArraysForCluster(functionalAPI, siteSettings.getCluster()));
            }

            return clusterStorageSystems;
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingArrays(e);
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage(), e);
            throw RecoverPointException.exceptions.exceptionGettingArrays(e);
        }
    }

    public String getUsername() {
        return _username;
    }

    public void setUsername(String _username) {
        this._username = _username;
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(String _password) {
        this._password = _password;
    }

    /**
     * in a Metropoint environment, adds a link between the standby production copy and the remote/DR copy
     *
     * @param activeProdCopy the CG copy uid of the active production copy
     * @param standbyProdCopy the CG copy uid of the standby production copy
     * @throws FunctionalAPIInternalError_Exception
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIValidationException_Exception
     * @throws RecoverPointException
     */
    private void addStandbyCopyLinkSettings(ConsistencyGroupCopyUID activeProdCopy, ConsistencyGroupCopyUID standbyProdCopy)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
            FunctionalAPIValidationException_Exception {

        logger.info("Preparing link settings between standby production copy and remote copy after Metropoint swap production copies.");

        String activeCgCopyName = functionalAPI.getGroupCopyName(activeProdCopy);
        String standbyCgCopyName = functionalAPI.getGroupCopyName(standbyProdCopy);
        String cgName = functionalAPI.getGroupName(activeProdCopy.getGroupUID());

        ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(activeProdCopy.getGroupUID());

        // find the remote copy; with metropoint, you're only allowed one remote copy
        // so it must be the one with cluster id not equal to the active or standby cluster ids

        ClusterUID activeClusterId = activeProdCopy.getGlobalCopyUID().getClusterUID();
        ClusterUID standbyClusterId = standbyProdCopy.getGlobalCopyUID().getClusterUID();
        for (ConsistencyGroupCopySettings copySetting : groupSettings.getGroupCopiesSettings()) {

            // see if this is the remote copy; that is it's not the active and not the standby
            ClusterUID copyClusterId = copySetting.getCopyUID().getGlobalCopyUID().getClusterUID();
            if (copyClusterId.getId() != activeClusterId.getId() && copyClusterId.getId() != standbyClusterId.getId()) {

                String targetCopyName = functionalAPI.getGroupCopyName(copySetting.getCopyUID());

                // get the link settings for the active production copy and remote copy
                ConsistencyGroupLinkSettings linkSettings = findLinkSettings(groupSettings.getActiveLinksSettings(),
                        activeProdCopy.getGlobalCopyUID(), copySetting.getCopyUID().getGlobalCopyUID(), activeCgCopyName, targetCopyName);

                if (linkSettings != null) {
                    logger.info(String
                            .format("Generate new link settings between %s and %s based on existing link settings between the current production copy %s and %s.",
                                    standbyCgCopyName, targetCopyName, activeCgCopyName, targetCopyName));
                    ConsistencyGroupLinkUID cgLinkUID = linkSettings.getGroupLinkUID();
                    // Set the link copies appropriately
                    GlobalCopyUID standbyCopyUid = standbyProdCopy.getGlobalCopyUID();
                    GlobalCopyUID remoteTargetCopyUid = copySetting.getCopyUID().getGlobalCopyUID();

                    cgLinkUID.setFirstCopy(standbyCopyUid);
                    cgLinkUID.setSecondCopy(remoteTargetCopyUid);

                    ConsistencyGroupLinkPolicy linkPolicy = linkSettings.getLinkPolicy();

                    // create the link between the standby production copy and the remote copy
                    // this has to be a remote copy
                    logger.info(String.format("Creating new remote copy link settings between %s and %s, for consistency group %s.",
                            standbyCgCopyName, targetCopyName, cgName));
                    linkPolicy.getProtectionPolicy().setReplicatingOverWAN(true);

                    functionalAPI.validateAddConsistencyGroupLink(cgLinkUID, linkPolicy);
                    functionalAPI.addConsistencyGroupLink(cgLinkUID, linkPolicy);

                    break;
                }
            }
        }
    }

    /**
     * adds one copy to an existing CG
     *
     * @param cgUID CG uid where new copy should be added
     * @param allSites list of sites that see journal and copy file WWN's
     * @param copyParams the copy to be added
     * @param clusterUid the uid of the cluster the copy should be added to
     * @param rSetUid replication set uid where copy files should be added to
     * @param volumes list of copy files to add to the replication set
     * @param copyType either production, local or remote
     * @return the CG copy uid that was added
     * @throws FunctionalAPIActionFailedException_Exception
     * @throws FunctionalAPIInternalError_Exception
     * @throws FunctionalAPIValidationException_Exception
     */
    private ConsistencyGroupCopyUID addCopyToCG(ConsistencyGroupUID cgUID, Set<RPSite> allSites, CreateCopyParams copyParams,
            ClusterUID clusterUid, List<CreateRSetParams> rSets, RecoverPointCGCopyType copyType)
            throws FunctionalAPIActionFailedException_Exception, FunctionalAPIInternalError_Exception,
            FunctionalAPIValidationException_Exception {

        boolean isProduction = copyType == RecoverPointCGCopyType.PRODUCTION;
        String copyTypeStr = copyType.toString();

        logger.info(String.format("Adding new copy %s to cg", copyParams.getName()));

        ConsistencyGroupCopyUID copyUid = new ConsistencyGroupCopyUID();
        ConsistencyGroupCopySettingsParam copySettingsParam = new ConsistencyGroupCopySettingsParam();

        GlobalCopyUID globalCopyUID = new GlobalCopyUID();
        globalCopyUID.setClusterUID(clusterUid);
        globalCopyUID.setCopyUID(copyType.getCopyNumber());

        copyUid.setGroupUID(cgUID);
        copyUid.setGlobalCopyUID(globalCopyUID);

        copySettingsParam.setCopyName(copyParams.getName());
        copySettingsParam.setCopyPolicy(null);
        copySettingsParam.setEnabled(false);
        copySettingsParam.setGroupCopy(copyUid);
        copySettingsParam.setProductionCopy(isProduction);
        copySettingsParam.setTransferEnabled(false);

        // we can't call validateAddConsistencyGroupCopy here because during a swap operation, it throws an exception
        // which is just a warning that a full sweep will be required. There didn't seem to be a way to catch
        // just the warning and let other errors propegate as errors.
        logger.info("Add Production copy (no validation): " + copyParams.toString());
        functionalAPI.addConsistencyGroupCopy(copySettingsParam);

        // add journals
        for (CreateVolumeParams journalVolume : copyParams.getJournals()) {
            logger.info("Adding Journal : " + journalVolume.toString() + " for Production copy : " + copyParams.getName());            
            functionalAPI.addJournalVolume(copyUid, RecoverPointUtils.getDeviceID(allSites, journalVolume.getWwn()));
        }

        if (rSets != null) {
            ConsistencyGroupSettings groupSettings = functionalAPI.getGroupSettings(cgUID);
            for (CreateRSetParams rSet : rSets) {
                ReplicationSetUID rSetUid = null;
                if (rSet != null && rSet.getName() != null && !rSet.getName().isEmpty()) {
                    for (ReplicationSetSettings rSetSetting : groupSettings.getReplicationSetsSettings()) {
                        if (rSetSetting.getReplicationSetName().equalsIgnoreCase(rSet.getName())) {
                            rSetUid = rSetSetting.getReplicationSetUID();
                            break;
                        }
                    }
                }
                if (rSetUid != null) {
                    for (CreateVolumeParams volume : rSet.getVolumes()) {
                        if ((isProduction && volume.isProduction()) || (!isProduction && !volume.isProduction())) {
                            logger.info(String.format("Adding %s copy volume : %s", copyTypeStr, copyParams.toString()));                           
                            functionalAPI.addUserVolume(copyUid, rSetUid, RecoverPointUtils.getDeviceID(allSites, volume.getWwn()));
                        }
                    }
                }
            }
        }

        return copyUid;
    }

    /**
     * In a metropoint environment, adds the standby production and CDP copies to the CG after failover
     * and set as production back to the original vplex metro
     *
     * @param standbyProdCopy has info about the standby production copy to be added
     * @param standbyLocalCopyParams local standby copies
     * @param rSet contains volume info for standby local copies
     * @param activeProdCopy has info about the active production copy
     *
     */
    public void addStandbyProductionCopy(CreateCopyParams standbyProdCopy,
            CreateCopyParams standbyLocalCopyParams, List<CreateRSetParams> rSets,
            RPCopyRequestParams activeProdCopy) {

        String cgName = "";
        String activeCgCopyName = "";

        try {
            ConsistencyGroupCopyUID activeProdCopyUID = RecoverPointUtils.mapRPVolumeProtectionInfoToCGCopyUID(activeProdCopy
                    .getCopyVolumeInfo());
            ConsistencyGroupUID cgUID = activeProdCopyUID.getGroupUID();
            cgName = functionalAPI.getGroupName(cgUID);

            logger.info(String.format("Adding Standby production and local volumes to Metropoint CG %s", cgName));

            activeCgCopyName = functionalAPI.getGroupCopyName(activeProdCopyUID);
            List<CreateCopyParams> copies = new ArrayList<CreateCopyParams>();
            copies.add(standbyProdCopy);
            if (standbyLocalCopyParams != null) {
                copies.add(standbyLocalCopyParams);
            }
            Set<RPSite> allSites = scan(copies, rSets);
            CreateVolumeParams volume = standbyProdCopy.getJournals().get(0);
            ClusterUID clusterUid = RecoverPointUtils.getRPSiteID(functionalAPI, volume.getInternalSiteName());

            // add the standby production copy
            ConsistencyGroupCopyUID standbyProdCopyUID = addCopyToCG(cgUID, allSites, standbyProdCopy, clusterUid,
                    null, RecoverPointCGCopyType.PRODUCTION);

            // set up a link between the newly added standby prod copy and the remote copy
            addStandbyCopyLinkSettings(activeProdCopyUID, standbyProdCopyUID);

            // add the standby local copies if we have any
            ConsistencyGroupCopyUID standbyLocalCopyUID = null;
            if (standbyLocalCopyParams != null) {
                standbyLocalCopyUID = addCopyToCG(cgUID, allSites, standbyLocalCopyParams, clusterUid,
                        rSets, RecoverPointCGCopyType.LOCAL);

                logger.info("Setting link policy between production copy and local copy on standby cluster(id) : "
                        + standbyLocalCopyUID.getGlobalCopyUID().getClusterUID().getId());
                setLinkPolicy(false, standbyProdCopyUID, standbyLocalCopyUID, cgUID);
            }

            // enable the local copy
            if (standbyLocalCopyUID != null) {
                logger.info("enable standby local copy for CG ", cgName);
                functionalAPI.enableConsistencyGroupCopy(standbyLocalCopyUID, true);
            }

            // enable the production copy
            logger.info("enable production standby copy for CG ", cgName);
            functionalAPI.enableConsistencyGroupCopy(standbyProdCopyUID, true);

            // enable the CG
            logger.info("enable CG " + cgName + " after standby copies added");
            functionalAPI.startGroupTransfer(cgUID);                        

            RecoverPointImageManagementUtils rpiMgmt = new RecoverPointImageManagementUtils();            
            rpiMgmt.waitForCGLinkState(functionalAPI, cgUID, RecoverPointImageManagementUtils.getPipeActiveState(functionalAPI, cgUID));

        } catch (Exception e) {
            throw RecoverPointException.exceptions.failedToFailoverCopy(activeCgCopyName, cgName, e);
        }
    }

    /**
     * checks to see if there is a protection volume with a given wwn
     *
     * @param volumeWWN the WWN of the volume being checked for existence
     * @return
     */
    public boolean doesProtectionVolumeExist(String volumeWWN) {
        try {
            List<ConsistencyGroupSettings> cgsSettings = functionalAPI.getAllGroupsSettings();
            for (ConsistencyGroupSettings cgSettings : cgsSettings) {
                // See if it is a production source, or an RP target
                for (ReplicationSetSettings rsSettings : cgSettings.getReplicationSetsSettings()) {
                    for (UserVolumeSettings uvSettings : rsSettings.getVolumes()) {
                        String volUID = RecoverPointUtils.getGuidBufferAsString(uvSettings.getVolumeInfo().getRawUids(), false);
                        if (volUID.toLowerCase(Locale.ENGLISH).equalsIgnoreCase(volumeWWN)) {
                            return true;
                        }
                    }
                }
            }
        } catch (FunctionalAPIActionFailedException_Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        } catch (FunctionalAPIInternalError_Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
        return false;
    }
}
