/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;

public class TokenIdleTimeHandler extends DefaultUpdateHandler {
    private static final Logger _log = LoggerFactory.getLogger(TokenIdleTimeHandler.class);

    private String _propertyName = Constants.TOKEN_IDLE_TIME;

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
                Constants.MIN_TOKEN_IDLE_TIME,
                Constants.MAX_TOKEN_IDLE_TIME,
                _propertyName);
    }
}
