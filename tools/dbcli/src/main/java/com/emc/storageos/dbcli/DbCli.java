/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.dbcli;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.impl.ColumnField;
import com.emc.storageos.db.client.impl.DataObjectType;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.impl.TypeMap;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Encrypt;
import com.emc.storageos.db.client.model.FSExportMap;
import com.emc.storageos.db.client.model.Name;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OpStatusMap;
import com.emc.storageos.db.client.model.SMBShareMap;
import com.emc.storageos.db.client.model.ScopedLabelSet;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.common.DataObjectScanner;
import com.emc.storageos.db.common.DependencyChecker;
import com.emc.storageos.db.common.DependencyTracker;
import com.emc.storageos.db.common.PackageScanner;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.dbcli.wrapper.FSExportMapWrapper;
import com.emc.storageos.dbcli.wrapper.OpStatusMapWrapper;
import com.emc.storageos.dbcli.wrapper.SMBShareMapWrapper;
import com.emc.storageos.dbcli.wrapper.ScopedLabelSetWrapper;
import com.emc.storageos.dbcli.wrapper.StringMapWrapper;
import com.emc.storageos.dbcli.wrapper.StringSetMapWrapper;
import com.emc.storageos.dbcli.wrapper.StringSetWrapper;

public class DbCli {
    private static final Logger log = LoggerFactory.getLogger(DbCli.class);

    DbClientImpl _dbClient = null;
    DataObjectScanner dataObjectscanner = null;
    private DependencyChecker _dependencyChecker = null;

    HashMap<String, Class> _cfMap = new HashMap<String, Class>();
    private static final boolean DEBUG = false;
    private static final String pkgs = "com.emc.storageos.db.client.model";

    private static final String QUITCHAR = "q";
    private int listLimit = 100;
    private boolean turnOnLimit = false;
    private boolean activeOnly = false;

    Document doc = null;
    Element schemaNode = null;

    public enum DbCliOperation {
        LIST, DUMP, LOAD, CREATE
    }

    public DbCli() {
        DataObjectModelPackageScanner dataObjectModelPackageScanner = new DataObjectModelPackageScanner();
        _cfMap = dataObjectModelPackageScanner.getCfMaps();
    }

