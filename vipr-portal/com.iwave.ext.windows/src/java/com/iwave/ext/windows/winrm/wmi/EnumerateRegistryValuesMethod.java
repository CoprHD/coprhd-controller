/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.math.NumberUtils;
import org.w3c.dom.Element;

import com.google.common.collect.Lists;
import com.iwave.ext.windows.model.wmi.RegistryValueDef;
import com.iwave.ext.windows.model.wmi.RegistryValueType;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

public class EnumerateRegistryValuesMethod extends AbstractRegistryMethod<List<RegistryValueDef>> {
    private static final String ENUM_VALUES = "EnumValues";
    private static final XPathExpression NAMES_EXPR = XmlUtils.compileXPath(XPATH, "ns:sNames");
    private static final XPathExpression TYPES_EXPR = XmlUtils.compileXPath(XPATH, "ns:Types");

    private long registryTree;
    private String subKeyName;

    public EnumerateRegistryValuesMethod(WinRMTarget target) {
        super(target, ENUM_VALUES);
    }

    public EnumerateRegistryValuesMethod(WinRMTarget target, String subKeyName) {
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
    protected List<RegistryValueDef> processOutput(Element output) {
        List<RegistryValueDef> results = Lists.newArrayList();

        List<Element> names = XmlUtils.selectElements(NAMES_EXPR, output);
        List<Element> types = XmlUtils.selectElements(TYPES_EXPR, output);
        for (int i = 0; i < names.size() && i < types.size(); i++) {
            String name = XmlUtils.getText(names.get(i));
            int type = NumberUtils.toInt(XmlUtils.getText(types.get(i)));
            RegistryValueType valueType = RegistryValueType.fromValue(type);

            results.add(new RegistryValueDef(name, valueType));
        }

        return results;
    }
}
