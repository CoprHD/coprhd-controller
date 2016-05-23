/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.util;


import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.mail.MailHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class MailHandler {

    private static final Logger log = LoggerFactory.getLogger(MailHandler.class);

    @Autowired
    private DbClient dbClient;

    @Autowired
    private AuditLogManager auditLogManager;

    @Autowired
    private CoordinatorClient coordinator;

    private MailHelper mailHelper;

    /**
     * Sends mail alert that site network is broken.
     * 
     * @param site the site with broken network
     */
    public void sendSiteNetworkBrokenMail(Site site) {
        String to = getMailAddressOfUser("root");
        if (to == null || to.isEmpty()) {
            log.warn("Can't send mail alert, no email address for root user");

            // audit the mail sent failure
            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_FAILURE,
                    null, site.getName());
            return;
        }

        Map<String, String> parameters = Maps.newHashMap();
        parameters.put("standbyName", site.getName());

        String title = String.format("ATTENTION - %s network is broken",
                site.getName());
        String content = MailHelper.readTemplate("StandbySiteBroken.html");
        content = MailHelper.parseTemplate(parameters, content);
        if (getMailHelper().sendMailMessage(to, title, content)) {
            // audit the mail sent success
            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS,
                    null, site.getName());
        } else {
            // audit the mail sent failure
            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_FAILURE,
                    null, site.getName());
        }
    }

    /**
     * Send alert mail that standby site is marked as STANDBY_DEGRADED
     * @param siteName name of site that is degraded
     * @param degradeTimeStamp time the site is marked as degraded
     */
    public void sendSiteDegradedMail(String siteName, long degradeTimeStamp) {
        String degradeTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(degradeTimeStamp));
        String to = getMailAddressOfUser("root");
        if (to == null || to.isEmpty()) {
            log.warn("Can't send mail alert, no email address for root user");

            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_DEGRADED_MAIL,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_FAILURE,
                    null,
                    siteName,
                    degradeTime);
            return;
        }

        Map<String, String> params = Maps.newHashMap();
        params.put("siteName", siteName);
        params.put("degradeTime", String.format("%s (TimeZone: %s)", degradeTime, TimeZone.getDefault().getID()));
        String title = String.format("ATTENTION - %s site has been marked as STANDBY_DEGRADED state", siteName);
        String content = MailHelper.readTemplate("StandbySiteDegraded.html");
        content = MailHelper.parseTemplate(params, content);
        if (getMailHelper().sendMailMessage(to, title, content)) {
            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_DEGRADED_MAIL,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS,
                    null,
                    siteName,
                    degradeTime,
                    to);
        } else {
            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_DEGRADED_MAIL,
                    System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_FAILURE,
                    null,
                    siteName,
                    degradeTime);
        }
    }

    private MailHelper getMailHelper() {
        if (mailHelper == null) {
            mailHelper = new MailHelper(coordinator);
        }

        return mailHelper;
    }

    /**
     * get user's mail address from UserPreference CF
     *
     * @param userName
     * @return
     */
    private String getMailAddressOfUser(String userName) {

        DataObjectType doType = TypeMap.getDoType(UserPreferences.class);
        AlternateIdConstraint constraint = new AlternateIdConstraintImpl(
                doType.getColumnField(UserPreferences.USER_ID), userName);
        NamedElementQueryResultList queryResults = new NamedElementQueryResultList();
        this.dbClient.queryByConstraint(constraint, queryResults);

        List<URI> userPrefsIds = new ArrayList<>();
        for (NamedElementQueryResultList.NamedElement namedElement : queryResults) {
            userPrefsIds.add(namedElement.getId());
        }
        if (userPrefsIds.isEmpty()) {
            return null;
        }

        final List<UserPreferences> userPrefs = new ArrayList<>();
        Iterator<UserPreferences> iter = this.dbClient.queryIterativeObjects(UserPreferences.class, userPrefsIds);
        while (iter.hasNext()) {
            userPrefs.add(iter.next());
        }

        if (userPrefs.size() > 1) {
            throw new IllegalStateException("There should only be 1 user preferences object for a user");
        }
        if (userPrefs.isEmpty()) {
            // if there isn't a user prefs object in the DB yet then we haven't saved one for this user yet.
            return null;
        }

        return userPrefs.get(0).getEmail();
    }

}
