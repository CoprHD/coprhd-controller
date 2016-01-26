/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package util;

import static util.BourneUtil.getViprClient;

import java.util.List;

import com.emc.storageos.model.dr.SiteDetailRestRep;
import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteErrorResponse;
import com.emc.storageos.model.dr.SiteIdListParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteUpdateParam;
import com.emc.storageos.coordinator.client.model.SiteState;
import com.emc.storageos.model.dr.SiteActive;
import com.emc.storageos.model.dr.SiteRestRep;
import com.google.common.collect.Lists;
import com.sun.jersey.api.client.ClientResponse;

public class DisasterRecoveryUtils {

    public static List<SiteRestRep> getSiteDetails() {
        List<SiteRestRep> sites = Lists.newArrayList();
        sites.addAll(getAllSites().getSites());
        return sites;
    }

    public static SiteList getAllSites() {
        return getViprClient().site().listAllSites();
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
        return getViprClient().site().pauseSite(ids);
    }

    public static SiteRestRep resumeStandby(String uuid) {
        return getViprClient().site().resumeSite(uuid);
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

    public static boolean isActiveSite() {
        SiteActive siteCheck = checkActiveSite();
        return siteCheck.getIsActive();
    }

    public static String getLocalSiteName() {
        SiteActive siteCheck = checkActiveSite();
        return siteCheck.getLocalSiteName();
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

}
