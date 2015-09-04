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

import com.emc.storageos.installer.util.InstallerConstants;
import com.emc.storageos.installer.util.InstallerUtil;

import charva.awt.GridBagConstraints;
import charva.awt.GridBagLayout;
import charva.awt.Insets;
import charva.awt.event.ActionListener;
import charvax.swing.JButton;
import charvax.swing.JLabel;
import charvax.swing.JPanel;
import charvax.swing.JProgressBar;
import charvax.swing.JScrollPane;
import charvax.swing.JTextArea;
import charvax.swing.border.TitledBorder;

/**
 * Class implements text display panel and a progress bar
 *
 */
public class DisplayPanel extends JPanel {
    private LinkedHashMap<String, String> map;
    private JProgressBar progressBar = new JProgressBar();
    private JButton startButton;
    private JLabel progress;
    private String label;
    private String label1;
    private String label2;
    private InstallerWizard root;

    public DisplayPanel(InstallerWizard root, String label, String label1, String label2,
            LinkedHashMap<String, String> map, String buttonLabel) {
        this.map = map;
        this.label = label;
        this.label1 = label1;
        this.label2 = label2;
        this.root = root;
        this.startButton = new JButton(buttonLabel);
        initComponent();
    }

    public InstallerWizard getRoot() {
        return this.root;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public JButton getStartButton() {
        return startButton;
    }

    public JLabel getProgress() {
        return progress;
    }

    private void initComponent() {
        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        int x = 0;
        int y = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(1, 1, 0, 1);
        c.gridwidth = 1;
        JScrollPane scrollpane = new JScrollPane(createTextDisplay());
        TitledBorder viewportBorder = new TitledBorder(label);
        scrollpane.setViewportBorder(viewportBorder);
        WidgetElementUtil.makeGridElement(x, y++, scrollpane, c, this);
        // add progress panel at the bottom
        WidgetElementUtil.makeGridElement(x, y++, createProgressPanel(), c, this);
    }

    private JTextArea createTextDisplay() {
        String text = getDisplayTextString();
        // create a text of 10 rows and 62 columns (15 for label and 45 for value)
        JTextArea displayText = new JTextArea(text, 10, 62);
        displayText.setLocation(1, 0);
        displayText.setLineWrap(true);
        displayText.setWrapStyleWord(true);
        displayText.setEditable(false);
        return displayText;
    }

    private String getDisplayTextString() {
        StringBuilder builder = new StringBuilder();
        for (String key : map.keySet()) {
            String label = InstallerUtil.padRight(key, 15);
            String value = map.get(key);
            builder.append(label).append(value).append("\n");
        }
        return builder.substring(0, builder.length() - 1).toString();
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        // progress bar
        GridBagConstraints c = new GridBagConstraints();
        int left = 2;
        int right = 2;
        int x = 0;
        int y = 0;
        // labels
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, left, 0, right);
        c.gridwidth = 6;
        JLabel info = new JLabel(label1);
        WidgetElementUtil.makeGridElement(x, y++, info, c, panel);
        c.insets = new Insets(0, left, 0, right);
        JLabel info2 = new JLabel(label2);
        WidgetElementUtil.makeGridElement(x, y++, info2, c, panel);

        // status text
        x = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(1, 0, 0, 0);
        c.gridwidth = 5;
        progress = new JLabel();
        progress.setText("");
        WidgetElementUtil.makeGridElement(x, y++, progress, c, panel);

        // install button
        c.gridwidth = 1;
        x = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, left, 0, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        startButton.setActionCommand(InstallerConstants.BUTTON_ACTION_START);
        WidgetElementUtil.makeGridElement(x, y, startButton, c, panel);

        // progress bar
        c.anchor = GridBagConstraints.WEST;
        x = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        c.gridwidth = 5;
        progressBar.setMinimum(0);
        progressBar.setMaximum(30);
        WidgetElementUtil.makeGridElement(x, y++, progressBar, c, panel);
        return panel;
    }

    public void addButtonActionListener(ActionListener actionListener) {
        startButton.addActionListener(actionListener);
    }

}
