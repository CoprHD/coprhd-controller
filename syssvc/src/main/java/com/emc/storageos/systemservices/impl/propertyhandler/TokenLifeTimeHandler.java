/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import com.emc.storageos.security.password.InvalidLoginManager;
import com.emc.storageos.systemservices.impl.property.APINotifier;

public class TokenLifeTimeHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(TokenLifeTimeHandler.class);

    private String _propertyName = Constants.TOKEN_LIFE_TIME;

    /**
     * check if the value is between range [5, 1440], if not fail the property update.
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
                Constants.MIN_TOKEN_LIFE_TIME,
                Constants.MAX_TOKEN_LIFE_TIME,
                _propertyName);
    }
}
