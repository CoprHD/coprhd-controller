/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 *
 * Copyright (c) 2011-2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 *
 */

package com.emc.storageos.geomodel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;

public class ResourcesResponse<T> implements Serializable {
    static final long serialVersionUID = -91407659467595494L;

    private int size = 0;

    private List<T> objects = new ArrayList();

    public ResourcesResponse(){
    }


    public List<T> getObjects() {
        return objects;
    }

    public void add(T obj) {
        objects.add(obj);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        out.writeInt(size);
        for( T obj : objects)
            out.writeObject(obj);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();

        size = in.readInt();
        objects = new ArrayList(size);
        for (int i =0; i < size; i++)
            add((T)in.readObject());
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
