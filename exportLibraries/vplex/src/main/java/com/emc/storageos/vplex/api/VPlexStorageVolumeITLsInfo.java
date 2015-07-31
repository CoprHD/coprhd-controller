/**
 * 
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
	//List of initiator+target+lun items form this volume
    private List<String> itls = new ArrayList<>();
	
	public List<String> getItls()
    {
		return itls;
	}

	public void setItls(List<String> initiatorTargetLunList)
	{
		this.itls = initiatorTargetLunList;
	}
	
	/**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("StorageVolumeITLsInfo ( ");
        str.append(super.toString());        
        str.append(", initiatorTargetLunList: ");        
        for (String itl : itls)
        {
            str.append(", ");
            str.append(itl);
        }
        
        str.append(" )");
        return str.toString();
    }

}
