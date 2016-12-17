/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.VplexMirror;
import com.google.common.base.Function;
import com.google.common.base.Joiner;

public class CommonTransformerFunctions {
    public static final String EMPTY_STRING = "";
    public static final int MAX_COLLECTION_SIZE_TO_DISPLAY = 20;

    public static final Function<BlockMirror, URI> FCTN_MIRROR_TO_URI =
            new Function<BlockMirror, URI>() {
                @Override
                public URI apply(BlockMirror mirror) {
                    return mirror.getId();
                }
            };

    public static final Function<DataObject, String> FCTN_VOLUME_URI_TO_STR =
            new Function<DataObject,
            String>() {
                @Override
                public String apply(DataObject volume) {
                    String val = "";
                    if (volume != null) {
                        val = volume.getId().toString();
                    }
                    return val;
                }
            };

    public static final Function<String, URI> FCTN_STRING_TO_URI =
            new Function<String, URI>() {
                @Override
                public URI apply(String uri) {
                    return URI.create(uri);
                }
            };

    public static final Function<String, Integer> FCTN_STRING_TO_INTEGER =
            new Function<String, Integer>() {
                @Override
                public Integer apply(String str) {
                    return Integer.valueOf(str);
                }
            };

    public static final Function<URI, String> FCTN_URI_TO_STRING =
            new Function<URI, String>() {
                @Override
                public String apply(URI uri) {
                    return uri.toString();
                }
            };

    public static Function<String, Initiator>
            fctnStringToInitiator(final DbClient dbClient) {
        return new Function<String, Initiator>() {
            @Override
            public Initiator apply(String uriStr) {
                Initiator initiator = null;
                if (uriStr != null && !uriStr.isEmpty()) {
                    initiator = dbClient.queryObject(Initiator.class,
                            URI.create(uriStr));
                }
                return initiator;
            }
        };
    }

    public static Function<String, Host>
            fctnStringToHost(final DbClient dbClient) {
        return new Function<String, Host>() {
            @Override
            public Host apply(String uriStr) {
                Host host = null;
                if (uriStr != null && !uriStr.isEmpty()) {
                    host = dbClient.queryObject(Host.class,
                            URI.create(uriStr));
                }
                return host;
            }
        };
    }

    public static Function<Initiator, String>
            fctnInitiatorToPortName() {
        return new Function<Initiator, String>() {
            @Override
            public String apply(Initiator initiator) {
                if (initiator != null) {
                    return Initiator.normalizePort(initiator.getInitiatorPort());
                }
                return NullColumnValueGetter.getNullStr();
            }
        };
    }

    public static Function<DataObject, URI>
            fctnDataObjectToID() {
        return new Function<DataObject, URI>() {

            @Override
            public URI apply(DataObject dataObject) {
                return dataObject.getId();
            }
        };
    }

    public static Function<BlockObject, String>
            fctnBlockObjectToNativeID() {
        return new Function<BlockObject, String>() {

            @Override
            public String apply(BlockObject blockObject) {
                return blockObject.getNativeId();
            }
        };
    }

    public static Function<BlockObject, String>
            fctnBlockObjectToLabel() {
        return new Function<BlockObject, String>() {
            @Override
            public String apply(BlockObject blockObject) {
                return blockObject.getLabel();
            }
        };
    }

    public static Function<BlockObject, String>
            fctnBlockObjectToNativeGuid() {
        return new Function<BlockObject, String>() {

            @Override
            public String apply(BlockObject blockObject) {
                return blockObject.getNativeGuid();
            }
        };
    }

    public static Function<BlockObject, String>
            fctnBlockObjectToForDisplay() {
        return new Function<BlockObject, String>() {

            @Override
            public String apply(BlockObject blockObject) {
                return blockObject.forDisplay();
            }
        };
    }

    public static Function<StoragePort, String>
            fctnStoragePortToPortName() {
        return new Function<StoragePort, String>() {

            @Override
            public String apply(StoragePort port) {
                return port.getPortName();
            }
        };
    }

    public static Function<DataObject, String> fctnDataObjectToForDisplay() {
        return new Function<DataObject, String>() {

            @Override
            public String apply(DataObject obj) {
                return obj != null ? obj.forDisplay() : EMPTY_STRING;
            }
        };
    }

    public static final Function<VplexMirror, URI> FCTN_VPLEX_MIRROR_TO_URI =
            new Function<VplexMirror, URI>() {
                @Override
                public URI apply(VplexMirror mirror) {
                    return mirror.getId();
                }
            };

    public static final Function<StoragePool, URI> fctnStoragePoolToStorageSystemURI() {
        return new Function<StoragePool, URI>() {
            @Override
            public URI apply(StoragePool pool) {
                return pool.getStorageDevice();
            }
        };
    }

    public static String collectionToString(StringMap map) {
        String collectionAsString = EMPTY_STRING;
        if (map != null) {
            collectionAsString = collectionToString(map.entrySet());
        }
        return collectionAsString;
    }

    public static String collectionToString(Collection collection) {
        String collectionAsString = EMPTY_STRING;
        if (collection != null) {
            boolean isTruncatedList = false;
            int originalSize = collection.size();
            // Limit the number of items to display
            if (originalSize > MAX_COLLECTION_SIZE_TO_DISPLAY) {
                Collection truncatedCollection = new ArrayList<>();
                Iterator it = collection.iterator();
                int count = 0;
                while (it.hasNext() && (count++ < MAX_COLLECTION_SIZE_TO_DISPLAY)) {
                    truncatedCollection.add(it.next());
                }
                isTruncatedList = true;
                collection = truncatedCollection;
            }
            collectionAsString = Joiner.on(',').skipNulls().join(collection);
            if (isTruncatedList) {
                collectionAsString = collectionAsString.concat(String.format("... %d elements skipped",
                        originalSize - MAX_COLLECTION_SIZE_TO_DISPLAY));
            }
        }
        return collectionAsString;
    }

    public static String collectionString(final StringMap map) {
        return collectionString((map != null) ? map.entrySet() : Collections.emptyList());
    }

    public static String collectionString(final Collection collection) {
        final int size = (collection != null) ? collection.size() : 0;
        return String.format("size=%-4d [%s]", size, CommonTransformerFunctions.collectionToString(collection));
    }
}
