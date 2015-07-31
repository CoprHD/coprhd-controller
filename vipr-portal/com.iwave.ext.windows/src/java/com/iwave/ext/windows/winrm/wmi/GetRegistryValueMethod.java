/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import java.util.List;

import javax.xml.xpath.XPathExpression;

import org.apache.commons.lang.math.NumberUtils;
import org.w3c.dom.Element;

import com.iwave.ext.windows.model.wmi.RegistryValueType;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

public class GetRegistryValueMethod extends AbstractRegistryMethod<Object> {
    private static final String GET_STRING_VALUE = "GetStringValue";
    private static final String GET_EXPANDED_STRING_VALUE = "GetExpandedStringValue";
    private static final String GET_BINARY_VALUE = "GetBinaryValue";
    private static final String GET_DWORD_VALUE = "GetDWORDValue";
    private static final String GET_MULTI_STRING_VALUE = "GetMultiStringValue";

    private static final XPathExpression STRING_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:sValue");
    private static final XPathExpression EXPANDED_STRING_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:sValue");
    private static final XPathExpression BINARY_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:uValue");
    private static final XPathExpression DWORD_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:uValue");
    private static final XPathExpression MULTI_STRING_VALUE_EXPR = XmlUtils.compileXPath(XPATH, "ns:sValue");

    private long registryTree;
    private String subKeyName;
    private String valueName;
    private RegistryValueType valueType;

    public GetRegistryValueMethod(WinRMTarget target) {
        super(target);
    }

    public void setRegistryTree(long registryTree) {
        this.registryTree = registryTree;
    }

    public void setSubKeyName(String subKeyName) {
        this.subKeyName = subKeyName;
    }

    public void setValueName(String valueName) {
        this.valueName = valueName;
    }

    public void setValueType(RegistryValueType valueType) {
        this.valueType = valueType;
        switch (valueType) {
            case STRING:
                setMethodName(GET_STRING_VALUE);
                break;
            case EXPANDED_STRING:
                setMethodName(GET_EXPANDED_STRING_VALUE);
                break;
            case BINARY:
                setMethodName(GET_BINARY_VALUE);
                break;
            case DWORD:
                setMethodName(GET_DWORD_VALUE);
                break;
            case MULTI_STRING:
                setMethodName(GET_MULTI_STRING_VALUE);
                break;
            default:
                throw new IllegalArgumentException("Unsupported value type: " + valueType);
        }
    }

    @Override
    protected void buildInput(XmlStringBuilder xml) {
        if (registryTree != 0) {
            xml.element("hDefKey", registryTree);
        }
        xml.element("sSubKeyName", subKeyName);
        xml.element("sValueName", valueName);
    }

    @Override
    protected Object processOutput(Element output) {
        switch (valueType) {
            case STRING:
                return getStringValue(output);
            case EXPANDED_STRING:
                return getExpandedStringValue(output);
            case BINARY:
                return getBinaryValue(output);
            case DWORD:
                return getDWordValue(output);
            case MULTI_STRING:
                return getMultiStringValue(output);
            default:
                throw new IllegalStateException("Unsupported value type: " + valueType);
        }
    }

    protected String getStringValue(Element output) {
        Element value = XmlUtils.selectElement(STRING_VALUE_EXPR, output);
        return value != null ? XmlUtils.getText(value) : null;
    }

    protected String getExpandedStringValue(Element output) {
        Element value = XmlUtils.selectElement(EXPANDED_STRING_VALUE_EXPR, output);
        return value != null ? XmlUtils.getText(value) : null;
    }

    protected byte[] getBinaryValue(Element output) {
        List<Element> values = XmlUtils.selectElements(BINARY_VALUE_EXPR, output);
        byte[] result = new byte[values.size()];
        for (int i = 0; i < values.size(); i++) {
            String value = XmlUtils.getText(values.get(i));
            int x = NumberUtils.toInt(value);
            result[i] = (byte) (0xff & x);
        }
        return result;
    }

    protected int getDWordValue(Element output) {
        String value = XmlUtils.selectText(DWORD_VALUE_EXPR, output);
        // A DWord is an unsigned 32 bit value, so we need to convert it to a long first, then cast to an int
        long longValue = NumberUtils.toLong(value);
        int intValue = (int) longValue;
        return intValue;
    }

    protected String[] getMultiStringValue(Element output) {
        List<Element> values = XmlUtils.selectElements(MULTI_STRING_VALUE_EXPR, output);
        String[] results = new String[values.size()];
        for (int i = 0; i < values.size(); i++) {
            results[i] = XmlUtils.getText(values.get(i));
        }
        return results;
    }
}