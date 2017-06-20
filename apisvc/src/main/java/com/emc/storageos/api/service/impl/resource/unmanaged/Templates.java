package com.emc.storageos.api.service.impl.resource.unmanaged;

public interface Templates {

    // URIs
    public static final String URI_UMV = "/vdc/unmanaged/volumes/%s";
    public static final String URI_UEM = "/vdc/unmanaged/export-masks/%s";
    public static final String URI_VOL = "/block/volume/%s";
    public static final String URI_VPOOL = "%s/blockvirtualpools/edit/%s";

    // messages
    public static final String NONE_FOUND = "<ul><li>None found</li></ul>";
    public static final String NOT_INGESTED = "Not ingested";
    public static final String PARTIALLY_INGESTED = "Partially ingested as <a href=\"%s\">%s</a>";
    public static final String EMPTY_STRING = "";

    // org.apache.commons.lang.text.StrSubstitutor templates
    public static final String TEMPLATE_HTML_PAGE = "<html><head>"
            + "<title>${title}</title>"
            + "<style>body {font-size:1em;font-family:Helvetica;color:#444} table {border-collapse:collapse;border:1px} table td {vertical-align:top;padding:8px;border:1px solid #CCC}</style>"
            + "</head><body>${body}</body></html>";
    public static final String TEMPLATE_UMV_TREE = "<h1>${label}</h1>"
            + "<table border=\"1\">"
            + "<tr><td nowrap>Ingestion Status:</td><td>${ingestionStatus}</td></tr>"
            + "<tr><td nowrap>Storage Array:</td><td>${storageDevice}</td></tr>"
            + "<tr><td nowrap>Storage Pool:</td><td>${storagePool}</td></tr>"
            + "<tr><td nowrap>Device ID:</td><td>${nativeGuid}</td></tr>"
            + "<tr><td nowrap>WWN:</td><td>${wwn}</td></tr>"
            + "<tr><td nowrap>Consistency Group:</td><td>${consistencyGroup}</td></tr>"
            + "${vplexRelationships}"
            + "<tr><td nowrap>Matching Virtual Pools:</td><td>${supportedVpoolUris}</td></tr>"
            + "<tr><td nowrap>Unmanaged Exports:</td><td>${unmanagedExportMasks}</td></tr>"
            + "<tr><td nowrap>Ingested/Managed Exports:</td><td>${managedExportMasks}</td></tr>"
            + "<tr><td nowrap>Unmanaged Snapshots:</td><td>${unmanagedSnapshots}</td></tr>"
            + "<tr><td nowrap>Unmanaged Clones (Full Copies):</td><td>${unmanagedClones}</td></tr>"
            + "<tr><td nowrap>Ingested/Managed Replicas:</td><td>${managedReplicas}</td></tr>"
            + "<tr><td nowrap>Raw Volume Characterstics:</td><td>${volumeCharacterstics}</td></tr>"
            + "<tr><td nowrap>Raw Volume Information:</td><td>${volumeInformation}</td></tr>"
            + "<tr><td nowrap>Operation Status:</td><td>${status}</td></tr>"
            + "</table>";
    public static final String TEMPLATE_UEM_TREE = "<h1>${maskName}</h1>"
            + "<table border=\"1\">"
            + "<tr><td nowrap>Ingestion Status:</td><td>${ingestionStatus}</td></tr>"
            + "<tr><td nowrap>Storage Array:</td><td>${storageSystem}</td></tr>"
            + "<tr><td nowrap>Masking View Path:</td><td>${maskingViewPath}</td></tr>"
            + "<tr><td nowrap>Supported Vpools:</td><td>${supportedVpoolUris}</td></tr>"
//            + "<tr><td nowrap>Known Initiator Network Ids:</td><td>${knownInitiatorNetworkIds}</td></tr>"
            + "<tr><td nowrap>Known Initiators:</td><td>${knownInitiatorUris}</td></tr>"
            + "<tr><td nowrap>Known Storage Ports:</td><td>${knownStoragePortUris}</td></tr>"
            + "<tr><td nowrap>Unmanaged Initiator Network Ids:</td><td>${unmanagedInitiatorNetworkIds}</td></tr>"
            + "<tr><td nowrap>Unmanaged Storage Port Network Ids:</td><td>${unmanagedStoragePortNetworkIds}</td></tr>"
            + "<tr><td nowrap>Unmanaged Volumes:</td><td>${unmanagedVolumeUris}</td></tr>"
            + "<tr><td nowrap>Zoning Map Entry Keys:</td><td>${zoningMap}</td></tr>"
            + "<tr><td nowrap>Operation Status:</td><td>${status}</td></tr>"
            + "</table>";
    
    // String.format templates
    public static final String TEMPLATE_UL = "<ul>%s</ul>";
    public static final String TEMPLATE_LI = "<li>%s</li>";
    public static final String TEMPLATE_LI_LINK = "<li><a href=\"%s\">%s</a></li>";
    public static final String TEMPLATE_LI_LINK_SORTABLE = "<li><a title=\"%s\" href=\"%s\">%s</a></li>";
    public static final String TEMPLATE_TR = "<tr><td>%s</td><td>%s</td></tr>";

}
