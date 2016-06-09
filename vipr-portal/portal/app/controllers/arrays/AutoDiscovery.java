/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;


import static com.emc.vipr.client.core.util.ResourceUtils.id;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.io.File;
import jobs.RegenerateCertificateJob;
import jobs.UpdateCertificateJob;
import models.HostTypes;
import models.NetworkSystemTypes;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

import play.Logger;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.With;
import util.BourneUtil;
import util.HostUtils;
import util.MessagesUtils;
import util.NetworkSystemUtils;
import util.StorageProviderUtils;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.smis.StorageProviderRestRep;
import com.emc.storageos.model.tenant.TenantOrgRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.Keystore;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.emc.vipr.model.keystore.CertificateChain;
import com.emc.vipr.model.keystore.KeyAndCertificateChain;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.Maintenance;
import controllers.arrays.SanSwitches.SanSwitchForm;
import controllers.arrays.StorageProviders.*;
import controllers.compute.Hosts.HostForm;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.security.Security;
import controllers.util.ViprResourceController;
import controllers.util.FlashException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
//Hosts
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;
import static controllers.Common.flashException;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import models.HostTypes;
import models.datatable.HostDataTable;
import models.datatable.HostDataTable.HostInfo;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Min;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.i18n.Messages;
import play.mvc.With;
import util.ClusterUtils;
import util.HostUtils;
import util.MessagesUtils;
import util.StringOption;
import util.TenantUtils;
import util.VCenterUtils;
import util.builders.ACLUpdateBuilder;
import util.datatable.DataTablesSupport;
import util.validation.HostNameOrIpAddress;