    /**
     * Initiate the dbclient
     */
    public void initDbClient() {
        try {
            System.out.println("Initializing db client ...");
            _dbClient.start();

        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

    public DbClientImpl getDbClient() {
        return _dbClient;
    }

    public void setDbClient(DbClientImpl dbClient) {
        this._dbClient = dbClient;
    }

    public DataObjectScanner getDataObjectscanner() {
        return dataObjectscanner;
    }

    public void setDataObjectscanner(DataObjectScanner dataObjectscanner) {
        this.dataObjectscanner = dataObjectscanner;
    }

    public void stop() {
        if (_dbClient != null) {
            _dbClient.stop();
        }
    }

    public void start(boolean skipMigrationCheck) {
        _dbClient.setBypassMigrationLock(skipMigrationCheck);
        _dbClient.start();
    }

    /**
     * Print column families.
     */
    public void printCfMaps() {
        Iterator it = _cfMap.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = (Entry) it.next();
            System.out.println(String.format("\t\tColumn family: %s", entry.getKey()));
        }
    }

    /**
     * Print the fields' info of column family.
     * 
     * @Param cfName
     */
    public void printFieldsByCf(String cfName) {
        final Class clazz = getClassFromCFName(cfName); // fill in type from cfName
        if(clazz == null) {
            return;
        }
        if (DataObject.class.isAssignableFrom(clazz)) {
            DataObjectType doType = TypeMap.getDoType(clazz);
            System.out.println(String.format("Column Family: %s", doType.getCF().getName()));
            Collection<ColumnField> cfs = doType.getColumnFields();
            Iterator it = cfs.iterator();
            while (it.hasNext()) {
                ColumnField field = (ColumnField) it.next();
                System.out.println(String.format("\tfield=%-30s\ttype=%s", field.getName(),
                        field.getPropertyDescriptor().getPropertyType().toString().substring(6)));
            }
        }
    }

    /**
     * Load xml file and persist model object
     * 
     * @Param fileName
     */
    public void loadFileAndPersist(String fileName) {
        try {
            readXMLAndPersist(fileName, DbCliOperation.LOAD);
            System.out.println(String.format("Load from file: %s successfully", fileName));
            log.info("Load from file: {} successfully", fileName);
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

    /**
     * Load xml file, create and persist model object
     * 
     * @Param fileName
     */
    public void loadFileAndCreate(String fileName) {
        try {
            readXMLAndPersist(fileName, DbCliOperation.CREATE);
            System.out.println(String.format("Load and create from file: %s successfully", fileName));
            log.info("Load and create from file: {} successfully", fileName);
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

    /**
     * Load xml file and save model object into Cassandra.
     * 
     * @Param fileName
     */
    private <T extends DataObject> void readXMLAndPersist(String fileName, DbCliOperation operation) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = dbf.newDocumentBuilder();
        Document doc = builder.parse(fileName);

        // Read root node
        Element root = doc.getDocumentElement();
        Element dataObjectNode = (Element) root.getElementsByTagName("data_object_schema").item(0);

        // Get column family's name
        String cfName = dataObjectNode.getAttribute("name");
        System.out.println("Column Family based on XML: " + cfName);
        NodeList recordNodes = dataObjectNode.getElementsByTagName("record");

        Class<T> clazz = _cfMap.get(cfName);
        if (clazz == null) {
            System.out.println("Unknown Column Family: " + cfName);
            return;
        }
        // Get class info
        BeanInfo bInfo;
        try {
            bInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException ex) {
            throw new RuntimeException("Unexpected exception getting bean info", ex);
        }

        PropertyDescriptor[] pds = bInfo.getPropertyDescriptors();

        // get position of xml node
        InputStream xmlIs = new FileInputStream(new File(fileName));
        Document docForPosition = PositionalXMLReader.readXML(xmlIs);
        xmlIs.close();

        for (int i = 0; i < recordNodes.getLength(); i++) {
            Element record = (Element) recordNodes.item(i);
            T object = null;
            String idStr = null;

            if (operation == DbCliOperation.LOAD) {// query record based id
                String recordId = record.getAttribute("id");
                System.out.println(String.format("Object id:\t%s", recordId));
                idStr = recordId;
                object = queryObject(URI.create(recordId), clazz);
            } else if (operation == DbCliOperation.CREATE) { // create new id for create record
                URI id = URIUtil.createId(clazz);
                object = clazz.newInstance();
                object.setId(id);
                System.out.println(String.format("Create new data object id:\t%s", object.getId()));
                idStr = object.getId().toString();
            }

            HashMap<String, String> fieldValueMap = new HashMap<String, String>();
            HashMap<String, Class> fieldTypeMap = new HashMap<String, Class>();
            HashMap<String, String> fieldLocationMap = new HashMap<String, String>();
            HashMap<String, Node> fieldNodeMap = new HashMap<String, Node>();

            NodeList fields = record.getElementsByTagName("field");

            // get field info from xml file
            for (int j = 0; j < fields.getLength(); j++) {
                Element field = (Element) fields.item(j);
                if (DEBUG) {
                    System.out.println(field.getAttribute("name") + "\t" + field.getAttribute("type") + "\t" + field.getAttribute("value"));
                }
                fieldValueMap.put(field.getAttribute("name"), field.getAttribute("value"));
                fieldTypeMap.put(field.getAttribute("name"), Class.forName(field.getAttribute("type")));
                fieldLocationMap.put(field.getAttribute("name"),
                        ((Element) docForPosition.getElementsByTagName("record").item(i)).getElementsByTagName("field").item(j)
                                .getUserData("lineNumber").toString());

                if (field.getElementsByTagName("wrapper").item(0) != null) {
                    fieldNodeMap.put(field.getAttribute("name"), field.getElementsByTagName("wrapper").item(0));
                }
            }

            Iterator locationIt = fieldLocationMap.entrySet().iterator();
            while (locationIt.hasNext()) {
                Entry entry = (Entry) locationIt.next();
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();

                if (DEBUG) {
                    System.out.println("key:\t" + key + "\tvalue\t" + value);
                }
            }

            // update object's fields
            for (PropertyDescriptor pd : pds) {
                // skip class property, id property
                if (pd.getName().equals("class") || pd.getName().equals("id")) {
                    continue;
                }

                Name name = pd.getReadMethod().getAnnotation(Name.class);
                if (name == null) {
                    log.info(
                            "Ignore data object fields without @Name annotation, fieldName={}.",
                            pd.getName());
                    continue;
                }

                String objKey = name.value();
                String fieldValue = fieldValueMap.get(objKey);
                if (fieldValue == null) {
                    //To support xml file that the old version dumped, it used method name not @Name value
                    objKey = pd.getName();
                }

                fieldValue = fieldValueMap.get(objKey);
                Class fieldClass = fieldTypeMap.get(objKey);
                String fieldLocation = fieldLocationMap.get(objKey);

                Node fieldNode = fieldNodeMap.get(objKey);

                if (fieldValue != null) {
                    Class type = pd.getPropertyType();
                    if (DEBUG) {
                        System.out.print("\t" + objKey + " = " + type);
                    }

                    try {
                        if (type == URI.class) {
                            pd.getWriteMethod().invoke(object, URI.create(fieldValue));
                        } else if (type == NamedURI.class) {
                            pd.getWriteMethod().invoke(object, NamedURI.fromString(fieldValue));
                        } else if (type == Date.class) {
                            // Can not find records with value which owns this type. Remains to be verified correct or not.
                            // System.out.println("\ttype: Date ");
                        } else if (type == Calendar.class) {
                            Calendar calendar = FieldType.toCalendar(fieldValue);
                            if (!verifyField(calendar)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, calendar);
                        } else if (type == StringMap.class) {
                            StringMap newStringMap = FieldType.convertType(fieldNode, StringMapWrapper.class);
                            if (!verifyField(newStringMap)) {
                                throw new Exception("field format exception");
                            }
                            StringMap sMap = (StringMap) pd.getReadMethod().invoke(object);
                            if (sMap == null) {
                            	sMap = new StringMap();
                            }
                            sMap.clear();

                            Set<String> keys = newStringMap.keySet();
                            for (String key : keys) {
                                sMap.put(key, newStringMap.get(key));
                            }
                            pd.getWriteMethod().invoke(object, sMap);
                        } else if (type == StringSet.class) {
                            StringSet stringSet = FieldType.convertType(fieldNode, StringSetWrapper.class);
                            if (!verifyField(stringSet)) {
                                throw new Exception("field format exception");
                            }

                            StringSet updateSet = (StringSet) pd.getReadMethod().invoke(object);
                            if (updateSet != null) {
                                updateSet.clear();
                                updateSet.addAll(stringSet);
                            } else {
                                pd.getWriteMethod().invoke(object, stringSet);
                            }

                        } else if (type == OpStatusMap.class) {
                            OpStatusMap opStatusMap = FieldType.convertType(fieldNode, OpStatusMapWrapper.class);
                            if (!verifyField(opStatusMap)) {
                                throw new Exception("field format exception");
                            }
                        } else if (type == StringSetMap.class) {
                            StringSetMap newSetMap = FieldType.convertType(fieldNode, StringSetMapWrapper.class);
                            if (!verifyField(newSetMap)) {
                                throw new Exception("field format exception");
                            }
                            StringSetMap sMap = (StringSetMap) pd.getReadMethod().invoke(object);
                            if (sMap == null) {
                                sMap = new StringSetMap();
                            }
                            Set<String> keys = sMap.keySet();
                            for (String key : keys) {
                                sMap.remove(key);
                            }

                            keys = newSetMap.keySet();
                            for (String key : keys) {
                                sMap.put(key, newSetMap.get(key));
                            }

                        } else if (type == FSExportMap.class) {
                            FSExportMap fSExportMap = FieldType.convertType(fieldNode, FSExportMapWrapper.class);
                            if (!verifyField(fSExportMap)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, fSExportMap);
                        } else if (type == SMBShareMap.class) {
                            SMBShareMap sMBShareMap = FieldType.convertType(fieldNode, SMBShareMapWrapper.class);
                            if (!verifyField(sMBShareMap)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, sMBShareMap);
                        } else if (type == ScopedLabelSet.class) {
                            ScopedLabelSet scopedLabelSet = FieldType.convertType(fieldNode, ScopedLabelSetWrapper.class);
                            if (!verifyField(scopedLabelSet)) {
                                throw new Exception("field format exception");
                            }
                            
                            ScopedLabelSet updateSet = (ScopedLabelSet) pd.getReadMethod().invoke(object);
                            if (updateSet != null) {
                                updateSet.clear();
                                updateSet.addAll(scopedLabelSet);
                            } else {
                                pd.getWriteMethod().invoke(object, scopedLabelSet);
                            }

                        } else if (type == String.class) {
                            pd.getWriteMethod().invoke(object, fieldClass.cast(fieldValue));
                        } else if (type.isEnum()) {
                            Object enumTypeObject = null;
                            try {
                                enumTypeObject = Enum.valueOf(type, fieldValue);
                            } catch (Exception e) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, enumTypeObject);
                        } else if (type == Integer.class) {
                            Integer intNum = FieldType.toInteger(fieldValue);
                            if (!verifyField(intNum)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, intNum);
                        } else if (type == Boolean.class) {
                            Boolean boolVal = FieldType.toBoolean(fieldValue);
                            if (!verifyField(boolVal)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, boolVal);
                        } else if (type == Long.class) {
                            Long longNum = FieldType.toLong(fieldValue);
                            if (!verifyField(longNum)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, longNum);
                        } else if (type == Short.class) {
                            Short shortNum = FieldType.toShort(fieldValue);
                            if (!verifyField(shortNum)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, shortNum);
                        } else if (type == Double.class) {
                            Double doubleNum = FieldType.toDouble(fieldValue);
                            if (!verifyField(doubleNum)) {
                                throw new Exception("field format exception");
                            }
                            pd.getWriteMethod().invoke(object, doubleNum);
                        } else {
                            pd.getWriteMethod().invoke(object, fieldValue);
                        }
                    } catch (Exception e) {
                        System.out
                                .println(String.format("Exception in getting field:%s in xml file line:%s.", pd.getName(), fieldLocation));
                        log.error("Exception in getting field value in xml file line:{}.", fieldLocation, e);
                        throw new Exception(String.format("Exception in getting field value in line:%s.", fieldLocation));
                    }

                    if (DEBUG) {
                        Object fieldValue1 = pd.getReadMethod().invoke(object);
                        System.out.println("write " + fieldValue1 + "\ttype: " + type + " success");
                    }
                }
            }

            if (operation == DbCliOperation.CREATE) {
                _dbClient.createObject(object);// Save model object.
            } else if (operation == DbCliOperation.LOAD) {
                _dbClient.persistObject(object);
            }
            log.info(String.format("Successfully update Column family:%s, \tdata object id:%s \tinto Cassandra, based on xml file %s",
                    cfName, idStr, fileName));
        }
    }

    private boolean verifyField(Object fieldObject) {
        if (fieldObject == null) {
            return false;
        }
        return true;
    }

    /**
     * Query for a record with the given id and type, and print the contents in human readable format
     * if query URI list, use queryAndPrintRecords(ids, clazz) method instead.
     * 
     * @param id
     * @param clazz
     * @param <T>
     */
    private <T extends DataObject> void queryAndPrintRecord(URI id, Class<T> clazz, DbCliOperation operationType) throws Exception {
        T object = queryObject(id, clazz);

        if (object == null) {
            // id object deleted
            System.out.println("id: " + id + " [ Deleted ]");
            return;
        }

        BeanInfo bInfo;

        try {
            bInfo = Introspector.getBeanInfo(clazz);
        } catch (IntrospectionException ex) {
            throw new RuntimeException("Unexpected exception getting bean info", ex);
        }

        if (operationType == DbCliOperation.LIST) {
            printBeanProperties(bInfo.getPropertyDescriptors(), object);
        } else {
            dumpBeanProperties(bInfo.getPropertyDescriptors(), object);
        }
    }

    /**
     * Initiate the root node in xml file
     * 
     * @Param cfName
     */
    private void initDumpXmlFile(String cfName) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        try {
            builder = dbf.newDocumentBuilder();
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
        doc = builder.newDocument();
        Element root = doc.createElement("dbschemas");
        doc.appendChild(root);

        schemaNode = doc.createElement("data_object_schema");
        schemaNode.setAttribute("name", cfName);
        root.appendChild(schemaNode);
    }

    /**
     * Write model object records into xml file
     * 
     * @Param outFileName
     */
    private void writeToXmlFile(String outFileName) {
        try {
            FileOutputStream fos = new FileOutputStream(outFileName);
            OutputStreamWriter outwriter = new OutputStreamWriter(fos);
            callWriteXmlFile(doc, outwriter, "utf-8");
            outwriter.close();
            fos.close();
            System.out.println(String.format("Dump into file: %s successfully", outFileName));
            log.info("Dump into file: {} successfully", outFileName);
        } catch (Exception e) {
            System.err.println("Caught Exception: " + e);
            log.error("Caught Exception: ", e);
        }
    }

    /**
     * Dump the contents in xml format
     * 
     * @param pds
     * @param object
     * @throws Exception
     */
    private <T extends DataObject> void dumpBeanProperties(PropertyDescriptor[] pds,
            T object) throws Exception {
        Element record = doc.createElement("record");
        record.setAttribute("id", object.getId().toString());
        schemaNode.appendChild(record);

        // Add readOnlyField node.
        Element readOnlyElement = doc.createElement("readOnlyField");
        record.appendChild(readOnlyElement);

        System.out.println("id: " + object.getId().toString());
        Object objValue;
        Class type;
        for (PropertyDescriptor pd : pds) {
            objValue = pd.getReadMethod().invoke(object);
            if (objValue == null) {
                continue;
            }

            // Skip password property.
            if (pd.getName().toLowerCase().matches("[a-zA-Z\\d]*password[a-zA-Z\\d]*")) {
                continue;
            }

            // Skip some properties.
            if (pd.getName().equals("class") || pd.getName().equals("id")) {
                Element readOnlyfieldNode = doc.createElement("field");
                readOnlyfieldNode.setAttribute("type", pd.getPropertyType().toString().substring(6)); // delete the prefix string "class "
                readOnlyfieldNode.setAttribute("name", pd.getName().toString());
                readOnlyfieldNode.setAttribute("value", objValue.toString());
                readOnlyElement.appendChild(readOnlyfieldNode);
                continue;
            }

            // Skip the fields without @Name annotation
            Name name = pd.getReadMethod().getAnnotation(Name.class);
            if (name == null) {
                log.info(
                        "Ignore data object fields without @Name annotation, fieldName={}.",
                        pd.getName());
                continue;
            }

            String objKey = name.value(); //use value from @Name instead of mtehod name
            type = pd.getPropertyType();
            if (DEBUG) {
                System.out.print("\t" + pd.getPropertyType() + "\t" + objKey + " = ");
            }

            Element fieldNode = doc.createElement("field");
            fieldNode.setAttribute("type", type.toString().substring(6)); // delete the prefix string "class "
            fieldNode.setAttribute("name", objKey);

            if (type == StringSetMap.class) {
                StringSetMap stringSetMap = (StringSetMap) objValue;
                FieldType.marshall(stringSetMap, fieldNode, StringSetMapWrapper.class);
            } else if (type == StringSet.class) {
                StringSet stringSet = (StringSet) objValue;
                FieldType.marshall(stringSet, fieldNode, StringSetWrapper.class);
            } else if (type == ScopedLabelSet.class) {
                ScopedLabelSet scopedLabelSet = (ScopedLabelSet) objValue;
                FieldType.marshall(scopedLabelSet, fieldNode, ScopedLabelSetWrapper.class);
            } else if (type == OpStatusMap.class) {
                OpStatusMap opStatusMap = (OpStatusMap) objValue;
                FieldType.marshall(opStatusMap, fieldNode, OpStatusMapWrapper.class);
            } else if (type == StringMap.class) {
                StringMap stringMap = (StringMap) objValue;
                FieldType.marshall(stringMap, fieldNode, StringMapWrapper.class);
            } else if (type == FSExportMap.class) {
                FSExportMap fSExportMap = (FSExportMap) objValue;
                FieldType.marshall(fSExportMap, fieldNode, FSExportMapWrapper.class);
            } else if (type == SMBShareMap.class) {
                SMBShareMap sMBShareMap = (SMBShareMap) objValue;
                FieldType.marshall(sMBShareMap, fieldNode, SMBShareMapWrapper.class);
            } else {
                fieldNode.setAttribute("value", objValue.toString());
            }
            record.appendChild(fieldNode);

        }
    }

    /**
     * Print the contents in human readable format
     * 
     * @param pds
     * @param object
     * @throws Exception
     */
    private <T extends DataObject> void printBeanProperties(PropertyDescriptor[] pds,
            T object) throws Exception {
        System.out.println("id: " + object.getId().toString());
        Object objValue;
        Class type;
        for (PropertyDescriptor pd : pds) {
            // skip class property
            if (pd.getName().equals("class") || pd.getName().equals("id")) {
                continue;
            }

            Name nameAnnotation = pd.getReadMethod().getAnnotation(Name.class);
            String objKey;
            if (nameAnnotation == null) {
                objKey = pd.getName();
            } else {
                objKey = nameAnnotation.value();
            }

            objValue = pd.getReadMethod().invoke(object);
            if (objValue == null) {
                continue;
            }

            System.out.print("\t" + objKey + " = ");

            Encrypt encryptAnnotation = pd.getReadMethod().getAnnotation(Encrypt.class);
            if (encryptAnnotation != null) {
                System.out.println("*** ENCRYPTED CONTENT ***");
                continue;
            }

            type = pd.getPropertyType();
            if (type == URI.class) {
                System.out.println("URI: " + objValue);
            } else if (type == StringMap.class) {
                System.out.println("StringMap " + objValue);
            } else if (type == StringSet.class) {
                System.out.println("StringSet " + objValue);
            } else if (type == StringSetMap.class) {
                System.out.println("StringSetMap " + objValue);
            } else if (type == OpStatusMap.class) {
                System.out.println("OpStatusMap " + objValue);
            } else {
                System.out.println(objValue);
            }
        }
        System.out.println();
    }

    /**
     * Query and dump into xml for a particular id in a ColumnFamily
     * 
     * @param cfName
     * @param ids
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void queryForDump(String cfName, String fileName, String[] ids) throws Exception {
        final Class clazz = getClassFromCFName(cfName); // fill in type from cfName
        if(clazz == null) {
            return;
        }
        initDumpXmlFile(cfName);
        for (String id : ids) {
            queryAndPrintRecord(URI.create(id), clazz, DbCliOperation.DUMP);
        }
        writeToXmlFile(fileName);
    }

    /**
     * Query and list for a particular id in a ColumnFamily
     * 
     * @param cfName
     * @param ids
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void queryForList(String cfName, String[] ids) throws Exception {
        final Class clazz = getClassFromCFName(cfName); // fill in type from cfName
        if(clazz == null) {
            return;
        }
        for (String id : ids) {
            queryAndPrintRecord(URI.create(id), clazz, DbCliOperation.LIST);
        }
    }

    private <T extends DataObject> T queryObject(URI id, Class<T> clazz) throws Exception {
        T object = null;
        try {
            object = _dbClient.queryObject(clazz, id);
        } catch (DatabaseException ex) {
            System.out.println("Error querying from db: " + ex);
            throw ex;
        }
        return object;
    }

    /**
     * Write contents into xml file
     * 
     * @Param doc
     * @Param writer
     * @Param encoding
     */
    public static void callWriteXmlFile(Document doc, Writer w, String encoding) {
        try {
            Source source = new DOMSource(doc);
            Result result = new StreamResult(w);
            Transformer xformer = TransformerFactory.newInstance().newTransformer();
            xformer.setOutputProperty(OutputKeys.INDENT, "yes");
            xformer.setOutputProperty(OutputKeys.ENCODING, encoding);
            xformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            xformer.transform(source, result);
        } catch (TransformerConfigurationException e) {
            System.err.println("Caught TransformerConfigurationException" + e);
            log.error("Caught TransformerConfigurationException: ", e);
        } catch (TransformerException e) {
            System.err.println("Caught TransformerException" + e);
            log.error("Caught TransformerException: ", e);
        }
    }

    /**
     * Delete objects.
     * 
     * @Param cfName
     * @Param ids
     * @Param force
     */
    public void deleteRecords(String cfName, String[] ids, boolean force) {
        for (String id : ids) {
            try {
                delete(id, cfName, force);
            } catch (Exception e) {
                System.err.println("Caught exception" + e);
                log.error("Caught Exception: ", e);
            }
        }
    }

    /**
     * Delete object.
     * 
     * @param id
     * @param cfName
     * @param force
     */
    private void delete(String id, String cfName, boolean force) throws Exception {
        final Class clazz = getClassFromCFName(cfName); // fill in type from cfName
        if(clazz == null) {
            return;
        }

        boolean deleted = queryAndDeleteObject(URI.create(id), clazz, force);

        if (deleted) {
            log.info("The object {} is deleted from the column family {}", id, cfName);
            System.out.println(String.format("The object %s is deleted from the column family %s", id, cfName));
        }
        else {
            log.info("The object {} is NOT deleted from the column family {}", id, cfName);
            System.out.println(String.format("The object %s is NOT deleted from the column family %s", id, cfName));
        }
    }

    /**
     * Query for a record with the given id and type, and print the contents in human readable format
     * 
     * @param id
     * @param clazz
     * @param <T>
     */
    private <T extends DataObject> boolean queryAndDeleteObject(URI id, Class<T> clazz, boolean force)
            throws Exception {
        if (_dependencyChecker == null) {
            DependencyTracker dependencyTracker = dataObjectscanner.getDependencyTracker();
            _dependencyChecker = new DependencyChecker(_dbClient, dependencyTracker);
        }

        if (_dependencyChecker.checkDependencies(id, clazz, false) != null) {
            if (!force) {
                System.out.println(String.format("Failed to delete the object %s: there are active dependencies", id));
                return false;
            }
            log.info("Force to delete object {} that has active dependencies", id);
        }

        T object = queryObject(id, clazz);

        if (object == null) {
            System.out.println(String.format("The object %s has already been deleted", id));
            return false;
        }

        if ((object.canBeDeleted() == null) || force) {
            if (object.canBeDeleted() != null) {
                log.info("Force to delete object {} that can't be deleted", id);
            }

            _dbClient.internalRemoveObjects(object);
            return true;
        }

        System.out.println(String.format("The object %s can't be deleted", id));

        return false;
    }

    /**
     * Iteratively list records from DB in a user readable format
     * 
     * @param cfName
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void listRecords(String cfName) throws Exception {
        final Class clazz = getClassFromCFName(cfName); // fill in type from cfName
        if(clazz == null) {
            return;
        }
        List<URI> uris = null;
        uris = getColumnUris(clazz, activeOnly);
        if (uris == null || !uris.iterator().hasNext()) {
            System.out.println("No records found");
            return;
        }
        int count = queryAndPrintRecords(uris, clazz);
        System.out.println("Number of All Records is: " + count);
    }

    /**
     * Query for records with the given ids and type, and print the contents in human readable format
     * 
     * @param ids
     * @param clazz
     * @param <T>
     */
    private <T extends DataObject> int queryAndPrintRecords(List<URI> ids, Class<T> clazz)
            throws Exception {

        Iterator<T> objects;
        BeanInfo bInfo;
        int countLimit = 0;
        int countAll = 0;
        String input;
        BufferedReader buf = new BufferedReader(new InputStreamReader(System.in));

        try {
            objects = _dbClient.queryIterativeObjects(clazz, ids);
            bInfo = Introspector.getBeanInfo(clazz);
            while (objects.hasNext()) {
                T object = (T) objects.next();
                printBeanProperties(bInfo.getPropertyDescriptors(), object);
                countLimit++;
                countAll++;
                if (!turnOnLimit || countLimit != listLimit) {
                    continue;
                }
                System.out.println(String.format("Read %s rows ", countAll));
                do {
                    System.out.println("\nPress 'ENTER' to continue or 'q<ENTER>' to quit...");
                    input = buf.readLine();
                    if (input.isEmpty()) {
                        countLimit = 0;
                        break;
                    }
                    if (input.equalsIgnoreCase(QUITCHAR)) {
                        return countAll;
                    }
                } while (!input.isEmpty());
            }
        } catch (DatabaseException ex) {
            log.error("Error querying from db: " + ex);
            System.out.println("Error querying from db: " + ex);
            throw ex;
        } catch (IntrospectionException ex) {
            log.error("Unexpected exception getting bean info", ex);
            throw new RuntimeException("Unexpected exception getting bean info", ex);
        } finally {
            buf.close();
        }
        return countAll;
    }

    /**
     * get the keys of column family for list/count
     */
    private List<URI> getColumnUris(Class clazz, boolean isActive) {
        List<URI> uris = null;
        try {
            uris = _dbClient.queryByType(clazz, isActive);
        } catch (DatabaseException e) {
            System.out.println("Error querying from db: " + e);
            return null;
        }
        return uris;
    }

    public void setListLimit(int listLimit) {
        this.listLimit = listLimit;
    }

    public void setTurnOnLimit(boolean turnOnLimit) {
        this.turnOnLimit = turnOnLimit;
    }

    public void setActiveOnly(boolean activeOnly) {
        this.activeOnly = activeOnly;
    }

    private static class DataObjectModelPackageScanner extends PackageScanner {
        private HashMap<String, Class> cfMap;

        public DataObjectModelPackageScanner() {
            cfMap = new HashMap<String, Class>();
            setPackages("com.emc.storageos.db.client.model");
            scan(Cf.class);
        }

        public HashMap<String, Class> getCfMaps() {
            return cfMap;
        }

        /**
         * Processes data object or time series class and extracts CF
         * requirements
         * 
         * @param clazz data object or time series class
         */
        @Override
        protected void processClass(Class clazz) {
            if (DataObject.class.isAssignableFrom(clazz)) {
                DataObjectType doType = TypeMap.getDoType(clazz);
                cfMap.put(doType.getCF().getName(), clazz);
            }
        }
    }

    private Class<? extends DataObject>  getClassFromCFName(String cfName) {
        Class<? extends DataObject> clazz = _cfMap.get(cfName); // fill in type from cfName
        if (clazz == null) {
            System.err.println("Unknown Column Family: " + cfName);
            return null;
        }
        if (!DataObject.class.isAssignableFrom(clazz)) {
            System.err.println("TimeSeries data not supported with this command.");
            return null;
        }
        return clazz;
    }

}
