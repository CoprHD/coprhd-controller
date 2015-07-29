/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.model.wmi.MSClusterToNetworkInterface;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import java.util.List;

public class ListClusterToNetworkInterfaceQuery extends WinRMEnumerateOperation<MSClusterToNetworkInterface> {
    public static final String CLUSTER_RESOURCE_TO_NETWORK_INTERFACE = WinRMConstants.WMI_BASE_URI
            + "root/mscluster/MSCluster_ClusterToNetworkInterface";

    private static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s", CLUSTER_RESOURCE_TO_NETWORK_INTERFACE));

    private static final XPathExpression CLUSTER_TO_NETWORK_INTERFACE_EXPR = XmlUtils.compileXPath(XPATH,
            "ns:MSCluster_ClusterToNetworkInterface");
    private static final XPathExpression GROUP_COMPONENT_EXPR = XmlUtils.compileXPath(XPATH, "ns:GroupComponent");
    private static final XPathExpression PART_COMPONENT_EXPR = XmlUtils.compileXPath(XPATH, "ns:PartComponent");

    private static final XPathExpression SELECTOR_EXPR = XmlUtils.compileXPath(WinRMConstants.XPATH, "*/*/w:Selector");

    public ListClusterToNetworkInterfaceQuery(WinRMTarget target) {
        super(target, CLUSTER_RESOURCE_TO_NETWORK_INTERFACE);
    }

    @Override
    protected void processItems(Element items, List<MSClusterToNetworkInterface> results) {
        for (Element e : XmlUtils.selectElements(CLUSTER_TO_NETWORK_INTERFACE_EXPR, items)) {
            MSClusterToNetworkInterface resourceToDisk = new MSClusterToNetworkInterface();
            resourceToDisk.setClusterName(getClusterName(e));
            resourceToDisk.setNetworkInterface(getNetworkInterface(e));

            results.add(resourceToDisk);
        }
    }

    private String getNetworkInterface(Element parent) {
        Element partComponent = XmlUtils.selectElement(PART_COMPONENT_EXPR, parent);
        Element selector = XmlUtils.selectElement(SELECTOR_EXPR, partComponent);
        return selector.getTextContent();
    }

    private String getClusterName(Element parent) {
        Element partComponent = XmlUtils.selectElement(GROUP_COMPONENT_EXPR, parent);
        return getSelector(partComponent);
    }

    public String getSelector(Element parent) {
        Element selector = XmlUtils.selectElement(SELECTOR_EXPR, parent);

        return XmlUtils.getText(selector);
    }
}
