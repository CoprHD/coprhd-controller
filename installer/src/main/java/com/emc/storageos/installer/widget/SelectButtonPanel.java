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

import java.util.ArrayList;
import java.util.List;

import charva.awt.GridBagConstraints;
import charva.awt.GridBagLayout;
import charva.awt.Insets;
import charva.awt.event.ItemListener;
import charvax.swing.ButtonGroup;
import charvax.swing.JLabel;
import charvax.swing.JPanel;
import charvax.swing.JRadioButton;

/**
 * Class implements button group(s) for selection.
 * 
 */
public class SelectButtonPanel extends JPanel {
    private ButtonGroup buttonGroup1 = new ButtonGroup();
    private ButtonGroup buttonGroup2 = new ButtonGroup();
    private List<JRadioButton> buttonList1 = new ArrayList<JRadioButton>();
    private List<JRadioButton> buttonList2 = new ArrayList<JRadioButton>();
    private JLabel eastLabel;
    private InstallerWizard root;

    // for two list of buttons (one on west side, one on east side)
    public SelectButtonPanel(InstallerWizard root, String label1, String label2, List<String> list1, List<String> list2) {
        this.root = root;
        intiComponents(label1, label2, list1, list2);
    }

    // for one list of buttons
    public SelectButtonPanel(InstallerWizard root, String label1, List<String> list1) {
        this.root = root;
        intiComponents(label1, list1);
    }

    // for two lists
    private void intiComponents(String label1, String label2, List<String> list1, List<String> list2) {
        int left = 0;
        int right = 8;
        int top = 2;
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        // add list1 on the left
        c.insets = new Insets(top, left, 1, right);
        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = 1;
        int x = 0;
        int y = 0;
        WidgetElementUtil.makeGridElement(x, y++, (new JLabel(label1)), c, this);
        c.insets = new Insets(0, left, 0, right);
        for (String name : list1) {
            JRadioButton b = new JRadioButton(name);
            WidgetElementUtil.makeGridElement(x, y++, b, c, this);
            buttonGroup1.add(b);
            buttonList1.add(b);
        }
        // select default one in the group
        buttonGroup1.setSelected(buttonList1.get(0), true);

        // add list2 on the right side
        left = 5;
        right = 5;
        x = 1;
        y = 0;
        c.insets = new Insets(top, left, 1, right);
        eastLabel = new JLabel(label2);
        WidgetElementUtil.makeGridElement(x, y++, (eastLabel), c, this);
        c.insets = new Insets(0, left, 0, right);
        for (String name : list2) {
            JRadioButton b = new JRadioButton(name);
            WidgetElementUtil.makeGridElement(x, y++, b, c, this);
            buttonGroup2.add(b);
            buttonList2.add(b);
        }
    }

    public InstallerWizard getRoot() {
        return this.root;
    }

    // for only one list
    private void intiComponents(String label, List<String> list) {
        if (list.isEmpty()) {
            root.displayWarningMessage(new String[] { "List is empty, please exit and retry" });
            return;
        }
        int left = 0;
        int right = 8;
        int top = 2;
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        // add list1 on the left
        c.insets = new Insets(top, left, 1, right);
        c.anchor = GridBagConstraints.WEST;
        c.gridwidth = 1;
        int x = 0;
        int y = 0;
        WidgetElementUtil.makeGridElement(x, y++, (new JLabel(label)), c, this);
        c.insets = new Insets(0, left, 0, right);
        for (String name : list) {
            JRadioButton b = new JRadioButton(name);
            WidgetElementUtil.makeGridElement(x, y++, b, c, this);
            buttonGroup1.add(b);
            buttonList1.add(b);
        }
        // select default one in the group
        buttonGroup1.setSelected(buttonList1.get(0), true);

    }

    public JLabel getEastLabel() {
        return eastLabel;
    }

    public List<JRadioButton> getButtonList1() {
        return this.buttonList1;
    }

    public List<JRadioButton> getButtonList2() {
        return this.buttonList2;
    }

    public void selectList1ItemListener(ItemListener selectItemListener) {
        for (JRadioButton b : buttonList1) {
            b.addItemListener(selectItemListener);
        }
    }

    public void selectList2ItemListener(ItemListener selectItemListener) {
        for (JRadioButton b : buttonList2) {
            b.addItemListener(selectItemListener);
        }
    }
}
