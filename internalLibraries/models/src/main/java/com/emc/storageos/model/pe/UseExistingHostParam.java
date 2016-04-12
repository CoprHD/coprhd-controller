package com.emc.storageos.model.pe;

public class UseExistingHostParam {

    private String hostId;

    public String getHostId ()
    {
        return hostId;
    }

    public void setHostId (String hostId)
    {
        this.hostId = hostId;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [hostId = "+hostId+"]";
    }
}
