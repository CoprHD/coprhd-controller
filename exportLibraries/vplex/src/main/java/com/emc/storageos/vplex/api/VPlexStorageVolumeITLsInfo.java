/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.vplex.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Storage Volume Object with ITLs information populated.
 * Use it if ITLs are required for business logic.
 * 
 * Currently it is used for VPLEX+Cinder use case.
 *
 * @author hallup
 *
 */

public class VPlexStorageVolumeITLsInfo extends VPlexStorageVolumeInfo 
{
	// Enumerates the storage volume attributes we are interested in and
    // parse from the VPlex storage volume response. There must be a setter
    // method for each attribute specified. The format of the setter
    // method must be as specified by the base class method
    // getAttributeSetterMethodName.
    public static enum StorageVolumeAttribute
    {
        NAME("name"),
        ITLS("itls");

        // The VPlex name for the attribute.
        private String _name;

        /**
         * Constructor.
         * 
         * @param name The VPlex attribute name.
         */
        StorageVolumeAttribute(String name)
        {
            _name = name;
        }

        /**
         * Getter for the VPlex name for the attribute.
         * 
         * @return The VPlex name for the attribute.
         */
        public String getAttributeName()
        {
            return _name;
        }

        /**
         * Returns the enum whose name matches the passed name, else null when
         * not found.
         * 
         * @param name The name to match.
         * 
         * @return The enum whose name matches the passed name, else null when
         *         not found.
         */
        public static StorageVolumeAttribute valueOfAttribute(String name)
        {
        	StorageVolumeAttribute[] storageVolumeAtts = values();
            for (int i = 0; i < storageVolumeAtts.length; i++)
            {
                if (storageVolumeAtts[i].getAttributeName().equals(name))
                {
                    return storageVolumeAtts[i];
                }
            }
            return null;
        }
    };
    
    
	//List of initiator+target+lun items form this volume
    private List<String> itls = new ArrayList<>();
    private boolean isItlsFormatted = false;
	
    /**
     * Gets the formatted ITLs
     * @return
     */
	public List<String> getItls()
    {
		
		if(!isItlsFormatted)
		{
			if(null!=itls && !itls.isEmpty())
			{
				/*
				 * Converting from "0x5000144260037d13\/0x5006016036601854\/2"
				 * to "5000144260037d13-5006016036601854-2" (<initiator-wwn>-<targte-wwn>-<lun-id>)
				 */
				List<String> formatttedItls = new ArrayList<String>(itls.size());
				for(String itl : itls)
				{
					String[] itlParts = itl.split("/");
					
					String part1 = itlParts[0];
					String part2 = itlParts[1];
					String part3 = itlParts[2];
					
					String formattedItl = part1.substring(2)+"-"
					                      +part2.substring(2)+"-"
							              +part3;
					formatttedItls.add(formattedItl);
				}
				isItlsFormatted = true;
				
				//replace the original list with formatted itls
				itls.clear();
				itls.addAll(formatttedItls);				
			}
		}
		
		return itls;
	}

	/**
	 * Set the list of ITLs fetched from the response
	 * @param initiatorTargetLunList
	 */
	public void setItls(List<String> initiatorTargetLunList)
	{
		itls.clear();
		for(String itl : initiatorTargetLunList)
		{
			itls.add(itl);
		}
	}
	
	/**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAttributeFilters()
    {
        List<String> attFilters = new ArrayList<String>();
        for (StorageVolumeAttribute att : StorageVolumeAttribute.values())
        {
            attFilters.add(att.getAttributeName());
        }
        return attFilters;
    }
	
	/**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("StorageVolumeITLsInfo ( ");
        str.append(super.toString());        
        str.append(", itls: ");
        
        List<String> itls = getItls();
        if(null!=itls)
        {
        	for (String itl : getItls())
            {
                str.append(itl);
                str.append(", ");
            }
        }
        
        
        str.append(" )");
        return str.toString();
    }

}
