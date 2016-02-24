/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.customconfigcontroller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.customconfigcontroller.exceptions.CustomConfigControllerException;
import com.emc.storageos.db.client.model.StringMap;

public class CustomNameResolver extends CustomConfigResolver {

    private static final long serialVersionUID = 7846294202585888699L;

    private static final Logger logger = LoggerFactory
            .getLogger(CustomNameResolver.class);
    private static final String START_DELIMITER = "{";
    private static final String END_DELIMITER = "}";
    private static final String METHOD_NAMES = "%METHODS%";
    private static final String START_PAREN = "(";
    private static final String END_PAREN = ")";
    // Regex to find the first open parenthesis that is not proceeded by one of the method names
    // The code will loop to find the inner-most expression, the regex only needs to find the first
    private String NESTED_EXPRESSION_START = "(?<!" + METHOD_NAMES
            + ")+\\((.*?)$";
    // Regex to find all chained methods that apply to an expression
    private static final String METHODS_EXPRESSION = "(\\.(" + METHOD_NAMES + ")\\([^\\)]*\\))+";

    // Regex to find . not proceeded by a \ i.e. not escaped
    public static final String DOT_EXPRESSION = "(?<!" + Pattern.quote("\\") + ")" + Pattern.quote(".");

    // The maximum number of time the name resolution code is allowed to loop trying
    // to resolve an expression. This is a protection measure against infinite loops.
    // If we loop more than MAX_LOOP, the expression is most probably not resolvable
    public static final int MAX_LOOP = 100;

    private List<CustomConfigMethod> stringManipulationMethods;

    public List<CustomConfigMethod> getStringManipulationMethods() {
        return stringManipulationMethods;
    }

    public void setStringManipulationMethods(
            List<CustomConfigMethod> stringManipulationMethods) {
        this.stringManipulationMethods = stringManipulationMethods;
    }

