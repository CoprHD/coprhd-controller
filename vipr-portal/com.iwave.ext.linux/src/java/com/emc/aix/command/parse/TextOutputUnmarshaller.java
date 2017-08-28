/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command.parse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.beanutils.ConvertUtilsBean;

import com.google.common.collect.Lists;

/**
 * @author logelj
 * 
 *         <p>
 *         The TextOutputUnmarshaller is a parsing utility for used with textual, tabular data input, often found with command line output.
 *         It reflectively analyses classes annotated with @TextObject and splits, parses, and converts input text into target domain Java
 *         objects.
 *         </p>
 * 
 *         Text Input:
 * 
 *         <p>
 *         <blockquote>
 * 
 *         <pre>
 * My Command Line Output Version 1.2.3.4
 * EMC Corporation
 * 
 * name		age		employee number		dob
 * ===================================================================
 * Jay		37		12345			Apr 20 1977
 * Mel		14		54311			Sep 07 2000
 * Maddy		11		32478			Jun 05 2003
 * Jojo		9		48399			Jul 02 2005
 * </pre>
 * 
 *         </blockquote>
 *         </p>
 * 
 *         Domain object:
 * 
 *         <pre>
 * &#064;TextObject(startLine = 6)
 * class Employee {
 * 
 *     &#064;Position(1)
 *     private String name;
 * 
 *     &#064;Position(2)
 *     private int age;
 * 
 *     &#064;Position(3)
 *     private double employeeId;
 * 
 *     &#064;MultiPosition(value = { 4, 5, 6 }, formatter = MyMultiFieldFormatter.class)
 *     private Date dob;
 * }
 * </pre>
 * 
 *         Call the unmarshaller as follows:
 *         <p>
 *         <blockquote>
 * 
 *         <pre>
 * TextOuputUnmarshaller unmarshaller = TextOuputUnmarshaller.instance();
 * List&lt;Employee&gt; employees = unmarshaller.with(inputText).parse(Employee.class);
 * 
 * </pre>
 * 
 *         </blockquote>
 *         </p>
 */
public final class TextOutputUnmarshaller {

    private static final String DEFAULT_LINE_SPLITTER = "\\s+";

    private static final int UNDEFINED_EOL = -1;

    private ConvertUtilsBean converter = new ConvertUtilsBean();

    private String text;

    private static TextOutputUnmarshaller instance;

    static {
        instance = new TextOutputUnmarshaller();
    }

    private TextOutputUnmarshaller() {
    }

    public static TextOutputUnmarshaller instance() {
        return instance;
    }

    public TextOutputUnmarshaller with(String text) {
        this.text = text;
        return this;
    }

    public <T> List<T> parse(Class<? extends T> clazz) throws ParseException {

        List<T> results;

        try {

            int startLine = 0;
            int endLine = UNDEFINED_EOL;
            String lineSeparator = DEFAULT_LINE_SPLITTER;

            TextObject to = clazz.getAnnotation(TextObject.class);

            if (to != null) {
                startLine = to.startLine();
                endLine = to.endLine();
                lineSeparator = to.separatorExpression();
            }

            results = Lists.newArrayList();

            if (text != null) {

                String[] textLines = text.split(System.getProperty("line.separator"));
                List<String> linesList = Lists.newArrayList(textLines);
                List<String> lines;

                if (endLine != UNDEFINED_EOL) {
                    lines = linesList.subList(startLine - 1, endLine);
                } else {
                    lines = linesList.subList(startLine - 1, linesList.size());
                }

                for (String line : lines) {

                    String[] attributes = line.split(lineSeparator);
                    T object = clazz.newInstance();
                    int fieldPosition = 0;

                    for (Field f : clazz.getDeclaredFields()) {

                        f.setAccessible(true);
                        Object value = null;

                        if (f.isAnnotationPresent(Position.class)) {
                            Position position = f.getAnnotation(Position.class);
                            value = processPosition(position, attributes, fieldPosition, f);
                        } else if (f.isAnnotationPresent(MultiPosition.class)) {
                            MultiPosition multi = f.getAnnotation(MultiPosition.class);
                            value = processMultiPosition(multi, attributes, fieldPosition, f);
                        }

                        f.set(object, value);

                        fieldPosition++;
                    }

                    results.add(object);
                }

            }
        } catch (InstantiationException | IllegalAccessException | SecurityException | IllegalArgumentException e) {
            throw new ParseException(e);
        }

        return results;

    }

    private Object processPosition(Position position, String[] attributes, int fieldPosition, Field f)
            throws InstantiationException, IllegalAccessException {

        Object value = null;
        if (position != null) {
            FieldFormatter formatter = position.formatter().newInstance();
            value = converter.convert(formatter.format(attributes[position.value() - 1]), f.getType());

        } else {
            // assume field position as the default
            value = converter.convert(attributes[fieldPosition], f.getType());
        }

        return value;
    }

    private Object processMultiPosition(MultiPosition position, String[] attributes, int fieldPosition, Field f)
            throws InstantiationException, IllegalAccessException {

        Object value = null;

        MultiFieldFormatter formatter = position.formatter().newInstance();

        int[] positions = position.value();

        List<Object> positionValues = new ArrayList<Object>();

        for (int p : positions) {
            if (checkRange(p, attributes)) {
                positionValues.add(attributes[p - 1]);
            } else {
                return null;
            }
        }

        Object[] array = positionValues.toArray(new Object[0]);

        value = converter.convert(formatter.format(array), f.getType());

        return value;
    }

    private boolean checkRange(int index, String[] attributes) {
        return (index >= 0) && (index < attributes.length);
    }

}
