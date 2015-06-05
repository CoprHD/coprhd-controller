/**
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

import charva.awt.FlowLayout;
import charva.awt.Insets;
import charvax.swing.JPanel;
import charvax.swing.JTabbedPane;

public class TabbedPanel extends JPanel {
	private JTabbedPane tabPanel;
	private String title1;
	private String title2;
	private TextInputPanel panel1;
	private TextInputPanel panel2;

	public TabbedPanel(String title1, String title2, TextInputPanel panel1, TextInputPanel panel2) {
		this.title1 = title1;
		this.title2 = title2;
		this.panel1 = panel1;
		this.panel2 = panel2;
		initComponenets();
	}

	private void initComponenets() {
		this._insets = new Insets(1, 0, 1, 0);
		setLayout(new FlowLayout(FlowLayout.CENTER, 2, 2));
		tabPanel = new JTabbedPane();
		tabPanel.addTab(title1, null, panel1, "");
		tabPanel.addTab(title2, null, panel2, "");
		add(tabPanel);
	}
}
