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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MailHandler {

    private static final Logger _log = LoggerFactory.getLogger(MailHandler.class);

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
     * @return true if mail sends successfully
     */
    public void sendSiteNetworkBrokenMail(Site site) {
        String to = getMailAddressOfUser("root");
        if (to == null || to.isEmpty()) {
            _log.warn("Can't send mail alert, no email address for root user");

            // audit the mail sent fail
            auditLogManager.recordAuditLog(
                    null, null, "syssvc",
                    OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                    new Date().getTime(),
                    AuditLogManager.AUDITLOG_FAILURE,
                    null, site.getName());
        }

        Map parameters = Maps.newHashMap();
        parameters.put("standbyName", site.getName());

        String title = String.format("ATTENTION - %s network is broken",
                site.getName());
        String content = MailHelper.readTemplate("StandbySiteBroken.html");
        content = MailHelper.parseTemplate(parameters, content);
        getMailHelper().sendMailMessage(to, title, content);

        // audit the mail sent success
        auditLogManager.recordAuditLog(
                null, null, "syssvc",
                OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                new Date().getTime(),
                AuditLogManager.AUDITLOG_SUCCESS,
                null, site.getName());
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
