/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.emc.sa.service.vipr.customservices.tasks;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.core.UriBuilder;
import javax.xml.bind.annotation.XmlElement;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriTemplate;

import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.svcs.errorhandling.resources.InternalServerErrorException;

public final class RESTHelper {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(RESTHelper.class);
    public static final String TASKLIST = "TaskList";
    public static final String TASK = TaskResourceRep.class.getSimpleName();
    private RESTHelper() {
    };
    
    private final static Pattern GETTER_PATTERN = Pattern.compile("(^get|is)");
    private final static Pattern SETTER_PATTERN = Pattern.compile("(^set)");
    
    /**
     * POST body format:
     * "{\n" +
     * " \"consistency_group\": \"$consistency_group\",\n" +
     * " \"count\": \"$count\",\n" +
     * " \"name\": \"$name\",\n" +
     * " \"project\": \"$project\",\n" +
     * " \"size\": \"$size\",\n" +
     * " \"varray\": \"$varray\",\n" +
     * " \"vpool\": \"$vpool\"\n" +
     * "}";
     * 
     * @param body
     * @return
     */
    public static String makePostBody(final String body, final int pos, final Map<String, List<String>> input) {

        logger.info("make body for" + body);
        if (StringUtils.isEmpty(body)) {
            return "";
        }
        final String[] strs = body.split("(?<=:)");

        for (int j = 0; j < strs.length; j++) {
            if (StringUtils.isEmpty(strs[j])) {
                continue;
            }

            if (!strs[j].contains("{")) {
                final String value = createBody(strs[j], pos, input);

                if (value.isEmpty()) {
                    if (strs[j].contains("}")) {
                        //it is the last line
                        String[] ar1 = strs[j].split("}");
                        strs[j] = strs[j].replace(ar1[0], "");
                        String[] pre = StringUtils.substringsBetween(strs[j - 1], "\"", "\"");
                        strs[j - 1] = strs[j - 1].replace("\"" + pre[pre.length - 1] + "\"" + ":", "");
                        for (int k = 1; k <= j; k++) {
                            if (!strs[j - k].trim().isEmpty()) {
                                strs[j - k] = strs[j - k].trim().replaceAll(",$", "");
                                break;
                            }
                        }
                    } else {
                        final String[] ar = strs[j].split(",");
                        if (ar.length > 1) {
                            strs[j] = strs[j].replace(ar[0] + ",", "");
                            String[] pre = StringUtils.substringsBetween(strs[j - 1], "\"", "\"");
                            strs[j - 1] = strs[j - 1].replace("\"" + pre[pre.length - 1] + "\"" + ":", "");
                        }
                    }
                } else {
                    strs[j] = value;
                }
                continue;
            }

            // Complex Array of Objects type
            if (strs[j].contains("[{")) {
                int start = j;
                final StringBuilder secondPart = new StringBuilder(strs[j].split("\\[")[1]);

                final String firstPart = strs[j].split("\\[")[0];
                j++;
                int count = -1;
                while (!strs[j].contains("}]")) {
                    // Get the number of Objects in array of object type
                    final int cnt = getCountofObjects(strs[j], input);
                    if (count < cnt) {
                        count = cnt;
                    }
                    secondPart.append(strs[j]);

                    j++;
                }
                final String[] splits = strs[j].split("\\}]");
                final String firstOfLastLine = splits[0];
                final String end = splits[1];
                secondPart.append(firstOfLastLine).append("}");

                int last = j;

                // join all the objects in an array
                strs[start] = firstPart + "[" + makeComplexBody(count, secondPart.toString(), input) + "]" + end;

                while (start + 1 <= last) {
                    strs[++start] = "";
                }
            }
        }

        logger.info("ViPR Request body" + joinStrs(strs));

        return joinStrs(strs);
    }

    public static String createBody(final String strs, final int pos, final Map<String, List<String>> input) {
        if ((!strs.contains("["))) {
            // Single type parameter
            return findReplace(strs, pos, false, input);
        } else {
            // Array type parameter
            return findReplace(strs, pos, true, input);
        }
    }

    public static int getCountofObjects(final String strs, final Map<String, List<String>> input) {
        final Matcher m = Pattern.compile("\\$([\\w\\.\\@]+)").matcher(strs);
        while (m.find()) {
            final String p = m.group(1);
            if (input.get(p) == null) {
                return -1;
            }
            return input.get(p).size();
        }

        return -1;
    }

    public static String joinStrs(final String[] strs) {
        final StringBuilder sb = new StringBuilder(strs[0]);
        for (int j = 1; j < strs.length; j++) {
            sb.append(strs[j]);
        }
        return sb.toString();
    }

