/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;


import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.application.VolumeGroupCopySetList;
import com.emc.storageos.model.application.VolumeGroupCreateParam;
import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.storageos.model.application.VolumeGroupUpdateParam;
import com.emc.storageos.model.block.NamedVolumesList;


/**
 * Utility for application support.
 * 
 * @author hr2
 */

public class AppSupportUtil {
    
    public static List<NamedRelatedResourceRep> getApplications() {
        return BourneUtil.getViprClient().application().getApplications().getVolumeGroups();
    }
    
    public static VolumeGroupRestRep createApplication(String name, String description, Set<String> roles){
        VolumeGroupCreateParam create = new VolumeGroupCreateParam();
        create.setName(name);
        create.setDescription(description);
        create.setRoles(roles);
        return BourneUtil.getViprClient().application().createApplication(create);
    }
    
    public static void deleteApplication(URI id) {
        BourneUtil.getViprClient().application().deleteApplication(id);
    }
    
    public static TaskList updateApplication(String name, String description, String id) {
        VolumeGroupUpdateParam update = new VolumeGroupUpdateParam();
        if(!name.isEmpty()) {
            update.setName(name);
        }
        if(!description.isEmpty()) {
            update.setDescription(description);
        }
        return BourneUtil.getViprClient().application().updateApplication(uri(id), update);
    }
    
    public static VolumeGroupRestRep getApplication(String id) {
        return BourneUtil.getViprClient().application().getApplication(uri(id));
    }
    
    public static List<NamedRelatedResourceRep> getVolumesByApplication(String id) {
        return BourneUtil.getViprClient().application().getVolumeByApplication(uri(id)).getVolumes();
    }
    
    public static List<NamedRelatedResourceRep> getFullCopiesByApplication(String id) {
    	return BourneUtil.getViprClient().application().getClonesByApplication(uri(id)).getVolumes();
    }
    
    public static Set<String> getFullCopySetsByApplication(String id) {
        return BourneUtil.getViprClient().application().getFullCopySetsByApplication(uri(id)).getCopySets();
    }
    
    public static List<NamedRelatedResourceRep> getVolumeGroupFullCopiesForSet(String id, Set<String> copySets) {
        VolumeGroupCopySetList getSetsForCopies = new VolumeGroupCopySetList();
        getSetsForCopies.setCopySets(copySets);
        return BourneUtil.getViprClient().application().getVolumeGroupFullCopiesForSet(uri(id), getSetsForCopies).getVolumes();
    }
}
