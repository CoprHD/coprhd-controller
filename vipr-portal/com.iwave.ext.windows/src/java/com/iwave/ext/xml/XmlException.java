/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.xml;

public class XmlException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public XmlException(String message) {
        super(message);
    }

    public XmlException(Throwable cause) {
        super(cause);
    }

    public XmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
