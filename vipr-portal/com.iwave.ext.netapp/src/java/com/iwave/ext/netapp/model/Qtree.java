/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netapp.model;

import java.io.Serializable;

public class Qtree implements Serializable {

    private Integer id;
    private String oplocks;
    private String owningVfiler;
    private String qtree;
    private String securityStyle;
    private String status;
    private String volume;
    
    public Integer getId() {
        return id;
    }
    public void setId(Integer id) {
        this.id = id;
    }
    public String getOplocks() {
        return oplocks;
    }
    public void setOplocks(String oplocks) {
        this.oplocks = oplocks;
    }
    public String getOwningVfiler() {
        return owningVfiler;
    }
    public void setOwningVfiler(String owningVfiler) {
        this.owningVfiler = owningVfiler;
    }
    public String getQtree() {
        return qtree;
    }
    public void setQtree(String qtree) {
        this.qtree = qtree;
    }
    public String getSecurityStyle() {
        return securityStyle;
    }
    public void setSecurityStyle(String securityStyle) {
        this.securityStyle = securityStyle;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }
    public String getVolume() {
        return volume;
    }
    public void setVolume(String volume) {
        this.volume = volume;
    }

}
