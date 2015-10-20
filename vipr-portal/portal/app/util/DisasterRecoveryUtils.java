/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package util;

import static util.BourneUtil.getViprClient;

import java.util.List;

import com.emc.storageos.model.dr.SiteAddParam;
import com.emc.storageos.model.dr.SiteList;
import com.emc.storageos.model.dr.SiteRestRep;
import com.google.common.collect.Lists;

public class DisasterRecoveryUtils {

    public static List<SiteRestRep> getSiteDetails() {
        List<SiteRestRep> sites = Lists.newArrayList();
        sites.addAll(getAllSites().getSites());
        return sites;
    }

    public static SiteList getAllSites() {
        return getViprClient().site().listAllSites();
    }

    public static SiteRestRep addStandby(SiteAddParam standbySite) {
        return getViprClient().site().createSite(standbySite);
    }

    public static SiteRestRep deleteStandby(String uuid) {
        return getViprClient().site().deleteSite(uuid);
    }

    public static SiteRestRep pauseStandby(String uuid) {
        return getViprClient().site().pauseSite(uuid);
    }

    public static SiteRestRep getSite(String uuid) {
        return getViprClient().site().getSite(uuid);
    }

    public static boolean hasStandbySite(String id) {
        SiteRestRep standbySite = getViprClient().site().getSite(id);
        if (standbySite == null) {
            return false;
        }
        return true;
    }

    public static boolean hasStandbySites(List<String> ids) {
        for (String id : ids) {
            if (hasStandbySite(id)) {
                return true;
            }
        }
        return false;
    }
}
