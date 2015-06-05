
/**
 * NotFound.java
 *
 * This file was auto-generated from WSDL
 * by the Apache Axis2 version: 1.6.2  Built on : Apr 17, 2012 (05:33:49 IST)
 */

package com.emc.storageos.vasa;

public class NotFound extends java.lang.Exception{

    private static final long serialVersionUID = 1348562968868L;
    
    private com.emc.storageos.vasa.VasaServiceStub.NotFoundE faultMessage;

    
        public NotFound() {
            super("NotFound");
        }

        public NotFound(java.lang.String s) {
           super(s);
        }

        public NotFound(java.lang.String s, java.lang.Throwable ex) {
          super(s, ex);
        }

        public NotFound(java.lang.Throwable cause) {
            super(cause);
        }
    

    public void setFaultMessage(com.emc.storageos.vasa.VasaServiceStub.NotFoundE msg){
       faultMessage = msg;
    }
    
    public com.emc.storageos.vasa.VasaServiceStub.NotFoundE getFaultMessage(){
       return faultMessage;
    }
}
    