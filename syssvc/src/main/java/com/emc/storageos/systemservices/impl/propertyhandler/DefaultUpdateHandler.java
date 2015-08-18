/**
 *  Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.PropertyInfoRestRep;

public class DefaultUpdateHandler implements UpdateHandler {

    @Override
    public void before(PropertyInfoRestRep oldValue, PropertyInfoRestRep newValue) {
        // empty implementation
    }

    @Override
    public void after(PropertyInfoRestRep oldValue, PropertyInfoRestRep newValue) {
        // empty implementation
    }

    /**
     * check if the value of specified property get changed.
     *
     * @param oldProps
     * @param newProps
     * @param property
     * @return
     */
    public boolean isPropertyChanged(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps, String property) {
        String oldValue = oldProps.getProperty(property);
        String newValue = newProps.getProperty(property);

        if (newValue == null) {
            return false;
        }

        if (oldValue == null) {
            oldValue = "0";
        }

        if (oldValue.equals(newValue)) {
            return false;
        }

        return true;
    }
}