    public static String makeComplexBody(final int vals, final String secondPart, final Map<String, List<String>> input) {
        String get = "";
        if (vals == -1) {
            logger.error("Cannot Build ViPR Request body");
            throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Cannot Build ViPR Request body");
        }
        for (int k = 0; k < vals; k++) {
            // Recur for number of Objects
            get = get + makePostBody(secondPart, k, input) + ",";
        }

        // remove the trailing "," of json body and return
        return get.replaceAll(",$", "");
    }

    public static String findReplace(final String str, final int pos, final boolean isArraytype, final Map<String, List<String>> input) {
        final Matcher m = Pattern.compile("\\$([\\w\\.\\@]+)").matcher(str);
        while (m.find()) {
            final String pat = m.group(0);
            final String pat1 = m.group(1);

            final List<String> val = input.get(pat1);
            final StringBuilder sb = new StringBuilder();
            String vals = "";
            if (val != null && pos < val.size() && !StringUtils.isEmpty(val.get(pos))) {
                if (!isArraytype) {
                    sb.append("\"").append(val.get(pos)).append("\"");
                    vals = sb.toString();

                } else {

                    final String temp = val.get(pos);
                    final String[] strs = temp.split(",");
                    for (int i = 0; i < strs.length; i++) {
                        sb.append("\"").append(strs[i]).append("\"").append(",");
                    }
                    final String value = sb.toString();

                    vals = value.replaceAll(",$", "");

                }
                return str.replace(pat, vals);
            } else {
                return "";
            }

        }

        return "";
    }

    /**
     * Example uri: "/block/volumes/{id}/findname/{name}?query1=value1";
     * 
     * @param templatePath the path template containing path parameters
     * @param input map of input values
     * @param queryParams the query parameter keys
     * @return The path URI as a String
     */
    public static String makePath(final String templatePath,
            final Map<String, List<String>> input) {
        final UriTemplate template = new UriTemplate(templatePath);
        final List<String> pathParameters = template.getVariableNames();
        final Map<String, Object> pathParameterMap = new HashMap<String, Object>();

        for(final String key : pathParameters) {
            final List<String> value = input.get(key);
            if (CollectionUtils.isEmpty(value)) {
                throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled path parameter: " + key);
            }
            //TODO find a better fix
            pathParameterMap.put(key, value.get(0).replace("\"", ""));
        }
        
        final String path = template.expand(pathParameterMap).getPath();
        logger.info("URI string is: {}", path);
        return path;
    }
    
    public static UriBuilder addQueryParams(final UriBuilder builder, final List<InputParameter> params, final Map<String, List<String>> input) {
        if( null != params) {
            for( final InputParameter param : params ) {
                final List<String> value = input.get(param.getName());
                if( !CollectionUtils.isEmpty(value) && !StringUtils.isEmpty(value.get(0))) {
                    builder.queryParam(param.getName(), value.toArray());
                } else if( param.isBasicInputParameter() && param.asBasicInputParameter().getRequired()){
                    throw InternalServerErrorException.internalServerErrors.customServiceExecutionFailed("Unfulfilled required query parameter: " + param.getName());
                } else {
                    logger.debug("Query parameter " + param.getName() + " is blank");
                }
            }
        }
        return builder;
    }
    
    public static List<String> parserOutput(final String[] bits, final int i, final Object className) throws Exception {

        if (className == null) {
            logger.warn("class name is null, cannot parse output");

            return null;
        }
        final Method method = findMethod(bits[i], className);

        if (method == null) {
            logger.warn("method is null. cannot parse output");

            return null;
        }
        logger.debug("bit:{}", bits[i]);

        final Class<?> returnType = method.getReturnType();
        if (returnType == null) {
            logger.info("Could not find return type of method:{}", method.getName());

            return null;
        }
        
        if( returnType.isArray() || Collection.class.isAssignableFrom(returnType)) {
            return getCollectionValue(method, bits, i, className);
        } else {
            // 1) primitive
            if (i == bits.length - 1) {
                final Object value = method.invoke(className);
                logger.debug("value:{}", value);
    
                if (value != null) {
                    return Arrays.asList(value.toString());
                } else {
                    return null;
                }
            }
            
            // 2) Class single object
            if (returnType instanceof Class<?>) {
                return parserOutput(bits, i + 1, method.invoke(className));
            }
    
            return null;
        }
    }

