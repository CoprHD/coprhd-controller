/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;



import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.io.File;
import models.HostTypes;
import models.NetworkSystemTypes;

import org.apache.commons.lang.StringUtils;
import org.springframework.util.CollectionUtils;

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
import com.google.common.collect.Sets;

import controllers.Common;
import controllers.arrays.StorageProviders.*;
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

import java.net.URI;
import java.util.List;

import util.ClusterUtils;
import util.StringOption;
import util.TenantUtils;
import util.VCenterUtils;
import util.builders.ACLUpdateBuilder;

import com.emc.storageos.model.host.vcenter.VcenterCreateParam;
import com.emc.storageos.model.host.vcenter.VcenterParam;
import com.emc.storageos.model.host.vcenter.VcenterRestRep;
import com.emc.storageos.model.host.vcenter.VcenterUpdateParam;
import com.emc.storageos.model.network.NetworkSystemCreate;
import com.emc.storageos.model.network.NetworkSystemRestRep;
import com.emc.storageos.model.network.NetworkSystemUpdate;
import com.emc.storageos.model.project.ProjectRestRep;

import controllers.compute.Hosts.HostForm;
import controllers.compute.VCenters.VCenterForm;
import controllers.arrays.SanSwitches.SanSwitchForm;
import controllers.util.Models;
//hosts

@With(Common.class)
@Restrictions({ @Restrict("SECURITY_ADMIN"),
        @Restrict("RESTRICTED_SECURITY_ADMIN") })
public class AutoDiscovery extends ViprResourceController {
	
	
	 protected static final String smis = "SMI-VMAX";
	 protected static final String win = "windows";
	 protected static final String lin = "linux";
	 protected static final String SAVED = "Hosts.saved";
	
	// Arrays that hold all the arguments.
     public static String _device[]= new String[100];
     static String _type[]= new String[100];
     static String _name[]= new String[100];
     static String _host[]= new String[100];
     static String _port[]= new String[100];
     static String _ssl[]= new String[100];
     static String _usr1[]= new String[100];
     static String _pass1[]= new String[100];
     static String _proto[]= new String[100];

     // Counters for all the arguments
    public static int a,b,c,d,e,f,g,h,j=0;
	
//	private static void addReferenceData() {
//        renderArgs.put("types", StringOption.options(HostTypes.STANDARD_CREATION_TYPES, HostTypes.OPTION_PREFIX, false));
//        renderArgs.put("clusters", ClusterUtils.getClusterOptions(Models.currentTenant()));
//
//        List<ProjectRestRep> projects = getViprClient().projects().getByTenant(ResourceUtils.uri(Models.currentTenant()));
//        renderArgs.put("projects", projects);
//    }

    public static void updateCertificate() {
        render();
    }

    
    public static void discoverArray(int r) {

  	 
       StorageProviderForm providerForm = new StorageProviderForm();
       
       providerForm.name=_name[r];
       if(_type[r].equals(smis))
           providerForm.interfaceType="smis";
       providerForm.ipAddress=_host[r];
       int foo = Integer.parseInt(_port[r]);
       providerForm.portNumber=foo;
       providerForm.userName=_usr1[r];
       providerForm.password=_pass1[r];
       boolean boolean2 = Boolean.parseBoolean(_ssl[r]);
       providerForm.useSSL=boolean2;
       
       providerForm.save();
	 }
    
    public static void discoverHost(int s)
    {
	  		HostForm hostfr = new HostForm();
	  		hostfr.tenantId = Models.currentAdminTenant();
  
		 	boolean validateConnection = false;
	  		
	  		hostfr.name = _name[s];
	  		hostfr.hostname = _host[s];
	  		int foo = Integer.parseInt(_port[s]);
	  		hostfr.port =foo;
	  		
	  		if(_type[s].equals(win))
	  		hostfr.type ="Windows";
	  		else if (_type[s].equals(lin))
	  		hostfr.type="Linux";
	  		
	  		hostfr.username = _usr1[s];
	  		hostfr.password = _pass1[s];
	  		
	  		/*if(_proto[i].equals("https"))
	  			{hostfr.useHttps = true;
	  			}
	  		*/
	  		hostfr.save(validateConnection);
    }
    
    
    public static void discoverVcenter(int t)
    
    {   VCenterForm vCenter = new VCenterForm();
    	vCenter.name = _name[t];
    	vCenter.hostname = _host[t];
    	vCenter.username = _usr1[t];
    	vCenter.password = _pass1[t];    	
    	
    	Boolean validateConnectionParam = params.get("validateConnection", Boolean.class);
        boolean validateConnection = validateConnectionParam != null ? validateConnectionParam.booleanValue() : false;
        
        vCenter.save(validateConnection);
    }
    
    public static void discoverSanSwitch( int u)
    
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
    	sanSwitch.save();
    	
    }
    
    @FlashException(value = "updateCertificate", keep = true)
    public static void save(AutoDiscoveryForm impert) {
    	
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
                           
                        }
                       
                        break;
                  }		    
               }
             // XML Parser ends here
               
              //Printing all the data.
              for(int i=0;i<4;i++)
             	{
             	
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
     

    public static class AutoDiscoveryForm {
        public File imp;
    }

}
