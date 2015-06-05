/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.model.varray;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.model.pools.VirtualArrayAssignmentChanges;

public class VirtualArrayResourceUpdateParam {

    private VirtualArrayAssignmentChanges varrayAssignmentChanges;
    
    public VirtualArrayResourceUpdateParam() {}
    
    public VirtualArrayResourceUpdateParam(VirtualArrayAssignmentChanges varrayChanges) {
        varrayAssignmentChanges = varrayChanges;
    }
    
    /**
    * The list of virtual arrays to be added to or removed from the resource.
    * 
    * @valid none
    */
   @XmlElement(name = "varray_assignment_changes")
   public VirtualArrayAssignmentChanges getVarrayChanges() {
       return varrayAssignmentChanges;
   }

   public void setVarrayChanges(VirtualArrayAssignmentChanges varrayChanges) {
       varrayAssignmentChanges = varrayChanges;
   }
}
