package com.emc.storageos.dbcli.exportmask;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.dbcli.DbCli;

public class ExportMaskDBUpdate {

    private static final String COMMA_SEPERATOR = ",";

    private static final Logger log = LoggerFactory.getLogger(ExportMaskDBUpdate.class);

    private String migrationCallbackClass;
    private String filePath;
    private DbCli dbCli;
    private DbClientImpl dbClient;

    public ExportMaskDBUpdate(String[] args, DbCli dbCli) {
        /*
         * if (args.length == 2) {
         * migrationCallbackClass = args[1];
         * } else {
         * throw new IllegalArgumentException("Invalid command option. ");
         * }
         */

        this.filePath = args[1];
        this.dbCli = dbCli;
        dbClient = dbCli.getDbClient();
        System.out.println(filePath);
    }

    public void process() {
        try {
            log.info("Inside ExportMaskDBUpdate.process");
            /*
             * Class clazz = Class.forName(migrationCallbackClass);
             * BaseCustomMigrationCallback callback = (BaseCustomMigrationCallback) clazz.newInstance();
             * callback.setDbClient(_client.getDbClient());
             * callback.setCoordinatorClient(_client.getDbClient().getCoordinatorClient());
             * System.out.print("Running migration callback:");
             * System.out.println(migrationCallbackClass);
             * callback.process();
             */

            log.info("File Path :{}", filePath);
            System.out.println("File Path :" + filePath);
            List<ExportMaskModel> modelList = readFromXmlFile(filePath);
            for (ExportMaskModel model : modelList) {
                System.out.println(model);
                log.info("ExportMask object :{}",model);
                
                ExportMask maskObj = dbClient.queryObject(ExportMask.class, model.getId());
                if (maskObj != null) {
                    maskObj.setMaskName(model.getMaskName());
                    maskObj.setNativeId(model.getNativeId());
                    maskObj.setStoragePorts(model.getStoragePorts());
                    StringSetMap zoningMap = new StringSetMap();
                    zoningMap.putAll(model.getZoningMap());
                    maskObj.setZoningMap(zoningMap);
                    dbClient.updateObject(maskObj);
                }
            }

            URIQueryResultList uris = new URIQueryResultList();
            dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFCZoneReferenceKeyConstraint("4000AABBCCDD3101_5000097358193048"),
                    uris);
            Iterator<URI> uriIterator = uris.iterator();
            while (uriIterator.hasNext()) {
                URI uri = uriIterator.next();
                System.out.println(uri);
                log.info("FCZoneReference URI: {}", uri.toString());
            }
            System.out.println("Done");
        } catch (Exception ex) {
            System.out.println(String.format("Unknown exception. Please check dbutils.log", ex.getMessage()));
            log.error("Unexpected exception when executing migration callback", ex);
        }
    }

    private List<ExportMaskModel> readFromXmlFile(String filePath) {

        List<ExportMaskModel> emModelList = new ArrayList<>();
        try {
            // Get the DOM Builder Factory
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Get the DOM Builder
            DocumentBuilder builder = factory.newDocumentBuilder();
            File inputFile = new File(filePath);
            // Load and Parse the XML document
            // document contains the complete XML as a Tree.
            /*
             * Document document = builder.parse(
             * ClassLoader.getSystemResourceAsStream("xml/employee.xml"));
             */
            Document document = builder.parse(inputFile);

            Element root = document.getDocumentElement();
            NodeList emNodeList = document.getElementsByTagName("exportMask");
            for (int temp = 0; temp < emNodeList.getLength(); temp++) {
                ExportMaskModel obj = new ExportMaskModel();
                Node nNode = emNodeList.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                   Element eElement = (Element) nNode;
                   /*System.out.println("Student roll no : " 
                      + eElement.getAttribute("rollno"));*/
                    obj.setId(URI.create(eElement.getElementsByTagName("id").item(0).getTextContent()));
                    obj.setMaskName(eElement.getElementsByTagName("maskName").item(0).getTextContent());
                    obj.setNativeId(eElement.getElementsByTagName("nativeId").item(0).getTextContent());
                    StringSet ports = new StringSet();
                    for (String port : eElement.getElementsByTagName("storagePorts").item(0).getTextContent().split(COMMA_SEPERATOR)) {
                        ports.add(port);
                    }
                    obj.setStoragePorts(ports);
                    // obj.setInitiators(eElement.getElementsByTagName("initators").item(0).getTextContent());

                    // NodeList zoneMapNodeList = (Element) (eElement.getElementsByTagName("zoningMaps").item(0));
                    Element zoneMapsElement = (Element) eElement.getElementsByTagName("zoningMaps").item(0);
                    NodeList zoneMapNodeList = zoneMapsElement.getElementsByTagName("zoningMap");
                    Map<String, StringSet> zoningMap = new HashMap<>();
                    obj.setZoningMap(zoningMap);
                    for (int j = 0; j < zoneMapNodeList.getLength(); j++) {
                        Node zoneMapNode = zoneMapNodeList.item(j);
                        if (zoneMapNode.getNodeType() == Node.ELEMENT_NODE) {
                            Element zoneMapElement = (Element) zoneMapNode;
                            String key = zoneMapElement.getElementsByTagName("zInitiator").item(0).getTextContent();
                            StringSet valueList = null;
                            if (zoningMap.containsKey(key)) {
                                valueList = zoningMap.get(key);
                            } else {
                                valueList = new StringSet();
                            }
                            zoningMap.put(key, valueList);
                            String[] zPorts = zoneMapElement.getElementsByTagName("zPort").item(0).getTextContent().split(COMMA_SEPERATOR);
                            for (String zPort : zPorts) {
                                valueList.add(zPort);
                            }
                        }
                    }
                }
                emModelList.add(obj);
            }
        } catch (ParserConfigurationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (SAXException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return emModelList;
    }

    public static void main(String[] args) {
        ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/dbcli-conf.xml");
        DbCli dbCli = (DbCli) ctx.getBean("dbcli"); // NOSONAR ("squid:S2444")
        dbCli.start(true);

        ExportMaskDBUpdate dbUpdate = new ExportMaskDBUpdate(args, dbCli);
        List<ExportMaskModel> modelList = dbUpdate.readFromXmlFile(dbUpdate.filePath);
        for (ExportMaskModel model : modelList) {
            System.out.println(model);
        }


    }

}
