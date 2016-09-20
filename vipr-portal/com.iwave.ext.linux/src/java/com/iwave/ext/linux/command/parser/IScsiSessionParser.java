/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.IScsiTarget;
import com.iwave.ext.linux.model.IScsiSession;
import com.iwave.ext.text.TextParser;

//Target: iqn.1992-04.com.emc:cx.apm00113903177.a3
//Current Portal: 10.200.0.102:3260,2
//Persistent Portal: 10.200.0.102:3260,2
//  **********
//  Interface:
//  **********
//  Iface Name: default
//  Iface Transport: tcp
//  Iface Initiatorname: iqn.1996-04.de.suse:01:ce24157495dc
//  Iface IPaddress: 10.200.0.90
//  Iface HWaddress: <empty>
//  Iface Netdev: <empty>
//  SID: 16
//  iSCSI Connection State: LOGGED IN
//  iSCSI Session State: LOGGED_IN
//  Internal iscsid Session State: NO CHANGE
public class IScsiSessionParser {
    private static final Pattern TARGET_PATTERN = Pattern.compile("Target:\\s*(.*)");
    private static final String TARGET = "Target";
    private static final String CURRENT_PORTAL = "Current Portal";
    private static final String PERSISTENT_PORTAL = "Persistent Portal";
    private static final String IFACE_NAME = "Iface Name";
    private static final String IFACE_TRANSPORT = "Iface Transport";
    private static final String IFACE_INITIATOR_NAME = "Iface Initiatorname";
    private static final String IFACE_IP_ADDRESS = "Iface IPaddress";
    private static final String IFACE_HW_ADDRESS = "Iface HWaddress";
    private static final String IFACE_NETDEV = "Iface Netdev";
    private static final String SESSION_ID = "SID";
    private static final String CONNECTION_STATE = "iSCSI Connection State";
    private static final String SESSION_STATE = "iSCSI Session State";

    private TextParser parser;

    public IScsiSessionParser() {
        parser = new TextParser();
        parser.setRepeatPattern(TARGET_PATTERN);
    }

    public List<IScsiSession> parseSessions(String text) {
        List<IScsiSession> sessions = Lists.newArrayList();
        for (String textBlock : parser.parseTextBlocks(text)) {
            IScsiSession session = parseSession(textBlock);
            if (session != null) {
                sessions.add(session);
            }
        }
        return sessions;
    }

    public IScsiSession parseSession(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Map<String, String> properties = parser.parseProperties(text, ':');

        IScsiSession session = new IScsiSession();
        IScsiTarget target = new IScsiTarget();
        String targetValue = null;
        if (properties.get(TARGET) != null && properties.get(TARGET).contains(" ")) {
            targetValue = StringUtils.substring(properties.get(TARGET), 0, properties.get(TARGET).indexOf(" "));
        } else {
            targetValue = properties.get(TARGET);
        }
        target.setIqn(targetValue);
        target.setPortal(properties.get(CURRENT_PORTAL));
        target.setIfaceName(properties.get(IFACE_NAME));
        session.setTarget(target);
        session.setPersistentPortal(properties.get(PERSISTENT_PORTAL));
        session.setIfaceTransport(properties.get(IFACE_TRANSPORT));
        session.setIfaceInitiatorName(properties.get(IFACE_INITIATOR_NAME));
        session.setIfaceIPAddress(properties.get(IFACE_IP_ADDRESS));
        session.setIfaceHWAddress(properties.get(IFACE_HW_ADDRESS));
        session.setIfaceNetdev(properties.get(IFACE_NETDEV));
        session.setSessionID(properties.get(SESSION_ID));
        session.setConnectionState(properties.get(CONNECTION_STATE));
        session.setSessionState(properties.get(SESSION_STATE));
        return session;
    }
}
