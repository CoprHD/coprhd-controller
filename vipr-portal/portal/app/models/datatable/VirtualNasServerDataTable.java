package models.datatable;

import static util.BourneUtil.getViprClient;

import java.util.Set;

import util.datatable.DataTable;

import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.vnas.VirtualNASRestRep;

public class VirtualNasServerDataTable extends DataTable {

    public VirtualNasServerDataTable() {
        addColumn("id").hidden();
        addColumn("nasName");
        addColumn("registrationStatus").setRenderFunction("render.registrationStatus");
        addColumn("project");
        addColumn("storageDeviceURI").hidden();
        addColumn("maxFSID").hidden();
        addColumn("maxExports").hidden();
        addColumn("maxProvisionedCapacity").hidden();
        addColumn("protocols");
        addColumn("parentNASURI");
        addColumn("storageDomain");
        addColumn("cifsServers").hidden();;
        addColumn("storagePorts").hidden();;
        addColumn("nasState");
        
        
        addColumn("compatibilityStatus").hidden();
        addColumn("discoveryStatus").hidden();;
        addColumn("vNasTag").hidden();
        
        addColumn("vNasType").hidden();
        addColumn("baseDirPath").hidden();;
        
        sortAll();
        setDefaultSort("name", "asc");
        
      
    }

    public static class VirtualNasServerInfo {
        
    	private String id;
        // NAS Server name
        private String nasName;
        
        
        // storageSystem, which it belongs
        private String storageDeviceURI;
        private String maxFSID;
        private String maxExports;
        private String maxProvisionedCapacity;
        private Set<String> protocols;
        
        // Set of Authentication providers for the VNasServer - set values will of type AunthnProvider
        private Set<String> cifsServers;
        
        // List of Storage Ports associated with this Nas Server
        private String storagePorts;
        
        // State of the NAS server
        private String nasState;
        
        
        // Place holder for hosting storageDomain's information
        private String storageDomain;
        
        private String registrationStatus ;
        private String compatibilityStatus; 
        private String discoveryStatus ;
        
        // Place holder for Tag
        private Set<String> nasTag;
        
        
        // Project name which this VNAS belongs to
        private String project = "";

        private String vNasType;

        // Base directory Path for the VNAS applicable in AccessZones & vFiler device types
        private String baseDirPath;

        // place holder for the Parent NAS server the Data Mover
        private String parentNASURI;
        
        public VirtualNasServerInfo(VirtualNASRestRep vNasRestRep){
           this.id = vNasRestRep.getId().toString();
           this.nasName = vNasRestRep.getNasName();
           this.storageDeviceURI = (vNasRestRep.getStorageDeviceURI() != null) ? vNasRestRep.getStorageDeviceURI().toString() : "";
           if(vNasRestRep.getProject() != null){
        	   ProjectRestRep projectRestRep = getViprClient().projects().get(vNasRestRep.getProject().getId());
               this.project = projectRestRep.getName();
           }
           this.maxExports = vNasRestRep.getMaxExports();
           this.maxFSID = vNasRestRep.getMaxFSID();
           this.maxProvisionedCapacity = vNasRestRep.getMaxProvisionedCapacity();
           this.protocols = vNasRestRep.getProtocols();
           this.baseDirPath = vNasRestRep.getBaseDirPath();
           this.nasTag = vNasRestRep.getTags();
           this.nasState = vNasRestRep.getNasState();
           this.cifsServers = vNasRestRep.getCifsServers();
           this.storagePorts = (vNasRestRep.getStoragePorts() != null) ? vNasRestRep.getStoragePorts().toString() : "";
           this.storageDomain = (vNasRestRep.getStorageDomain() != null) ? vNasRestRep.getStorageDomain().toString() : "";
           if(vNasRestRep.getParentNASURI() != null){
               this.parentNASURI = vNasRestRep.getParentNASURI().getLink().getLinkName(); 
           }
           this.registrationStatus = vNasRestRep.getRegistrationStatus();
           this.compatibilityStatus = vNasRestRep.getCompatibilityStatus();
           this.discoveryStatus = vNasRestRep.getDiscoveryStatus(); 
           
        }
    }

}
