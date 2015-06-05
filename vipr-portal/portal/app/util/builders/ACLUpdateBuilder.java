/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.builders;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import models.ACLs;

import org.apache.commons.lang.StringUtils;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;
import com.google.common.collect.Sets;

public class ACLUpdateBuilder {
    private List<ACLEntry> entries;
    private ACLAssignmentChanges changes;

    public ACLUpdateBuilder(List<ACLEntry> entries) {
        this.entries = entries;
        this.changes = new ACLAssignmentChanges();
    }

    public ACLAssignmentChanges getACLUpdate() {
        return changes;
    }

    public void setTenants(Collection<String> tenants) {
        Set<String> newTenants = Sets.newHashSet(tenants);

        Set<ACLEntry> remove = Sets.newHashSet();
        for (ACLEntry entry : getTenantACLs()) {
            // If the tenant is not in the set, remove the entry
            if (!tenants.contains(entry.getTenant())) {
                remove.add(entry);
            }
            // Otherwise remove the tenant from the 'newTenants' set
            else {
                newTenants.remove(entry.getTenant());
            }
        }

        // Create ACLs for any remaining 'new' tenants
        Set<ACLEntry> add = createTenantACLs(newTenants);

        // Update the ACL changes
        changes.getAdd().addAll(add);
        changes.getRemove().addAll(remove);
    }

    private Set<ACLEntry> getTenantACLs() {
        Set<ACLEntry> tenantACLs = Sets.newHashSet();
        for (ACLEntry entry : entries) {
            if (StringUtils.isNotBlank(entry.getTenant())) {
                tenantACLs.add(entry);
            }
        }
        return tenantACLs;
    }

    private Set<ACLEntry> createTenantACLs(Set<String> tenants) {
        Set<ACLEntry> entries = Sets.newHashSet();
        for (String tenant : tenants) {
            ACLEntry entry = new ACLEntry();
            entry.setTenant(tenant);
            entry.getAces().add(ACLs.USE);
            entries.add(entry);
        }
        return entries;
    }
}
