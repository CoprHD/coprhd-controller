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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charva.awt.event.ActionEvent;
import charva.awt.event.ActionListener;

import com.emc.storageos.services.util.Configuration;
import com.emc.storageos.installer.util.InstallationTask;
import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.installer.widget.DisplayPanel;

/**
 * Class implements the control on the Confirmation page.
 * 
 */
public class ConfirmationController implements IConfigPanelController {
    private static final Logger log = LoggerFactory.getLogger(ConfirmationController.class);
    private static final String MSG_INFO_INSTALL_DONE = "Local Configuration already done!";
    private static final String MSG_INFO_INSTALL_DONE_2 = "Press Reboot after the whole cluster configured.";
    private static final String MSG_INSTALLATION_IN_PROGRESS = "Configuration is in progress. Please wait.";
    private Configuration config;
    private DisplayPanel confPanel;
    private String releaseVersion;
    private ButtonActionListener actionListener;
    private boolean isInstallStarted = false;

    public ConfirmationController(Configuration config, DisplayPanel confPanel, String releaseVersion) {
        this.confPanel = confPanel;
        this.config = config;
        this.releaseVersion = releaseVersion;
        setupEventListner();
    }

    private void setupEventListner() {
        actionListener = new ButtonActionListener();
        this.confPanel.addButtonActionListener(actionListener);
    }

    /**
     * Checks if all nodes in the cluster finish local installation/configuration.
     * 
     * @return true if all nodes done; otherwise false.
     */
    public boolean isAllNodesDoneInstallation() {
        return actionListener.getTask().isClusterInstallDone();
    }

    /**
     * Checks if installation/configuration has started.
     * 
     * @return true if started; otherwise false.
     */
    public boolean isInstallStarted() {
        return isInstallStarted;
    }

    /**
     * Class listens on the Install/Config/Redeploy button, starts specific operations based
     * on the button clicked.
     * 
     */
    private class ButtonActionListener implements ActionListener {
        private InstallationTask installationTask;

        private ButtonActionListener() {
            installationTask = new InstallationTask(config, confPanel, releaseVersion);
        }

        private InstallationTask getTask() {
            return installationTask;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String cmd = e.getActionCommand();
            if (cmd.equals(InstallerConstants.BUTTON_ACTION_START)) {
                if (installationTask.isLocalInstallDone()) {
                    confPanel.getRoot().displayInfoMessage(new String[] { MSG_INFO_INSTALL_DONE, MSG_INFO_INSTALL_DONE_2 });
                } else {
                    if (!installationTask.isAlive()) {
                        // installation already done successfully before
                        if (installationTask.localInstallFailed()) {
                            // installation ran before but failed, create a new task for user to retry
                            log.info("Installation failed before, re-run installation again.");
                            installationTask = new InstallationTask(config, confPanel, releaseVersion);
                        }
                        log.info("Start installation tasks now ...");
                        isInstallStarted = true;
                        installationTask.start();
                    } else {
                        // installation already started
                        confPanel.getRoot().displayWarningMessage(new String[] { MSG_INSTALLATION_IN_PROGRESS });
                    }
                }
            }
        }
    }

    @Override
    public String[] configurationIsCompleted() {
        return null;
    }
}
