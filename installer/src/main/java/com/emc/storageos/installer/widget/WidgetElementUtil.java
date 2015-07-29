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

import com.emc.storageos.installer.util.InstallerConstants;

import charva.awt.Component;
import charva.awt.GridBagConstraints;
import charvax.swing.JLabel;
import charvax.swing.JOptionPane;
import charvax.swing.JPanel;

/**
 * Util class for using some charva elements.
 * 
 */
public class WidgetElementUtil {

    public static void makeGridElement(int x, int y, Component comp, GridBagConstraints gbc, JPanel pan) {
        gbc.gridx = x;
        gbc.gridy = y;
        pan.add(comp, gbc);
    }

    public static void makeGridEmptyLine(int x, int y, GridBagConstraints gbc, JPanel pan) {
        gbc.gridx = x;
        gbc.gridy = y;
        pan.add(new JLabel(""), gbc);
    }

    public static JLabel makeEmptyLabel() {
        return new JLabel("");
    }

    public static void confirmExit(Component component, String message) {
        int option = JOptionPane.showConfirmDialog(component,
                message, InstallerConstants.DIALOG_LABEL_CONFIRM,
                JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            System.exit(1);
        }
    }

    public static void showConfirmDialog(Component component, String title, String[] message) {
        int option = JOptionPane.showConfirmDialog(component,
                message, title,
                JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            System.exit(0);
        }
    }

    public static void ShowErrorMessage(Component component, String[] msg) {
        JOptionPane.showMessageDialog(component, msg, InstallerConstants.DIALOG_LABEL_ERROR, JOptionPane.ERROR_MESSAGE);
    }

    public static void ShowInfoMessage(Component component, String[] msg) {
        JOptionPane.showMessageDialog(component, msg, InstallerConstants.DIALOG_LABEL_INFO, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void ShowWarningMessage(Component component, String[] msg) {
        JOptionPane.showMessageDialog(component, msg, InstallerConstants.DIALOG_LABEL_WARNING, JOptionPane.WARNING_MESSAGE);
    }
}