    public CustomConfigMethod getCustomConfigMethod(String methodName) {
        for (CustomConfigMethod method : stringManipulationMethods) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    /**
     * Gets an array of all supported method display names
     * 
     * @return an array of all supported method names
     */
    public String[] getCustomConfigMethodNames() {
        String[] names = new String[stringManipulationMethods.size()];
        for (int i = 0; i < stringManipulationMethods.size(); i++) {
            names[i] = stringManipulationMethods.get(i).getName();
        }
        return names;
    }

    /**
     * The expression is expected to be in the format
     * datasource.operation(params..).operation(params...)
     * 
     * @param expression
     * @param datasource
     * @return
     */
    private String getDataSourceValue(String expression, DataSource datasource,
            IllegalCharsConstraint constraint, String systemType) {
        // Split the expression based on dot
        String[] tokenArray = expression.split(DOT_EXPRESSION);
        // first token is the field
        String datasourceField = tokenArray[0];
        String fieldValue = (String) datasource
                .getPropertyValue(datasourceField);
        if (fieldValue == null) {
            logger.error("Couldn't find a datasource property with name {}",
                    datasourceField);
            fieldValue = "";
        } else {
            fieldValue = processStringMethods(tokenArray, fieldValue);
        }
        return fieldValue;
    }

    /**
     * Given an expression, perform the list of string manipulation functions of
     * it.
     * 
     * @param array
     *            of string expressions of methods in the format
     *            operation(param...). Note, the first method is expected to be
     *            at index 1 in the array. The methid has to be one that is
     *            defined in {@link #getStringManipulationMethods()}
     * @param value
     *            the string to be manipulated
     * @return the resolved string
     */
    private String processStringMethods(String[] tokens, String value) {
        String fieldValue = value;
        for (int i = 1; i < tokens.length; i++) {
            String[] operationTokens = StringUtils.split(tokens[i].replaceAll("\\s+", ""), "[,()]");
            // first token is the method name, remaining tokens are the method
            // arguments
            List<String> methodArgs = new ArrayList<String>();
            String stringMethod = operationTokens[0];
            for (int argIndex = 1; argIndex < operationTokens.length; argIndex++) {
                if (operationTokens[argIndex].startsWith("\"")
                        && operationTokens[argIndex].endsWith("\"")) {
                    // it is a string - strip out the quotes
                    methodArgs.add(operationTokens[argIndex].substring(1,
                            operationTokens[argIndex].length() - 1));
                } else {
                    methodArgs.add(operationTokens[argIndex]);
                }
            }

            fieldValue = invokeStringMethod(fieldValue, stringMethod,
                    methodArgs);
        }
        return fieldValue;
    }

    /**
     * Given a method name and its arguments, invoke the method and return its
     * results.
     * 
     * @param stringObj
     *            the string to be transformed
     * @param methodName
     *            the name of the method which has to be one of the methods in {@link #getStringManipulationMethods()}
     * @param methodArgs
     *            the method arguments
     * @return
     */
    public String invokeStringMethod(String stringObj, String methodName,
            List<String> methodArgs) {
        String value = "";
        try {
            CustomConfigMethod configMethodDef = getCustomConfigMethod(methodName);
            if (configMethodDef == null) {
                logger.error(
                        "Couldn't find a string manipulation function with name {}",
                        methodName);
                return null;
            }
            logger.debug("Invoking string method on string: {}", stringObj);
            logger.debug("Method: {} with method args: {}",
                    configMethodDef.getName(), methodArgs);
            value = configMethodDef.invoke(stringObj, methodArgs);
        } catch (Exception e) {
            logger.error("Exception while invoking string method-", e);
        }

        return value;
    }

    @Override
    public void validate(CustomConfigType configTemplate, StringMap scope,
            String value) {

        simpleSyntaxValidation(value);

        // Parse the value and validate the datasource properties
        StringTokenizer tokenizer = new StringTokenizer(value, START_DELIMITER + END_DELIMITER, true);

        // make a map of datasource property and their names. Use this for
        // datasource property validation
        Map<DataSourceVariable, String> datasourcePropMap = new HashMap<DataSourceVariable, String>();
        for (DataSourceVariable datasourceProp : configTemplate
                .getDataSourceVariables().keySet()) {
            datasourcePropMap.put(datasourceProp,
                    datasourceProp.getDisplayName());
        }

        while (tokenizer.hasMoreTokens()) {
            if (tokenizer.nextToken()
                    .equals(CustomNameResolver.START_DELIMITER)
                    && tokenizer.hasMoreTokens()) {
                // this is a datasource property which should be computed
                String expression = tokenizer.nextToken();

                // Split the expression based on dot
                String[] expressionTokens = expression.split(DOT_EXPRESSION);

                // first token is the field
                String datasourceField = expressionTokens[0];
                // check if it is a legal datasource property name
                if (!datasourcePropMap.containsValue(datasourceField)) {
                    throw CustomConfigControllerException.exceptions
                            .illegalDatasourceProperty(value, datasourceField);
                }

                for (int i = 1; i < expressionTokens.length; i++) {
                    String[] methodTokens = StringUtils.split(
                            expressionTokens[i], "[,()\"]");
                    // first token is the method name, remaining tokens are the
                    // method arguments
                    String stringMethod = methodTokens[0];
                    if (getCustomConfigMethod(stringMethod) == null) {
                        throw CustomConfigControllerException.exceptions
                                .illegalStringFunction(value, stringMethod);
                    }
                }
            }
        }

    }

    /**
     * Custom name can have 1) Literals 2) Datasource fields and the operation
     * to be applied on them 3) String manipulation functions
     * 
     * Datasource fields and the string manipulation functions will be within {}
     * 
     * The custom name definition is expected to be in the format below:
     * {datafield1
     * .operation(params..).operation(params..)}literal{datasource.field2
     * .operation(params..).operation(params..)} Eg:
     * Brcd_{host_name.FIRST(8)}_{hba_port_wwn
     * .LAST(12)}_{array_port_wwn.LAST(12)}_{array_serial_number.LAST(8)} or Eg:
     * Brcd_
     * ({cluster_name.FIRST(8)}_{host_name.LAST(12)}_{array_serial_number.LAST
     * (8)}).TRIM("_")
     * 
     * @param value
     *            - custom definition value
     * @param dataSource
     *            - datasource property details
     * @return resolved name
     */
    public String resolve(CustomConfigType configTemplate, StringMap scope,
            String value, DataSource dataSource) {
        IllegalCharsConstraint illegalCharConstraint = null;
        // Get the illegal char constraint to apply to the generated value from
        // datasource and string functions
        for (CustomConfigConstraint constraint : configTemplate
                .getConstraints()) {
            if (constraint instanceof IllegalCharsConstraint) {
                illegalCharConstraint = (IllegalCharsConstraint) constraint;
                break;
            }
        }

        // get systemType or global for use by constraints
        String systemType = CustomConfigConstants.DEFAULT_KEY;
        if (scope != null) {
            systemType = scope.get(CustomConfigConstants.SYSTEM_TYPE_SCOPE);
            // if the system type scope is not available, check for host type scope.
            // host type scope is only available for Hitachi Host Mode Option
            if (systemType == null) {
                systemType = scope.get(CustomConfigConstants.HOST_TYPE_SCOPE);
            }
        }

        // Look for expressions enclosed in parenthesis, the expression
        // does not include any methods for example if the expression is
        // ({cluster_name.FIRST(8)}_{host_name.LAST(12)}).TOLOWER(), this
        // function finds {cluster_name.FIRST(8)}_{host_name.LAST(12)}
        String subExp = getMostNestedExpression(value);
        String methodsRegex = getRegexWithMethodNames(METHODS_EXPRESSION);
        int loopCounter = 0;
        while (subExp != null && loopCounter < MAX_LOOP) {
            loopCounter++;
            String fullExpression = value;
            // get the sub-expression between parenthesis e.g.
            // {cluster_name.FIRST(8)}_{host_name.LAST(12)}
            logger.info("subExp is " + subExp);
            // get the full expression e.g.
            // ({cluster_name.FIRST(8)}_{host_name.LAST(12)}).TOLOWER()
            String subExpFull = "(" + subExp + ")";
            Pattern p = Pattern.compile("(" + Pattern.quote(subExpFull) + "(" + methodsRegex + "))");
            Matcher m = p.matcher(value);
            List<String> methods = new ArrayList<String>();
            if (m.find()) {
                fullExpression = m.group(1);
                logger.info("full expression is " + fullExpression);
                // remove the sub expression from the full expression
                // and extract the methods from the full expression
                // m.group(2) should return .TOLOWER()
                String[] tokens = m.group(2).split(DOT_EXPRESSION);

                int size = tokens.length;
                if (size > 1) {
                    // has the string methods add the first member as empty string,
                    // since it is not used later.
                    methods.add(" ");
                    for (int i = 1; i < size; i++) {
                        methods.add(tokens[i]);
                    }
                }
            }
            // resolve the sub-expression
            String rsubExp = resolveSubExpression(subExp, dataSource,
                    illegalCharConstraint, systemType);

            // execute the methods
            subExp = processStringMethods(
                    methods.toArray(new String[methods.size()]), rsubExp);

            // replace the full nested expression with its value and repeat
            // looking for more
            value = value.replace(fullExpression, subExp);
            // repeat until all nested expressions are resolved
            subExp = getMostNestedExpression(value);
        }

        // After all nested expressions are resolved, do the first-level of
        // expressions
        value = resolveSubExpression(value, dataSource, illegalCharConstraint,
                systemType);
        // make sure we have a string that is at least 1 char long
        if (StringUtils.isEmpty(value.trim())) {
            throw CustomConfigControllerException.exceptions
                    .resolvedCustomNameEmpty(configTemplate.getName());
        }
        return value;
    }

    /**
     * Look for the most nested expressions first.
     */
    private String getMostNestedExpression(String value) {
        String regex = getRegexWithMethodNames(NESTED_EXPRESSION_START);
        // I want the mosted nested expression so looking for the most nested (
        // not proceeded
        // not proceeded with one of the methods. Say I start with:
        // Brcd_({vsan/fabric.FIRST(8)}_({array_serial_number.LAST(8)}_{host_name.FIRST(8)}).LAST(11)_{array_port_wwn.LAST(12)}).TRIM("_")
        // the first loop I get:
        // {vsan/fabric.FIRST(8)}_({array_serial_number.LAST(8)}_{host_name.FIRST(8)}).LAST(11)_{array_port_wwn.LAST(12)}).TRIM("_")
        // the second (and last) loop I get:
        // ({array_serial_number.LAST(8)}_{host_name.FIRST(8)}).LAST(11)_{array_port_wwn.LAST(12)}).TRIM("_")
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(value);
        String fullExpression = null;
        int loopCounter = 0;
        while (m.find() && loopCounter < MAX_LOOP) {
            // make sure we do not try more than MAX_LOOP
            loopCounter++;
            // get the substring that come after the parenthesis
            fullExpression = m.group(1);
            // check for the next parenthesis
            m = p.matcher(fullExpression);
        }
        // at this time full expression is :
        // ({array_serial_number.LAST(8)}_{host_name.FIRST(8)}).LAST(11)_{array_port_wwn.LAST(12)}).TRIM("_")
        // and it is the substring that starts with the most nest (
        // use the full parse regex to get the actual expression
        // the next regex should get
        // ({array_serial_number.LAST(8)}_{host_name.FIRST(8)}).LAST(11)
        if (m != null && fullExpression != null && fullExpression.length() > 0) {
            int index = getClosingParenthesisIndex(fullExpression);
            if (index > 0) {
                return fullExpression.substring(0, index);
            }
        }
        return null;
    }

    private int getClosingParenthesisIndex(String fullExpression) {
        int c = 0;
        for (int i = 0; i < fullExpression.length(); i++) {
            if (fullExpression.charAt(i) == '(') {
                c++;
            } else if (fullExpression.charAt(i) == ')') {
                c--;
            }
            if (c < 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Takes Resolved an expression in the form
     * Brcd_{host_name.FIRST(8)}_{hba_port_wwn
     * .LAST(12)}_{array_port_wwn.LAST(12)}_{array_serial_number.LAST(8)}
     * 
     * @param value
     *            the expression
     * @param dataSource
     *            the object containing the value of the variables.
     * @param constraint
     *            the illegal char constraint for this config type
     * @param systemType
     *            the system type
     * @return the resolved expression
     */
    private String resolveSubExpression(String value, DataSource dataSource,
            IllegalCharsConstraint constraint, String systemType) {
        StringBuffer resolvedValue = new StringBuffer();
        StringTokenizer tokenizer = new StringTokenizer(value, START_DELIMITER + END_DELIMITER, true);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if (token.equals(START_DELIMITER) && tokenizer.hasMoreTokens()) {
                // this is a datasource property which should be computed
                String propertyValue = getDataSourceValue(
                        tokenizer.nextToken().replaceAll("\\s+", ""), dataSource,
                        constraint, systemType);
                if (constraint != null) {
                    propertyValue = constraint.applyConstraint(propertyValue,
                            systemType);
                }
                resolvedValue.append(propertyValue);
            }  else if (!token.equals(START_DELIMITER)
                    && !token.equals(END_DELIMITER)) {
                // a literal - just append it
                resolvedValue.append(token);
            }
        }
        return resolvedValue.toString();
    }

    /**
     * If the method names are not yet replaced, replace them.
     * 
     * @return the regex expression with method names replaced
     * 
     */
    private String getRegexWithMethodNames(String expression) {
        if (expression.contains(METHOD_NAMES)) {
            String methods = StringUtils
                    .join(getCustomConfigMethodNames(), "|");
            expression = expression.replaceAll(METHOD_NAMES, methods);
        }
        return expression;
    }

    /**
     * Routine determines if the value looks invalid, like having too many parenthesis or not enough.
     * 
     * @param value [IN] - Configuration value to validate
     */
    private void simpleSyntaxValidation(String value) {
        // Do a simple validation of value by tracking opening/closing brackets and parenthesis
        Map<String, Integer> characterCounts = new HashMap<>();
        characterCounts.put(START_DELIMITER, 0);
        characterCounts.put(START_PAREN, 0);
        for (char theChar : value.toCharArray()) {
            if (START_DELIMITER.indexOf(theChar) != -1) {
                Integer count = characterCounts.get(START_DELIMITER);
                characterCounts.put(START_DELIMITER, count + 1);
            } else if (END_DELIMITER.indexOf(theChar) != -1) {
                Integer count = characterCounts.get(START_DELIMITER);
                characterCounts.put(START_DELIMITER, count - 1);
            } else if (START_PAREN.indexOf(theChar) != -1) {
                Integer count = characterCounts.get(START_PAREN);
                characterCounts.put(START_PAREN, count + 1);
            } else if (END_PAREN.indexOf(theChar) != -1) {
                Integer count = characterCounts.get(START_PAREN);
                characterCounts.put(START_PAREN, count - 1);
            }
        }

        // Any of the character counts not zero means there is too much
        // of it or not enough. String together the reason
        for (String character : characterCounts.keySet()) {
            Integer count = characterCounts.get(character);
            if (count != 0) {
                String invalid = "";
                // Get the closing character ...
                if (START_DELIMITER.contains(character)) {
                    invalid = END_DELIMITER;
                } else if (START_PAREN.contains(character)) {
                    invalid = END_PAREN;
                }
                // Build the reason message
                String reason = "";
                if (count > 0) {
                    reason = String.format("Missing a '%s'", invalid);
                } else if (count < 0) {
                    reason = String.format("Extra '%s' characters", invalid);
                }
                throw CustomConfigControllerException.exceptions.invalidSyntax(value, reason);
            }
        }
    }
}
