/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.core;

import java.net.URI;
import java.util.List;

import com.emc.storageos.model.auth.ACLAssignmentChanges;
import com.emc.storageos.model.auth.ACLEntry;

/**
 * Interface for resources which support ACLs.
 */
public interface ACLResources {
    /**
     * Gets the ACL entries for a given resource by ID.
     * <p>
     * API Call: GET <tt><i>baseUrl</i>/{id}/acl</tt>
     * 
     * @param id
     *        the resource ID.
     * @return the list of ACL entries.
     */
    public List<ACLEntry> getACLs(URI id);

    /**
     * Updates the ACL entries for a given resource by ID.
     * <p>
     * API Call: PUT <tt><i>baseUrl</i>/{id}/acl</tt>
     * 
     * @param id
     *        the resource ID.
     * @param aclChanges
     *        the ACL changes to perform (ACL entries to add/remove).
     * @return the resulting list of ACL entries after performing the update.
     */
    public List<ACLEntry> updateACLs(URI id, ACLAssignmentChanges aclChanges);
}
