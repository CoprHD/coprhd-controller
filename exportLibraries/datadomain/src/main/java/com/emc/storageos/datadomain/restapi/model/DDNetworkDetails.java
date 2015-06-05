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

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.annotate.JsonRootName;

/**
 * Created by zeldib on 2/10/14.
 */
@JsonRootName(value="network_detail")
public class DDNetworkDetails {

    private String id;

    private String name;

    private boolean enabled;

    @SerializedName("address")
    private String ip;

    private String netmask;

    private boolean dhcp;

    private boolean booting;

    private boolean virtual;

    private boolean primary;

    private int mtu;

    @SerializedName("link_speed")
    private int linkSpeed;

    @SerializedName("link_duplex")
    private int linkDuplex;

    @SerializedName("link_auto_nego")
    private boolean linkAutoNego;

    @SerializedName("master_id")
    private String masterId;

    private int updelay;

    private int downdelay;

    private int rate;

    private int txqueuelen;


    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public boolean getEnabled() {
        return enabled;
    }
    public void setName(boolean enabled) {
        this.enabled = enabled;
    }

    @JsonProperty(value="address")
    public String getIp(){
        return ip;
    }
    public void setIp(String ip){
        this.ip = ip;
    }

    public String getNetmask() {
        return netmask;
    }
    public void setNetmask(String netmask) {
        this.netmask = netmask;
    }

    public boolean getDhcp() {
        return dhcp;
    }
    public void setDhcp(boolean dhcp) {
        this.dhcp = dhcp;
    }

    public boolean getBooting() {
        return booting;
    }
    public void setBooting(boolean booting) {
        this.booting = booting;
    }

    public boolean getVirtual() {
        return virtual;
    }
    public void setVirtual(boolean virtual) {
        this.virtual = virtual;
    }

    public boolean getPrimary() {
        return primary;
    }
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public int getMtu(){
        return mtu;
    }
    public void setMtu(int mtu){
        this.mtu = mtu;
    }

    @JsonProperty(value="link_speed")
    public int getLinkSpeed(){
        return linkSpeed;
    }
    public void setLinkSpeed(int speed){
        this.linkSpeed = speed;
    }

    @JsonProperty(value="link_duplex")
    public int getLinkDuplex() {
        return linkDuplex;
    }
    public void setLinkDuplex(int linkDuplex) {
        this.linkDuplex = linkDuplex;
    }

    @JsonProperty(value="link_auto_nego")
    public boolean getLinkAutoNego(){
        return linkAutoNego;
    }
    public void setLinkAutoNego(boolean lan){
        this.linkAutoNego = lan;
    }

    @JsonProperty(value="master_id")
    private String getMasterId() {
        return masterId;
    }
    public void setMasterId(String masterId) {
        this.masterId = masterId;
    }

    public int getUpdelay(){
        return updelay;
    }
    public void setUpdelay(int delay){
        this.updelay = delay;
    }

    public int getDowndelay(){
        return downdelay;
    }
    public void setDowndelay(int delay){
        this.downdelay = delay;
    }

    public int getRate(){
        return rate;
    }
    public void setRate(int rate){
        this.rate = rate;
    }

    public int getTxqueuelen(){
        return txqueuelen;
    }
    public void setTxqueuelen(int txqueuelen){
        this.txqueuelen = txqueuelen;
    }

    public String toString() {
        return new Gson().toJson(this).toString();
    }
}
