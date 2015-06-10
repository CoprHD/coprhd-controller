/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.model.auth;

import org.codehaus.jackson.annotate.JsonProperty;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Authentication provider object for POST
 */
@XmlRootElement(name = "authnprovider_create")
public class AuthnCreateParam extends AuthnProviderBaseParam {

    /**
     * Valid ldap or ldaps url strings.
     * @valid Example: ldap://10.10.10.145
     * @valid Example: ldaps://10.10.10.145
     */
    private Set<String> serverUrls;

    /**
     * Active Directory domain names associated with this
     * provider.  If the server_url points to a Active Directory forest
     * global catalog server, you may specify all or a subset of the forest's
     * domains which this provider needs to interact with.
     * For non Active Directory servers, domain represents a logical
     * abstraction for this server which may not correspond to a network name.
     * @valid Example: domain.com
     */
    private Set<String> domains;

    /**
     * Names of the groups to be included when querying Active Directory
     * for group membership information about a user or group.  If the White List
     * is set to a value, the provider will only receive group membership information
     * about the groups matched by the value.  If the White List is empty, all group
     * membership information will be retrieved.  (blank == "*").
     * @valid The value accepts regular expressions.
     * @valid When empty, all groups are included implicitly
     * @valid Example: *Users*.
     */
    private Set<String> groupWhitelistValues;

    /**
     * Attribute for group search.  This is the group's objectClass attribute that will be used to represent group.
     * Once set during creation of the provider, the value for this parameter cannot be changed.
     * @valid Example: "group, groupOfNames, groupOfUniqueNames, posixGroup, organizationalRole"
     */
    private Set<String> groupObjectClasses;

    /**
     * Attribute for group search.  This is the group's member(like) attribute that will be used to represent group's members.
     * Once set during creation of the provider, the value for this parameter cannot be changed.
     * This applies only for the LDAP, for AD, usually user has the group information where as
     * in LDAP, group has the member information.
     * @valid Example: "member, memberUid, uniqueMember, roleOccupant".
     */
    private Set<String> groupMemberAttributes;

    public AuthnCreateParam() {}

    @XmlElementWrapper(name = "server_urls")
    @XmlElement(name = "server_url")
    public Set<String> getServerUrls() {
        if (serverUrls == null) {
            serverUrls = new LinkedHashSet<String>();
        }
        return serverUrls;
    }

    public void setServerUrls(Set<String> serverUrls) {
        this.serverUrls = serverUrls;
    }

    @XmlElementWrapper
    @XmlElement(name = "domain")
    public Set<String> getDomains() {
        if (domains == null) {
            domains = new LinkedHashSet<String>();
        }
        return domains;
    }

    public void setDomains(Set<String> domains) {
        this.domains = domains;
    }

    @XmlElementWrapper(name = "group_whitelist_values")
    @XmlElement(name = "group_whitelist_value")
    public Set<String> getGroupWhitelistValues() {
        if (groupWhitelistValues == null) {
            groupWhitelistValues = new LinkedHashSet<String>();
        }
        return groupWhitelistValues;
    }

    public void setGroupWhitelistValues(Set<String> groupWhitelistValues) {
        this.groupWhitelistValues = groupWhitelistValues;
    }

    @XmlElementWrapper(name = "group_object_classes")
    @XmlElement(name = "group_object_class")
    @JsonProperty("group_object_class")
    public Set<String> getGroupObjectClasses() {
        if (groupObjectClasses == null) {
            groupObjectClasses = new LinkedHashSet<String>();
        }
        return groupObjectClasses;
    }

    public void setGroupObjectClasses(Set<String> groupObjectClasses) {
        this.groupObjectClasses = groupObjectClasses;
    }

    @XmlElementWrapper(name = "group_member_attributes")
    @XmlElement(name = "group_member_attribute")
    @JsonProperty("group_member_attribute")
    public Set<String> getGroupMemberAttributes() {
        if (groupMemberAttributes == null) {
            groupMemberAttributes = new LinkedHashSet<String>();
        }
        return groupMemberAttributes;
    }

    public void setGroupMemberAttributes(Set<String> groupMemberAttributes) {
        this.groupMemberAttributes = groupMemberAttributes;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("AuthnCreateParam [");
        sb.append(super.toString());
        sb.append(", serverUrls=");

        if (serverUrls != null) {
            for (String s : serverUrls) {
                sb.append(s).append(",");
            }
        } else {
            sb.append("null");
        }
        sb.append(", domain=");
        if (domains != null) {
            for (String s : domains) {
                sb.append(s).append(",");
            }
        } else {
            sb.append("null");
        }
        sb.append(", groupWhitelistValues=");
        if (groupWhitelistValues != null) {
            for (String s : groupWhitelistValues) {
                sb.append(s).append(",");
            }
        } else {
            sb.append("null");
        }

        sb.append(", groupObjectClasses=");
        if (groupObjectClasses != null) {
            for (String s : groupObjectClasses) {
                sb.append(s).append(",");
            }
        } else {
            sb.append("null");
        }

        sb.append(", groupMemberAttributes=");
        if (groupMemberAttributes != null) {
            for (String s : groupMemberAttributes) {
                sb.append(s).append(",");
            }
        } else {
            sb.append("null");
        }

        sb.append("]");
        return sb.toString();
    }
}
