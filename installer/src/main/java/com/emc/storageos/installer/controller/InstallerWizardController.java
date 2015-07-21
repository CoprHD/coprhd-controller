/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import charva.awt.event.*;

import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.installer.widget.BasePanel;
import com.emc.storageos.installer.widget.InstallerWizard;

/**
 * Class implements main frame control (navigations between pages).
 *
 */
public class InstallerWizardController {
	private static final String BACK_OPERATION_IS_NOT_PERMITTED = "Back operation is not permitted.";
	private static final String INSTALLATION_ALREADY_STARTED = "Installation already started.";
	private static final String OPERATIONS_NOT_FINISHED_IN_CLUSTER = "The operations are not finished in the cluster.";
    private static final String ACTION_RECOMMENDATION = "Please config all required nodes then reboot.";
	private static final String CLICK_OK_TO_REBOOT = "Click Ok to reboot.";
	private static final String ALL_NODES_IN_CLUSTER_ARE_INSTALLED = "All nodes in cluster are installed.";
	private static final String DO_YOU_WANT_TO_EXIT_INSTALLER = "Do you want to exit installer?";
	private static final String LOCAL_INSTALLATION_HAS_NOT_STARTED = "Local installation has not started.";
	private static final Logger log = LoggerFactory.getLogger(InstallerWizardController.class);
	public InstallerWizard wizard;
	private InstallerManager manager;;
	
	public InstallerWizardController(InstallerWizard wizard, InstallerManager manager) {
		this.wizard = wizard;
		this.manager = manager;
		this.manager.setWizard(wizard);
		setupEventListener();
	}
	
	private void setupEventListener() {
		wizard.setButtonListener(new ButtonListener());
	}
	
	public void start() {
		wizard.updateView(null, manager.getFirstPanel(InstallerConstants.NETWORK_INT_PANEL_ID));
	    wizard.setVisible(true);
	}

	/**
	 * Class implements specific actions based on the button clicks.
	 *
	 */
	class ButtonListener implements ActionListener {
		private static final String CLICK_OK_TO_EXIT_INSTALLER = "Click Ok to exit installer";

		@Override
		public void actionPerformed(ActionEvent e) {
			String cmd = e.getActionCommand();
			if (cmd.equals(InstallerConstants.BUTTON_NEXT)) {
				nextButtonPressed();
			} else if (cmd.equals(InstallerConstants.BUTTON_BACK)) {
				backButtonPressed();
			} else if (cmd.equals(InstallerConstants.BUTTON_EXIT)) {
				wizard.confirmExit(CLICK_OK_TO_EXIT_INSTALLER);
			} else if (cmd.equals(InstallerConstants.BUTTON_REBOOT)) {
				rebootButtonPressed();
			}
		}
	}
	
	private void rebootButtonPressed() {
		IConfigPanelController controller = manager.getControllers()
				.get(InstallerConstants.SUMMARY_PANEL_ID);
		if (controller != null) {
			if (!((ConfirmationController) controller).isInstallStarted()) {
				wizard.showConfirmDialog(InstallerConstants.DIALOG_LABEL_WARNING, 
						new String[] {LOCAL_INSTALLATION_HAS_NOT_STARTED, 
						DO_YOU_WANT_TO_EXIT_INSTALLER});
			} else if (((ConfirmationController) controller).isAllNodesDoneInstallation()) {
				wizard.showConfirmDialog(InstallerConstants.DIALOG_LABEL_CONFIRM, 
						new String[] {ALL_NODES_IN_CLUSTER_ARE_INSTALLED, 
				        CLICK_OK_TO_REBOOT});
			} else {
				wizard.displayWarningMessage(new String[] {
                                OPERATIONS_NOT_FINISHED_IN_CLUSTER,
                                ACTION_RECOMMENDATION});
			}
		}
	}
	
	private void nextButtonPressed() {
		BasePanel current = wizard.getCurrentPanel();
		IConfigPanelController controller = manager.getControllers()
				.get(current.getId());
		if (controller != null) {
			String[] msg = controller.configurationIsCompleted();
			if (msg != null && msg.length != 0) {
				wizard.displayWarningMessage(msg);
				return;
			}
		}
		// current is first page, enable Back button on the next page
		if (current.isFirstPage()) {
			wizard.enableBackButton();
		}

		BasePanel next = manager.getNextPanel(current);
		wizard.updateView(current, next);
		// this has to go after updated view
		// next is the last page, disable next button
		if (next.isLastPage()) {
			wizard.disableNextButton();
			wizard.switchToRebootButton();
		}
		log.info("Next from {} to {}", current.getId(), next.getId());
	}
	
	private void backButtonPressed() {
		BasePanel current = wizard.getCurrentPanel();
		if (current.getId().equals(InstallerConstants.SUMMARY_PANEL_ID)) {
			IConfigPanelController controller = manager.getControllers()
					.get(InstallerConstants.SUMMARY_PANEL_ID);
			if (controller != null && ((ConfirmationController) controller).isInstallStarted()) {
					wizard.displayWarningMessage( 
							new String[] {INSTALLATION_ALREADY_STARTED, 
							BACK_OPERATION_IS_NOT_PERMITTED});
					return;
			}
		}
		BasePanel previous = manager.getPriviousPanel(current);
		wizard.updateView(current, previous);
		// navigate back to the first page now, disable back button on first page
		if (previous.isFirstPage()) {
			wizard.disableBackButton();
		}
		// navigate away from last page, enable Next button
		if (current.isLastPage()) {
			wizard.enableNextButton();
			wizard.switchToExitButton();
		}
		log.info("Back from {} to {}", current.getId(), previous.getId());
	}
}
