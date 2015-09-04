/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.response;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProjectResource;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.authorization.ACL;
import com.emc.storageos.security.authorization.Role;
import com.emc.storageos.model.RelatedResourceRep;

/*
 * Filter for project owned resource representations
 *
 */
public class ProjOwnedResRepFilter<E extends RelatedResourceRep, K extends DataObject & ProjectResource>
        extends ResRepFilter<E> {
    Class<K> _clazz = null;

    public ProjOwnedResRepFilter(StorageOSUser user,
            PermissionsHelper permissionsHelper,
            Class<K> clazz) {
        super(user, permissionsHelper);
        _clazz = clazz;
    }

    @Override
    public boolean isAccessible(E resrep) {
        boolean ret = false;
        URI id = resrep.getId();

        // bypass cache for all the project owned resources
        K obj = _permissionsHelper.getObjectById(id, _clazz, true);
        if (obj == null)
            return false;
        ret = isTenantAccessible(obj.getTenant().getURI());
        if (!ret) {
            NamedURI proj = obj.getProject();
            if (proj != null) {
                ret = isProjectAccessible(proj.getURI());
            }
        }
        return ret;
    }
}