import com.emc.storageos.model.host.HostCreateParam;
import com.emc.storageos.model.host.HostParam;
import com.emc.storageos.model.host.HostRestRep;
import com.emc.storageos.model.host.HostUpdateParam;
import com.emc.storageos.model.host.InitiatorRestRep;
import com.emc.storageos.model.host.IpInterfaceRestRep;
import com.emc.storageos.model.host.cluster.ClusterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterDataCenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterParam;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.storageos.model.network.NetworkSystemCreate;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.network.NetworkSystemUpdate;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.core.util.ResourceUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;
import controllers.util.FlashException;
import controllers.util.Models;
import controllers.util.ViprResourceController;
//hosts

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"),
        @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class AutoDiscovery extends ViprResourceController {
	
	
	 protected static final String smis = "SMI-VMAX";
	 protected static final String win = "windows";
	 protected static final String lin = "linux";
	 protected static final String SAVED = "Hosts.saved";
	
	// Beggining of the class for Hosts
	
	private static void addReferenceData() {
        renderArgs.put("types", StringOption.options(HostTypes.STANDARD_CREATION_TYPES, HostTypes.OPTION_PREFIX, false));
        renderArgs.put("clusters", ClusterUtils.getClusterOptions(Models.currentTenant()));

        List<ProjectRestRep> projects = getViprClient().projects().getByTenant(ResourceUtils.uri(Models.currentTenant()));
        renderArgs.put("projects", projects);
    }
	
	public static class HostForm {
        public String id;
        public String tenantId;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @HostNameOrIpAddress
        public String hostname;

        @Required
        @Min(1)
        public Integer port;

        @Required
        public String type;

        @MaxSize(1024)
        public String username;

        @MaxSize(1024)
        public String password;

        public String passwordConfirm;

        public boolean discoverable = true;

        public boolean useHttps;

        public HostForm() {
        }

        public HostForm(HostRestRep host) {
            this();
            doReadFrom(host);
        }

        protected void doReadFrom(HostRestRep host) {
            this.id = host.getId().toString();
            this.name = host.getName();
            this.hostname = host.getHostName();
            this.type = host.getType();

            if (!isManualHost()) {
                this.username = host.getUsername();
                if (host.getPortNumber() != null && host.getPortNumber() > -1) {
                    this.port = host.getPortNumber();
                }
            }

            this.discoverable = host.getDiscoverable() == null ? true : host.getDiscoverable();
            this.useHttps = host.getUseSsl() == null ? true : host.getUseSsl();
        }

        protected void doWriteTo(HostCreateParam hostCreateParam) {
            doWriteToHostParam(hostCreateParam);
            hostCreateParam.setType(this.type.toString());
            hostCreateParam.setHostName(this.hostname);
            hostCreateParam.setCluster(ResourceUtils.NULL_URI);
        }

        protected void doWriteTo(HostUpdateParam hostUpdateParam) {
            doWriteToHostParam(hostUpdateParam);
            hostUpdateParam.setType(this.type.toString());
            hostUpdateParam.setHostName(this.hostname);
        }

        protected void doWriteToHostParam(HostParam hostParam) {
            hostParam.setName(this.name);
            hostParam.setTenant(uri(tenantId));
            if (isManualHost()) {
                hostParam.setUserName(Messages.get("Hosts.defaultUsername"));
                hostParam.setPassword(Messages.get("Hosts.defaultPassword"));
                hostParam.setUseSsl(false);
                hostParam.setDiscoverable(false);
            }
            else {
                if (StringUtils.isNotBlank(this.username)) {
                    hostParam.setUserName(this.username);
                }
                if (StringUtils.isNotBlank(this.password)) {
                    hostParam.setPassword(this.password);
                }
                hostParam.setPortNumber(this.port);
                hostParam.setUseSsl(this.useHttps);
                hostParam.setDiscoverable(this.discoverable);
            }
        }

        private boolean isManualHost() {
            return HostTypes.isOther(this.type) || HostTypes.isSUNVCS(this.type);
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            doValidation(formName);
        }

        protected void doValidation(String formName) {
            if (isManualHost()) {
                validateManualHost(formName);
            }
            else {
                validateStandardHost(formName);
            }
        }

        protected void validateStandardHost(String formName) {
            if (discoverable) {
                Validation.required(String.format("%s.username", formName), this.username);
                if (this.isNew()) {
                    Validation.required(String.format("%s.password", formName), this.password);
                }

                boolean hasPassword = StringUtils.isNotBlank(password) || StringUtils.isNotBlank(passwordConfirm);
                boolean passwordMatches = StringUtils.equals(password, passwordConfirm);
                if (hasPassword && !passwordMatches) {
                    Validation.addError(String.format("%s.passwordConfirm", formName), "error.password.doNotMatch");
                }
            }
        }

        /**
         * Clears all other validation error, except for the specified fields.
         * 
         * @param formName
         *            the form name.
         * @param fieldsToKeep
         *            the fields to keep.
         */
        protected void clearOtherErrors(String formName, String... fieldsToKeep) {
            Set<play.data.validation.Error> errors = Sets.newHashSet();
            for (String name : fieldsToKeep) {
                play.data.validation.Error error = Validation.error(String.format("%s.%s", formName, name));
                if (error != null) {
                    errors.add(error);
                }
            }
            Validation.clear();
            for (play.data.validation.Error error : errors) {
                Validation.addError(error.getKey(), error.message());
            }
        }

        protected void validateManualHost(String formName) {
            clearOtherErrors(formName, "name", "hostname");
        }

        public void save(boolean validateConnection) {
            if (isNew()) {
                createHost(validateConnection);
            }
            else {
                updateHost(validateConnection);
            }
        }

        protected Task<HostRestRep> createHost(boolean validateConnection) {
            HostCreateParam hostCreateParam = new HostCreateParam();
            doWriteTo(hostCreateParam);
            return HostUtils.createHost(hostCreateParam, validateConnection);
        }

        protected Task<HostRestRep> updateHost(boolean validateConnection) {
            HostUpdateParam hostUpdateParam = new HostUpdateParam();
            doWriteTo(hostUpdateParam);
            return HostUtils.updateHost(uri(id), hostUpdateParam, validateConnection);
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }
    
	}

//End of class for hosts
	
	//Begin class for Vcenters
	public static class VCenterForm {

        public static final int DEFAULT_PORT = 443;

        public String id;

        public Set<String> tenants;
        public String tenant;

        @Required
        @MaxSize(128)
        @MinSize(2)
        public String name;

        @Required
        @HostNameOrIpAddress
        public String hostname;

        @Required
        @MaxSize(1024)
        public String username;

        @MaxSize(1024)
        public String password;

        public String passwordConfirm;

        @Required
        @Min(1)
        public Integer port = DEFAULT_PORT;

        public Boolean cascadeTenancy = Boolean.FALSE;

        public VCenterForm() {
        }

        public VCenterForm(VcenterRestRep vCenter) {
            this();
            doReadFrom(vCenter);
        }

        public void doReadFrom(VcenterRestRep vCenter) {
            this.id = vCenter.getId().toString();
            this.name = vCenter.getName();
            this.hostname = vCenter.getIpAddress();
            this.username = vCenter.getUsername();
            this.port = vCenter.getPortNumber();
            this.cascadeTenancy = vCenter.getCascadeTenancy();
            doReadAcls();
        }

        public void doReadAcls() {
            List<ACLEntry> aclEntries = VCenterUtils.getAcl(URI.create(this.id));
            if (CollectionUtils.isEmpty(aclEntries)) {
                if (!CollectionUtils.isEmpty(this.tenants)) {
                    this.tenants.clear();
                    this.tenant = "";
                }
                return;
            }

            this.tenants = new HashSet<String>();
            if (aclEntries.size() > 1) {
                Iterator<ACLEntry> aclIt = aclEntries.iterator();
                while (aclIt.hasNext()) {
                    this.tenants.add(aclIt.next().getTenant());
                }
                renderArgs.put("disableCascadeTenancy", true);
            } else {
                ACLEntry aclEntry = aclEntries.iterator().next();
                this.tenant = aclEntry.getTenant();
                this.tenants.add(aclEntry.getTenant());
            }
        }

        public void doWriteTo(VcenterCreateParam vcenterCreateParam) {
            doWriteToBase(vcenterCreateParam);
            vcenterCreateParam.setIpAddress(this.hostname);
        }

        public void doWriteTo(VcenterUpdateParam vcenterUpdateParam) {
            doWriteToBase(vcenterUpdateParam);
            vcenterUpdateParam.setIpAddress(this.hostname);
        }

        public void doWriteToBase(VcenterParam vCenter) {
            vCenter.setName(this.name);
            vCenter.setUserName(this.username);
            if (StringUtils.isNotBlank(this.password)) {
                vCenter.setPassword(StringUtils.trimToNull(this.password));
            }
            vCenter.setPortNumber(this.port);
            vCenter.setCascadeTenancy(this.cascadeTenancy);
        }

        public ACLAssignmentChanges getAclAssignmentChanges() {
            Set<String> tenantIds = Sets.newHashSet();

            if (this.cascadeTenancy) {
                if(StringUtils.isNotBlank(this.tenant) &&
                        !this.tenant.equalsIgnoreCase(NullColumnValueGetter.getNullStr().toString())) {
                    tenantIds.add(this.tenant);
                }
            } else if (!CollectionUtils.isEmpty(this.tenants)) {
                tenantIds.addAll(this.tenants);
            }

            List<ACLEntry> existingAcls = new ArrayList<ACLEntry>();
            if (StringUtils.isNotBlank(this.id)) {
                existingAcls = VCenterUtils.getAcl(URI.create(this.id));
            }
            ACLUpdateBuilder builder = new ACLUpdateBuilder(existingAcls);
            builder.setTenants(tenantIds);

            return builder.getACLUpdate();
        }

        public void doValidation(String formName) {
            if (this.isNew()) {
                Validation.required(String.format("%s.password", formName), this.password);
            }

            boolean hasPassword = StringUtils.isNotBlank(password) || StringUtils.isNotBlank(passwordConfirm);
            boolean passwordMatches = StringUtils.equals(password, passwordConfirm);
            if (hasPassword && !passwordMatches) {
                Validation.addError(String.format("%s.passwordConfirm", formName), "error.password.doNotMatch");
            }
        }

        public void validate(String formName) {
            Validation.valid(formName, this);
            doValidation(formName);
        }

        public void save(boolean validateConnection) {
            if (isNew()) {
                try {
                    createVCenter(validateConnection);
                } catch (Exception e) {
                    flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                    Common.handleError();
                }

            } else {
                try {
                    updateVCenter(validateConnection);
                } catch (Exception e) {
                    flash.error(MessagesUtils.get("validation.vcenter.messageAndError", e.getMessage()));
                    Common.handleError();
                }
            }
        }

        protected Task<VcenterRestRep> createVCenter(boolean validateConnection) {
            VcenterCreateParam vcenterCreateParam = new VcenterCreateParam();
            doWriteTo(vcenterCreateParam);

            if (Security.isSystemAdmin()) {
                return VCenterUtils.createVCenter(vcenterCreateParam, validateConnection, getAclAssignmentChanges());
            }

            vcenterCreateParam.setCascadeTenancy(Boolean.TRUE);
            return VCenterUtils.createVCenter(TenantUtils.getTenantFilter(Models.currentAdminTenantForVcenter()),
                    vcenterCreateParam, validateConnection);
        }

        protected Task<VcenterRestRep> updateVCenter(boolean validateConnection) {
            VcenterUpdateParam vcenterUpdateParam = new VcenterUpdateParam();
            doWriteTo(vcenterUpdateParam);

            if (Security.isSystemAdmin()) {
                ACLAssignmentChanges aclAssignmentChanges = getAclAssignmentChanges();
                return VCenterUtils.updateVCenter(uri(id), vcenterUpdateParam, validateConnection,
                        aclAssignmentChanges);
            } else {
                VcenterRestRep vcenterRestRep = VCenterUtils.getVCenter(uri(id));
                vcenterUpdateParam.setCascadeTenancy(vcenterRestRep.getCascadeTenancy());

                return VCenterUtils.updateVCenter(uri(id), vcenterUpdateParam, validateConnection, null);
            }
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void setTenantsForCreation() {
            this.tenants = new HashSet<String>();
            if (StringUtils.isNotBlank(Models.currentAdminTenantForVcenter()) &&
                    Models.currentAdminTenantForVcenter().equalsIgnoreCase(TenantUtils.getNoTenantSelector())) {
                List<TenantOrgRestRep> allTenants = TenantUtils.getAllTenants();
                Iterator<TenantOrgRestRep> tenantsIterator = allTenants.iterator();
                while (tenantsIterator.hasNext()) {
                    TenantOrgRestRep tenant = tenantsIterator.next();
                    if (tenant == null) {
                        continue;
                    }
                    this.tenants.add(tenant.getId().toString());
                    this.cascadeTenancy = Boolean.FALSE;
                }
            } else if (StringUtils.isNotBlank(Models.currentAdminTenantForVcenter()) &&
                    !Models.currentAdminTenantForVcenter().equalsIgnoreCase(TenantUtils.getTenantSelectorForUnassigned())) {
                this.tenants.clear();
                this.tenant = Models.currentAdminTenantForVcenter();
                this.cascadeTenancy = Boolean.TRUE;
            }
        }
    }
	//End class for Vcenters
	
	//Begin class for Switches
	 public static class SanSwitchForm {

	        public String id;

	        @Required
	        public String deviceType;

	        @MaxSize(128)
	        @MinSize(2)
	        @Required
	        public String name;

	        @HostNameOrIpAddress
	        @Required
	        public String ipAddress;

	        @Required
	        public Integer portNumber;

	        @MaxSize(2048)
	        public String userName;

	        @MaxSize(2048)
	        public String userPassword = ""; // NOSONAR ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

	        @MaxSize(2048)
	        public String confirmPassword = ""; // NOSONAR
	                                            // ("Suppressing Sonar violation of Password Hardcoded. Password is not hardcoded here.")

	        public boolean useSSL;

	        public Date lastDiscoveryRunTime;

	        public SanSwitchForm() {
	        }

	        public SanSwitchForm(NetworkSystemRestRep networkSystemResponse) {
	            readFrom(networkSystemResponse);
	        }

	        public void readFrom(NetworkSystemRestRep sanSwitch) {

	            this.id = stringId(sanSwitch);
	            this.name = sanSwitch.getName();
	            this.deviceType = sanSwitch.getSystemType();
	            if (NetworkSystemTypes.isSmisManaged(sanSwitch.getSystemType())) {
	                this.ipAddress = sanSwitch.getSmisProviderIP();
	                this.userName = sanSwitch.getSmisUserName();
	                this.portNumber = sanSwitch.getSmisPortNumber();
	                this.useSSL = sanSwitch.getSmisUseSSL();
	            }
	            else {
	                this.ipAddress = sanSwitch.getIpAddress();
	                this.userName = sanSwitch.getUsername();
	                this.portNumber = sanSwitch.getPortNumber();
	                this.useSSL = false;
	            }
	        }

	        public boolean isNew() {
	            return StringUtils.isBlank(id);
	        }

	        public Task<NetworkSystemRestRep> save() {
	            if (isNew()) {
	                return create();
	            }
	            else {
	                return update();
	            }
	        }

	        private Task<NetworkSystemRestRep> update() {
	            NetworkSystemUpdate sanSwitchParam = new NetworkSystemUpdate();

	            sanSwitchParam.setName(this.name);
	            // sanSwitchParam.setSystemType(this.deviceType);
	            sanSwitchParam.setIpAddress(this.ipAddress);
	            sanSwitchParam.setPortNumber(this.portNumber);
	            sanSwitchParam.setUserName(StringUtils.trimToNull(this.userName));
	            sanSwitchParam.setPassword(StringUtils.trimToNull(this.userPassword));
	            sanSwitchParam.setSmisProviderIp(this.ipAddress);
	            sanSwitchParam.setSmisPortNumber(this.portNumber);
	            sanSwitchParam.setSmisUserName(StringUtils.trimToNull(this.userName));
	            sanSwitchParam.setSmisPassword(StringUtils.trimToNull(this.userPassword));
	            sanSwitchParam.setSmisUseSsl(this.useSSL);

	            return NetworkSystemUtils.update(this.id, sanSwitchParam);
	        }

	        private Task<NetworkSystemRestRep> create() {
	            NetworkSystemCreate sanSwitchParam = new NetworkSystemCreate();
	            sanSwitchParam.setName(this.name);
	            sanSwitchParam.setUserName(this.userName);
	            sanSwitchParam.setPassword(StringUtils.trimToNull(this.userPassword));
	            sanSwitchParam.setPortNumber(this.portNumber);
	            sanSwitchParam.setIpAddress(this.ipAddress);
	            sanSwitchParam.setSystemType(this.deviceType);
	            sanSwitchParam.setSmisProviderIp(this.ipAddress);
	            sanSwitchParam.setSmisPortNumber(this.portNumber);
	            sanSwitchParam.setSmisUserName(this.userName);
	            sanSwitchParam.setSmisPassword(StringUtils.trimToNull(this.userPassword));
	            sanSwitchParam.setSmisUseSsl(this.useSSL);

	            Task<NetworkSystemRestRep> task = NetworkSystemUtils.create(sanSwitchParam);
	            this.id = stringId(task.getResource());
	            return task;
	        }

	        public void validate(String fieldName) {
	            Validation.valid(fieldName, this);

	            if (isNew()) {
	                Validation.required(fieldName + ".userName", this.userName);
	                Validation.required(fieldName + ".userPassword", this.userPassword);
	                Validation.required(fieldName + ".confirmPassword", this.confirmPassword);
	            }
	            if (!StringUtils.equals(StringUtils.trimToEmpty(this.userPassword),
	                    StringUtils.trimToEmpty(this.confirmPassword))) {
	                Validation.addError(fieldName + ".confirmPassword", "error.password.doNotMatch");
	            }
	            // Validation.ipv4Address(fieldName + ".ipAddress", this.ipAddress);
	        }

	    }
	//End class for Switches
	
	
	 // Arrays that hold all the arguments.
	 static String _device[]= new String[100];
  	 static String _type[]= new String[100];
  	 static String _name[]= new String[100];
  	 static String _host[]= new String[100];
  	 static String _port[]= new String[100];
  	 static String _ssl[]= new String[100];
  	 static String _usr1[]= new String[100];
  	 static String _pass1[]= new String[100];
  	 static String _proto[]= new String[100];

  	 // Counters for all the arguments
  	static int a=0;
  	static int b=0;
  	static int c=0;
  	static int d=0;
  	static int e=0;
  	static int f=0;
  	static int g=0;
  	static int h=0;
  	static int j=0;

    private static Keystore api() {
        return BourneUtil.getViprClient().keystore();
    }

    public static void updateCertificate() { // The method that gets called when we select the Discovery Automation option.
        KeystoreForm keystore = new KeystoreForm();
        String viewChain = viewChain(keystore);
        render(viewChain, keystore);
        Importer impert= new Importer();
    }

    
    public static int discoverArray(int r)

	{  String name=null;
       String systemType=null;
       String ipAddress=null;
       Integer portNumber=0;
       String userName=null;
       String password=null;
       Boolean useSSL=false;
       String interfaceType=null;
       String secondaryUsername=null;
       String secondaryPassword=null;
       String elementManagerURL=null;
       String secretKey=null;
  	 
       
       
       name=_name[r];
       if(_type[r].equals(smis))
       interfaceType="smis";
       ipAddress=_host[r];
       int foo = Integer.parseInt(_port[r]);
       portNumber=foo;
       userName=_usr1[r];
       password=_pass1[r];
       boolean boolean2 = Boolean.parseBoolean(_ssl[r]);
       useSSL=boolean2;
       
       System.out.println("Invoked the storage provider create Class");
  	   	Task<StorageProviderRestRep> task = StorageProviderUtils.create(name, ipAddress, portNumber, userName, password,useSSL, interfaceType, secondaryUsername, secondaryPassword, elementManagerURL, secretKey);
       new SaveWaitJob(getViprClient(), task).now();
       System.out.println("Storage Provider Successfully added");
       return 0;
	 }
    
    public static int discoverHost(int s)
    {
    		addReferenceData();
	  		HostForm hostfr = new HostForm();
	  		hostfr.tenantId = Models.currentAdminTenant();
  
		 	boolean validateConnection = false;
	  		
	  		hostfr.name = _name[s];
	  		hostfr.hostname = _host[s];
	  		int foo = Integer.parseInt(_port[s]);
	  		hostfr.port =foo;
	  		System.out.println("Check here");
	  		
	  		if(_type[s].equals(win))
	  		hostfr.type ="Windows";
	  		else if (_type[s].equals(lin))
	  		hostfr.type="Linux";
	  		
	  		System.out.println("Check here");
	  		hostfr.username = _usr1[s];
	  		hostfr.password = _pass1[s];
	  		
	  		/*if(_proto[i].equals("https"))
	  			{hostfr.useHttps = true;
	  			}
	  		*/
	  		System.out.println("Called up the host");
	  		hostfr.save(validateConnection);
	  		System.out.println("HOST Successfully added");
	  		return 0;
    }
    
    
    public static int discoverVcenter(int t)
    
    {   VCenterForm vCenter = new VCenterForm();
    	vCenter.name = _name[t];
    	vCenter.hostname = _host[t];
    	vCenter.username = _usr1[t];
    	vCenter.password = _pass1[t];
    	
    	
    	
    	Boolean validateConnectionParam = params.get("validateConnection", Boolean.class);
        boolean validateConnection = validateConnectionParam != null ? validateConnectionParam.booleanValue() : false;
        
        System.out.println("Called up the VCenter api");
        vCenter.save(validateConnection);
        System.out.println("VCenter Succesfully added");
        return 0;
    }
    
    public static int discoverSanSwitch( int u)
    
    {
    	SanSwitchForm sanSwitch = new SanSwitchForm();
    	if(_type[u].equals("Cisco"))
    		_type[u] = "mds";
    	sanSwitch.deviceType = _type[u];
    	sanSwitch.name = _name[u];
    	sanSwitch.ipAddress = _host[u];
    	int foo1 = Integer.parseInt(_port[u]);
    	sanSwitch.portNumber = foo1;
    	sanSwitch.userName = _usr1[u];
    	sanSwitch.userPassword = _pass1[u];
    	
    	
    	System.out.println("Called up the SanSwitch api");
    	sanSwitch.save();
    	System.out.println("Switch Successfully added");
    	return 0;
    	
    }
    
    @FlashException(value = "updateCertificate", keep = true)
    public static void save(Importer impert) {
        
        
    	
        // Checks for the possible arguments.
    	 boolean device = false;
         boolean type = false;
         boolean name1 = false;
         boolean host = false;
         boolean port = false;
   	  	 boolean ssl = false;
   	  	 boolean usr1 = false;
   	  	 boolean pass1 = false;
   	  	 boolean proto = false;
   	  	 
   	 
   	  	 
   	  // XML Parser begins here.
         try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLEventReader eventReader =
            factory.createXMLEventReader(
               new FileReader(impert.imp));

               while(eventReader.hasNext()){
                  XMLEvent event = eventReader.nextEvent();
                  switch(event.getEventType()){
                     case XMLStreamConstants.START_ELEMENT:
                        StartElement startElement = event.asStartElement();
                        String qName = startElement.getName().getLocalPart();
                        if (qName.equalsIgnoreCase("record")) {
                           System.out.println("Start Element : record");
                           Iterator<Attribute> attributes = startElement.getAttributes();
                           
                        } else if (qName.equalsIgnoreCase("device")) {
                           device = true;
                        } else if (qName.equalsIgnoreCase("type")) {
                           type = true;
                        } else if (qName.equalsIgnoreCase("name")) {
                           name1 = true;
                        }
                        else if (qName.equalsIgnoreCase("hostip")) {
                           host = true;
                        }
                        else if (qName.equalsIgnoreCase("port")) {
                           port = true;
                        }
   					 	else if (qName.equalsIgnoreCase("ssl")) {
                           ssl = true;
                        }
   					 	else if (qName.equalsIgnoreCase("usr1")) {
                           usr1 = true;
                        }
   					 	else if (qName.equalsIgnoreCase("pass1")) {
                           pass1 = true;}
   					 	else if (qName.equalsIgnoreCase("protocol")) {
                               proto = true;
                        }
                        break;
                         
                         
   				  
                    case XMLStreamConstants.CHARACTERS:
                    	 
                    	Characters characters = event.asCharacters();
                        if(device){
                           _device[a++] = characters.getData();
                           System.out.println(_device); 
                           device = false;
                           
                        }
                        if(type){
                           _type[b++] = characters.getData();
                           type = false;
                        }
                        if(name1){
                        	_name[c++] = characters.getData();
                           name1 = false;
                        }
                        if(host){
                        	_host[d++] = characters.getData();
                           host = false;
                        }
   					   if(port){
   						_port[e++] = characters.getData();
                           port = false;
                        }
                        if(ssl){
                        	_ssl[a] = characters.getData();
                           ssl = false;
                        }
                        if(usr1){
                        	_usr1[g++] = characters.getData();
                           usr1 = false;
                        }
                        if(pass1){
                        	_pass1[h++] = characters.getData();
                           pass1 = false;
                        }
                        
                        if(proto)
                        { _proto[a] = characters.getData();
                           proto = false;
                        }
                        
                
                      	break;
                     case  XMLStreamConstants.END_ELEMENT:
                        
                    	
                    	EndElement endElement = event.asEndElement();
                        if(endElement.getName().getLocalPart().equalsIgnoreCase("record")){
                           System.out.println("End Element : record");
                           
                        }
                       
                        break;
                  }		    
               }
             // XML Parser ends here
               
              //Printing all the data.
              for(int i=0;i<4;i++)
             	{System.out.println("Record start:");
            	 System.out.println(_device[i]);
             	 System.out.println(_type[i]);
             	 System.out.println(_name[i]);
             	 System.out.println(_ssl[i]);
             	 System.out.println(_usr1[i]);
             	 System.out.println(_pass1[i]);
             	 System.out.println(_port[i]);
             	 System.out.println("Record End.");
             	
             	 if(_device[i].equals("Array"))
             	 {  discoverArray(i);
                	                       	
             	 }
             	
             	 if(_device[i].equals("Host"))
             	  { discoverHost(i);
             	     
             	  }
             	 
             	if(_device[i].equals("vmware-vcenter"))
           	  	{ discoverVcenter(i);
           	     
           	  	}
             	 
             	if(_device[i].equals("Switch"))
           	  	{ discoverSanSwitch(i);
           	     
           	  	}
             	 
             	}
             
            } catch (FileNotFoundException e) {
               e.printStackTrace();
            } catch (XMLStreamException e) {
               e.printStackTrace();
         }
         flash.success("Bootstrap initiated");
         updateCertificate();
       }
     

 public static class Importer {    
     public File imp;
        }

    public static String viewChain(KeystoreForm keystore) {
        CertificateChain chain = BourneUtil.getViprClient().keystore().getCertificateChain();
        if (chain == null || chain.getChain() == null || chain.getChain().isEmpty()) {
            flash.error(MessagesUtils.get("vdc.certChain.empty.error"));
        }
        return chain.getChain();
    }

    private static void handleError(KeystoreForm form) {
        params.flash();
        Validation.keep();
        updateCertificate();
    }

    public static class KeystoreForm {

        public boolean rotate;
        public File certChain;
        public File certKey;

        public void validate(String formName) {

            if (!this.rotate) {
                Validation.required(formName + ".certKey", certKey);
                Validation.required(formName + ".certChain", certChain);
            }

        }
    }

}
