/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import com.iwave.ext.windows.model.wmi.MSCluster;
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
public class ListClustersQuery extends WinRMEnumerateOperation<MSCluster> {
    public static final String CLUSTERS_SERVICES_URI = WinRMConstants.WMI_BASE_URI + "root/mscluster/MSCluster_Cluster";

    private static final XPath XPATH = XmlUtils.createXPath("ns=" + CLUSTERS_SERVICES_URI);
    private static final XPathExpression SERVICE_EXPR = XmlUtils.compileXPath(XPATH, "ns:MSCluster_Cluster");
    private static final XPathExpression NAME_EXPR = XmlUtils.compileXPath(XPATH, "ns:Name");
    private static final XPathExpression FQDN_EXPR = XmlUtils.compileXPath(XPATH, "ns:Fqdn");

    public ListClustersQuery(WinRMTarget target) {
        super(target, CLUSTERS_SERVICES_URI);
    }

    @Override
    protected void processItems(Element items, List<MSCluster> results) {
        for (Element item : XmlUtils.selectElements(SERVICE_EXPR, items)) {
            MSCluster service = new MSCluster();
            service.setName(getName(item));
            service.setFqdn(getFQDN(item));
            results.add(service);
        }
    }

    protected String getName(Element item) {
        return XmlUtils.selectText(NAME_EXPR, item);
    }

    protected String getFQDN(Element item) {
        return XmlUtils.selectText(FQDN_EXPR, item);
    }

}
