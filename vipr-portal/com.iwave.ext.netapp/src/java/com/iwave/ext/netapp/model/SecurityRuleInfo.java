/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.Lists;

public class SecurityRuleInfo implements Serializable {

    private static final long serialVersionUID = -566181961050444064L;

    private String anon;
    private Boolean nosuid;
    private String secFlavor;
    private List<ExportsHostnameInfo> readOnly = Lists.newArrayList();
    private List<ExportsHostnameInfo> readWrite = Lists.newArrayList();
    private List<ExportsHostnameInfo> root = Lists.newArrayList();

    public String getAnon() {
        return anon;
    }

    public void setAnon(String anon) {
        this.anon = anon;
    }

    public Boolean getNosuid() {
        return nosuid;
    }

    public void setNosuid(Boolean nosuid) {
        this.nosuid = nosuid;
    }

    public String getSecFlavor() {
        return secFlavor;
    }

    public void setSecFlavor(String secFlavor) {
        this.secFlavor = secFlavor;
    }

    public List<ExportsHostnameInfo> getReadOnly() {
        return readOnly;
    }

    public void setReadOnly(List<ExportsHostnameInfo> readOnly) {
        this.readOnly = readOnly;
    }

    public List<ExportsHostnameInfo> getReadWrite() {
        return readWrite;
    }

    public void setReadWrite(List<ExportsHostnameInfo> readWrite) {
        this.readWrite = readWrite;
    }

    public List<ExportsHostnameInfo> getRoot() {
        return root;
    }

    public void setRoot(List<ExportsHostnameInfo> root) {
        this.root = root;
    }

}
