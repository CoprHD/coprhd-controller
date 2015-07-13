/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/* **********************************************************
 * Copyright 2010 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/
package com.emc.storageos.vasa.util;

import java.lang.reflect.Method;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Fault handling
 */
public class FaultUtil {
   private static Log log = LogFactory.getLog(FaultUtil.class);
   private static String VASA_PACKAGE_NAME = "com.vmware.vim.vasa.";

   private static final HashMap<String, String> FAULT_MAP = new HashMap<String, String>() {
      { put(VASA_PACKAGE_NAME + "_1_0.LostEvent",          ".xsd.LostEventE");
        put(VASA_PACKAGE_NAME + "_1_0.InvalidArgument",    ".xsd.InvalidArgumentE");
        put(VASA_PACKAGE_NAME + "_1_0.InvalidCertificate", ".xsd.InvalidCertificateE");
        put(VASA_PACKAGE_NAME + "_1_0.NotFound",           ".xsd.NotFoundE");
        put(VASA_PACKAGE_NAME + "_1_0.NotImplemented",     ".xsd.NotImplementedE");
        put(VASA_PACKAGE_NAME + "_1_0.StorageFault",       ".xsd.StorageFaultE");
        put(VASA_PACKAGE_NAME + "_1_0.LostAlarm",          ".xsd.LostAlarmE");
        put(VASA_PACKAGE_NAME + "_1_0.InvalidLogin",       ".xsd.InvalidLoginE");
        put(VASA_PACKAGE_NAME + "_1_0.InvalidSession",     ".xsd.InvalidSessionE");
      }
   };

   /**
    * Return the VASA API version that this VP is using in the
    * from "Major Number.Minor Number"
    */
   public static String getVasaApiVersion() {
      try {
         String version = getVasaApiVersionInClassNameFormat();
         version = version.replaceFirst("_","");
         version = version.replace('_','.');
         return version;
      } catch (Exception e) {
         log.error("Cannot convert VASA API version: " + e);
         return "unknown";
      }
   }

   /**
    * Return the VASA API version that this VP is using * in the format "_#_#"
    */
   private static String getVasaApiVersionInClassNameFormat() {
      return "_1_0";
   }

   /**
    *
    * @param VASA specific exception
    */
   public static void wrap(java.lang.Exception e) {
      try {
         Class exceptionClass = e.getClass();
         String faultName = FAULT_MAP.get(exceptionClass.getName());

         if (faultName != null) {
            String fullClassName = VASA_PACKAGE_NAME +
               getVasaApiVersionInClassNameFormat() + faultName;
            Class faultClass = Class.forName(fullClassName);
            Object faultObj = faultClass.newInstance();

            Method[] exceptionMethods = exceptionClass.getDeclaredMethods();
            for (Method setFaultMethod : exceptionMethods) {
               if (setFaultMethod.getName().startsWith("setFaultMessage")) {
                  setFaultMethod.invoke(e, faultObj);
                  return;
               }
            }
         }
         log.debug("Could not add FaultMessage for exception: " + exceptionClass);
         assert Boolean.FALSE;
      } catch (Exception ee) {
         log.debug("Fault occured during Exception wrapping: " + ee);
         assert Boolean.FALSE;
      } 
   }

   /**
    * @param none
    */
	public static com.vmware.vim.vasa._1_0.LostEvent LostEvent() {
		com.vmware.vim.vasa._1_0.LostEvent e = new com.vmware.vim.vasa._1_0.LostEvent();
		wrap(e);
		return e;
	}

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.LostEvent LostEvent(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.LostEvent e = new com.vmware.vim.vasa._1_0.LostEvent(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.LostEvent LostEvent(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.LostEvent(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.LostEvent LostEvent(Throwable cause) { //NOSONAR - ("xsd defined methods") 
      return FaultUtil.LostEvent("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.InvalidArgument InvalidArgument() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidArgument e = new com.vmware.vim.vasa._1_0.InvalidArgument();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidArgument InvalidArgument(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidArgument e = new com.vmware.vim.vasa._1_0.InvalidArgument(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */ 
   public static com.vmware.vim.vasa._1_0.InvalidArgument InvalidArgument(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidArgument(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidArgument InvalidArgument(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidArgument("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.InvalidCertificate InvalidCertificate() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidCertificate e = new com.vmware.vim.vasa._1_0.InvalidCertificate();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidCertificate InvalidCertificate(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidCertificate e = new com.vmware.vim.vasa._1_0.InvalidCertificate(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.InvalidCertificate InvalidCertificate(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidCertificate(message, null); 
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidCertificate InvalidCertificate(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidCertificate("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.NotFound NotFound() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.NotFound e = new com.vmware.vim.vasa._1_0.NotFound();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.NotFound NotFound(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.NotFound e = new com.vmware.vim.vasa._1_0.NotFound(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.NotFound NotFound(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.NotFound(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.NotFound NotFound(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.NotFound("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.NotImplemented NotImplemented() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.NotImplemented e = new com.vmware.vim.vasa._1_0.NotImplemented();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.NotImplemented NotImplemented(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.NotImplemented e = new com.vmware.vim.vasa._1_0.NotImplemented(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.NotImplemented NotImplemented(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.NotImplemented(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.NotImplemented NotImplemented(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.NotImplemented("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.StorageFault StorageFault() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.StorageFault e = new com.vmware.vim.vasa._1_0.StorageFault();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.StorageFault StorageFault(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.StorageFault e = new com.vmware.vim.vasa._1_0.StorageFault(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.StorageFault StorageFault(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.StorageFault(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.StorageFault StorageFault(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.StorageFault("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.LostAlarm LostAlarm() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.LostAlarm e = new com.vmware.vim.vasa._1_0.LostAlarm();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.LostAlarm LostAlarm(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.LostAlarm e = new com.vmware.vim.vasa._1_0.LostAlarm(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.LostAlarm LostAlarm(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.LostAlarm(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.LostAlarm LostAlarm(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.LostAlarm("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.InvalidLogin InvalidLogin() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidLogin e = new com.vmware.vim.vasa._1_0.InvalidLogin();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidLogin InvalidLogin(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidLogin e = new com.vmware.vim.vasa._1_0.InvalidLogin(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.InvalidLogin InvalidLogin(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidLogin(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidLogin InvalidLogin(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidLogin("", cause);
   }

   /**
    * @param none
    */
   public static com.vmware.vim.vasa._1_0.InvalidSession InvalidSession() { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidSession e = new com.vmware.vim.vasa._1_0.InvalidSession();
      wrap(e);
      return e;
   }

   /**
    * @param error string
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidSession InvalidSession(String message, Throwable cause) { //NOSONAR - ("xsd defined methods")
      com.vmware.vim.vasa._1_0.InvalidSession e = new com.vmware.vim.vasa._1_0.InvalidSession(message, cause);
      wrap(e);
      return e;
   }

   /**
    * @param error string
    */
   public static com.vmware.vim.vasa._1_0.InvalidSession InvalidSession(String message) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidSession(message, null);
   }

   /**
    * @param cause
    */
   public static com.vmware.vim.vasa._1_0.InvalidSession InvalidSession(Throwable cause) { //NOSONAR - ("xsd defined methods")
      return FaultUtil.InvalidSession("", cause);
   }
}
