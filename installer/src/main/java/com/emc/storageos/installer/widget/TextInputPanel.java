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

import java.util.LinkedHashMap;

import charva.awt.GridBagConstraints;
import charva.awt.GridBagLayout;
import charva.awt.Insets;
import charva.awt.event.ActionListener;
import charvax.swing.JLabel;
import charvax.swing.JPanel;
import charvax.swing.JTextField;

/**
 * Class implements text input from an input map.
 *
 */
public class TextInputPanel extends JPanel {
	
	private LinkedHashMap<String, JTextField> fieldMap = new LinkedHashMap<String, JTextField>();
	private LinkedHashMap<String, String> map;
	private String label;
	private int textLength;
	
	public TextInputPanel(String label, LinkedHashMap<String, String> map, int textLenght) {
		this.map = map;
		this.label = label;
		this.textLength = textLenght;
		initComponents();
	}
	
	private void initComponents() {
		setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        int left = 2;
        int right = 2;
        int x = 0;
        int y = 0;
        c.gridwidth = 2;
        c.insets = new Insets(1, left, 1, right);
        WidgetElementUtil.makeGridElement(x, y++, (new JLabel(label)), c, this);
        
        c.gridwidth = 1;
        for (String key : map.keySet()) {
        	c.anchor = GridBagConstraints.EAST;
			c.insets = new Insets(0, left, 0, 0);
			x =0;
			WidgetElementUtil.makeGridElement(x, y, new JLabel(key), c, this);
			JTextField field = new JTextField(map.get(key), textLength);
			x = 1;
			c.anchor = GridBagConstraints.WEST;
			c.insets = new Insets(0, 0, 0, right);
			WidgetElementUtil.makeGridElement(x, y++, field, c, this);
			fieldMap.put(key, field);
		}
        c.gridwidth = 2;
        c.insets = new Insets(0, left, 0, right);
        WidgetElementUtil.makeGridEmptyLine(x, y++, c, this);
	}
	
	public LinkedHashMap<String, JTextField> getFieldMap() {
		return fieldMap;
	}

	public void fieldSelectListener(ActionListener listenForFieldInput) {
		for (JTextField field: fieldMap.values()) {
			field.addActionListener(listenForFieldInput);
		}
	}
}
