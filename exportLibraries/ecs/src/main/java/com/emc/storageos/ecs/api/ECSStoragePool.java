package com.emc.storageos.ecs.api;

import java.net.URI;

public class ECSStoragePool {
	private String name;
	private int TotalCapacity;
	private int FreeCapacity;
	
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getTotalCapacity() {
        return TotalCapacity;
    }
    
    public void setTotalCapacity(int TotalCapacity) {
        this.TotalCapacity = TotalCapacity;
    }

    public int getFreeCapacity() {
        return FreeCapacity;
    }
    
    public void setFreeCapacity(int FreeCapacity) {
        this.FreeCapacity = FreeCapacity;
    }

}
