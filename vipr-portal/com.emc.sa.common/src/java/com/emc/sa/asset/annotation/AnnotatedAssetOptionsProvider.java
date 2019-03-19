/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.asset.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.apache.log4j.Logger;

import com.emc.sa.asset.AbstractAssetOptionsProvider;
import com.emc.sa.asset.AssetOptionsContext;
import com.emc.sa.asset.AssetOptionsMethodInfo;
import com.emc.sa.asset.AssetOptionsProvider;
import com.emc.vipr.model.catalog.AssetOption;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Abstract base class for all Asset Options Providers that use the {@link AssetNamespace} and {@link Asset} annotations.
 * 
 * An AssetOptionsProvider should use the {@link AssetNamespace} and {@link Asset} annotations in the following way.
 * (Methods must be public and return a {@link List}<{@link AssetOption}>)
 * 
 * <pre>
 * &#064;AssetNamespace(&quot;myAssets&quot;)
 * public class MyForDataProvider extends AssetOptionsProvider {
 *     &#064;Asset(&quot;vcenter&quot;)
 *     public static List&lt;AssetOptionsProvider.Option&gt; getVCenters() {
 *         // ...
 *     }
 * 
 *     &#064;Asset(value = &quot;host&quot;, dependsOn = { &quot;vcenter&quot; })
 *     public static List&lt;AssetOptionsProvider.Option&gt; getHosts(String vcenter) {
 *         // ...
 *     }
 * }
 * </pre>
 * 
 * The above provider registers two Assets, myAssets.vcenter and myAssets.host. When fetching a host, it also specifies
 * that a vcenter assetType name should be passed.
 * 
 * The parameter names are not important, but the order of the items in the 'dependsOn' list /IS/. This is how the
 * provider figures out how to whether the 'required parameters' requirements have been met.
 * 
 */
public abstract class AnnotatedAssetOptionsProvider extends AbstractAssetOptionsProvider implements AssetOptionsProvider {

    private static final Logger log = Logger.getLogger(AnnotatedAssetOptionsProvider.class);

    /**
     * a map of the supported asset types and the corresponding list of AssetOptionsMethods which are available to
     * support that asset type.
     */
    protected Map<String, List<AssetOptionsMethodInfo>> supportedAssetTypes = Maps.newHashMap();

    @PostConstruct
    public void init() {
        // discover all the asset options provided by this implementation and store them for later
        storeAssetOptionsMethods(discoverAssetsForClass());
    }

    @Override
    public boolean isAssetTypeSupported(String assetTypeName) {
        // if the supportedAssetTypes list has an entry for this asset type name then it's supported
        return supportedAssetTypes.containsKey(assetTypeName);
    }

