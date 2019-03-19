/*
 * Copyright (c) 2018 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.PrefixConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.StorageProtocol.Block;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.model.network.DataObjectSchemaRestRep;
import com.emc.storageos.model.network.DbSchemasRestRep;
import com.emc.storageos.model.network.FieldRestRep;
import com.emc.storageos.model.network.ReadOnlyFieldRestRep;
import com.emc.storageos.model.network.RecordRestRep;

/**
 * FCZoneReferenceUtils class will provide the utility methods for NetworkSystemService findMissingFCZoneReference method .
 * 
 */
public class FCZoneReferenceUtils {

    private static final Logger _log = LoggerFactory
            .getLogger(FCZoneReferenceUtils.class);

    /**
     * Find the FCZoneReference objects for the given pwwn key and Volume.
     * 
     * @param volUriStr
     * @param pwwnKeyList
     * @return
     */
    public static List<FCZoneReference> findFCZoneReferences(DbClient dbClient, String volUriStr, List<String> pwwnKeyList) {

        List<FCZoneReference> refs = new ArrayList<FCZoneReference>();
        for (String pwwnKey : pwwnKeyList) {
            String key = pwwnKey + "_" + volUriStr;
            URIQueryResultList queryList = new URIQueryResultList();
            dbClient.queryByConstraint(PrefixConstraint.Factory.getLabelPrefixConstraint(FCZoneReference.class, key),
                    queryList);
            while (queryList.iterator().hasNext()) {
                FCZoneReference ref = dbClient.queryObject(FCZoneReference.class, queryList.iterator().next());
                refs.add(ref);
            }
        }

        return refs;
    }

