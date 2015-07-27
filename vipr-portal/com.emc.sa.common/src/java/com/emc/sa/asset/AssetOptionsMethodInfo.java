/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset;

import java.lang.reflect.Method;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.asset.annotation.Asset;
import com.emc.sa.asset.annotation.AssetDependencies;
import com.emc.sa.asset.annotation.AssetNamespace;
import com.google.common.collect.Lists;

/** Describes a javaMethod that can be used to retrieve the specific Asset options */
public class AssetOptionsMethodInfo {
	
    public Method javaMethod;
    public String assetNamespace;
    public String assetName;
    public List<String> assetDependencies; 
    
    public AssetOptionsMethodInfo(AssetNamespace namespace, Asset asset, Method javaMethod) {
    	this.assetNamespace = namespace.value();
        this.assetName = formatAssetTypeName(namespace.value(), asset.value());
		this.assetDependencies = Lists.newArrayList();
		final AssetDependencies assetDependenciesAnnotation = javaMethod.getAnnotation(AssetDependencies.class);
		if ( assetDependenciesAnnotation != null ) {
			for ( String dependency : assetDependenciesAnnotation.value() ) {
				this.assetDependencies.add(formatAssetTypeName(namespace.value(), dependency));
			}
		}
		this.javaMethod = javaMethod;
	}

    public Object convertParameter(int index, String value) {
        Class<?>[] types = javaMethod.getParameterTypes();
        if ((index >= 0) && (index < types.length)) {
            Class<?> type = types[index];
            return AssetConverter.convert(value, type);
        }
        return value;
    }
    
	public String formatAssetTypeName(String namespace, String asset) {
	    if (StringUtils.contains(asset, '.')) {
	        return asset;
	    }
	    else {
	        return String.format("%s.%s", namespace, asset);
	    }
	}

	@Override
    public String toString() {
    	return "AssetOptionsMethod : " + assetNamespace + assetName +
    		   "\n\t Java Method: " + javaMethod.getName() +
               "\n\t Asset Dependencies: " + StringUtils.join(assetDependencies, ", ");
    }
}
