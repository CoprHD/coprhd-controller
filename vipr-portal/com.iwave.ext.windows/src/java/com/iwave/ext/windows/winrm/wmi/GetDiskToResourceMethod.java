/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.model.wmi.ResourceToDisk;
import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMEnumerateOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlUtils;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import java.util.List;

/**
 */
public class GetDiskToResourceMethod extends WinRMEnumerateOperation<ResourceToDisk> {
    public static final String CLUSTER_RESOURCE_TO_DISK_URI = WinRMConstants.WMI_BASE_URI + "root/mscluster/MSCluster_ResourceToDisk";

    private static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s", CLUSTER_RESOURCE_TO_DISK_URI));

    private static final XPathExpression CLUSTER_RESOURCE_TO_DISK_EXPR = XmlUtils.compileXPath(XPATH, "ns:MSCluster_ResourceToDisk");
    private static final XPathExpression GROUP_COMPONENT_EXPR = XmlUtils.compileXPath(XPATH, "ns:GroupComponent");
    private static final XPathExpression PART_COMPONENT_EXPR = XmlUtils.compileXPath(XPATH, "ns:PartComponent");

    private static final XPathExpression SELECTOR_EXPR = XmlUtils.compileXPath(WinRMConstants.XPATH, "*/*/w:Selector");

    public GetDiskToResourceMethod(WinRMTarget target) {
        super(target, CLUSTER_RESOURCE_TO_DISK_URI);
    }

    @Override
    protected void processItems(Element items, List<ResourceToDisk> results) {
        for (Element e : XmlUtils.selectElements(CLUSTER_RESOURCE_TO_DISK_EXPR, items)) {
            ResourceToDisk resourceToDisk = new ResourceToDisk();
            resourceToDisk.setResourceName(getResourceName(e));
            resourceToDisk.setDiskId(getDiskId(e));

            results.add(resourceToDisk);
        }
    }

    private String getDiskId(Element parent) {
        Element partComponent =  XmlUtils.selectElement(PART_COMPONENT_EXPR, parent);
        Element selector = XmlUtils.selectElement(SELECTOR_EXPR,partComponent);
        return selector.getTextContent();
    }

    private String getResourceName(Element parent) {
        System.out.println(XmlUtils.formatXml(parent,true));
        Element partComponent =  XmlUtils.selectElement(GROUP_COMPONENT_EXPR, parent);
        return getSelector(partComponent);
    }

    public String getSelector(Element parent) {
       Element selector = XmlUtils.selectElement(SELECTOR_EXPR, parent);

        return XmlUtils.getText(selector);
    }
}
