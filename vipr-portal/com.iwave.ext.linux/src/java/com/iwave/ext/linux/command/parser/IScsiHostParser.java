/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.parser;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.model.IScsiHost;
import com.iwave.ext.text.TextParser;

//Host Number: 22
//State: running
//Transport: tcp
//Initiatorname: <empty>
//IPaddress: 10.200.0.90
//HWaddress: <empty>
//Netdev: <empty>
//*********
//Sessions:
//*********
//Target: iqn.1992-04.com.emc:cx.apm00113903177.a3
//  Current Portal: 10.200.0.102:3260,2
//  Persistent Portal: 10.200.0.102:3260,2
//      **********
//      Interface:
//      **********
//      Iface Name: default
//      Iface Transport: tcp
//      Iface Initiatorname: iqn.1996-04.de.suse:01:ce24157495dc
//      Iface IPaddress: 10.200.0.90
//      Iface HWaddress: <empty>
//      Iface Netdev: <empty>
//      SID: 16
//      iSCSI Connection State: LOGGED IN
//      iSCSI Session State: LOGGED_IN
//      Internal iscsid Session State: NO CHANGE

public class IScsiHostParser {
    private static final Pattern HOST_PATTERN = Pattern.compile("Host Number:\\s*(\\d+)");
    private static final String HOST_NUMBER = "Host Number";
    private static final String STATE = "State";
    private static final String TRANSPORT = "Transport";
    private static final String INITIATOR_NAME = "Initiatorname";
    private static final String IP_ADDRESS = "IPaddress";
    private static final String HW_ADDRESS = "HWaddress";
    private static final String NETDEV = "Netdev";
    private TextParser parser;
    private IScsiSessionParser sessionParser;

    public IScsiHostParser() {
        parser = new TextParser();
        parser.setRepeatPattern(HOST_PATTERN);
        sessionParser = new IScsiSessionParser();
    }

    public List<IScsiHost> parseHosts(String text) {
        List<IScsiHost> hosts = Lists.newArrayList();
        for (String textBlock : parser.parseTextBlocks(text)) {
            IScsiHost host = parseHost(textBlock);
            if (host != null) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    public IScsiHost parseHost(String text) {
        if (StringUtils.isBlank(text)) {
            return null;
        }
        Map<String, String> properties = parser.parseProperties(text, ':');
        IScsiHost host = new IScsiHost();
        host.setHostId(NumberUtils.toInt(properties.get(HOST_NUMBER)));
        host.setState(properties.get(STATE));
        host.setTransport(properties.get(TRANSPORT));
        host.setInitiatorName(properties.get(INITIATOR_NAME));
        host.setIpAddress(properties.get(IP_ADDRESS));
        host.setHwAddress(properties.get(HW_ADDRESS));
        host.setNetdev(properties.get(NETDEV));
        host.setSessions(sessionParser.parseSessions(text));
        return host;
    }
}
