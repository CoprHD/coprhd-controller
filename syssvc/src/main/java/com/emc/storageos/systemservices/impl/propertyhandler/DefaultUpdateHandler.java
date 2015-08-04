package com.emc.storageos.systemservices.impl.propertyhandler;

import com.emc.storageos.model.property.PropertyInfoRestRep;
import com.emc.storageos.security.password.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DefaultUpdateHandler implements UpdateHandler {

    private static final Logger _log = LoggerFactory.getLogger(DefaultUpdateHandler.class);

    @Override
    public void before(PropertyInfoRestRep oldValue, PropertyInfoRestRep newValue) {
        // empty implementation
    }

    @Override
    public void after(PropertyInfoRestRep oldValue, PropertyInfoRestRep newValue) {
        // empty implementation
    }

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
