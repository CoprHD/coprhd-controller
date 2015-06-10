/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.model;

import java.io.Serializable;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.lang.builder.ToStringStyle;
import org.apache.commons.lang.math.NumberUtils;

public class IScsiTarget implements Serializable {
    private static final long serialVersionUID = 5790949345532206626L;
    /** The target's IQN. */
    private String iqn;
    /** The target portal (IP:port,targetPortalTagGroup) . */
    private String portal;
    /** The interface name. */
    private String ifaceName;

    public IScsiTarget() {
    }

    public IScsiTarget(String iqn, String portal, String ifaceName) {
        this.iqn = iqn;
        this.portal = portal;
        this.ifaceName = ifaceName;
    }

    public String getIqn() {
        return iqn;
    }

    public void setIqn(String iqn) {
        this.iqn = iqn;
    }

    public String getPortal() {
        return portal;
    }

    public void setPortal(String portal) {
        this.portal = portal;
    }

    public String getPortalIp() {
        return StringUtils.substringBefore(portal, ":");
    }

    public Integer getPortalPort() {
        String port = StringUtils.substringBetween(portal, ":", ",");
        if (StringUtils.isNotBlank(port)) {
            return NumberUtils.toInt(port);
        }
        return null;
    }

    public String getPortalGroupTag() {
        return StringUtils.substringAfter(portal, ",");
    }

    public String getIfaceName() {
        return ifaceName;
    }

    public void setIfaceName(String ifaceName) {
        this.ifaceName = ifaceName;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof IScsiTarget)) {
            return false;
        }

        IScsiTarget target = (IScsiTarget) obj;
        EqualsBuilder builder = new EqualsBuilder();
        builder.append(iqn, target.iqn);
        builder.append(portal, target.portal);
        builder.append(ifaceName, target.ifaceName);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(iqn);
        builder.append(portal);
        builder.append(ifaceName);
        return builder.toHashCode();
    }

    @Override
    public String toString() {
        return toString(ToStringStyle.SHORT_PREFIX_STYLE);
    }

    public String toString(ToStringStyle style) {
        ToStringBuilder builder = new ToStringBuilder(this, style);
        builder.append("iqn", iqn);
        builder.append("portal", portal);
        builder.append("ifaceName", ifaceName);
        return builder.toString();
    }
}
