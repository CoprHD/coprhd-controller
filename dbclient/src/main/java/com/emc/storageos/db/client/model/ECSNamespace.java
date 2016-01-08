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
    
    public enum ECS_RepGroup_Type {
        ALLOWED,
        DISALLOWED,
        NONE
    };
    
    // name of namespace
    private String _name;
    
    // id of namespace; 'id' is in super class
    private String _nsId;
    
    // Flag if mapped to a tenant or not
    private Boolean _mapped;
    
    // Tenant to which this namepsace is mapped;
    private NamedURI _tenant;
    
    // Discovered ECS ip
    private String _ecsIp;
    
    // Allowed ECS replication groups
    private ECS_RepGroup_Type _rgType;
    
    // Not Allowed ECS replication groups
    private List<String> _replicationGroups;
    
    // native GUID
    private String _nativeGuid;
   
    // get set methods
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

    @Name("rgType")
    public ECS_RepGroup_Type getRgType() {
        return _rgType;
    }
    
    public void setRgType(ECS_RepGroup_Type rgType) {
        this._rgType = rgType;
    }

    @Name("replicationGroups")
    public List<String> getReplicationGroups() {
        return _replicationGroups;
    }
    
    public void setReplicationGroups(String replicationGroups) {
        this._replicationGroups.add(replicationGroups);
    }

    @Name("nativeGuid")
    public String geNativeGuid() {
        return _nativeGuid;
    }
    
    public void setNativeGuid(String nativeGuid) {
        this._nativeGuid= nativeGuid;
    }

    @Override
    public Object[] auditParameters() {
        // TODO Auto-generated method stub
        return null;
    }
}