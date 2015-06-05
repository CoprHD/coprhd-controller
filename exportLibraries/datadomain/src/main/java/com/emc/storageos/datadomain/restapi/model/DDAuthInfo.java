/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.datadomain.restapi.model;

import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonTypeName;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/4/14.
 */


@JsonRootName(value="auth_info")
public class DDAuthInfo {

    private String username;
    private String password;

    String getUsername(){
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    String getPassword(){
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

}
