/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
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

import java.util.ArrayList;
import java.util.List;

public class PropertyHandlers {

    private List<UpdateHandler> handlers = new ArrayList<UpdateHandler>();

    public void setHandlers(List<UpdateHandler> handlers) {
        this.handlers = handlers;
    }

    public List<UpdateHandler> getHandlers() {
        return handlers;
    }

    /**
     * get called before any system properties updated
     *
     * @param oldProps
     * @param newProps
     */
    public void before(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        for (UpdateHandler handler : handlers) {
            handler.before(oldProps, newProps);
        }
    }

    /**
     * get called after system properties updated
     *
     * @param oldProps
     * @param newProps
     */
    public void after(PropertyInfoRestRep oldProps, PropertyInfoRestRep newProps) {
        for (UpdateHandler handler : handlers) {
            handler.after(oldProps, newProps);
        }
    }
}
