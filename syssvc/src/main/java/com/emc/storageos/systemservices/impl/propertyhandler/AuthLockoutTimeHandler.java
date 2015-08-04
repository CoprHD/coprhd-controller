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

package com.emc.storageos.systemservices.impl.propertyhandler;


import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.InvalidLoginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthLockoutTimeHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(AuthLockoutTimeHandler.class);

    private String _propertyName = Constants.AUTH_LOGOUT_TIMEOUT;

    private InvalidLoginManager _invalidLoginManager;

    /**
     * setter for InvalidLoginManager
     *
     * @param invalidLoginManager
     */
    public void setInvalidLoginManager(InvalidLoginManager invalidLoginManager) {
        this._invalidLoginManager = invalidLoginManager;
    }

    /**
     * check if the value is between range [0, 20], if not fail the property update.
     *
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        if (!isProprotyChanged(oldProps, newProps, _propertyName)) {
            return;
        }

        String newValue = newProps.getProperty(_propertyName);
        ArgumentValidator.checkRange(
                Integer.parseInt(newValue),
                Constants.MIN_AUTH_LOCKOUT_TIME_IN_MINUTES,
                Constants.MAX_AUTH_LOCKOUT_TIME_IN_MINUTES,
                _propertyName);
    }

    /**
     *
     * @param oldProps
     * @param newProps
     */
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        if (!isProprotyChanged(oldProps, newProps, _propertyName)) {
            return;
        }

        // reload invalidLoginManage in syssvc, clear block-ip list.
        _invalidLoginManager.loadParameterFromZK();
        _invalidLoginManager.invLoginCleanup(true);
    }
}
