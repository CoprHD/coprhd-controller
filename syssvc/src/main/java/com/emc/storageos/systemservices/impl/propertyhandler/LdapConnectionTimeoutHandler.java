/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.systemservices.impl.property.APINotifier;

public class LdapConnectionTimeoutHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(LdapConnectionTimeoutHandler.class);

    private String _propertyName = Constants.LDAP_CONNECTION_TIMEOUT;

    private APINotifier _apiNotifier;
    public void setApiNotifier(APINotifier apiNotifier) {
        this._apiNotifier = apiNotifier;
    }


    /**
     *
     * @param oldProps
     * @param newProps
     */
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        if (!isPropertyChanged(oldProps, newProps, _propertyName)) {
            return;
        }

        // notify authsvc to reload properties
        _apiNotifier.notifyChangeToAuthsvc();
    }
}
