/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.netapp;

import java.util.List;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.iwave.ext.netapp.model.Qtree;

public class QtreeCommands {
    
    private Logger log = Logger.getLogger(getClass());
    
    private NaServer server = null;
    
    public QtreeCommands(NaServer server) {
        this.server = server;
    }
    
    public List<Qtree> listQtree(String volume) {
        NaElement elem = new NaElement("qtree-list");
        if (StringUtils.isNotBlank(volume)) {
            elem.addNewChild("volume", volume);
        }
           
        NaElement resultElem = null;
        try {
            resultElem = server.invokeElem(elem);
        }
        catch( Exception e ) {
            throw createError(elem, e);
        }     
        
        List<Qtree> qtrees = Lists.newArrayList();
        for (NaElement qtreeElem : (List<NaElement>) resultElem.getChildByName("qtrees").getChildren()) {
            Qtree qtree = new Qtree();
            qtree.setId((Integer)ConvertUtils.convert(qtreeElem.getChildContent("id"), Integer.class));
            qtree.setOplocks(qtreeElem.getChildContent("oplocks"));
            qtree.setOwningVfiler(qtreeElem.getChildContent("owning-vfiler"));
            qtree.setQtree(qtreeElem.getChildContent("qtree"));
            qtree.setSecurityStyle(qtreeElem.getChildContent("security-style"));
            qtree.setStatus(qtreeElem.getChildContent("status"));
            qtree.setVolume(qtreeElem.getChildContent("volume"));
            qtrees.add(qtree);
        }
        return qtrees;        

    }
    
    public void createQtree(String qtree, String volume) {
        createQtree(qtree, volume, "");
    }

    public void createQtree(String qtree, String volume, String mode) {
        NaElement elem = new NaElement("qtree-create");
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("volume", volume);
        if (StringUtils.isNotBlank(mode)) {
            elem.addNewChild("mode", mode);
        }

        try {
            server.invokeElem(elem);
        }
        catch (Exception e) {
            throw createError(elem, e);
        }
    }
    
    public void deleteQtree(String qtree, boolean force) {
        NaElement elem = new NaElement("qtree-delete");
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("force", String.valueOf(force));

        try {
            server.invokeElem(elem);
        }
        catch (Exception e) {
            throw createError(elem, e);
        }
    }

    protected NetAppException createError(NaElement elem, Exception e) {
        String message = "Failed to run " + elem.getName();
        log.error(message, e);
        return new NetAppException(message, e);
    }
}
