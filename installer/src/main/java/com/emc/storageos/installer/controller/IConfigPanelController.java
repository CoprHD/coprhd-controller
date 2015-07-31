/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.controller;

public interface IConfigPanelController {

    /**
     * This method called when Next button clicks to navigate to next page.
     * It validate the data user entered on current page and saves the data
     * if it is valid. If the data is invalid or anything is wrong/miss from
     * the current configuration page, it returns the message which is diplayed
     * to user.
     * 
     * @return the message if anything wrong
     */
    public String[] configurationIsCompleted();
}
