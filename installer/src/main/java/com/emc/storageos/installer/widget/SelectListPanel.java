/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.widget;

import charva.awt.GridBagConstraints;
import charva.awt.GridBagLayout;
import charvax.swing.JList;
import charvax.swing.JPanel;
import charvax.swing.JScrollPane;
import charvax.swing.ListSelectionModel;
import charvax.swing.border.TitledBorder;
import charvax.swing.event.ListSelectionListener;

/**
 * Class implements list selection using a scroll panel.
 * 
 */
public class SelectListPanel extends JPanel {
    private JList jList;
    private String label;
    private String[] lists;
    private InstallerWizard root;

    public SelectListPanel(InstallerWizard root, String label, String[] lists) {
        this.root = root;
        this.label = label;
        this.lists = lists;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        jList = new JList(lists);
        if (lists.length > 10) {
            jList.setVisibleRowCount(10);
        } else {
            jList.setVisibleRowCount(lists.length);
        }
        int columnNum = 0;
        for (String s : lists) {
            if (s.length() > columnNum) {
                columnNum = s.length();
            }
        }
        if (columnNum > 20) {
            columnNum = columnNum + 5;
        } else {
            columnNum = 25;
        }
        jList.setColumns(columnNum);
        // only one selection allowed at a time
        jList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollpane = new JScrollPane(jList);
        TitledBorder viewportBorder = new TitledBorder(label);
        scrollpane.setViewportBorder(viewportBorder);
        add(scrollpane, c);
    }

    public void addListSelectionListener(
            ListSelectionListener listSelectionListener) {
        jList.addListSelectionListener(listSelectionListener);
    }

    public JList getList() {
        return this.jList;
    }

    public InstallerWizard getRoot() {
        return this.root;
    }

}
