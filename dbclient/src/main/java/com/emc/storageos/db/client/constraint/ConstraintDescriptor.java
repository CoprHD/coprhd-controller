/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.db.client.constraint;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.TypeMap;

/**
 * The constraint to be sent across DCs
 * The concreted constraints are too complicated to be sent,
 * so we use this class to represent a constraint to be sent across DCs
 * and use it to re-generate the corresponding Constraint subclass on the server side
 */
@XmlRootElement
public class ConstraintDescriptor {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ConstraintDescriptor.class);

    private String constraintClassName;
    private String dataObjectClassName;
    private String columnFieldName;
    private List<Object> arguments;
    private int columnFieldPosition;

    public ConstraintDescriptor() {
    }

    public String getConstraintClassName() {
        return constraintClassName;
    }

    public void setConstraintClassName(String constraintClassName) {
        this.constraintClassName = constraintClassName;
    }

    public String getDataObjectClassName() {
        return dataObjectClassName;
    }

    public void setDataObjectClassName(String dataObjectClassName) {
        this.dataObjectClassName = dataObjectClassName;
    }

    public String getColumnFieldName() {
        return columnFieldName;
    }

    public void setColumnFieldName(String columnFieldName) {
        this.columnFieldName = columnFieldName;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    public void setArguments(List<Object> arguments) {
        this.arguments = arguments;
    }

    public int getColumnFieldPosition() {
        return columnFieldPosition;
    }

    public void setColumnFieldPosition(int columnFieldPosition) {
        this.columnFieldPosition = columnFieldPosition;
    }

    public Constraint toConstraint() throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException
            , InvocationTargetException, InstantiationException {
        log.info("ConstraintDescriptor {}", this);

        Class type = Class.forName(dataObjectClassName);
        DataObjectType doType = TypeMap.getDoType(type);
        ColumnField field = doType.getColumnField(columnFieldName);

        Class constraintClass = Class.forName(constraintClassName);

        List<Class> parameterTypes = new ArrayList(arguments.size() + 1);
        List<Object> args = new ArrayList(arguments.size() + 1);

        int i = 1;
        for (Object arg : arguments) {
            if (i == columnFieldPosition) {
                parameterTypes.add(ColumnField.class);
                args.add(field);
            }

            i++;
            parameterTypes.add(arg.getClass());
            args.add(arg);
        }

        if (i == columnFieldPosition) {
            parameterTypes.add(ColumnField.class);
            args.add(field);
        }

        Constructor constructor = constraintClass.getConstructor(parameterTypes.toArray(new Class[0]));
        Constraint constraint = (Constraint) constructor.newInstance(args.toArray());

        return constraint;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getName());
        builder.append("\n\tConstraint Class:");
        builder.append(constraintClassName);
        builder.append("\n\tDataObject class:");
        builder.append(dataObjectClassName);
        builder.append("\n\tColumne field:");
        builder.append(columnFieldName);
        builder.append("\n\tColumnField position:");
        builder.append(columnFieldPosition);

        builder.append("\n\targuments:");
        for (Object arg : arguments) {
            builder.append("\n\t\t type:");
            builder.append(arg.getClass().getName());
            builder.append("\n\t\t value:");
            builder.append(arg.toString());
        }

        return builder.toString();
    }
}
