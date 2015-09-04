/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.networkcontroller.impl.mds;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.util.EndpointUtility;
import com.emc.storageos.model.network.WwnAliasParam;
import com.emc.storageos.model.valid.Endpoint.EndpointType;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

public class ZoneWwnAlias extends WwnAliasParam {
    private static final Logger _log = LoggerFactory.getLogger(ZoneWwnAlias.class);

    // indicate zone member is of type alias.
    private boolean aliasType;

    public ZoneWwnAlias() {
    }

    public ZoneWwnAlias(String name, String address) {
        super(name, address);
    }

    @Override
    public void setAddress(String address) {
        if (StringUtils.isEmpty(address))
            return;

        if (EndpointUtility.isValidEndpoint(address, EndpointType.WWN)) {
            super.setAddress(EndpointUtility.changeCase(address));
        } else {
            throw APIException.badRequests.illegalWWN(address);
        }
    }

    public void print() {
        _log.info("WWN Alias: " + getName() + " Address: " + getAddress());
    }

    public boolean hasAlias() {
        return !StringUtils.isEmpty(getName());
    }

    @XmlTransient
    public boolean isAliasType() {
        return aliasType;
    }

    public void setAliasType(boolean isAlias) {
        this.aliasType = isAlias;
    }
}