    /**
     * Return all the possible options for the given assetType
     */
    @Override
    public List<AssetOption> getAssetOptions(AssetOptionsContext context, String assetTypeName,
            Map<String, String> availableAssets) {

        // find the asset options method for the given asset type name
        final AssetOptionsMethodInfo assetMethod = findAssetMethodInfo(assetTypeName, availableAssets.keySet());

        log.debug(String.format("Calling asset method [%s] with available assets [%s]", assetMethod.javaMethod.getName(), availableAssets));
        try {
            return invokeAssetMethod(assetMethod, context, availableAssets);
        } catch (Exception e) {
            log.error("Error invoking assetType retrieval method " + assetMethod.javaMethod.getName(), e);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException("Error invoking assetType retrieval method " + assetMethod.javaMethod.getName(), e);
            }
        }
    }

    @Override
    public List<String> getAssetDependencies(String assetType, Set<String> availableTypes) {
        AssetOptionsMethodInfo method = findAssetMethodInfo(assetType, availableTypes);

        if (method == null) {
            StringBuffer buffer = new StringBuffer();
            for (AssetOptionsMethodInfo assetMethod : supportedAssetTypes.get(assetType)) {
                buffer.append("{" + assetMethod.assetNamespace + "." + assetMethod.assetName + "} " +
                        assetMethod.javaMethod.getName() + " " + assetMethod.assetDependencies + "\n");
            }

            throw new RuntimeException("Unable to find asset retrieval method for " + assetType + " with available types "
                    + availableTypes.toString() + "\n" +
                    "Possible matches : \n" + buffer.toString());
        }

        return method.assetDependencies;
    }

    /**
     * stores the asset options method information in the supportedAssetTypes list
     */
    public void storeAssetOptionsMethods(List<AssetOptionsMethodInfo> assetMethods) {
        // record the assets for later
        for (AssetOptionsMethodInfo annotatedMethodInfo : assetMethods) {
            final String assetTypeName = annotatedMethodInfo.assetName;

            // if the supported asset types map doesn't already contain an entry
            // for this asset type we need to add one
            if (!supportedAssetTypes.containsKey(assetTypeName)) {
                supportedAssetTypes.put(assetTypeName, Lists.<AssetOptionsMethodInfo> newArrayList());
            }

            // add the annotated method we found to the supported asset types map
            // using the asset type name as the key
            supportedAssetTypes.get(assetTypeName).add(annotatedMethodInfo);
        }
    }

    /**
     * Find the methods that have the @{@link Asset} annotation and have the correct signature
     */
    public List<AssetOptionsMethodInfo> discoverAssetsForClass() {
        final Class<? extends AssetOptionsProvider> providerClass = this.getClass();
        final AssetNamespace namespace = (AssetNamespace) providerClass.getAnnotation(AssetNamespace.class);
        final List<AssetOptionsMethodInfo> assetMethods = Lists.newArrayList();
        if (namespace != null) {
            for (Method javaMethod : providerClass.getMethods()) {
                if (isAssetMethod(javaMethod)) {
                    // Ensures that the asset method parameters/dependencies match
                    validateMethodSignature(javaMethod);
                    final Asset asset = javaMethod.getAnnotation(Asset.class);
                    assetMethods.add(new AssetOptionsMethodInfo(namespace, asset, javaMethod));
                }
            }
        }
        return assetMethods;
    }

    /**
     * Return true if the given java method is an 'Asset' method.
     * Which is to say, if the method is annotated with the @{@link Asset} annotation and
     * has the correct signature: <code>public List<AssetOption> (AssetOptionsContext context, ...)</code>
     */
    private boolean isAssetMethod(Method javaMethod) {
        final boolean isAnnotated = javaMethod.getAnnotation(Asset.class) != null;
        final boolean isPublic = Modifier.isPublic(javaMethod.getModifiers());
        final boolean returnsAssetOptionsList = List.class.isAssignableFrom(javaMethod.getReturnType());
        final boolean firstParamIsContext = isFirstParamAssetOptionsContext(javaMethod);
        final boolean correctSignature = isPublic && returnsAssetOptionsList && firstParamIsContext;

        // if the method is annotated with @Asset but doesn't have the correct signature we need to return
        // false, but we can also put a message in the log about the problem
        if (isAnnotated && !correctSignature) {
            log.error(String
                    .format("Method %s::%s has the @Asset annotation but does not conform to the correct signature: public List<%s> (AssetOptionsContext context, ...)",
                            javaMethod.getDeclaringClass().getName(), javaMethod.getName(), AssetOption.class.getSimpleName()));
            return false;
        }

        return isAnnotated && correctSignature;
    }

    /**
     * return true if the given java method has at least one parameter
     * and the first one is an {@link AssetOptionsContext}
     */
    private boolean isFirstParamAssetOptionsContext(Method javaMethod) {
        final boolean hasParams = javaMethod.getParameterTypes().length > 0;
        if (hasParams) {
            final Class<?> firstParamType = javaMethod.getParameterTypes()[0];
            return firstParamType.isAssignableFrom(AssetOptionsContext.class);
        }
        return false;
    }

    /**
     * Invoke the given {@link AssetOptionsMethodInfo} using the available asset information
     */
    @SuppressWarnings("unchecked")
    public List<AssetOption> invokeAssetMethod(AssetOptionsMethodInfo assetMethod, AssetOptionsContext context,
            Map<String, String> availableAssets)
            throws IllegalAccessException, InvocationTargetException {
        final List<Object> javaMethodParameters = buildJavaMethodParameters(context, availableAssets, assetMethod);
        try {
            return (List<AssetOption>) assetMethod.javaMethod.invoke(this, javaMethodParameters.toArray());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw e;
            }
        }
    }

    /**
     * Build the list of parameter objects to hand in to the method invocation
     */
    public List<Object> buildJavaMethodParameters(AssetOptionsContext context, Map<String, String> availableAssets,
            AssetOptionsMethodInfo assetMethod) {
        final List<Object> javaMethodParameters = Lists.newArrayList();

        // add the context
        javaMethodParameters.add(context);

        // cycle through the asset dependencies and lookup the value
        // for that dependency in the available assets map and add it
        // to the javaMethodParameters list for use in the method invocation.
        for (String parentAssetName : assetMethod.assetDependencies) {
            String parentAssetValue = availableAssets.get(parentAssetName);
            // Added check if the value contains any double quotes(") if so remove it.
            if (parentAssetValue.contains("\"")) {
            	parentAssetValue = parentAssetValue.replaceAll("^\"|\"$", "");
            }
            int index = javaMethodParameters.size();
            Object value = assetMethod.convertParameter(index, parentAssetValue);
            javaMethodParameters.add(value);
        }
        return javaMethodParameters;
    }

    /**
     * Finds a possible method that can be called for the given assetType type and the given parameter names
     * 
     * @param assetRetrialMethods
     */
    public AssetOptionsMethodInfo findAssetMethodInfo(String assetTypeName, Set<String> availableAssets) {
        if (!supportedAssetTypes.containsKey(assetTypeName)) {
            throw new RuntimeException(String.format("No asset found with name: %s", assetTypeName));
        }

        int lastMatch = -1;
        AssetOptionsMethodInfo foundMatch = null;
        for (AssetOptionsMethodInfo info : supportedAssetTypes.get(assetTypeName)) {
            int matchingParentAssets = 0;
            for (String parentAsset : availableAssets) {
                if (info.assetDependencies.contains(parentAsset)) {
                    matchingParentAssets++;
                }
            }

            // Only use this method if it has MORE matching parameters than the previous one found
            if (info.assetDependencies.size() == matchingParentAssets) {
                if (info.assetDependencies.size() > lastMatch) {
                    foundMatch = info;
                    lastMatch = info.assetDependencies.size();
                }
            }
        }

        if (foundMatch == null) {
            final String errorMessage = String.format("Query for Asset '%s' requires additional asset dependencies. Supplied: %s",
                    assetTypeName, availableAssets.toString());
            log.warn(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        return foundMatch;
    }

    /**
     * Dumps all registered assets along with the registered methods
     */
    @SuppressWarnings("unused")
    private void dump() {
        for (Map.Entry<String, List<AssetOptionsMethodInfo>> entry : supportedAssetTypes.entrySet()) {
            System.out.println("*" + entry.getKey());
            for (AssetOptionsMethodInfo info : entry.getValue()) {
                System.out.println("--" + info.javaMethod.getName());
                System.out.println("-- Requires:");
                for (String parentAsset : info.assetDependencies) {
                    System.out.println("-----" + parentAsset);
                }
            }
        }
    }

    /**
     * Validates that the provided method matches the expected signature.
     * 
     * @param method
     *            the method to validate.
     */
    public static void validateMethodSignature(Method method) {
        Class<?>[] parameterTypes = method.getParameterTypes();
        // Ensure the first parameter is an asset options context
        if ((parameterTypes.length == 0) || !parameterTypes[0].isAssignableFrom(AssetOptionsContext.class)) {
            throw new IllegalArgumentException("AssetOptionsContext must be the first parameter: "
                    + method.toGenericString());
        }

        // Ensure the method arguments match the number of dependencies
        int requiredNumberOfDependencies = parameterTypes.length - 1;
        AssetDependencies dependencies = method.getAnnotation(AssetDependencies.class);
        int dependencyCount = (dependencies != null) ? dependencies.value().length : 0;

        if (dependencyCount < requiredNumberOfDependencies) {
            throw new IllegalArgumentException("Method does not have enough parameters to satisfy dependencies: "
                    + method.toGenericString());
        }
        else if (dependencyCount > requiredNumberOfDependencies) {
            throw new IllegalArgumentException("Method has more parameters than will be provided by dependencies: "
                    + method.toGenericString());
        }
    }
}
