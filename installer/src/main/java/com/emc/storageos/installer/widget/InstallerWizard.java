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
package com.emc.storageos.installer.widget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.services.util.InstallerConstants;

import charva.awt.BorderLayout;
import charva.awt.Container;
import charva.awt.Dimension;
import charva.awt.GridBagConstraints;
import charva.awt.GridBagLayout;
import charva.awt.Insets;
import charva.awt.Toolkit;
import charva.awt.event.ActionListener;
import charvax.swing.JButton;
import charvax.swing.JPanel;
import charvax.swing.JFrame;

/**
 * Class implements the main frame of the installer.
 *
 */
public class InstallerWizard extends JFrame {
	private static final Logger log = LoggerFactory.getLogger(InstallerWizard.class);
	private JButton backButton;
	private JButton nextButton;
	private JButton exitButton;
	private JPanel buttonPanel;
	private BasePanel current;

	public InstallerWizard() {
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		log.debug("Screen width: {}, height: {}", screenSize.width, screenSize.height);
		setSize(80, 24);
		initComponent();
		Runtime.getRuntime().addShutdownHook(new Thread() {
		    public void run() {
		    	log.info("Exiting Installer now ..."); 
		    	// Close the terminal window and restore terminal settings
		        Toolkit.getDefaultToolkit().close();
		    }
		});
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	}
	
	private void initComponent() {
		Container contentPanel = getContentPane();

		nextButton = new JButton(InstallerConstants.BUTTON_NEXT);
		nextButton.setActionCommand(InstallerConstants.BUTTON_NEXT);
		
		backButton = new JButton(InstallerConstants.BUTTON_BACK);
		backButton.setActionCommand(InstallerConstants.BUTTON_BACK);
		backButton.setVisible(false);

		exitButton = new JButton(InstallerConstants.BUTTON_EXIT);
		exitButton.setActionCommand(InstallerConstants.BUTTON_EXIT);

        buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(1, 2, 1, 2);
        c.gridwidth = 1;
        int x = 0;
        int y = 0;
		WidgetElementUtil.makeGridElement(x, y, backButton, c, buttonPanel);
		x = 1;
        WidgetElementUtil.makeGridElement(x, y, nextButton, c, buttonPanel);
		x = 2;
		WidgetElementUtil.makeGridElement(x, y++, exitButton, c, buttonPanel);
		contentPanel.add(buttonPanel, BorderLayout.SOUTH);
	}
	
	private void removePanel(JPanel panel) {
		Container contentPanel = getContentPane();
		contentPanel.remove(panel);
		contentPanel.validate();
		contentPanel.repaint();
	}
	
	private void addPanel(JPanel panel) {
		Container contentPanel = getContentPane();
		contentPanel.add(panel);
		contentPanel.validate();
		contentPanel.repaint();
	}
	
	public void removeButtonPanel() {
		Container contentPanel = getContentPane();
		contentPanel.remove(buttonPanel);
		contentPanel.validate();
		contentPanel.repaint();
	}
	
	public JButton getNextButton() {
		return nextButton;
	}
	
	public void updateView(BasePanel oldPanel, BasePanel newPanel) {
		if (newPanel == null) {
			return;
		}
		if (oldPanel != null) {
			removePanel(oldPanel);
		}
		if (newPanel != null) {
			setTitle(newPanel.getTitle());
			addPanel(newPanel);
			// disable Next button on Error page
			if (newPanel.getId().equals(InstallerConstants.ERROR_PANEL_ID)) {
				disableNextButton();			
			} else if (newPanel.getId().equals(InstallerConstants.SUMMARY_PANEL_ID)) {
				// If it's the summary/confirmation view, we need to focus the cursor on install/config/redeploy button
				DisplayPanel displayPanel = (DisplayPanel) newPanel.getDataView().get(InstallerConstants.PANEL_POSITION_CENTER);
				displayPanel.getStartButton().requestFocus(); 
			}
		}
		current = newPanel;
	}
	
	public BasePanel getCurrentPanel() {
		return current;
	}
	
	public void enableBackButton() {
		backButton.setVisible(true);
	}
	
	public void disableBackButton() {
		backButton.setVisible(false);
	}
	
	public void enableNextButton() {
		nextButton.setVisible(true);
	}
	
	public void disableNextButton() {
		nextButton.setVisible(false);
	}
	
	public void switchToRebootButton() {
		exitButton.setText(InstallerConstants.BUTTON_REBOOT);
		exitButton.setActionCommand(InstallerConstants.BUTTON_REBOOT);
		exitButton.validate();
		exitButton.repaint();
	}
	
	public void switchToExitButton() {
		exitButton.setText(InstallerConstants.BUTTON_EXIT);
		exitButton.setActionCommand(InstallerConstants.BUTTON_EXIT);
		exitButton.validate();
		exitButton.repaint();
	}
	
	public void setButtonListener(ActionListener listenForButton) {
		nextButton.addActionListener(listenForButton);
		backButton.addActionListener(listenForButton);
		exitButton.addActionListener(listenForButton);
	}
		
	public void displayErrorMessage(String[] errorMessage) {
		WidgetElementUtil.ShowErrorMessage(this, errorMessage);
	}

	public void confirmExit(String message) {
		WidgetElementUtil.confirmExit(this, message);
	}

	public void showConfirmDialog(String title, String[] info) {
		WidgetElementUtil.showConfirmDialog(this, title, info);
	}
	
	public void displayInfoMessage(String[] info) {
		WidgetElementUtil.ShowInfoMessage(this, info);
	}
	
	public void displayWarningMessage(String[] info) {
		WidgetElementUtil.ShowWarningMessage(this, info);
	}

}
