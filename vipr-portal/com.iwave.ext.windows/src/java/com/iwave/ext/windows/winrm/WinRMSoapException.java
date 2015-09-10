/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm;

import javax.xml.soap.SOAPFault;

public class WinRMSoapException extends WinRMException {
    private static final long serialVersionUID = 7451082631659910359L;

    private SOAPFault soapFault;

    public WinRMSoapException(SOAPFault soapFault) {
        super(soapFault.getFaultString());
        this.soapFault = soapFault;
    }

    public WinRMSoapException(String message, SOAPFault soapFault) {
        super(message);
        this.soapFault = soapFault;
    }

    public SOAPFault getSoapFault() {
        return soapFault;
    }
}
