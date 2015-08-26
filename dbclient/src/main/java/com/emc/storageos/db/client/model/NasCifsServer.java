/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.model;

import java.util.ArrayList;
import java.util.List;

public class NasCifsServer extends AbstractSerializableNestedObject {

    private String _name;
    private int _id;
    private String _type;
    private boolean _moverIdIsVdm;
    private List<String> _interfaces = new ArrayList();

    public NasCifsServer() {
    }

    public NasCifsServer(String name) {
        _name = name;
    }

    public NasCifsServer(String name, String id, String type, boolean isMoverIsVdm, List<String> interfaces) {
        _name = name;
        _interfaces = interfaces;
        _type = type;
        _id = Integer.valueOf(id);
        _moverIdIsVdm = isMoverIsVdm;
    }
    
    public void setName(String name) {
        _name = name;
    }

    public String getName() {
        return _name;
    }

    public void setId(int id) {
        _id = id;
    }

    public int getId() {
        return _id;
    }

    public void setType(String type) {
        _type = type;
    }

    public String getType() {
        return _type;
    }

    public void setMoverIdIsVdm(boolean moverIdIsVdm) {
        _moverIdIsVdm = moverIdIsVdm;
    }

    public boolean getMoverIdIsVdm() {
        return _moverIdIsVdm;
    }

    public List<String> getInterfaces() {
        return _interfaces;
    }

    public void setInterfaces(List interfaces) {
        _interfaces = interfaces;
    }

   

    @Override
    public String toString() {

        return new StringBuilder().append("name : ").append(_name).append("interfaces : ").append(_interfaces.toString()).toString();

    }

    // <CifsServer interfaces="10.247.27.32" type="W2K" localUsers="false" name="LOSAT032" mover="1" moverIdIsVdm="false">
    // <CifsServer interfaces="10.247.27.31" type="W2K" localUsers="false" name="LOSAT031" mover="5" moverIdIsVdm="true">
}