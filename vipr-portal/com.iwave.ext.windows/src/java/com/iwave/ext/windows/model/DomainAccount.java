/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.model;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlRootElement;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;

/**
 * Represents a Domain Account that can be used for administration purposes like
 * editing share permissions.
 * 
 * @author Chris Dail
 */
@XmlRootElement
public class DomainAccount implements Serializable {
    private static final long serialVersionUID = 6760164250380429116L;

    private String label;
    private String domainName;
    private String domainControllerHost;
    private String username;
    private String password;
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getDomainName() {
        return domainName;
    }
    
    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }
    
    public String getDomainControllerHost() {
        return domainControllerHost;
    }
    
    public void setDomainControllerHost(String domainControllerHost) {
        this.domainControllerHost = domainControllerHost;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE);
        builder.append("label", label);
        builder.append("domainName", domainName);
        builder.append("domainControllerHost", domainControllerHost);
        builder.append("username", username);
        return builder.toString();
    }
}
