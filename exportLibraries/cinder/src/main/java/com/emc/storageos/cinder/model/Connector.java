/* Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.cinder.model;

import java.util.Arrays;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "connector")
public class Connector
{
    /** to be filled in for iSCSI attach */
    public String initiator;
    /** to be filled in for FC attach */
    public String[] wwpns;
    public String host;
    public String ip;

    public String getInitiator() {
        return initiator;
    }

    public void setInitiator(String initiator) {
        this.initiator = initiator;
    }

    public String[] getWwpns() {
        return wwpns;
    }

    public void setWwpns(String[] wwpns) {
        this.wwpns = wwpns;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Connector [initiator=");
        builder.append(initiator);
        builder.append(", wwpns=");
        builder.append(Arrays.toString(wwpns));
        builder.append(", host=");
        builder.append(host);
        builder.append(", ip=");
        builder.append(ip);
        builder.append("]");
        return builder.toString();
    }

}
