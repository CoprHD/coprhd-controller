/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.model.wmi.MSClusterNetworkInterface;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import java.util.List;

public class ListClusterNetworkInterfaceQuery    extends WinRMEnumerateOperation<MSClusterNetworkInterface> {
    public static final String NETWORK_INTERFACE_SERVICES_URI = WinRMConstants.WMI_BASE_URI + "root/mscluster/MSCluster_NetworkInterface";

    private static final XPath XPATH = XmlUtils.createXPath("ns=" + NETWORK_INTERFACE_SERVICES_URI);
    private static final XPathExpression SERVICE_EXPR = XmlUtils.compileXPath(XPATH, "ns:MSCluster_NetworkInterface");
    private static final XPathExpression NODE_EXPR = XmlUtils.compileXPath(XPATH, "ns:Node");
    private static final XPathExpression IPADDRESS_EXPR = XmlUtils.compileXPath(XPATH, "ns:Address");
    private static final XPathExpression NETWORK_EXPR = XmlUtils.compileXPath(XPATH, "ns:Network");
    private static final XPathExpression NAME_EXPR = XmlUtils.compileXPath(XPATH, "ns:Name");

    public ListClusterNetworkInterfaceQuery(WinRMTarget target) {
        super(target, NETWORK_INTERFACE_SERVICES_URI);
    }

    @Override
    protected void processItems(Element items, List<MSClusterNetworkInterface> results) {
        for (Element item : XmlUtils.selectElements(SERVICE_EXPR, items)) {
            MSClusterNetworkInterface network = new MSClusterNetworkInterface();
            network.setNode(getNode(item));
            network.setIpaddress(getIpAddress(item));
            network.setNetwork(getNetwork(item));
            network.setName(getName(item));
            results.add(network);
        }
    }

    protected String getName(Element item) {
        return XmlUtils.selectText(NAME_EXPR, item);
    }

    protected String getNode(Element item) {
        return XmlUtils.selectText(NODE_EXPR, item);
    }

    protected String getIpAddress(Element item) {
        return XmlUtils.selectText(IPADDRESS_EXPR, item);
    }

    protected String getNetwork(Element item) {
        return XmlUtils.selectText(NETWORK_EXPR, item);
    }
}