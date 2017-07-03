package controllers.resources;

import static com.emc.vipr.client.core.util.ResourceUtils.id;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.db.client.model.UnManagedDiscoveredObject.ExportType;
import com.emc.storageos.model.project.ProjectRestRep;
import com.emc.storageos.model.search.SearchResultResourceRep;
import com.emc.vipr.client.ViPRCoreClient;

import controllers.util.Models;
import models.datatable.BlockVolumesDataTable;
import util.BourneUtil;

public class SearchResources extends ResourceController {
    // searchable resources
	// all of them can be searched based on label, project and tag
	// Some have extra search parameters
	public enum resource {
    	VOLUMES("/block/volumes/search","wwn","personality","protection","virtual_array"), 
    	FILESYSTEMS("/file/filesystems/search","mountpath"), 
    	EXPORT_GROUPS("/block/exports/search","host", "cluster", "initiator","self_only"),
    	BLOCK_SNAPSHOTS("/block/snapshots/search"),
    	BLOCK_CONSISTENCY_GROUPS("/block/consistencygroups/search"),
    	FILE_SNAPSHOTS("/file/snapshots/search");
    	
		private List<String> extraParameters;
		private String baseUri;
    	resource(String baseUri, String ...extraParameters) {
    		this.baseUri = baseUri;
    	    this.extraParameters = Arrays.asList(extraParameters);
    	}

    	private static final resource[] resources = values();
        public static resource lookup(final String name) {
            for (resource value : resources) {
                if (value.name().equalsIgnoreCase(name)) {
                    return value;
                }
            }
            return null;
        }


    }
	
	public void search(resource resource, 
			           String Label, String projectName, String tag, String extraParameter, String value){
		String URI = resource.baseUri;
		
		if (StringUtils.isNotBlank(projectName)){
			URI += "?project="+getProjectId(projectName);
			if (StringUtils.isBlank(Label)){
				// case 1: project only
			}
			else {	
				// case 2: project and Label
				URI += "&Label="+Label;
			}
		}
		else {
			// Check for tag
			if (StringUtils.isNotBlank(tag)){
				// case 3: tag only
				URI += "?tag="+tag;
			}
			else{
				if (!resource.extraParameters.isEmpty()){
					// case 4: extra parameters
					if (resource.extraParameters.contains(extraParameter)){
						URI += "?"+extraParameter+"="+value;
					}
				}
			}
		}
		// Now send request
        ViPRCoreClient client = BourneUtil.getViprClient();
        List<SearchResultResourceRep> results = client.
        
        
        switch (resource){
        case VOLUMES:
        	com.emc.vipr.client.core.BlockVolumes volumes = client.blockVolumes();
        	Map <String,Object> params;
            

        	
        }

		
		
		
		
		
		// case 4: extra parameter
		
	}

	private String getProjectId(String projectName) {
        String tenantId = Models.currentAdminTenant();
        List<ProjectRestRep> projects = getProjects(tenantId);
        if (!projects.isEmpty()) {
        	for (ProjectRestRep project: projects){
        		if (project.getName().equalsIgnoreCase(projectName)){
        			return project.getId().toString();
        		}
        	}
        }
        // not found
        return "";
	}
	
	
}
