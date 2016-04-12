package com.emc.storageos.model.pe;

public class UseExistingPortGroupParam {

    private String portGroupId;

    public String getPortGroupId ()
    {
        return portGroupId;
    }

    public void setPortGroupId (String portGroupId)
    {
        this.portGroupId = portGroupId;
    }

    @Override
    public String toString()
    {
        return "ClassPojo [portGroupId = "+portGroupId+"]";
    }
}
