/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.wmi;

import javax.xml.xpath.XPath;

import com.iwave.ext.windows.winrm.WinRMConstants;
import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

public abstract class AbstractRegistryMethod<T> extends WinRMInvokeOperation<T> {
    public static final String REGISTRY_URI = WinRMConstants.WMI_BASE_URI + "root/default/StdRegProv";
    protected static final XPath XPATH = XmlUtils.createXPath(String.format("ns=%s", REGISTRY_URI));
    public static final long HKEY_CLASSES_ROOT = 0x80000000L;
    public static final long HKEY_CURRENT_USER = 0x80000001L;
    public static final long HKEY_LOCAL_MACHINE = 0x80000002L;
    public static final long HKEY_USERS = 0x80000003L;
    public static final long HKEY_CURRENT_CONFIG = 0x80000005L;
    public static final long HKEY_DYN_DATA = 0x80000006L;

    private String methodName;

    public AbstractRegistryMethod(WinRMTarget target) {
        super(target);
        setResourceUri(REGISTRY_URI);
    }

    public AbstractRegistryMethod(WinRMTarget target, String methodName) {
        super(target, REGISTRY_URI, REGISTRY_URI + "/" + methodName);
        this.methodName = methodName;
    }

    protected String getMethodName() {
        return methodName;
    }

    protected void setMethodName(String methodName) {
        this.methodName = methodName;
        setActionUri(REGISTRY_URI + "/" + methodName);
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start(methodName + "_INPUT").attr("xmlns", getResourceUri());
        buildInput(xml);
        xml.end();
        return xml.toString();
    }

    protected abstract void buildInput(XmlStringBuilder xml);
}
