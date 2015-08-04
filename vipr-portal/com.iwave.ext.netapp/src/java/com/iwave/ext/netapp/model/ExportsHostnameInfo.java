/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;

public class ExportsHostnameInfo implements Serializable {

    private static final long serialVersionUID = -6625387015375837227L;

    private Boolean allHosts;
    private String name;
    private Boolean negate;

    public Boolean getAllHosts() {
        return allHosts;
    }

    public void setAllHosts(Boolean allHosts) {
        this.allHosts = allHosts;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getNegate() {
        return negate;
    }

    public void setNegate(Boolean negate) {
        this.negate = negate;
    }

}
