/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package util;

import com.emc.storageos.model.usergroup.UserGroupCreateParam;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import com.emc.storageos.model.usergroup.UserGroupUpdateParam;
import com.emc.vipr.client.exceptions.ViPRHttpException;
import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;

import java.net.URI;
import java.util.List;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

public class UserGroupUtils {
    public static UserGroupRestRep getUserGroup(String id) {
        try {
            return getViprClient().getUserGroup().get(uri(id));
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    public static List<UserGroupRestRep> getUserGroups() {
        return getViprClient().getUserGroup().getAll();
    }

    public static List<UserGroupRestRep> getUserGroups(List<URI> ids) {
        return getViprClient().getUserGroup().getByIds(ids);
    }

    public static List<UserGroupRestRep> getUserGroupsByDomainName(String domain) {
        List<UserGroupRestRep> results = Lists.newArrayList();
        if (StringUtils.isNotBlank(domain)) {
            List<UserGroupRestRep> userGroupRestReps = UserGroupUtils.getUserGroups();
            for (UserGroupRestRep userGroupRestRep : userGroupRestReps) {
                   if (StringUtils.equalsIgnoreCase(userGroupRestRep.getDomain(), domain)) {
                        results.add(userGroupRestRep);
                   }
                }
            }

        return results;
    }

    public static UserGroupRestRep create(UserGroupCreateParam userGroupCreateParam) {
        return getViprClient().getUserGroup().create(userGroupCreateParam);
    }

    public static UserGroupRestRep update(String id, UserGroupUpdateParam userGroupUpdateParam) {
        return getViprClient().getUserGroup().update(uri(id), userGroupUpdateParam);
    }

    public static void delete(URI id) {
        getViprClient().getUserGroup().delete(id);
    }
}
