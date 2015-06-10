/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Authentication provider object for PUT
 */
@XmlRootElement(name = "authnprovider_update")
public class AuthnUpdateParam extends AuthnProviderBaseParam {

    /**
     * The changes for the URL set of this provider.  URLs
     * can be added, and removed.  Up to one add element and one remove element may be specified.
     * Multiple urls can be specified in the add and remove blocks. 
     * @valid Example: see ServerUrlChanges
     */
    private ServerUrlChanges serverUrlChanges;
    private DomainChanges domainChanges;
    private GroupWhitelistValueChanges groupWhitelistValueChanges;
    private GroupObjectClassChanges groupObjectClassChanges;
    private GroupMemberAttributeChanges groupMemberAttributeChanges;

    @XmlElement(name = "server_url_changes")
    public ServerUrlChanges getServerUrlChanges() {
        if (serverUrlChanges == null) {
            serverUrlChanges = new AuthnUpdateParam.ServerUrlChanges();
        }
        return serverUrlChanges;
    }

    public void setServerUrlChanges(ServerUrlChanges serverUrlChanges) {
        this.serverUrlChanges = serverUrlChanges;
    }

    @XmlElement(name = "domain_changes")
    public DomainChanges getDomainChanges() {
        if (domainChanges == null) {
            domainChanges = new AuthnUpdateParam.DomainChanges();
        }
        return domainChanges;
    }

    public void setDomainChanges(DomainChanges domainChanges) {
        this.domainChanges = domainChanges;
    }

    @XmlElement(name = "group_whitelist_value_changes")
    public GroupWhitelistValueChanges getGroupWhitelistValueChanges() {
        if (groupWhitelistValueChanges == null) {
            groupWhitelistValueChanges = new AuthnUpdateParam.GroupWhitelistValueChanges();
        }
        return groupWhitelistValueChanges;
    }

    public void setGroupWhitelistValueChanges(
            GroupWhitelistValueChanges groupWhitelistValueChanges) {
        this.groupWhitelistValueChanges = groupWhitelistValueChanges;
    }

    @XmlElement(name = "group_objclass_changes")
    public GroupObjectClassChanges getGroupObjectClassChanges() {
       if (groupObjectClassChanges == null) {
           groupObjectClassChanges = new GroupObjectClassChanges();
       }
       return groupObjectClassChanges;
    }

    public void setGroupObjectClassChanges(
            GroupObjectClassChanges groupGroupObjectClassChanges) {
        this.groupObjectClassChanges = groupGroupObjectClassChanges;
    }

    @XmlElement(name = "group_memberattr_changes")
    public GroupMemberAttributeChanges getGroupMemberAttributeChanges() {
        if (groupMemberAttributeChanges == null) {
            groupMemberAttributeChanges = new GroupMemberAttributeChanges();
        }
        return groupMemberAttributeChanges;
    }

    public void setGroupMemberAttributeChanges(
            GroupMemberAttributeChanges groupGroupMemberAttributeChanges) {
        this.groupMemberAttributeChanges = groupGroupMemberAttributeChanges;
    }

    public static class ServerUrlChanges {

        private Set<String> add;
        private Set<String> remove;
        
        /**
         * List of Server URLs to add.  You cannot mix ldap and ldaps URLs
         * @valid none
         */
        @XmlElementWrapper(name = "add")
        /**
         * Server URL to add.
         * @valid Example: ldap://10.10.10.145
         * @valid Example: ldaps://10.10.10.145
         */
        @XmlElement(name = "server_url")
        public Set<String> getAdd() {
            if (add == null) {
                add = new LinkedHashSet<String>();
            }
            return add;
        }

        public void setAdd(Set<String> add) {
            this.add = add;
        }

        /**
         * List of Server URLs to remove.
         * @valid none
         */
        @XmlElementWrapper(name = "remove")
        /**
         * Server URL to remove.
         * @valid Example: ldap://10.10.10.145
         * @valid Example: ldaps://10.10.10.145
         */
        @XmlElement(name = "server_url")
        public Set<String> getRemove() {
            if (remove == null) {
                remove = new LinkedHashSet<String>();
            }     
            return remove;
        }

        public void setRemove(Set<String> remove) {
            this.remove = remove;
        }          
    }

    public static class DomainChanges {

        private Set<String> add;
        private Set<String> remove;

