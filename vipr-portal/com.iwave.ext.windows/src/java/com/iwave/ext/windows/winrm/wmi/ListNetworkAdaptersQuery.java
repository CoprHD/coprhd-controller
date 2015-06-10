/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.NetworkAdapter;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class ListNetworkAdaptersQuery extends WinRMEnumerateOperation<NetworkAdapter> {
    public static final String NETWORK_ADAPTER_CONFIGURATION_URI = WinRMConstants.WMI_BASE_URI
            + "root/cimv2/Win32_NetworkAdapterConfiguration";

    private static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s",
            NETWORK_ADAPTER_CONFIGURATION_URI));

    private static final XPathExpression NETWORK_ADAPTERS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:Win32_NetworkAdapterConfiguration");
    private static final XPathExpression NAME_EXPR = XmlUtils.compileXPath(XPATH, "ns:Index");
    private static final XPathExpression IP_ADDRESS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:IPAddress");
    private static final XPathExpression SUBNET_MASK_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:IPSubnet");
    private static final XPathExpression MAC_ADDRESS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:MACAddress");

    public ListNetworkAdaptersQuery(WinRMTarget target) {
        super(target, NETWORK_ADAPTER_CONFIGURATION_URI);
    }

    @Override
    protected void processItems(Element items, List<NetworkAdapter> results) {
        for (Element e : XmlUtils.selectElements(NETWORK_ADAPTERS_EXPR, items)) {
            // Only consider network adapters with IP addresses
            String ipAddress = getIpAddress(e);
            if (StringUtils.isNotBlank(ipAddress)) {
                NetworkAdapter networkAdapter = new NetworkAdapter();
                networkAdapter.setIpAddress(ipAddress);
                networkAdapter.setIp6Address(getIp6Address(e));
                networkAdapter.setName(getName(e));
                networkAdapter.setSubnetMask(getSubnetMask(e));
                networkAdapter.setMacAddress(getMacAddress(e));
                results.add(networkAdapter);
            }
        }
    }

    protected String getIpAddress(Element parent) {
        for (Element e : XmlUtils.selectElements(IP_ADDRESS_EXPR, parent)) {
            String ipAddress = XmlUtils.getText(e);
            if (StringUtils.contains(ipAddress, ".")) {
                return ipAddress;
            }
        }
        return null;
    }

    protected String getIp6Address(Element parent) {
        for (Element e : XmlUtils.selectElements(IP_ADDRESS_EXPR, parent)) {
            String ipAddress = XmlUtils.getText(e);
            if (StringUtils.contains(ipAddress, ":")) {
                return ipAddress;
            }
        }
        return null;
    }

    protected Integer getName(Element parent) {
        return XmlUtils.selectInteger(NAME_EXPR, parent);
    }

    protected String getSubnetMask(Element parent) {
        return XmlUtils.selectText(SUBNET_MASK_EXPR, parent);
    }

    protected String getMacAddress(Element parent) {
        return XmlUtils.selectText(MAC_ADDRESS_EXPR, parent);
    }
}
