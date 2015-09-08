package com.emc.storageos.ecs.api;

import java.net.URI;

public class ECSStoragePool {
	private String name;
	private String id;
	private Long TotalCapacity;
	private Long FreeCapacity;
	
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Long getTotalCapacity() {
        return TotalCapacity;
    }
    
    public void setTotalCapacity(Long TotalCapacity) {
        this.TotalCapacity = TotalCapacity;
    }

    public Long getFreeCapacity() {
        return FreeCapacity;
    }
    
    public void setFreeCapacity(Long FreeCapacity) {
        this.FreeCapacity = FreeCapacity;
    }

}
