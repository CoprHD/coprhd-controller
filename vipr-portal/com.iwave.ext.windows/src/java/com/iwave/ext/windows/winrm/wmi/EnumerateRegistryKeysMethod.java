/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPathExpression;

import org.w3c.dom.Element;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

public class EnumerateRegistryKeysMethod extends AbstractRegistryMethod<List<String>> {
    private static final String ENUM_KEY = "EnumKey";
    private static final XPathExpression NAMES_EXPR = XmlUtils.compileXPath(XPATH, "ns:sNames");

    private long registryTree;
    private String subKeyName;

    public EnumerateRegistryKeysMethod(WinRMTarget target) {
        super(target, ENUM_KEY);
    }

    public EnumerateRegistryKeysMethod(WinRMTarget target, String subKeyName) {
        this(target);
        this.subKeyName = subKeyName;
    }

    public void setRegistryTree(long registryTree) {
        this.registryTree = registryTree;
    }

    public void setSubKeyName(String subKeyName) {
        this.subKeyName = subKeyName;
    }

    @Override
    protected void buildInput(XmlStringBuilder xml) {
        if (registryTree != 0) {
            xml.element("hDefKey", registryTree);
        }
        xml.element("sSubKeyName", subKeyName);
    }

    @Override
    protected List<String> processOutput(Element output) {
        List<String> results = Lists.newArrayList();
        for (Element entry : XmlUtils.selectElements(NAMES_EXPR, output)) {
            results.add(XmlUtils.getText(entry));
        }
        return results;
    }
}
