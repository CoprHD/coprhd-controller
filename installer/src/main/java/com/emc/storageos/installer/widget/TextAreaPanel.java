/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.installer.widget;

import charva.awt.GridBagConstraints;
import charva.awt.GridBagLayout;
import charva.awt.Insets;
import charvax.swing.JPanel;
import charvax.swing.JScrollPane;
import charvax.swing.JTextArea;
import charvax.swing.border.TitledBorder;

public class TextAreaPanel extends JPanel {
	
	public TextAreaPanel(String title, String text) {
		initComponents(title, text);
	}

	private void initComponents(String title, String text) {
		JTextArea displayText = new JTextArea(text, 8, 50);
		displayText.setLineWrap(true);
		displayText.setWrapStyleWord(true);
		displayText.setEditable(false);
		
		JScrollPane scrollpane = new JScrollPane(displayText);
		scrollpane.setViewportBorder(new TitledBorder(title));
		 
		setLayout(new GridBagLayout());	
		GridBagConstraints c = new GridBagConstraints();
	    c.anchor = GridBagConstraints.CENTER;
	    c.insets = new Insets(1, 2, 1, 2);
	    c.gridx = 0;
	    c.gridy = 0;
		add(scrollpane, c);
	}
}
