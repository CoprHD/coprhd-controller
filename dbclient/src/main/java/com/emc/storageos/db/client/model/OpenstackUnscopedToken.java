/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

@Cf("OpenstackUnscopedToken")
public class OpenstackUnscopedToken extends OpenstackToken {
    // encrypted scoped token
    private String _scopedToken;

    public OpenstackUnscopedToken() {
        super();
    }

    @Name("scopedToken")
    public String getScopedToken() {
        return _scopedToken;
    }

    public void setScopedToken(String token) {
        if (token == null) {
            token = "";
        }
        _scopedToken = token;
        setChanged("scopedToken");
    }
}
