/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.security.password;


import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.NamedElementQueryResultList;
import com.emc.storageos.db.client.constraint.impl.AlternateIdConstraintImpl;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.PasswordHistory;
import com.emc.storageos.db.client.model.UserPreferences;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.security.authentication.StorageOSUser;
import com.emc.storageos.security.mail.MailHelper;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.services.util.AlertsLogger;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * class to schedule a background thread to scan all localuser's password
 * expire time once per day to see if need to send notification mail to
 * root about the to be expired password.
 */
public class NotificationManager {

    private static final Logger _log = LoggerFactory.getLogger(NotificationManager.class);
    private static final AlertsLogger _alertsLog = AlertsLogger.getAlertsLogger();
    private final ScheduledExecutorService _scheduler = Executors.newScheduledThreadPool(1);

    private static CoordinatorClient _coordinator;
    
    public synchronized void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    private DbClient _dbClient;
    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    private Map<String, StorageOSUser> _localUsers;
    public void setLocalUsers(Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    private PasswordUtils _passwordUtils;
    public void setPasswordUtils(PasswordUtils passwordUtils) {
        _passwordUtils = passwordUtils;
    }

    private MailHelper mailHelper;

    private AuditLogManager _auditLogManager;
    public void setAuditLogManager(AuditLogManager auditLogManager) {
        _auditLogManager = auditLogManager;
    }

    /**
     * initialize a scheduler to run Notification Thread once per day at 3:00 am.
     */
    public void init() {
        int OneDayMinutes = 24 * 60;
        _scheduler.scheduleAtFixedRate(
                new PasswordExpireMailNotifier(),
                getFirstDelayInMin(),
                OneDayMinutes,
                TimeUnit.MINUTES);
    }

    /**
     * scan all local user's password expire time, send notification mail if a password is
     * to be expired.
     */
    private class PasswordExpireMailNotifier implements Runnable {
        final static String PASSWORD_EXPIRE_MAIL_LOCK = "password_expire_notifier_lock"; // NOSONAR ("Suppressing: removing this hard-coded password since it's just the name of attribute")

        @Override
        public void run() {
            InterProcessLock lock = null;
            try {
                _log.info("Starting password_expire_notifier ...");
                lock = _coordinator.getLock(PASSWORD_EXPIRE_MAIL_LOCK);
                lock.acquire();
                _log.info("Got password_expire_notifier_lock ...");

                // check if global expire rule enabled.
                String configExpireDays = _passwordUtils.getConfigProperty(Constants.PASSWORD_EXPIRE_DAYS);
                if (configExpireDays == null || Integer.parseInt(configExpireDays) == 0) {
                    return;
                }

                Calendar now = Calendar.getInstance();
                Calendar gracePoint = Calendar.getInstance();
                gracePoint.add(Calendar.DATE, Constants.GRACE_DAYS);
                _log.info("now: " + now.getTime());
                _log.info("grace point: " + gracePoint.getTime());

                // loop through local users
                for (String user : _localUsers.keySet()) {
                    _log.info("checking for user: " + user);
                    PasswordHistory ph = _passwordUtils.getPasswordHistory(user);
                    Calendar expireDate = ph.getExpireDate();
                    _log.info("expire time: " + expireDate.getTime());
                    if (expireDate == null || expireDate.before(now) || expireDate.after(gracePoint)) {
                        _log.info("password not in grace period, skip mail");
                        continue;
                    }

                    Calendar lastMailSendDate = ph.getLastNotificationMailSent();
                    if (lastMailSendDate != null &&
                            lastMailSendDate.get(Calendar.DATE) == now.get(Calendar.DATE)) {
                        _log.info("last mail send date: " + lastMailSendDate.getTime());
                        _log.info("already sent by other vipr nodes today, skip mail");
                        continue;
                    }

                    // check if the day is a NOTIFICATION_DAY, which defined in Constants class.
                    int daysToExpire = PasswordUtils.getDaysAfterEpoch(expireDate)
                            - PasswordUtils.getDaysAfterEpoch(now);
                    for (int day : Constants.NOTIFICATION_DAYS ) {
                        if (day == daysToExpire) {
                            _log.info("send notification mail for " + user + " at day " + daysToExpire);
                            _alertsLog.warn(user + "'s password is about to expire in " + daysToExpire + " days");
                            Map parameters = Maps.newHashMap();
                            parameters.put("user", user);
                            parameters.put("daysToExpire", daysToExpire);
                            parameters.put("configExpireDays", configExpireDays);

                            if (sendPasswordToBeExpiredMail(parameters)) {
                                // update mail sent time in Cassandra
                                ph.setLastNotificationMailSent(now);
                                _dbClient.updateAndReindexObject(ph);

                                // audit the mail sent success
                                _auditLogManager.recordAuditLog(
                                        null, null, "syssvc",
                                        OperationTypeEnum.SEND_PASSWORD_TO_BE_EXPIRE_MAIL,
                                        new Date().getTime(),
                                        AuditLogManager.AUDITLOG_SUCCESS,
                                        null, user);
                            } else {
                                // audit the mail sent fail
                                _auditLogManager.recordAuditLog(
                                        null, null, "syssvc",
                                        OperationTypeEnum.SEND_PASSWORD_TO_BE_EXPIRE_MAIL,
                                        new Date().getTime(),
                                        AuditLogManager.AUDITLOG_FAILURE,
                                        null, user);
                            }

                            break;
                        }
                    }

                }


            } catch (Exception e) {
                _log.warn("Unexpected exception during db maintenance", e);
            } finally {
                if (lock != null) {
                    try {
                        lock.release();
                    } catch (Exception e) {
                        _log.warn("Unexpected exception unlocking repair lock", e);
                    }
                }
            }
        }
    }

    /**
     * run the MailNotifier now
     */
    public void runMailNotifierNow() {
        new PasswordExpireMailNotifier().run();
    }

    private long getFirstDelayInMin() {
        Date aDate = new Date();
        Calendar with = Calendar.getInstance();
        with.setTime(aDate);
        int hour = with.get(Calendar.HOUR_OF_DAY);
        int Minutes = with.get(Calendar.MINUTE);

        int MinutesPassed12AM = hour * 60 + Minutes;
        int MinutesAtMailSendHour = Constants.MAIL_SEND_HOUR * 60;
        int OneDayMinutes = 24 * 60;
        long DelayInMinutes = (MinutesPassed12AM <= MinutesAtMailSendHour) ?
                MinutesAtMailSendHour - MinutesPassed12AM :
                OneDayMinutes - (MinutesPassed12AM - MinutesAtMailSendHour);
        return DelayInMinutes;
    }

    /**
     * send notification mail to root about the to be expired local user.
     *
     * as for now, only 4 special local users: sysmonitor, proxyuser, root, svcuser.
     * all mails will send to root.
     *
     * if vipr introduces manage other local users in the future, need change
     * the logic to send mail to the user whose password to be expired.
     *
     * @param parameters
     */
    public boolean sendPasswordToBeExpiredMail(Map parameters) {
        String to = getMailAddressOfUser("root");
        if (to == null || to.isEmpty()) {
            _log.warn("root's mail address haven't configured, skip sending mail");
            return false;
        }

        String title = String.format("ATTENTION - %s Password Is About To Expire",
                parameters.get("user"));
        String content = MailHelper.readTemplate("PasswordToBeExpireEmail.html");
        content = MailHelper.parseTemplate(parameters, content);
        getMailHelper().sendMailMessage(to, title, content);
        return true;
    }

    private MailHelper getMailHelper() {
        if (mailHelper == null) {
            mailHelper = new MailHelper(_coordinator);
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
        _dbClient.queryByConstraint(constraint, queryResults);

        List<URI> userPrefsIds = Lists.newArrayList();
        for (NamedElementQueryResultList.NamedElement namedElement : queryResults) {
            userPrefsIds.add(namedElement.getId());
        }

        final List<UserPreferences> userPrefs = Lists.newArrayList();
        Iterator<UserPreferences> iter = _dbClient.queryIterativeObjects(UserPreferences.class, userPrefsIds);
        while (iter.hasNext()) {
            userPrefs.add(iter.next());
        }

        if (userPrefs.size() > 1) {
            throw new IllegalStateException("There should only be 1 user preferences object for a user");
        }
        else if (userPrefs.isEmpty()) {
            // if there isn't a user prefs object in the DB yet then we haven't saved one for this user yet.
            return null;
        }

        return userPrefs.get(0).getEmail();
    }
}
