/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;
import java.util.List;

import com.google.common.collect.Lists;

public class ExportsRuleInfo implements Serializable {

    private static final long serialVersionUID = 7422578081857162870L;
    
    private String actualPathname;
    private String pathname;
    private List<SecurityRuleInfo> securityRuleInfos = Lists.newArrayList();
    
    public String getActualPathname() {
        return actualPathname;
    }
    public void setActualPathname(String actualPathname) {
        this.actualPathname = actualPathname;
    }
    public String getPathname() {
        return pathname;
    }
    public void setPathname(String pathname) {
        this.pathname = pathname;
    }
    public List<SecurityRuleInfo> getSecurityRuleInfos() {
        return securityRuleInfos;
    }
    public void setSecurityRuleInfos(List<SecurityRuleInfo> securityRuleInfos) {
        this.securityRuleInfos = securityRuleInfos;
    }

}
