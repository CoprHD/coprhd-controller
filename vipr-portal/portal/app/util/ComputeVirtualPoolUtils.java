/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.BourneUtil.getViprClient;

import java.net.URI;
import java.util.Collection;
import java.util.List;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.emc.storageos.model.compute.ComputeElementListRestRep;
import com.emc.storageos.model.compute.ComputeElementRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolCreateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolElementUpdateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolRestRep;
import com.emc.storageos.model.vpool.ComputeVirtualPoolUpdateParam;
import com.emc.storageos.model.vpool.ComputeVirtualPoolAssignmentChanges;
import com.emc.storageos.model.vpool.ComputeVirtualPoolAssignments;
import com.emc.vipr.client.core.filters.ResourceFilter;
import com.emc.vipr.client.core.util.CachedResources;
import com.emc.vipr.client.exceptions.ViPRHttpException;

import controllers.security.Security;

public class ComputeVirtualPoolUtils {

    public static boolean canUpdateACLs() {
        return Security.hasAnyRole(Security.SECURITY_ADMIN, Security.SYSTEM_ADMIN, Security.RESTRICTED_SYSTEM_ADMIN);
    }
	
    public static CachedResources<ComputeVirtualPoolRestRep> createBlockCache() {
        return new CachedResources<ComputeVirtualPoolRestRep>(getViprClient().computeVpools());
    }

    public static ComputeVirtualPoolRestRep getComputeVirtualPool(String id) {
        return getComputeVirtualPool(uri(id));
    }

    public static ComputeVirtualPoolRestRep getComputeVirtualPool(URI id) {
        try {
            return getViprClient().computeVpools().get(id);
        }
        catch (ViPRHttpException e) {
            if (e.getHttpCode() == 404) {
                return null;
            }
            throw e;
        }
    }
    
    public static List<ComputeVirtualPoolRestRep> getComputeVirtualPools() {
        return getViprClient().computeVpools().getAll();
    }
    
    public static List<ComputeVirtualPoolRestRep> getComputeVirtualPools(ResourceFilter<ComputeVirtualPoolRestRep> filter) {
        return getViprClient().computeVpools().getAll(filter);
    }

    public static List<ComputeVirtualPoolRestRep> getComputeVirtualPools(Collection<URI> ids) {
        return getViprClient().computeVpools().getByIds(ids);
    }

    public static ComputeVirtualPoolRestRep create(ComputeVirtualPoolCreateParam virtualPool) {
        return getViprClient().computeVpools().create(virtualPool);
    }

    public static ComputeVirtualPoolRestRep update(String id, ComputeVirtualPoolUpdateParam virtualPool) {
        return getViprClient().computeVpools().update(uri(id), virtualPool);
    }

    public static void deactivateCompute(URI id) {
        getViprClient().computeVpools().deactivate(id);
    }

    public static List<ComputeElementRestRep> listMatchingComputeElements(ComputeVirtualPoolCreateParam virtualPool) {
        return getViprClient().computeVpools().listMatchingComputeElements(virtualPool);
    }    

    public static List<ACLEntry> getComputeACLs(String id) {
        return getViprClient().computeVpools().getACLs(uri(id));
    }

    public static List<ACLEntry> updateComputeACLs(String id, ACLAssignmentChanges changes) {
        return getViprClient().computeVpools().updateACLs(uri(id), changes);
    }

    public static ComputeVirtualPoolRestRep updateAssignedComputeElements(String id, Collection<String> addElements, Collection<String> removeElements) {
        return getViprClient().computeVpools().assignComputeElements(uri(id), createElementAssignments(addElements, removeElements));
    }

    private static ComputeVirtualPoolElementUpdateParam createElementAssignments(Collection<String> addElements,
            Collection<String> removeElements) {
        ComputeVirtualPoolAssignmentChanges changes = new ComputeVirtualPoolAssignmentChanges();
        if (addElements != null && addElements.size() > 0) {
            ComputeVirtualPoolAssignments add = new ComputeVirtualPoolAssignments();
            add.getComputeElements().addAll(addElements);
            changes.setAdd(add);
        }
        if (removeElements != null && removeElements.size() > 0) {
            ComputeVirtualPoolAssignments remove = new ComputeVirtualPoolAssignments();
            remove.getComputeElements().addAll(removeElements);
            changes.setRemove(remove);
        }
        return new ComputeVirtualPoolElementUpdateParam(changes);
    }

    public static ComputeElementListRestRep getAssignedComputeElements(String id) {
        return getViprClient().computeVpools().getMatchedComputeElements(uri(id));
    }    
    
}
