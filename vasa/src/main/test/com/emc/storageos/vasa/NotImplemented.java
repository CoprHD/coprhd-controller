
/**
 * NotImplemented.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class NotImplemented extends java.lang.Exception{

    private static final long serialVersionUID = 1348562968787L;
    
    private com.emc.storageos.vasa.VasaServiceStub.NotImplementedE faultMessage;

    
        public NotImplemented() {
            super("NotImplemented");
        }

        public NotImplemented(java.lang.String s) {
           super(s);
        }

        public NotImplemented(java.lang.String s, java.lang.Throwable ex) {
          super(s, ex);
        }

        public NotImplemented(java.lang.Throwable cause) {
            super(cause);
        }
    

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.NotImplementedE msg){
       faultMessage = msg;
    }
    
    public com.emc.storageos.vasa.VasaServiceStub.NotImplementedE getFaultMessage(){
       return faultMessage;
    }
}
    