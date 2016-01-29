/*
 * Copyright (c) 2015 EMC Corporation
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
     * The changes for the URL set of this provider. URLs
     * can be added, and removed. Up to one add element and one remove element may be specified.
     * Multiple urls can be specified in the add and remove blocks.
     * 
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
         * List of Server URLs to add. You cannot mix ldap and ldaps URLs
         * 
         */
        @XmlElementWrapper(name = "add")
        /**
         * Server URL to add.
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
         * 
         */
        @XmlElementWrapper(name = "remove")
        /**
         * Server URL to remove.
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
         * 
         */
        @XmlElementWrapper(name = "add")
        /**
         * Active Directory domain names associated with this
         * provider.  For non Active Directory servers, domain represents a logical
         * abstraction for this server which may not correspond to a network name.
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
         * 
         */
        @XmlElementWrapper(name = "remove")
        /**
         * domain to remove
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
         * 
         */
        @XmlElementWrapper(name = "add")
        /**
         * Names of the groups to be included when querying Active Directory
         * for group membership information about a user or group.  
         * Valid values:
         *  regular expressions
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
         * 
         */
        @XmlElementWrapper(name = "remove")
        /**
         * White list value to remove.
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
         * 
         */
        @XmlElementWrapper(name = "add")
        /**
         * Group object classes to be included when querying LDAP
         * for searching the group.
         * Valid values:
         *  LDAP schema objectClasses
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
         * 
         */
        @XmlElementWrapper(name = "remove")
        /**
         * group object classes to remove.
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
         * 
         */
        @XmlElementWrapper(name = "add")
        /**
         * Group member attributes to be included when querying LDAP
         * for searching the user and group membership.
         * Valid value:
         *  Valid LDAP schema attributes
         *  When empty, search for user's membership in LDAP will fail
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
         * 
         */
        @XmlElementWrapper(name = "remove")
        /**
         * List of group member attributes to remove.
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
