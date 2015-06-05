/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import com.iwave.ext.netapp.model.ShareState;

import netapp.manage.NaElement;
import netapp.manage.NaServer;

/**
 * @author sdorcas
 * Not to be used directly by users or Orchestrator services. Use NetAppFacade.
 */
public class Lun {
	private Logger log = Logger.getLogger(getClass());
	
	private String path = "";
	private NaServer server = null;
	
	public Lun(NaServer server, String lunPath)
	{
		path = lunPath;	
		this.server = server;
	}
	/**
	 * Resize an existing LUN
	 * @param size - size in Bytes to resize the LUN
	 * @param force - forcibly reduce the size. Must be true to reduce size.
	 * @return - actual size of LUN.
     * @throws NetAppException
	 */
	long resizeLun(long size, boolean force)
	{
		NaElement elem = new NaElement("lun-resize");
		elem.addNewChild("force", Boolean.toString(force));
		elem.addNewChild("path", path);
		elem.addNewChild("size", Long.toString(size));
		
		NaElement result = null;
		try {
			result = server.invokeElem(elem);
			return result.getChildLongValue("actual-size", -1);
		} catch( Exception e ) {
			String msg = "Failed to resize LUN path=" + path;
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}	
	}
	
	/**
	 * Takes a LUN online or offline.
	 * @param onlineState - true to take LUN online; false to take LUN offline
	 * @param forceOnline - force LUN online, bypassing mapping conflict checks
	 * @return
     * @throws NetAppException
	 */
    boolean setLunOnline(boolean onlineState, boolean forceOnline)
    {
        NaElement elem = null;

        if (onlineState == isOnline()) {
            // already in desired state
            return true;
        }

        if (onlineState) { // take LUN online
            elem = new NaElement("lun-online");
            elem.addNewChild("path", path);
            elem.addNewChild("force", Boolean.toString(forceOnline));
        }
        else { // take LUN offline
            elem = new NaElement("lun-offline");
            elem.addNewChild("path", path);
        }

        try {
            server.invokeElem(elem);
            return true;
        }
        catch (Exception e) {
            String msg = "Failed to change LUN online status path=" + path;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }
	
	/**
	 * 
	 * @param osType - the OS type for the LUN. Its important this is the correct OS type.
	 * @param size - size in bytes for the new LUN
	 * @param reserveSpace - reserve the space 
	 * @return - the actual size (bytes) of the newly created LUN.
     * @throws NetAppException
	 */
	long createLunBySize(LunOSType osType, long size, boolean reserveSpace)
	{
		NaElement elem = new NaElement("lun-create-by-size");
		elem.addNewChild("ostype", osType.apiName());
		elem.addNewChild("path", path);
		elem.addNewChild("size", Long.toString(size));
		elem.addNewChild("space-reservation-enabled", Boolean.toString(reserveSpace));
		
		NaElement result = null;
		try {
			result = server.invokeElem(elem);
			return result.getChildLongValue("actual-size", -1);
		} catch(Exception e) {
			String msg = "Failed to create LUN path=" + path;			
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
	}
	
	/**
	 * 
	 * @param force - forcibly destroy the LUN
	 * @return - true/success, false/failed
     * @throws NetAppException
	 */
	boolean destroyLun(boolean force)
	{
		NaElement elem = new NaElement("lun-destroy");
		elem.addNewChild("force", Boolean.toString(force));
		elem.addNewChild("path", path);
		try {
			server.invokeElem(elem);
            return true;
		} catch(Exception e) {
			String msg = "Failed to destroy LUN path=" + path;			
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
	}
	
	/**
	 * Retrieves the occupied size in bytes of the LUN.
	 * @return - the size, -1 if the operation failed.
     * @throws NetAppException
	 */
	long getLunOccupiedSize()
	{
		NaElement elem = new NaElement("lun-get-occupied-size");
		elem.addNewChild("path", path);
		try {		
			NaElement result = server.invokeElem(elem);
			return result.getChildLongValue("occupied-size", -1);
		} catch(Exception e) {
			String msg = "Failed to retrieve occupied size of LUN path=" + path;			
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
	}	
	
	/**
	 * Map the LUN to initiators in the initiator group
	 * @param force - Forcibly map LUN, disabling conflict checks.
	 * @param initGroup - Initiator group to the specified LUN
	 * @param lunId - Lun ID to use when mapping to initiators. -1 means auto-assign.
	 * @return - the actual Lun ID assigned for this mapping. If not auto-assigned it should
	 * 				match the value provided if successful.
     * @throws NetAppException
	 */
	int mapLun(boolean force, String initGroup, int lunId)
	{
		if(lunId == -1) {
			log.info("LUN Id will be auto-assigned.");
		}
		
		NaElement elem = new NaElement("lun-map");
		elem.addNewChild("force", Boolean.toString(force));
		elem.addNewChild("initiator-group", initGroup);
		if(lunId != -1) {
			elem.addNewChild("lun-id", Integer.toString(lunId));
		}
		elem.addNewChild("path", path);
		try {		
			NaElement result = server.invokeElem(elem);
			return result.getChildIntValue("lun-id-assigned", -1);
		} catch(Exception e) {
			String msg = "Failed to map LUN path=" + path + " to group=" + initGroup;
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
	}
	
	/**
	 * Unmaps a LUN from an initiatorGroup
	 * @param initGroup - name of initiator group
	 * @return - true if successful, false otherwise
     * @throws NetAppException
	 */
	boolean unmapLun(String initGroup)
	{
		NaElement elem = new NaElement("lun-unmap");
		elem.addNewChild("initiator-group", initGroup);		
		elem.addNewChild("path", path);
		try {		
			server.invokeElem(elem);
			return true;
		} catch(Exception e) {
			String msg = "Failed to unmap LUN path=" + path;
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
	}
	
	/**
	 * Unmaps all initiator groups from the LUN. Mainly used prior to destroying the LUN.
     * @throws NetAppException
	 */
	boolean unmapAll()
	{
		// First get all the initiator groups.
		NaElement elem = new NaElement("lun-map-list-info");	
		elem.addNewChild("path", path);
		
		NaElement result = null;
		try {
			result = server.invokeElem(elem).getChildByName("initiator-groups");
		} catch(Exception e) {
			String msg = "Failed unmapping all groups from LUN. Unable to retrieve list of maps for LUN path=" + path;
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
		
		// Now unmap each individually
		boolean success = true;

		for (NaElement el : (List<NaElement>)result.getChildren()) {
			String iGroup = el.getChildContent("initiator-group-name");
            try {
    			unmapLun(iGroup);
            }
            catch (NetAppException e) {
                success = false;
            }
		}
		return success;
	}
	
	/**
	 * Sets a description on a LUN
	 * @param description
     * @throws NetAppException
	 */
	void setLunDescription(String description)
	{
		NaElement elem = new NaElement("lun-set-comment");
		elem.addNewChild("comment", description);
		elem.addNewChild("path", path);
		try {		
			server.invokeElem(elem);
		} catch(Exception e) {
			String msg = "Failed to set comment on LUN path=" + path;
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
	}
    
	String getLunDescription() {
	       NaElement elem = new NaElement("lun-get-comment");
	        elem.addNewChild("path", path);
	        try {       
	            return server.invokeElem(elem).getChildByName("comment").getContent();
	            
	        } catch(Exception e) {
	            String msg = "Failed to set comment on LUN path=" + path;
	            log.error(msg, e);
	            throw new NetAppException(msg, e);
	        }
	}
    
    boolean isOnline() {
        LunInfo info = listLUNs(false).get(0);
        return info.isOnline();
    }
	
    /**
     * @throws NetAppException
	 */
	int getLunIdForGroup(String initGroup)
	{
        Map<String, Integer> lunMap = getLunMap();

		// Get the LUN id for the desired group
		Integer id = lunMap.get(initGroup);
        if (id == null)
            id = -1;
		return id;
	}
    
    Map<String, Integer> getLunMap()
    {
        NaElement elem = new NaElement("lun-map-list-info");
        elem.addNewChild("path", path);

        NaElement result = null;
        try {
            result = server.invokeElem(elem).getChildByName("initiator-groups");
        }
        catch (Exception e) {
            String msg = "Failed to get LUN map list info";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        HashMap<String, Integer> map = new HashMap<String, Integer>();

        for (NaElement el : (List<NaElement>) result.getChildren()) {
            String group = el.getChildContent("initiator-group-name");
            int id = el.getChildIntValue("lun-id", -1);
            map.put(group, id);
        }

        return map;
    }
    
    /**
     * @throws NetAppException
	 */
	List<LunInfo> listLUNs(boolean listAll)
	{
		ArrayList<LunInfo> luns = new ArrayList<LunInfo>();	
		NaElement elem = new NaElement("lun-list-info");
		if( !listAll ) {
			elem.addNewChild("path", path);
		}
		
		NaElement result = null;
		try {
			result = server.invokeElem(elem).getChildByName("luns");
		} catch( Exception e ) {
			String msg = "Failed to get LUN list info";
			log.error(msg, e);
            throw new NetAppException(msg, e);
		}
		
		// Create a LunInfo object for each LUN
		for(NaElement lun : (List<NaElement>)result.getChildren()) {
			LunInfo info = new LunInfo();
            
			info.setPath(lun.getChildContent("path"));
			info.setId(lun.getChildIntValue("device-id", -1));
			info.setOnline(Boolean.valueOf(lun.getChildContent("online")));
			info.setSize(lun.getChildLongValue("size", -1));
			info.setSizeUsed(Long.parseLong(lun.getChildContent("size-used")));
			info.setMapped(Boolean.valueOf(lun.getChildContent("mapped")));
			info.setSpaceReserved(Boolean.valueOf(lun.getChildContent("is-space-reservation-enabled")));
            info.setShareState(ShareState.valueOf(lun.getChildContent("share-state")));
            info.setLunType(LunOSType.valueOf(lun.getChildContent("multiprotocol-type")));
            
			luns.add(info);
		}
		return luns;
	}
}
