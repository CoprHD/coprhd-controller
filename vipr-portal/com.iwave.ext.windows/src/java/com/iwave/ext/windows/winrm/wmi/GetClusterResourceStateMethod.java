/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMGetOperation;
import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;

public class GetClusterResourceStateMethod extends WinRMGetOperation<String> {
    public static final String CLUSTER_RESOURCE_URI = WinRMConstants.WMI_BASE_URI + "root/mscluster/MSCluster_Resource";

    private static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s", CLUSTER_RESOURCE_URI));
    private static final XPathExpression STATE_EXPR = XmlUtils.compileXPath(XPATH, "ns:State");

    public GetClusterResourceStateMethod(WinRMTarget target, String diskId) {
        super(target, CLUSTER_RESOURCE_URI);
        setSelector("Name", diskId);
    }

    @Override
    protected String processResponse(Document response) {
        NodeList nodes = response.getElementsByTagNameNS(CLUSTER_RESOURCE_URI, "State");

        return nodes.item(0).getTextContent();
    }

    public String getState(Element parent) {
        for (Element e : XmlUtils.selectElements(STATE_EXPR, parent)) {
            return XmlUtils.getText(e);
        }
        return null;
    }
}