    private static List<String> getCollectionValue(final Method method, final String[] bits, final int i, final Object className)
            throws Exception {
        final Type objectType;

        final Collection<?> arrayValue;
        if( method.getReturnType().isArray() ) {
            objectType = method.getReturnType().getComponentType();
            arrayValue = Arrays.asList((Object[]) method.invoke(className));
        } else if(method.getGenericReturnType() instanceof ParameterizedType){
            objectType = ((ParameterizedType)method.getGenericReturnType()).getActualTypeArguments()[0];
            arrayValue = (Collection<?>) method.invoke(className);
        } else {
            logger.warn("Parsing vipr output failed unknown collection type: " + method.getReturnType() + " field " + bits[i] + " in " + bits);
            return null;
        }
        
        if (i == bits.length - 1) {
            logger.debug("array value:{}", arrayValue);
            return toStringList(arrayValue);
        } else if (objectType instanceof Class<?>) {
            return parseObjectCollection(bits, i, arrayValue);
        } else {
            logger.warn("Parsing vipr output failed.  Unexpected primitive before end of output: " + objectType + " field " + bits[i] + " in " + bits);
            return null;
        }
    }

    private static List<String> toStringList(final Collection<?> arrayValue) {
        if( null == arrayValue ) {
            return null;
        }
        
        final List<String> list = new ArrayList<String>();
        for( final Object value : arrayValue) {
            list.add(value.toString());
        }
        return list;
    }

    private static List<String> parseObjectCollection(final String[] bits, final int i, final Collection<?> arrayValue) throws Exception {
        final List<String> list = new ArrayList<String>();
        for (final Object o1 : arrayValue) {
            final List<String> value = parserOutput(bits, i + 1, o1);
            if (value != null) {
                list.addAll(value);
            }
        }
        
        return !list.isEmpty() ? list : null; 
    }

    private static Method findMethod(final String str, final Object className) throws Exception {
        final Method[] methods = className.getClass().getMethods();
        for (Method method : methods) {
            final XmlElement elem = method.getAnnotation(XmlElement.class);
            if (elem == null) {
                continue;
            }
            
            if(isGetter(method)) {
                final String fieldName;
                
                if(elem.name().equals("##default")) {
                    fieldName = getFieldName(method.getName(), GETTER_PATTERN);
                } else {
                    fieldName = elem.name();
                }
                
                if( fieldName.equals(str)) {
                    return method; 
                }
            } else if( isSetter(method)) {
                final String fieldName = getFieldName(method.getName(), SETTER_PATTERN);
                
                if(elem.name().equals(str) || 
                        (elem.name().equals("##default") && fieldName.equals(str))) {
                    return findReadMethod(fieldName, className.getClass());
                } 
            } else {
                continue;
            }
        }
        logger.info("didn't match in xml. str:{} check for getter", str);

        Class<?> clazz = className.getClass();
        do {
            for( final Field field : clazz.getDeclaredFields()) {
                final XmlElement elem = field.getAnnotation(XmlElement.class);
                final String fieldName;
                if( elem == null || elem.name().equals("##default")) {
                    fieldName = field.getName();
                } else {
                    fieldName = elem.name();
                }
                if( fieldName.equals(str)) {
                    return findReadMethod(field.getName(), clazz);
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        logger.info("could not find getter");

        return null;
    }
    
    private static String getFieldName(final String methodName, final Pattern pattern) {
        final String fieldName = pattern.matcher(methodName).replaceFirst("");
        if (!fieldName.isEmpty() && !fieldName.equals(methodName)) {
            return Character.toLowerCase(fieldName.charAt(0))
                    + fieldName.substring(1);
        }

        return fieldName;
    }
    
    private static Method findReadMethod(final String fieldName, final Class<?> clazz ) {
        final Method getter = getMethodNoThrow("get", fieldName, clazz);
        return null == getter ? getMethodNoThrow("is", fieldName, clazz) : getter;
    }
    
    private static Method getMethodNoThrow(final String prefix, final String fieldName, final Class<?> clazz ) {
        final String methodName = prefix+ fieldName.substring(0, 1).toUpperCase()+ fieldName.substring(1);
        try {
            return clazz.getMethod(methodName);
        } catch (final  NoSuchMethodException  e) {
            return null;
        }
    }
    
    private static boolean isGetter(final Method method) {
        if ( method.getParameterTypes().length != 0
                || void.class.equals(method.getReturnType())) {
            return false;
        }

        return true;
    }
    private static boolean isSetter(final Method method) {
        if ( method.getParameterTypes().length == 0
                || !void.class.equals(method.getReturnType())) {
            return false;
        }

        return true;
    }
}
