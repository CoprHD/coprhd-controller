/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class PortMembers {
    private Position portPos;
    private Integer mode;
    private Integer linkState;
    private String portWWN;
    private Integer type;
    private Integer protocol;
    private Position partnerPos;
    private ArrayList<String> device;
    private String label;
    private String IPAddr;
    private String iSCSINmae;
    private ISCSIInfo iSCSIPortInfo;
    
    public Position getPortPos() {
        return portPos;
    }
    public void setPortPos(Position portPos) {
        this.portPos = portPos;
    }
    public Integer getMode() {
        return mode;
    }
    public void setMode(Integer mode) {
        this.mode = mode;
    }
    public Integer getLinkState() {
        return linkState;
    }
    public void setLinkState(Integer linkState) {
        this.linkState = linkState;
    }
    public String getPortWWN() {
        return portWWN;
    }
    public void setPortWWN(String portWWN) {
        this.portWWN = portWWN;
    }
    public Integer getType() {
        return type;
    }
    public void setType(Integer type) {
        this.type = type;
    }
    public Integer getProtocol() {
        return protocol;
    }
    public void setProtocol(Integer protocol) {
        this.protocol = protocol;
    }
    public Position getPartnerPos() {
        return partnerPos;
    }
    public void setPartnerPos(Position partnerPos) {
        this.partnerPos = partnerPos;
    }
    public ArrayList<String> getDevice() {
        return device;
    }
    public void setDevice(ArrayList<String> device) {
        this.device = device;
    }
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }
    public String getIPAddr() {
        return IPAddr;
    }
    public void setIPAddr(String iPAddr) {
        IPAddr = iPAddr;
    }
    public String getiSCSINmae() {
        return iSCSINmae;
    }
    public void setiSCSINmae(String iSCSINmae) {
        this.iSCSINmae = iSCSINmae;
    }
    public ISCSIInfo getiSCSIPortInfo() {
        return iSCSIPortInfo;
    }
    public void setiSCSIPortInfo(ISCSIInfo iSCSIPortInfo) {
        this.iSCSIPortInfo = iSCSIPortInfo;
    }    
}
