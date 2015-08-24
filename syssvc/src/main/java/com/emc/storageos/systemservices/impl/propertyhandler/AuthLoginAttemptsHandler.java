/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;


import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.systemservices.impl.property.APINotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthLoginAttemptsHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(AuthLoginAttemptsHandler.class);

    private String _propertyName = Constants.AUTH_LOGIN_ATTEMPTS;

    private InvalidLoginManager _invalidLoginManager;

    /**
     * setter for InvalidLoginManager
     *
     * @param invalidLoginManager
     */
    public void setInvalidLoginManager(InvalidLoginManager invalidLoginManager) {
        this._invalidLoginManager = invalidLoginManager;
    }

    private APINotifier _apiNotifier;

    public void setApiNotifier(APINotifier apiNotifier) {
        this._apiNotifier = apiNotifier;
    }

    /**
     * check if the value is between range [0, 20], if not fail the property update.
     *
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        if (!isPropertyChanged(oldProps, newProps, _propertyName)) {
            return;
        }

        String newValue = newProps.getProperty(_propertyName);
        ArgumentValidator.checkRange(
                Integer.parseInt(newValue),
                Constants.MIN_AUTH_LOGIN_ATTEMPTS,
                Constants.MAX_AUTH_LOGIN_ATTEMPTS,
                _propertyName);
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

        // reload invalidLoginManage in syssvc, clear block-ip list.
        _invalidLoginManager.loadParameterFromZK();
        _invalidLoginManager.invLoginCleanup(true);

        // notify authsvc to reload properties
        _apiNotifier.notifyChangeToAuthsvc();
    }
}
