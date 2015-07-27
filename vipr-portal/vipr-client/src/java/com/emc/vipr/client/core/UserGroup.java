/*
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

package com.emc.vipr.client.core;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.usergroup.UserGroupCreateParam;
import com.emc.storageos.model.usergroup.UserGroupList;
import com.emc.storageos.model.usergroup.UserGroupRestRep;
import com.emc.storageos.model.usergroup.UserGroupUpdateParam;
import com.emc.vipr.client.ViPRCoreClient;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.impl.PathConstants;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.client.impl.RestClient;

import java.net.URI;
import java.util.List;

public class UserGroup extends AbstractCoreResources<UserGroupRestRep> implements
        TopLevelResources<UserGroupRestRep> {
    public UserGroup(ViPRCoreClient parent, RestClient client) {
        super(parent, client, UserGroupRestRep.class, PathConstants.USER_GROUP_URL);
    }

    @Override
    public UserGroup withInactive(boolean inactive) {
        return (UserGroup) super.withInactive(inactive);
    }

    @Override
    public UserGroup withInternal(boolean internal) {
        return (UserGroup) super.withInternal(internal);
    }

    /**
     * Lists all user groups.
     * <p>
     * API Call: <tt>GET /vdc/admin/user-groups</tt>
     *
     * @return the list of user groups.
     */
    @Override
    public List<NamedRelatedResourceRep> list() {
        UserGroupList response = client.get(UserGroupList.class, baseUrl);
        return ResourceUtils.defaultList(response.getUserGroups());
    }

    @Override
    public List<UserGroupRestRep> getAll() {
        return getAll(null);
    }

    @Override
    public List<UserGroupRestRep> getAll(ResourceFilter<UserGroupRestRep> filter) {
        List<NamedRelatedResourceRep> refs = list();
        return getByRefs(refs);
    }

    /**
     * Creates an user group.
     * <p>
     * API Call: <tt>POST /vdc/admin/user-groups</tt>
     *
     * @param input
     *        the create configuration.
     * @return the created user group.
     */
    public UserGroupRestRep create(UserGroupCreateParam input) {
        return client.post(UserGroupRestRep.class, input, baseUrl);
    }

    /**
     * Updates an user group.
     * <p>
     * API Call: <tt>PUT /vdc/admin/user-groups</tt>
     *
     * @param input
     *        the update configuration.
     * @return the updated user group.
     */
    public UserGroupRestRep update(URI id, UserGroupUpdateParam input) {
        return client.put(UserGroupRestRep.class, input, getIdUrl(), id);
    }

    /**
     * Deletes an user group.
     * <p>
     * API Call: <tt>DELETE /vdc/admin/user-groups/{id}</tt>
     *
     * @param id the user group ID.
     */
    public void delete(URI id) {
        client.delete(String.class, getIdUrl(), id);
    }
}
