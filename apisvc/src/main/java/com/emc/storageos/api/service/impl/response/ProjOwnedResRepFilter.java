/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service.impl.response;

import java.net.URI;
import com.emc.storageos.api.service.authorization.PermissionsHelper;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.ProjectResource;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.security.authentication.StorageOSUser;
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
        if (obj == null) {
            return false;
        }
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