    /**
     * Generate the pwwn key form the ExportMask
     *
     * @param dbClient DbClient instance
     * @param exportMask
     * @return
     */
    public static List<String> generatePwwnKeys(DbClient dbClient, ExportMask exportMask) {
        List<String> pwwnKeyList = new ArrayList<>();
        if (exportMask != null) {
            StringSetMap zoneMap = exportMask.getZoningMap();
            // Construct Initiator_Port pair (pwwnKey)
            for (String initKey : zoneMap.keySet()) {
                Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initKey));
                if (initiator.getProtocol().equals(Block.FC.name())) {
                    StringSet portsKey = zoneMap.get(initKey);
                    for (String portkey : portsKey) {
                        StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(portkey));
                        String pwwnKey = FCZoneReference.makeEndpointsKey(initiator.getInitiatorPort(), port.getPortNetworkId());
                        pwwnKeyList.add(pwwnKey);
                    }
                }
            }
        }
        return pwwnKeyList;
    }

    /**
     * Generate FCZoneReference list for existing zone and new volume combination
     * 
     * @param dbClient DbClient instance
     * @param exportGroup The ViPR ExportGroup in question
     * @param oldRefList Collection of FCZoneReference for same ExportGroup
     * @param blockRefList The set of URIs to block volumes for export
     * @return
     */
    public static List<FCZoneReference> generateFCZoneReferences(DbClient dbClient, URI exportGroup, Collection<FCZoneReference> oldRefList,
            Set<URI> blockRefList) {
        List<FCZoneReference> fcList = new ArrayList<>();
        if (oldRefList == null || exportGroup == null || blockRefList == null) {
            _log.info("Returing empty FCZoneReferenceList as value of oldRefList: {}, exportGroup: {}, blockRefList: {}", oldRefList,
                    exportGroup, blockRefList);
            return fcList;
        }
        for (URI blockRef : blockRefList) {
            for (FCZoneReference oldRef : oldRefList) {
                // Check to see that we don't add multiple references for same Volume/Export Group combination
                FCZoneReference ref = findFCZoneReferenceForVolGroupKey(dbClient, exportGroup, blockRef, oldRef.getPwwnKey());
                if (ref == null) {
                    ref = new FCZoneReference();
                    ref.setPwwnKey(oldRef.getPwwnKey());
                    ref.setFabricId(oldRef.getFabricId());
                    ref.setNetworkSystemUri(oldRef.getNetworkSystemUri());
                    ref.setVolumeUri(blockRef);
                    if (exportGroup != null) {
                        ref.setGroupUri(exportGroup);
                    } else {
                        ref.setGroupUri(oldRef.getGroupUri());
                    }
                    ref.setZoneName(oldRef.getZoneName());
                    ref.setId(URIUtil.createId(FCZoneReference.class));
                    ref.setInactive(false);
                    ref.setLabel(FCZoneReference.makeLabel(oldRef.getPwwnKey(), blockRef.toString()));
                    ref.setExistingZone(oldRef.getExistingZone());
                    ref.setCreationTime(Calendar.getInstance());
                    fcList.add(ref);
                    _log.info("Genrated FCZoneReference Id: {} for existing Zone: {} PwwnKey: {} VolumeUri: {} ExportGroup: {}",
                            ref.getId(), ref.getZoneName(), ref.getPwwnKey(), ref.getVolumeUri(), ref.getGroupUri());

                }
            }
        }
        return fcList;
    }

    /**
     * Looks in the database for a zone for the same volume and export group and key
     * 
     * @param dbClient DbClient instance
     * @param exportGroupURI -- the export group URI
     * @param volumeURI -- the volume URI
     * @param refKey -- the FCZoneReference key which is the concatenation of the initiator
     *            and storage port WWNs. Note that this key is formed by sorting the WWNs
     * @return The zone reference instance if found, null otherwise
     */
    private static FCZoneReference findFCZoneReferenceForVolGroupKey(DbClient dbClient, URI exportGroupURI, URI volumeURI, String refKey) {
        Map<String, FCZoneReference> volRefMap = makeExportToReferenceMap(dbClient, refKey);
        String volExportKey = make2UriKey(volumeURI, exportGroupURI);
        if (volRefMap.containsKey(volExportKey)) {
            FCZoneReference ref = volRefMap.get(volExportKey);
            // If we have an active reference, don't make another
            if (ref != null && ref.getInactive() == false) {
                _log.info(String.format("Existing zone reference: vol %s group %s refkey %s",
                        volumeURI, exportGroupURI, refKey));
                return ref;
            }
        }
        return null;
    }

    /**
     * Make a String key from two URIs.
     * 
     * @param uri1
     * @param uri2
     * @return
     */
    public static String make2UriKey(URI uri1, URI uri2) {
        String part1 = "null";
        String part2 = "null";
        if (uri1 != null) {
            part1 = uri1.toString();
        }
        if (uri2 != null) {
            part2 = uri2.toString();
        }
        return part1 + "+" + part2;
    }

    /**
     * Makes a map from a volume/export group key to the FCZoneReference.
     * 
     * @param dbClient DbClient instance
     * @param key - Endpoint key consisting of concatenated WWNs
     * @return Map of volume/export group key to FCZoneReference
     */
    private static Map<String, FCZoneReference> makeExportToReferenceMap(DbClient dbClient, String key) {
        Map<String, FCZoneReference> volRefMap = new HashMap<String, FCZoneReference>();
        List<FCZoneReference> refs = getFCZoneReferencesForKey(dbClient, key);
        for (FCZoneReference ref : refs) {
            String uri2key = make2UriKey(ref.getVolumeUri(), ref.getGroupUri());
            volRefMap.put(uri2key, ref);
        }
        return volRefMap;
    }

    /**
     * Find the FCZoneReferences for a given zone reference key.
     * 
     * @param dbClient DbClient instance
     * @param key - Endpoint key consisting of concatenated WWNs
     * @return List of FCZoneReference
     */
    private static List<FCZoneReference> getFCZoneReferencesForKey(DbClient dbClient, String key) {
        List<FCZoneReference> list = new ArrayList<FCZoneReference>();
        URIQueryResultList uris = new URIQueryResultList();
        Iterator<FCZoneReference> itFCZoneReference = null;
        dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFCZoneReferenceKeyConstraint(key), uris);
        itFCZoneReference = dbClient.queryIterativeObjects(FCZoneReference.class,
                DataObjectUtils.iteratorToList(uris), true);
        if (itFCZoneReference.hasNext()) {
            while (itFCZoneReference.hasNext()) {
                list.add(itFCZoneReference.next());
            }
        } else {
            _log.info("No FC Zone References for key found");
        }
        return list;
    }

    /**
     * Convert FCZoneReference list into dbCli friendly DbSchemasRestRep.
     * This is format is compatible to dbCli dump
     * Compatible to create new FCZoneReference db entry by dbCli create command
     * 
     * @param fcList
     * @return
     */
    public static DbSchemasRestRep convertToDbCliObjectFormat(List<FCZoneReference> fcList) {
        DbSchemasRestRep dbs = new DbSchemasRestRep();
        DataObjectSchemaRestRep dos = new DataObjectSchemaRestRep();
        dos.setName("FCZoneReference");
        dbs.setData_object_schema(dos);
        for (FCZoneReference fcRef : fcList) {
            RecordRestRep rec = new RecordRestRep();
            rec.setId("");
            dos.getRecord().add(rec);
            // Add read only field.
            ReadOnlyFieldRestRep readOnlyField = new ReadOnlyFieldRestRep();
            rec.setReadOnlyField(readOnlyField);
            FieldRestRep field = new FieldRestRep("class", "java.lang.Class", "class com.emc.storageos.db.client.model.FCZoneReference");
            FieldRestRep fieldId = new FieldRestRep("id", "java.net.URI", "");
            readOnlyField.getField().add(field);
            readOnlyField.getField().add(fieldId);

            // Add the other field in the RecordRestRep field list

            List<FieldRestRep> fieldList = rec.getField();
            FieldRestRep fieldInactiveFlag = new FieldRestRep("inactive", "java.lang.Boolean", "false");
            fieldList.add(fieldInactiveFlag);

            FieldRestRep fieldCreationTime = new FieldRestRep("creationTime", "java.util.Calendar", Calendar.getInstance().toString());
            fieldList.add(fieldCreationTime);

            if (fcRef.getExistingZone() != null) {
                FieldRestRep fieldExistingZone = new FieldRestRep("existingZone", "java.lang.Boolean", fcRef.getExistingZone().toString());
                fieldList.add(fieldExistingZone);
            }
            if (fcRef.getFabricId() != null) {
                FieldRestRep fieldFabricId = new FieldRestRep("fabricId", "java.lang.String", fcRef.getFabricId());
                fieldList.add(fieldFabricId);
            }
            if (fcRef.getGroupUri() != null) {
                FieldRestRep fieldGroupUri = new FieldRestRep("groupUri", "java.net.URI", fcRef.getGroupUri().toString());
                fieldList.add(fieldGroupUri);
            }

            if (fcRef.getLabel() != null) {
                FieldRestRep fieldLabel = new FieldRestRep("label", "java.lang.String", fcRef.getLabel());
                fieldList.add(fieldLabel);
            }
            if (fcRef.getNetworkSystemUri() != null) {
                FieldRestRep fieldNetworkSystemUri = new FieldRestRep("networkSystemUri", "java.net.URI",
                        fcRef.getNetworkSystemUri().toString());
                fieldList.add(fieldNetworkSystemUri);
            }
            if (fcRef.getPwwnKey() != null) {
                FieldRestRep fieldPwwnKey = new FieldRestRep("pwwnKey", "java.lang.String", fcRef.getPwwnKey());
                fieldList.add(fieldPwwnKey);
            }
            if (fcRef.getVolumeUri() != null) {
                FieldRestRep fieldVolumeUri = new FieldRestRep("volumeUri", "java.net.URI", fcRef.getVolumeUri().toString());
                fieldList.add(fieldVolumeUri);
            }

            if (fcRef.getZoneName() != null) {
                FieldRestRep fieldZoneName = new FieldRestRep("zoneName", "java.lang.String", fcRef.getZoneName());
                fieldList.add(fieldZoneName);
            }
        }
        return dbs;

    }

}
