/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.FibreChannelHBA;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;

public class ListFibreChannelHBAsQuery extends WinRMEnumerateOperation<FibreChannelHBA> {
    public static final String FIBRE_PORT_HBA_ATTRIBUTES_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSFC_FibrePortHBAAttributes";
    public static final String HBA_PORT_ATTRIBUTES_RESULTS_URI = WinRMConstants.WMI_BASE_URI
            + "root/wmi/MSFC_HBAPortAttributesResults";

    private static XPath XPATH = XmlUtils.createXPath(
            String.format("ns1=%s", FIBRE_PORT_HBA_ATTRIBUTES_URI),
            String.format("ns2=%s", HBA_PORT_ATTRIBUTES_RESULTS_URI));

    private static XPathExpression ATTRIBUTES_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:MSFC_FibrePortHBAAttributes");
    private static XPathExpression NODE_WWNS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:Attributes/ns2:NodeWWN");
    private static XPathExpression PORT_WWNS_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:Attributes/ns2:PortWWN");
    private static XPathExpression INSTANCE_NAME_EXPR = XmlUtils.compileXPath(XPATH,
            "ns1:InstanceName");

    public ListFibreChannelHBAsQuery(WinRMTarget target) {
        super(target, FIBRE_PORT_HBA_ATTRIBUTES_URI);
    }

    @Override
    protected void processItems(Element items, List<FibreChannelHBA> results) {
        for (Element item : XmlUtils.selectElements(ATTRIBUTES_EXPR, items)) {
            String portWWN = getPortWWN(item);
            if (StringUtils.isNotBlank(portWWN)) {
                FibreChannelHBA hba = new FibreChannelHBA();
                hba.setPortWWN(portWWN);
                hba.setNodeWWN(getNodeWWN(item));
                hba.setInstanceName(getInstanceName(item));
                results.add(hba);
            }
        }
    }

    protected String getPortWWN(Element parent) {
        StrBuilder portWWN = new StrBuilder();
        for (Element e : XmlUtils.selectElements(PORT_WWNS_EXPR, parent)) {
            portWWN.appendSeparator(":");
            int value = Integer.parseInt(XmlUtils.getText(e));
            portWWN.append(String.format("%02x", value));
        }
        return portWWN.toString();
    }

    protected String getNodeWWN(Element parent) {
        StrBuilder nodeWWN = new StrBuilder();
        for (Element e : XmlUtils.selectElements(NODE_WWNS_EXPR, parent)) {
            nodeWWN.appendSeparator(":");
            int value = Integer.parseInt(XmlUtils.getText(e));
            nodeWWN.append(String.format("%02x", value));
        }
        return nodeWWN.toString();
    }

    protected String getInstanceName(Element parent) {
        return XmlUtils.selectText(INSTANCE_NAME_EXPR, parent);
    }
}
