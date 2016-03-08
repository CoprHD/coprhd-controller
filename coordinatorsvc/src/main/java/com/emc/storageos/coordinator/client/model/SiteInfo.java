/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.coordinator.client.model;

import java.util.HashMap;
import java.util.Map;

import com.emc.storageos.coordinator.exceptions.CoordinatorException;
import com.emc.storageos.coordinator.exceptions.DecodingException;
import com.emc.storageos.services.util.Strings;

/**
 * This class stores only the hash for the vdc config for now
 * The complete vdc configuration is found in the local db
 * We are simply creating a target here for VdcManager to watch.
 */
public class SiteInfo implements CoordinatorSerializable {
    public static final String CONFIG_KIND = "sitetargetconfig";
    public static final String CONFIG_ID = "global";
    
    public static final String IPSEC_OP_ROTATE_KEY = "ipsec_rotate_key";
    public static final String IPSEC_OP_ENABLE_INIT = "ipsec_enable_init";
    public static final String DR_OP_ADD_STANDBY = "dr_add_standby";
    public static final String DR_OP_REMOVE_STANDBY = "dr_remove_standby";
    public static final String DR_OP_PAUSE_STANDBY = "dr_pause_standby";
    public static final String DR_OP_RESUME_STANDBY = "dr_resume_standby";
    public static final String DR_OP_DEGRADE_STANDBY = "dr_degrade_standby";
    public static final String DR_OP_REJOIN_STANDBY = "dr_rejoin_standby";
    public static final String DR_OP_SWITCHOVER = "dr_switchover";
    public static final String DR_OP_FAILOVER = "dr_failover";
    public static final String DR_OP_FAILBACK_DEGRADE = "dr_failbackDegrade";
    public static final String DR_OP_CHANGE_DATA_REVISION = "dr_change_data_revision";
    public static final String DR_OP_PURGE_DATA_REVISION = "dr_purge_data_revision";
    public static final String IP_OP_CHANGE = "ip_change";
    public static final String GEO_OP_CONFIG_CHANGE = "geo_config_change";
    public static final String NONE = "noop";

    /**
     *  Action Scope represents if an action involves nodes of the entire VDC or just ones of local site.
     */
    public enum ActionScope {
        VDC,
        SITE
    };

    public static final String DEFAULT_TARGET_VERSION="0"; // No target data revision set

    private static final String ENCODING_SEPARATOR = "\0";

    private final long vdcConfigVersion;
    private final String actionRequired;
    private final String targetDataRevision;
    private final ActionScope actionScope;
    private final String sourceSiteUUID;
    private final String targetSiteUUID;
    
    public SiteInfo() {
        vdcConfigVersion = 0;
        actionRequired = NONE;
        targetDataRevision = DEFAULT_TARGET_VERSION;
        actionScope = ActionScope.SITE;
        sourceSiteUUID = "";
        targetSiteUUID = "";
    }

    public SiteInfo(final long version, final String actionRequired) {
        this(version, actionRequired, DEFAULT_TARGET_VERSION);
    }

    public SiteInfo(final long version, final String actionRequired, final String targetDataRevision) {
        this(version, actionRequired, targetDataRevision, ActionScope.SITE);
    }

    public SiteInfo(final long version, final String actionRequired, final ActionScope vdc) {
        this(version, actionRequired, DEFAULT_TARGET_VERSION, ActionScope.SITE);
    }

    public SiteInfo(final long version, final String actionRequired, final String targetDataRevision, final ActionScope scope) {
        this(version, actionRequired, targetDataRevision, scope, "", "");
    }
    
    public SiteInfo(final long version, final String actionRequired, final String targetDataRevision, final ActionScope scope, 
            final String sourceSiteUUID, final String targetSiteUUID) {
        this.vdcConfigVersion = version;
        this.actionRequired = actionRequired;
        this.targetDataRevision = targetDataRevision;
        this.actionScope = scope;
        this.sourceSiteUUID = sourceSiteUUID == null ? "" : sourceSiteUUID;
        this.targetSiteUUID = targetSiteUUID == null ? "" : targetSiteUUID;
    }

    public long getVdcConfigVersion() {
        return vdcConfigVersion;
    }

    public String getActionRequired() {
        return actionRequired;
    }
    
    public String getTargetDataRevision() {
        return targetDataRevision;
    }

    public ActionScope getActionScope() {
        return actionScope;
    }
    public boolean isNullTargetDataRevision() {
        return SiteInfo.DEFAULT_TARGET_VERSION.equals(targetDataRevision);
    }

    public String getSourceSiteUUID() {
        return sourceSiteUUID;
    }

    public String getTargetSiteUUID() {
        return targetSiteUUID;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("SiteInfo [vdcConfigVersion=");
        builder.append(vdcConfigVersion);
        builder.append(", actionRequired=");
        builder.append(actionRequired);
        builder.append(", targetDataRevision=");
        builder.append(targetDataRevision);
        builder.append(", actionScope=");
        builder.append(actionScope);
        builder.append(", sourceSiteUUID=");
        builder.append(sourceSiteUUID);
        builder.append(", targetSiteUUID=");
        builder.append(targetSiteUUID);
        builder.append("]");
        return builder.toString();
    }

    @Override
    public String encodeAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append(vdcConfigVersion);
        sb.append(ENCODING_SEPARATOR);
        sb.append(actionRequired);
        sb.append(ENCODING_SEPARATOR);
        sb.append(targetDataRevision);
        sb.append(ENCODING_SEPARATOR);
        sb.append(actionScope);
        sb.append(ENCODING_SEPARATOR);
        sb.append(sourceSiteUUID);
        sb.append(ENCODING_SEPARATOR);
        sb.append(targetSiteUUID);
        return sb.toString();
    }

    @Override
    public SiteInfo decodeFromString(String infoStr) throws DecodingException {
        if (infoStr == null) {
            return null;
        }

        final String[] strings = infoStr.split(ENCODING_SEPARATOR);
        if (strings.length < 4) {
            throw CoordinatorException.fatals.decodingError("invalid site info");
        }

        Long hash = Long.valueOf(strings[0]);
        if (strings.length == 4) {
            return new SiteInfo(hash, strings[1], strings[2], ActionScope.valueOf(strings[3]));
        } else {
            return new SiteInfo(hash, strings[1], strings[2], ActionScope.valueOf(strings[3]), strings[4], strings[5]);
        }
    }

    @Override
    public CoordinatorClassInfo getCoordinatorClassInfo() {
        return new CoordinatorClassInfo(CONFIG_ID, CONFIG_KIND, "siteInfo");
    }
}
