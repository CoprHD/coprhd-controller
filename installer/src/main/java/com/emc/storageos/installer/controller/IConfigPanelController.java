/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.installer.controller;

public interface IConfigPanelController {

	/**
	 * This method called when Next button clicks to navigate to next page.
	 * It validate the data user entered on current page and saves the data 
	 * if it is valid. If the data is invalid or anything is wrong/miss from 
	 * the current configuration page, it returns the message which is diplayed
	 * to user.
	 * @return the message if anything wrong
	 */
	public String[] configurationIsCompleted();
}
