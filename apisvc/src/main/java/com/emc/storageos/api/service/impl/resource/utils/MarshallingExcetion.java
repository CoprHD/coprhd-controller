/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.api.service.impl.resource.utils;

/**
 * Internal exception when db data is being marshaled and failed.
 *
 */
public class MarshallingExcetion extends Exception {


    private static final long serialVersionUID = 1L;

    public MarshallingExcetion(String string, Exception e) {
        super(string, e);
    }
}
