/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.vmax;

import java.io.Serializable;

/**
 * Class the keeps track of the operations that are in flight for VMAX exports
 */
public class VmaxExportOperationContext extends ExportOperationContext implements Serializable {

	private static final long serialVersionUID = 6432280209330129103L;
	public static final String OPERATION_ADD_INITIATORS_TO_INITIATOR_GROUP = "Add initiators to initiator group";
	public static final String OPERATION_ADD_INITIATOR_GROUPS_TO_INITIATOR_GROUP = "Add child initiator group to cascaded initiator group";
	public static final String OPERATION_CREATE_STORAGE_GROUP = "Create storage group on VMAX";
	public static final String OPERATION_CREATE_CASCADING_STORAGE_GROUP = "Create cascading storage group on VMAX";
	public static final String OPERATION_CREATE_PORT_GROUP = "Create port group";
	public static final String OPERATION_ADD_TIER_TO_STORAGE_GROUP = "Assign FAST Policy to Storage group";
	public static final String OPERATION_ADD_STORAGE_GROUP_TO_CASCADING_STORAGE_GROUP = "Add child storage group to cascaded storage group";
	public static final String OPERATION_CREATE_INITIATOR_GROUP = "Create initiator group";
	public static final String OPERATION_CREATE_CASCADED_INITIATOR_GROUP = "Create cascaded initiator group";
	public static final String OPERATION_CREATE_MASKING_VIEW = "Create masking view";
	public static final String OPERATION_ADD_PORTS_TO_PORT_GROUP = "Add target ports to port group";
	public static final String OPERATION_ADD_VOLUMES_TO_STORAGE_GROUP = "Add volumes to storage group";

}
