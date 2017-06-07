/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package util;

import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.model.dr.SiteActive;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteDetailRestRep;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.dr.SiteRestRep;
import com.emc.storageos.model.dr.SiteUpdateParam;
import com.emc.storageos.model.property.PropertyInfo;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.vipr.client.exceptions.ServiceErrorException;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.ClientResponse;
import play.Play;
import plugin.StorageOsPlugin;

import java.util.Iterator;
import java.util.List;
import static util.BourneUtil.getViprClient;

public class DisasterRecoveryUtils {

    public static List<SiteRestRep> getSites() {
        List<SiteRestRep> sites = Lists.newArrayList();
        sites.addAll(getViprClient().site().listAllSites().getSites());
        return sites;
    }

    public static boolean isLocalSiteRemoved() {
        return getViprClient().site().isLocalSiteRemoved();
    }

    public static int getSiteCount() {
        return getViprClient().site().listAllSites().getSites().size();
    }

    public static SiteActive checkActiveSite() {
        return getViprClient().site().checkIsActive();
    }

    public static SiteRestRep addStandby(SiteAddParam standbySite) {
        return getViprClient().site().createSite(standbySite);
    }

    public static ClientResponse deleteStandby(SiteIdListParam ids) {
        return getViprClient().site().deleteSite(ids);
    }

    public static ClientResponse pauseStandby(SiteIdListParam ids) {
        ClientResponse restresponse = null;
        try {
            restresponse = getViprClient().site().pauseSite(ids);
        } catch (ServiceErrorException ex) {
            throw APIException.internalServerErrors.pauseStandbyPrecheckFailed(ex.getServiceError().getCodeDescription(),
                    ex.getServiceError().getDetailedMessage());
        } catch (Exception ex) {
            throw APIException.internalServerErrors.pauseStandbyPrecheckFailed(ex.getCause().toString(), ex.getMessage());
        }

        return restresponse;
    }

    public static SiteRestRep resumeStandby(String uuid) {
        return getViprClient().site().resumeSite(uuid);
    }

    public static SiteRestRep retryStandby(String uuid) {
        return getViprClient().site().retrySite(uuid);
    }

    public static SiteRestRep getSite(String uuid) {
        try {
            return getViprClient().site().getSite(uuid);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasStandbySite(String id) {
        try {
            SiteRestRep standbySite = getViprClient().site().getSite(id);
            if (standbySite == null) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasStandbySites(List<String> ids) {
        for (String id : ids) {
            if (hasStandbySite(id)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnyStandbySite() {
        List<SiteRestRep> sites = DisasterRecoveryUtils.getSites();
        return sites.size() > 1;
    }

    public static boolean hasPausedSite() {
        List<SiteRestRep> sites = DisasterRecoveryUtils.getSites();
        for (SiteRestRep site : sites) {
            if (SiteState.STANDBY_PAUSED.toString().equals(site.getState())) {
                return true;
            }
        }
        return false;
    }
    
    public static boolean hasActiveDegradedSite() {
        List<SiteRestRep> sites = DisasterRecoveryUtils.getSites();
        for (SiteRestRep site : sites) {
            if (SiteState.ACTIVE_DEGRADED.toString().equals(site.getState())) {
                return true;
            }
        }
        return false;
    }

    public static ClientResponse doSwitchover(String id) {
        return getViprClient().site().doSwitchover(id);
    }

    public static ClientResponse doFailover(String id) {
        return getViprClient().site().doFailover(id);
    }

    public static SiteRestRep getActiveSite() {
        List<SiteRestRep> sites = getViprClient().site().listAllSites().getSites();
        for (SiteRestRep activeSite : sites) {
            if (activeSite.getState().toUpperCase().equals(String.valueOf(SiteState.ACTIVE))) {
                return activeSite;
            }
        }
        return null;
    }

    public static List<SiteRestRep> getStandbySites() {
        List<SiteRestRep> sites = getViprClient().site().listAllSites().getSites();
        Iterator<SiteRestRep> iterator = sites.iterator();
        while (iterator.hasNext()) {
            SiteRestRep site = iterator.next();
            if (site.getState().toUpperCase().equals(String.valueOf(SiteState.ACTIVE))) {
                iterator.remove();
            }
        }
        return sites;
    }

    public static boolean isActiveSite() {
        SiteActive siteCheck = checkActiveSite();
        return siteCheck.getIsActive();
    }

    public static String getLocalSiteName() {
        SiteActive siteCheck = checkActiveSite();
        return siteCheck.getLocalSiteName();
    }
    
    public static String getLocalUuid() {
        SiteActive siteCheck = checkActiveSite();
        return siteCheck.getLocalUuid();
    }

    public static SiteErrorResponse getSiteError(String uuid) {
        return getViprClient().site().getSiteError(uuid);
    }

    public static ClientResponse updateSite(String uuid, SiteUpdateParam updatesite) {
        return getViprClient().site().updateSite(uuid, updatesite);
    }

    public static SiteDetailRestRep getSiteDetails(String uuid) {
        return getViprClient().site().getSiteDetails(uuid);
    }
    
    public static SiteRestRep getLocalSite() {
        return getViprClient().site().getLocalSite();
    }
    
    public static boolean isMultiDrSite() {
        SiteActive siteCheck = checkActiveSite();
        return siteCheck.getIsMultiSite();
    }

    public static boolean isCustomServicesEnabled() {
        if(!Play.mode.isDev()) {
            PropertyInfo propInfo = StorageOsPlugin.getInstance().getCoordinatorClient().getPropertyInfo();
            if (propInfo != null) {
                final String isCustomEnableProp = propInfo.getProperty(ConfigProperty.CUSTOM_SEREVICES_ENABLE);
                if(isCustomEnableProp != null) {
                    final boolean isEnable = Boolean.valueOf(isCustomEnableProp);
                    return isEnable;
                }
            }
        }
        else {
            return true;
        }
        return false;
    }

}
