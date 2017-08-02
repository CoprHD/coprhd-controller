/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl.mds;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * instance of CISCO_ZoneMemberSettingData {

 Caption = null;
 Description = null;
 InstanceID = "3176^2^5006016446E47142^0";
 ElementName = null;
 OtherConnectivityMemberType = null;
 ConnectivityMemberID = "5006016446E47142";
 ConnectivityMemberType = 2;
 };
 */
public class ZoneMember extends ZoneWwnAlias {
    private static final Logger _log = LoggerFactory.getLogger(ZoneMember.class);

    String instanceID;
    String description;
    ConnectivityMemberType type;
    /**
     * marked transient because it cannot be serialized
     */
    // The path to the ZoneMembershipSettingData object for
    // the zone WWN or alias member
    transient Object cimObjectPath = null;
    // The path to the alias object that is a member of a
    // zone when one exists
    transient Object cimAliasPath = null;

    public enum ConnectivityMemberType {
        NONE(0), WWPN(2), FCID(3), SWITCHPORT(4), PORTGROUP(5);
        private int value;

        ConnectivityMemberType(int value) {
            this.value = value;
        }

        static public ConnectivityMemberType byValue(int v) {
            for (ConnectivityMemberType t : values()) {
                if (v == t.value) {
                    return t;
                }
            }
            return NONE;
        }
    };

    public ZoneMember(ConnectivityMemberType type) {
        this.type = type;
    }

    public ZoneMember(String address, ConnectivityMemberType type) {
        this(type);
        setAddress(address);
    }

    public String getLogString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ZoneMember: ");
        if (getAddress() != null) {
            builder.append(getAddress());
        }
        builder.append(" ");
        if (type != null) {
            builder.append("Type: " + type.toString());
        }
        builder.append(" ");
        if (getName() != null) {
            builder.append("Alias: " + getName());
        }
        return builder.toString();
    }

    /**
     * name and alias are interchangeable in zone member.
     * 
     * @return name of zone member
     */
    public String getAlias() {
        return getName();
    }

    public void setAlias(String alias) {
        setName(alias);
    }

    public String getInstanceID() {
        return instanceID;
    }

    public void setInstanceID(String instanceID) {
        this.instanceID = instanceID;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ConnectivityMemberType getType() {
        return type;
    }

    public void setType(ConnectivityMemberType type) {
        this.type = type;
    }

    public Object getCimObjectPath() {
        return cimObjectPath;
    }

    public void setCimObjectPath(Object cimObjectPath) {
        this.cimObjectPath = cimObjectPath;
    }

    public Object getCimAliasPath() {
        return cimAliasPath;
    }

    public void setCimAliasPath(Object cimAliasPath) {
        this.cimAliasPath = cimAliasPath;
    }
}
