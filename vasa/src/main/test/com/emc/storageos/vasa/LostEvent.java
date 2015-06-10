/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */

/**
 * LostEvent.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class LostEvent extends java.lang.Exception{

    private static final long serialVersionUID = 1348562968769L;
    
    private com.emc.storageos.vasa.VasaServiceStub.LostEventE faultMessage;

    
        public LostEvent() {
            super("LostEvent");
        }

        public LostEvent(java.lang.String s) {
           super(s);
        }

        public LostEvent(java.lang.String s, java.lang.Throwable ex) {
          super(s, ex);
        }

        public LostEvent(java.lang.Throwable cause) {
            super(cause);
        }
    

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.LostEventE msg){
       faultMessage = msg;
    }
    
    public com.emc.storageos.vasa.VasaServiceStub.LostEventE getFaultMessage(){
       return faultMessage;
    }
}
    