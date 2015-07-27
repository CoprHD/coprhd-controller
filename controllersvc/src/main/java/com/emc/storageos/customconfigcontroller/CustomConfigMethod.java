/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;

import com.emc.storageos.db.client.model.Name;

public class CustomConfigMethod implements Serializable {
	private String name;
	private String description;
	private String methodName;
	private List<String> parameterTypes;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getMethodName() {
		return methodName;
	}

	public void setMethodName(String methodName) {
		this.methodName = methodName;
	}

	public List<String> getParameterTypes() {
		return parameterTypes;
	}

	public void setParameterTypes(List<String> parameterTypes) {
		this.parameterTypes = parameterTypes;
	}

	public Class[] getMethodParams() throws ClassNotFoundException {
		if (parameterTypes == null)
			return new Class[] {};
		else {
			Class[] methodParams = new Class[parameterTypes.size()];
			for (int i = 0; i < parameterTypes.size(); i++) {
				String className = parameterTypes.get(i);
				Class clazz = null;
				if (className.equals("int")) {
					clazz = int.class;
				} else {
					clazz = Class.forName(className);
				}
				methodParams[i] = clazz;
			}

			return methodParams;
		}
	}

	public String invoke(String str, List<String> args) throws Exception {
		Method stringMethod = String.class.getDeclaredMethod(getMethodName(),
				getMethodParams());
		return (String) stringMethod.invoke(str, args.toArray());
	}
}
