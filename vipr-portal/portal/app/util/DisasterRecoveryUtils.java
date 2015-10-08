/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package util;

import static util.BourneUtil.getViprClient;

import java.util.List;

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
}
