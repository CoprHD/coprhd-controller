/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.netappc;

import java.util.List;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.iwave.ext.netappc.NetAppCException;
import com.iwave.ext.netapp.model.Qtree;

public class ClusterQtreeCommands {
    
    private Logger log = Logger.getLogger(getClass());
    
    private static final String ENABLE = "enabled";
    private static final String DISABLE = "disabled";
    
    private static final String UNIX = "unix";
    private static final String NTFS = "ntfs";
    private static final String MIXED = "mixed";
    
    private NaServer server = null;
    
    public ClusterQtreeCommands(NaServer server) {
        this.server = server;
    }
    
    public List<Qtree> listQtree(String volume) {
        NaElement elem = new NaElement("qtree-list-iter");
        
        if (volume != null && !volume.isEmpty()) {
        	NaElement qtreeAttrs = new NaElement("qtree-info");
        	qtreeAttrs.addNewChild("volume", volume);
        	NaElement query = new NaElement("query");
        	query.addChildElem(qtreeAttrs);
            elem.addChildElem(query);
        }
           
        NaElement resultElem = null;
        String tag = null;
        List<Qtree> qtrees = Lists.newArrayList();
        try {
        	do {
        		NaElement results = server.invokeElem(elem);
        		tag = results.getChildContent("next-tag");
        		resultElem = results.getChildByName("attributes-list");
        		if(resultElem != null) {
        			// Get the number of records returned by API.
        			for (NaElement qtreeElem : (List<NaElement>) resultElem.getChildren()) {
        				if(qtreeElem != null){
        					Qtree qtree = new Qtree();
        					qtree.setId((Integer)ConvertUtils.convert(qtreeElem.getChildContent("id"), Integer.class));
        					qtree.setOplocks(qtreeElem.getChildContent("oplocks"));
        					qtree.setOwningVfiler(qtreeElem.getChildContent("vserver"));
        					qtree.setQtree(qtreeElem.getChildContent("qtree"));
        					qtree.setSecurityStyle(qtreeElem.getChildContent("security-style"));
        					qtree.setStatus(qtreeElem.getChildContent("status"));
        					qtree.setVolume(qtreeElem.getChildContent("volume"));
        					qtrees.add(qtree);
        				}
        			}
        		}
        		if(tag != null && !tag.isEmpty()) {
        			elem = new NaElement("qtree-list-iter");
        			elem.addNewChild("tag", tag);
        		}
        	} while(tag != null && !tag.isEmpty());
        }
        catch( Exception e ) {
        	throw createError(elem, e);
        }             
        return qtrees;        

    }
    
    public boolean isQtree(String volume, String qtreeName) {
    	NaElement elem = new NaElement("qtree-list-iter");

    	if ((volume != null && !volume.isEmpty()) && 
    			(qtreeName != null && !qtreeName.isEmpty())) {
    		NaElement qtreeAttrs = new NaElement("qtree-info");
    		qtreeAttrs.addNewChild("volume", volume);
    		qtreeAttrs.addNewChild("qtree", qtreeName);
    		NaElement query = new NaElement("query");
    		query.addChildElem(qtreeAttrs);
    		elem.addChildElem(query);
    	}

    	try {
    		NaElement results = server.invokeElem(elem);
    		if(results.getChildIntValue("num-records", 0) > 0) {
    			return true;
    		}
    	}
    	catch( Exception e ) {
    		throw createError(elem, e);
    	}             
    	return false;        
    }
    
    public void createQtree(String qtree, String volume, Boolean opLocks, String securityStyle) {
        createQtree(qtree, volume, "", opLocks, securityStyle);
    }

    public void createQtree(String qtree, String volume, String mode, Boolean opLocks, String securityStyle) {
        NaElement elem = new NaElement("qtree-create");
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("volume", volume);
        
        // Set the oplocks for qtree
        if (opLocks.booleanValue() == true) {
        	elem.addNewChild("oplocks", ENABLE);
        } else {
        	elem.addNewChild("oplocks", DISABLE);
        }
        
        /* Set the security style; if input is default we do not set it.
         * In that case, the qtree inherits the parent volume's security 
         * style.
         */
        if (securityStyle.equalsIgnoreCase(UNIX)) {
        	elem.addNewChild("security-style", UNIX);           	
        } else if (securityStyle.equalsIgnoreCase(NTFS)) {
        	elem.addNewChild("security-style", NTFS);
        } else if (securityStyle.equalsIgnoreCase(MIXED)) {
        	elem.addNewChild("security-style", MIXED);
        }
        
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
    
    public void updateQtree(String qtree, String volume, Boolean opLocks, String securityStyle) {
        updateQtree(qtree, volume, "", opLocks, securityStyle);
    }
    
    public void updateQtree(String qtree, String volume, String mode, Boolean opLocks, String securityStyle) {
        NaElement elem = new NaElement("qtree-modify");
        elem.addNewChild("qtree", qtree);
        elem.addNewChild("volume", volume);
        
        // Set the oplocks for qtree
        if (opLocks.booleanValue() == true) {
        	elem.addNewChild("oplocks", ENABLE);
        } else {
        	elem.addNewChild("oplocks", DISABLE);
        }
        
        /* Set the security style; if input is default we do not set it.
         * In that case, the qtree inherits the parent volume's security 
         * style.
         */
        if (securityStyle.equalsIgnoreCase(UNIX)) {
        	elem.addNewChild("security-style", UNIX);           	
        } else if (securityStyle.equalsIgnoreCase(NTFS)) {
        	elem.addNewChild("security-style", NTFS);
        } else if (securityStyle.equalsIgnoreCase(MIXED)) {
        	elem.addNewChild("security-style", MIXED);
        }
        
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

    protected NetAppCException createError(NaElement elem, Exception e) {
        String message = "Failed to run " + elem.getName();
        log.error(message, e);
        return new NetAppCException(message, e);
    }
}
