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
    public boolean isProprotyChanged(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps, String property) {
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
