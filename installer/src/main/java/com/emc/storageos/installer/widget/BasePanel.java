/*
 * Copyright (c) 2008-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.installer.widget;

import java.util.Map;

import com.emc.storageos.installer.util.InstallerConstants;

import charva.awt.BorderLayout;
import charvax.swing.JLabel;
import charvax.swing.JPanel;

/**
 * Class implements each page base layout based on the input view data.
 * 
 */
public class BasePanel extends JPanel {
    private String id;
    private String title;
    private String instruction;
    private JPanel east;
    private JPanel west;
    private JPanel center;
    private BasePanel next;
    private BasePanel previous;
    private boolean isFirst = false;
    private boolean isLast = false;
    private Map<Object, Object> dataView;

    public Map<Object, Object> getDataView() {
        return dataView;
    }

    public BasePanel(String id, Map<Object, Object> view) {
        this.id = id;
        this.title = (String) view.get(InstallerConstants.PANEL_TITLE_KEY);
        this.instruction = (String) view.get(InstallerConstants.PANEL_INST_KEY);
        this.setLayout(new BorderLayout());
        setupInstructionPanel();
        dataView = view;
        setupContentPanel(view);
    }

    /*
     * Set up content panel using the view data.
     * 
     * @param view - a map of view data with layout positions
     */
    private void setupContentPanel(Map<Object, Object> view) {
        if (view == null || view.isEmpty()) {
            return;
        }
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());

        west = (JPanel) view.get(InstallerConstants.PANEL_POSITION_WEST);
        if (west != null) {
            contentPanel.add(west, BorderLayout.WEST);
        }

        east = (JPanel) view.get(InstallerConstants.PANEL_POSITION_EAST);
        if (east != null) {
            contentPanel.add(east, BorderLayout.EAST);
        }

        center = (JPanel) view.get(InstallerConstants.PANEL_POSITION_CENTER);
        if (center != null) {
            contentPanel.add(center, BorderLayout.CENTER);
        }
        add(contentPanel, BorderLayout.CENTER);
    }

    public String getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    private void setupInstructionPanel() {
        if (instruction == null || instruction.isEmpty()) {
            return;
        }
        setLayout(new BorderLayout());
        add(new JLabel(instruction), BorderLayout.SOUTH);
    }

    public BasePanel getNext() {
        return next;
    }

    public BasePanel getPrevious() {
        return previous;
    }

    public void setNext(BasePanel next) {
        this.next = next;
    }

    public void setPrevious(BasePanel previous) {
        this.previous = previous;
    }

    public boolean isFirstPage() {
        return isFirst;
    }

    public void setFirstPage(boolean b) {
        this.isFirst = b;
    }

    public boolean isLastPage() {
        return isLast;
    }

    public void setLastPage(boolean b) {
        this.isLast = b;
    }
}