        /**
         * List of domains to add.  
         * @valid none
         */
        @XmlElementWrapper(name = "add")
        /**
         * Active Directory domain names associated with this
         * provider.  For non Active Directory servers, domain represents a logical
         * abstraction for this server which may not correspond to a network name.
         * @valid Example: domain.com
         */
        @XmlElement(name = "domain")
        public Set<String> getAdd() {
            if (add == null) {
                add = new LinkedHashSet<String>();
            }
            return add;
        }

        public void setAdd(Set<String> add) {
            this.add = add;
        }

        /**
         * List of domains to remove.  
         * @valid none
         */
        @XmlElementWrapper(name = "remove")
        /**
         * domain to remove
         * @valid Example: domain.com
         */
        @XmlElement(name = "domain")
        public Set<String> getRemove() {
            if (remove == null) {
                remove = new LinkedHashSet<String>();
            }     
            return remove;
        }

        public void setRemove(Set<String> remove) {
            this.remove = remove;
        }  
    }

    public static class GroupWhitelistValueChanges {
        private Set<String> add;
        private Set<String> remove;

        /**
         * List of white list values to add.  
         * @valid none
         */
        @XmlElementWrapper(name = "add")
        /**
         * Names of the groups to be included when querying Active Directory
         * for group membership information about a user or group.  
         * @valid The value accepts regular expressions.
         * @valid When empty, all groups are included implicitly
         * @valid Example: *Users*.
         */
        @XmlElement(name = "group_whitelist_value")
        public Set<String> getAdd() {
            if (add == null) {
                add = new LinkedHashSet<String>();
            }
            return add;
        }

        public void setAdd(Set<String> add) {
            this.add = add;
        }

        /**
         * List of white list values to remove.  
         * @valid none
         */
        @XmlElementWrapper(name = "remove")
        /**
         * White list value to remove.
         * @valid Example: *Users*.
         */
        @XmlElement(name = "group_whitelist_value")      
        public Set<String> getRemove() {
            if (remove == null) {
                remove = new LinkedHashSet<String>();
            }            
            return remove;
        }

        public void setRemove(Set<String> remove) {
            this.remove = remove;
        }
    }

    public static class GroupObjectClassChanges {
        private Set<String> add;
        private Set<String> remove;

        /**
         * List of group object classes to add.
         * @valid none
         */
        @XmlElementWrapper(name = "add")
        /**
         * Group object classes to be included when querying LDAP
         * for searching the group.
         * @valid Valid LDAP schema objectClasses.
         * @valid When empty, search for groups in LDAP will fail.
         * @valid Example: groupOfNames.
         */
        @XmlElement(name = "group_object_class")
        public Set<String> getAdd() {
            if (add == null) {
                add = new LinkedHashSet<String>();
            }
            return add;
        }

        public void setAdd(Set<String> add) {
            this.add = add;
        }

        /**
         * List of group object classes to remove.
         * @valid none
         */
        @XmlElementWrapper(name = "remove")
        /**
         * group object classes to remove.
         * @valid Example: groupOfNames.
         */
        @XmlElement(name = "group_object_class")
        public Set<String> getRemove() {
            if (remove == null) {
                remove = new LinkedHashSet<String>();
            }
            return remove;
        }

        public void setRemove(Set<String> remove) {
            this.remove = remove;
        }
    }

    public static class GroupMemberAttributeChanges {
        private Set<String> add;
        private Set<String> remove;

        /**
         * List of group member attributes to add.
         * @valid none
         */
        @XmlElementWrapper(name = "add")
        /**
         * Group member attributes to be included when querying LDAP
         * for searching the user and group membership.
         * @valid Valid LDAP schema attributes.
         * @valid When empty, search for user's membership in LDAP will fail.
         * @valid Example: member.
         */
        @XmlElement(name = "group_member_attribute")
        public Set<String> getAdd() {
            if (add == null) {
                add = new LinkedHashSet<String>();
            }
            return add;
        }

        public void setAdd(Set<String> add) {
            this.add = add;
        }

        /**
         * List of group member attribute type names to remove.
         * @valid none
         */
        @XmlElementWrapper(name = "remove")
        /**
         * List of group member attributes to remove.
         * @valid Example: member.
         */
        @XmlElement(name = "group_member_attribute")
        public Set<String> getRemove() {
            if (remove == null) {
                remove = new LinkedHashSet<String>();
            }
            return remove;
        }

        public void setRemove(Set<String> remove) {
            this.remove = remove;
        }
    }
}
