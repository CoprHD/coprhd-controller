/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vasa;

// VASA API version specific classes
import com.emc.storageos.vasa.util.SSLUtil;
import com.vmware.vim.vasa._1_0.InvalidArgument;
import com.vmware.vim.vasa._1_0.InvalidCertificate;
import com.vmware.vim.vasa._1_0.InvalidLogin;
import com.vmware.vim.vasa._1_0.InvalidSession;
import com.vmware.vim.vasa._1_0.StorageFault;
import com.vmware.vim.vasa._1_0.data.xsd.MessageCatalog;
import com.vmware.vim.vasa._1_0.data.xsd.UsageContext;
import com.vmware.vim.vasa._1_0.data.xsd.VasaProviderInfo;

public interface ContextManager {


   public VasaProviderInfo registerVASACertificate(String userName,
      String password, String newCertificate) throws InvalidCertificate, InvalidLogin,
      InvalidSession, StorageFault;

   public void unregisterVASACertificate(String existingCertificate)
      throws InvalidCertificate, InvalidSession, StorageFault;

   /** Configuration/Setup related APIs */

   public MessageCatalog[] queryCatalog() throws InvalidSession, StorageFault;

   public VasaProviderInfo setContext(UsageContext uc) throws InvalidArgument, InvalidSession, StorageFault;
   
   public void init(SSLUtil sslUtil);
   
   public VasaProviderInfo initializeVasaProviderInfo();

   public UsageContext getUsageContext() throws InvalidSession, StorageFault;

   /**
	 * Returns instance of <code>SOSManager</code> of the current VASA session
	 * 
	 * @return instance of <code>SOSManager</code>
	 * @throws InvalidSession
	 * @throws StorageFault
	 */
   public SOSManager getSOSManager() throws StorageFault, InvalidSession;

   // public MountInfo[] getMountInfo() throws InvalidSession, StorageFault;
}
