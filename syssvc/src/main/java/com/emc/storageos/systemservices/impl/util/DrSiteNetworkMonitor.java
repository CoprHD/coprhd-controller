package com.emc.storageos.systemservices.impl.util;

import com.emc.storageos.coordinator.client.model.Site;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.coordinator.client.service.DrUtil;
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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by aquinn on 1/11/16.
 */
public class DrSiteNetworkMonitor implements Runnable{

    private static final Logger _log = LoggerFactory.getLogger(DrSiteNetworkMonitor.class);

    private CoordinatorClient coordinatorClient;
    private DbClient dbClient;
    private AuditLogManager auditLogManager;

    private DrUtil drUtil;
    private String myNodeId;
    private MailHelper mailHelper;

    String ZOOKEEPER_MODE_LEADER = "leader";
    String NETWORK_HEALTH_BROKEN = "Broken";
    String NETWORK_HEALTH_GOOD = "Good";
    String NETWORK_HEALTH_SLOW = "Slow";

    public DrSiteNetworkMonitor(String myNodeId) {
        this.drUtil = new DrUtil(coordinatorClient);
        this.myNodeId = myNodeId;
    }

    public void setCoordinator(CoordinatorClient coordinatorClient) {
        this.coordinatorClient = coordinatorClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setAuditLogManager(AuditLogManager auditLogManager) {
        this.auditLogManager = auditLogManager;
    }

    public void run() {

        try {
            checkPing();
        } catch (Exception e) {
            //try catch exception to make sure next scheduled run can be launched.
            _log.error("Error occurs when monitor standby network", e);
        }

    }

    private void checkPing() {

        //Only leader on active site will test ping (no networking info if active down?)
        String zkState = drUtil.getLocalCoordinatorMode(myNodeId);


        if (ZOOKEEPER_MODE_LEADER.equals(zkState)) {
            //I'm the leader
            for (Site site : drUtil.listStandbySites()){
                String previousState = site.getNetworkHealth();
                String host = site.getVip();
                double ping = testPing(host,80);
                _log.info("Ping: "+ping);
                site.setPing(ping);
                if (ping > 150) {
                    site.setNetworkHealth(NETWORK_HEALTH_SLOW);
                    _log.warn("Network for standby {} is slow",site.getName());
                }
                else if (ping < 0) {
                    site.setNetworkHealth(NETWORK_HEALTH_BROKEN);
                    _log.error("Network for standby {} is broken",site.getName());
                }
                else {
                    site.setNetworkHealth(NETWORK_HEALTH_GOOD);
                }

                coordinatorClient.persistServiceConfiguration(site.toConfiguration());

                if (!NETWORK_HEALTH_BROKEN.equals(previousState)
                        && NETWORK_HEALTH_BROKEN.equals(site.getNetworkHealth())){
                    //send email alert
                    if (sendMail(site)) {

                        // audit the mail sent success
                        auditLogManager.recordAuditLog(
                                null, null, "syssvc",
                                OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                                new Date().getTime(),
                                AuditLogManager.AUDITLOG_SUCCESS,
                                null, site.getName());
                    } else {
                        // audit the mail sent fail
                        auditLogManager.recordAuditLog(
                                null, null, "syssvc",
                                OperationTypeEnum.SEND_STANDBY_NETWORK_BROKEN_MAIL,
                                new Date().getTime(),
                                AuditLogManager.AUDITLOG_FAILURE,
                                null, site.getName());
                    }
                }
            }
        }
    }

    private boolean sendMail(Site site) {
        String to = getMailAddressOfUser("root");
        if (to == null || to.isEmpty()) {
            _log.warn("Can't send mail alert, no email address for root user");
            return false;
        }

        Map parameters = Maps.newHashMap();
        parameters.put("standbyName", site.getName());

        String title = String.format("ATTENTION - %s network is broken",
                site.getName());
        String content = MailHelper.readTemplate("StandbySiteBroken.html");
        content = MailHelper.parseTemplate(parameters, content);
        getMailHelper().sendMailMessage(to, title, content);
        return true;
    }

    private MailHelper getMailHelper() {
        if (mailHelper == null) {
            mailHelper = new MailHelper(coordinatorClient);
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

    /**
     * Connect using layer4 (sockets)
     *
     * @return delay if the specified host responded, -1 if failed
     */
    private double testPing(String hostAddress, int port) {
        InetAddress inetAddress = null;
        InetSocketAddress socketAddress = null;
        SocketChannel sc = null;
        long timeToRespond = -1;
        long start, stop;

        try {
            inetAddress = InetAddress.getByName(hostAddress);
        } catch (UnknownHostException e) {
            _log.error("Problem, unknown host:",e);
        }

        try {
            socketAddress = new InetSocketAddress(inetAddress, port);
        } catch (IllegalArgumentException e) {
            _log.error("Problem, port may be invalid:",e);
        }

        // Open the channel, set it to non-blocking, initiate connect
        try {
            sc = SocketChannel.open();
            sc.configureBlocking(true);
            start = System.nanoTime();
            if (sc.connect(socketAddress)) {
                stop = System.nanoTime();
                timeToRespond = (stop - start);
            }
        } catch (IOException e) {
            _log.error("Problem, connection could not be made:",e);
        }

        try {
            sc.close();
        } catch (IOException e) {
            _log.error("Error closing socket during latency test",e);
        }

        //The ping failed, return -1
        if (timeToRespond == -1) {
            return -1;
        }

        //the ping suceeded, convert from ns to ms with 3 decimals
        timeToRespond = timeToRespond/1000;
        return timeToRespond/1000.0;
    }

};