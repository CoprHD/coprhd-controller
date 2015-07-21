/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.response;

import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Project;
import com.emc.storageos.db.client.model.ProjectResourceSnapshot;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.security.authentication.StorageOSUser;

import java.net.URI;

/**
 * Filter for snapshot resources
 */
public class ProjOwnedSnapResRepFilter <E extends RelatedResourceRep, K extends DataObject&ProjectResourceSnapshot>
        extends ResRepFilter<E> {
    Class<K> _clazz = null;

    public ProjOwnedSnapResRepFilter(StorageOSUser user,
                                 PermissionsHelper permissionsHelper,
                                 Class<K> clazz) {
        super(user, permissionsHelper);
        _clazz = clazz;
    }

    @Override
    public boolean isAccessible(E resrep) {
        boolean ret = false;
        URI id = resrep.getId();
        // bypass cache for all the project owned snapshots
        K obj = _permissionsHelper.getObjectById(id, _clazz, true);
        if (obj == null || obj.getProject() == null)
            return false;

        ret = isProjectAccessible(obj.getProject().getURI());
        if (!ret) {
            Project project =  _permissionsHelper.getObjectById(obj.getProject(), Project.class);
            ret = isTenantAccessible(project.getTenantOrg().getURI());
        }
        return ret;
    }
}
