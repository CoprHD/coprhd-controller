/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.List;
import netapp.manage.NaElement;
import netapp.manage.NaServer;
import org.apache.log4j.Logger;

/**
 * @author sdorcas Operations against an Initiator Group.
 */
public class IGroup {

    private Logger log = Logger.getLogger(getClass());
    private String name = "";
    private NaServer server = null;

    public IGroup(NaServer server, String name)
    {
        this.server = server;
        this.name = name;
    }

    void createIGroup(IGroupType type, LunOSType osType)
    {
        NaElement elem = new NaElement("igroup-create");
        elem.addNewChild("initiator-group-name", name);
        elem.addNewChild("initiator-group-type", type.name());
        elem.addNewChild("os-type", osType.apiName());

        // Invoke
        try {
            server.invokeElem(elem);
        }
        catch (Exception e) {
            String msg = "Failed to create initiator group: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    /**
     * Destroys an initiator group. Best practice is to unmap all LUNs before destroying the IGroup.
     * 
     * @param force
     * @return
     */
    boolean destroyIGroup(boolean force)
    {
        NaElement elem = new NaElement("igroup-destroy");
        elem.addNewChild("initiator-group-name", name);
        elem.addNewChild("force", Boolean.toString(force));

        // Invoke
        try {
            server.invokeElem(elem);
        }
        catch (Exception e) {
            String msg = "Failed to destroy initiator group: " + name;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    void addInitiatorToIGroup(String initiator)
    {
        NaElement elem = new NaElement("igroup-add");
        elem.addNewChild("initiator-group-name", name);
        elem.addNewChild("initiator", initiator);

        // Invoke
        try {
            server.invokeElem(elem);
        }
        catch (Exception e) {
            String msg = "Failed to add initiator '" + initiator + "' to group '" + name + "'";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    boolean removeInitiatorFromIGroup(String initiator, boolean force)
    {
        NaElement elem = new NaElement("igroup-remove");
        elem.addNewChild("initiator-group-name", name);
        elem.addNewChild("initiator", initiator);
        elem.addNewChild("force", Boolean.toString(force));

        // Invoke
        try {
            server.invokeElem(elem);
        }
        catch (Exception e) {
            String msg = "Failed to remove initiator '" + initiator + "' from group '" + name + "'";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    List<IGroupInfo> listInitiatorGroups(boolean listAll)
    {
        List<IGroupInfo> iGroups = new ArrayList<IGroupInfo>();
        NaElement elem = new NaElement("igroup-list-info");
        if (!listAll) {
            elem.addNewChild("initiator-group-name", name);
        }
        // Invoke
        NaElement result = null;
        try {
            result = server.invokeElem(elem).getChildByName("initiator-groups");
        }
        catch (Exception e) {
            String msg = "Failed to list initiator groups";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        for (NaElement igroup : (List<NaElement>) result.getChildren()) {
            String groupName = igroup.getChildContent("initiator-group-name");
            IGroupInfo info = new IGroupInfo();
            info.setName(groupName);
            info.setType(IGroupType.valueOf(igroup.getChildContent("initiator-group-type")));
            info.setOsType(LunOSType.apiValueOf(igroup.getChildContent("initiator-group-os-type")));

            NaElement initiators = igroup.getChildByName("initiators");

            if (initiators != null) {
                for (NaElement i : (List<NaElement>) initiators.getChildren()) {
                    String iName = i.getChildContent("initiator-name");
                    info.addInitiator(iName);
                }
            }

            iGroups.add(info);
        }
        
        return iGroups;
    }
}
