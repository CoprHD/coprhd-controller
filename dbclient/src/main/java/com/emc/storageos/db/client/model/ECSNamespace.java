/*
 * Copyright (c) 2008-2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.model;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlTransient;

/**
 * ECS object storage namespace configuration
 *
 */
@Cf("ECSNamespace")
public class ECSNamespace extends AbstractTenantResource {
    
    //name of namespace
    private String _name;
    
    //id of namespace; 'id' is in super class
    private String _nsId;
    
    //Flag if mapped to a tenant or not
    private Boolean _mapped;
    
    //Tenant to which this namepsace is mapped;
    private NamedURI _tenant;
    
    //Discovered ECS ip
    private String _ecsIp;
    
    //Allowed ECS replication groups
    private List<String> _allowedReplicationGroups;
    
    //Not Allowed ECS replication groups
    private List<String> _disallowedReplicationGroups;
    
    //native device ID
    private String _nativeId;
   
    //get set methods
    @Name("name")
    public String getName() {
        return _name;
    }
    
    public void setName(String name) {
        this._name = name;
    }

    @Name("nsId")
    public String getNsId() {
        return _nsId;
    }
    
    public void setNsId(String nsId) {
        this._nsId = nsId;
    }

    @Name("mapped")
    public Boolean getMapped() {
        return _mapped;
    }
    
    public void setMapped(Boolean mapped) {
        this._mapped = mapped;
    }
    
    @XmlTransient
    @NamedRelationIndex(cf = "NamedRelation")
    @Name("tenant")
    public NamedURI geTenant() {
        return _tenant;
    }
    
    public void setTenant(NamedURI tenant) {
        this._tenant = tenant;
    }

    @Name("ecsIp")
    public String getEcsIp() {
        return _ecsIp;
    }
    
    public void setEcsIp(String ecsIp) {
        this._ecsIp = ecsIp;
    }

    @Name("allowedReplicationGroups")
    public List<String> getAllowedReplicationGroups() {
        return _allowedReplicationGroups;
    }
    
    public void setAllowedReplicationGroups(String allowedReplicationGroups) {
        this._allowedReplicationGroups.add(allowedReplicationGroups);
    }

    @Name("disallowedReplicationGroups")
    public List<String> getDisallowedReplicationGroups() {
        return _disallowedReplicationGroups;
    }
    
    public void setDisllowedReplicationGroups(String disallowedReplicationGroups) {
        this._disallowedReplicationGroups.add(disallowedReplicationGroups);
    }

    @Name("nativeId")
    public String geNativeId() {
        return _nativeId;
    }
    
    public void setNativeId(String nativeId) {
        this._nativeId= nativeId;
    }

    @Override
    public Object[] auditParameters() {
        // TODO Auto-generated method stub
        return null;
    }
}
