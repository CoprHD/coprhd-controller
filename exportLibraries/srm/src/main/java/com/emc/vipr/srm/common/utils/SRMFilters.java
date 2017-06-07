package com.emc.vipr.srm.common.utils;

public final class SRMFilters {

    public static final String FILTER_VSTATUS_ACTIVE = "!(vstatus='inactive') & ";
    public static final String FILTER_DEVICE = "device='v_device'";
    public static final String VPLEX_FILTER_DEVICE = "serialnb='v_serialnb'";
    public static final String ALL_FILTER_DEVICES = FILTER_DEVICE
            + SRMViprConstants.PIPE + VPLEX_FILTER_DEVICE;

    public static final String ARRAY_FILTER = "(devtype='Array' | devtype='UnifiedArray') & device='v_device'";
    public static final String VNX_FILTER = "datagrp='VNXBlock-Array' & device='v_device'";
    public static final String VMAX_FILTER = "datagrp='VMAX-ARRAYS' & device='v_device'";
    public static final String XTREMIO_FILTER = "datagrp='XtremIO%' & device='v_device'";
    public static final String ARRAY_PORT_FILTER_1 = "((devtype='Array' | devtype='UnifiedArray') & parttype='Port' & portwwn='v_portwwn')";
    public static final String ARRAY_PORT_FILTER_2 = "((devtype='Array' | devtype='UnifiedArray') & parttype='Port' & name='Availability' & portwwn='v_portwwn')";
    public static final String ALL_ARRAY_PORTS_FILTER = "(devtype='Array' | devtype='UnifiedArray') & parttype = 'Port' & device='v_device'";
    public static final String LUNS_OF_STORAGE_GROUP_FILTER = "datagrp='VNXBlock-%' & parttype='LUN' & sgname='v_sgname'";
    public static final String HOSTS_OF_STORAGE_GROUP_FILTER = "datagrp='VNXBlock-%' & parttype='Host' & sgname='v_sgname'";
    public static final String STORAGE_GROUPS_FILTER = "datagrp='VNXBlock-%' & device='v_device' & parttype='LUN'";
    public static final String ARRAY_MASKED_MAPPED_LUNS_FILTER = "(devtype='Array' | devtype='UnifiedArray') & parttype='LUN' & ismasked='1' & ismapped='1' & device='v_device'";
    public static final String ARRAY_MASKED_MAPPED_LUN_FILTER = "(devtype='Array' | devtype='UnifiedArray') & parttype='LUN' & ismasked='1' & ismapped='1' & partsn='v_partsn'";
    public static final String ARRAY_AND_VIRTUAL_STORAGE_LUN_FILTER = "((devtype='Array' | devtype='UnifiedArray') & parttype='LUN' & ismasked='1' & ismapped='1' & partsn='v_partsn') | "
            + "(devtype='VirtualStorage' & parttype='VirtualVolume'  & ismasked='1' & ismapped='1' & (partsn='v_partsn' | lunwwn='v_lunwwn' ) & name='Availability')";
    public static final String ARRAY_LUN_FILTER = "((devtype='Array' | devtype='UnifiedArray') & parttype='LUN' & partsn = 'v_partsn' & name='Capacity')";
    public static final String ARRAY_FE_PORT_VMAX_FILTER = "parttype='Port' & devtype='Array' & portwwn='v_portwwn' & partgrp='Front-End'";
    public static final String SYMM_ARRAY_FE_FILTER = "device='v_device' & partgrp='Front-End'";
    public static final String VIRTUAL_STORAGE_VIRTUAL_VOLUME_FILTER = "(devtype='VirtualStorage' & parttype='VirtualVolume' & (partsn='v_partsn' | lunwwn='v_lunwwn') & name='Availability')";
    public static final String TARGET_LUN_FILTER = ARRAY_LUN_FILTER
            + SRMViprConstants.PIPE + VIRTUAL_STORAGE_VIRTUAL_VOLUME_FILTER;

    public static final String ZONESET_FILTER = "pswwn='v_pswwn' & parttype='ZoneSet'";
    public static final String ZONEDETAILS_FILTER = "parttype='Zone' & zsetname='v_zsetname' & pswwn='v_pswwn'";
    public static final String CISCO_ZONE_FILTER = "parttype='Zone' & zsetname='v_zsetname' & pswwn='v_pswwn' & zname='v_zname'";
    public static final String ZONE_MEMBER_FILTER = "parttype='ZoneMember' & zsetname='v_zsetname' & pswwn='v_pswwn' & zname='v_zname'";
    public static final String ZONE_MEMBER_ID_FILTER = "zmemid='v_zmemid'";
    public static final String ZONE_AND_ZONE_MEMBER_FILTER = "zname='v_zname' & "
            + ZONE_MEMBER_ID_FILTER;
    public static final String ZONE_AND_ZONE_MEMBER_FILTER_1 = "(parttype='ZoneMember' | parttype='Zone') & pswwn='v_pswwn'";
    public static final String FABRIC_FILTER_1 = "pswwn='v_pswwn'";
    public static final String FABRIC_FILTER_2 = "(parttype='Fabric' | parttype='VSAN') & part='v_part'";
    public static final String FABRIC_FILTER_3 = "(parttype='Fabric' | parttype='VSAN') & partwwn='v_partwwn'";
    public static final String FABRIC_FILTER_4 = "devtype='FabricSwitch' & parttype='Fabric' & lswwn='v_lswwn'";
    public static final String FABRIC_FILTER_5 = "devtype='FabricSwitch' & parttype='Fabric' & partwwn='v_partwwn'";
    public static final String VSAN_FILTER = "parttype='VSAN' & partwwn='v_partwwn'";

    public static final String SWITCH_PORT_FILTER_1 = "devtype='FabricSwitch' & parttype='Port' &  portwwn='v_portwwn'";
    public static final String SWITCH_PORT_FILTER_2 = "devtype='FabricSwitch' & parttype='Port' & portwwn='v_portwwn' & device='v_device'";
    public static final String SWITCH_PORT_FILTER_3 = "devtype='FabricSwitch' & parttype='Port' & partwwn='v_partwwn'";

    public static final String SWITCH_FILTER_1 = "devtype='FabricSwitch' & device='v_device'";
    public static final String SWITCH_FILTER_2 = "devtype='FabricSwitch' & device ='v_device' & name='Availability'";
    public static final String ALL_SWITCH_PORTS_FILTER = "devtype='FabricSwitch' & parttype='Port' & device='v_device'";
    public static final String LOGICAL_SWITCH_PORTS_FILTER = "devtype='FabricSwitch' & parttype='Port' & lswwn='v_lswwn'";
    public static final String BROCADE_SWITCH_FILTER = "devtype='FabricSwitch' & pswwn='v_pswwn'";

    public static final String HOST_MULTIPATH_SW_FILTER_1 = "device='v_device' & name='Availability'";
    public static final String HOST_MULTIPATH_SW_FILTER_2 = "devtype='Host' & device='v_device' & parttype='Multipath'";
    public static final String HOST_MULTIPATH_SW_FILTER_3 = "devtype='Hypervisor' & device='v_device' & parttype='Multipath' & !(part='PowerPath')";
    public static final String HOST_MULTIPATH_SW_FILTER_4 = "devtype='Hypervisor' & device='v_device' & parttype='Multipath' & source='RSC-ESXPowerPathPerformance'";

    public static final String AIX_PHYSICAL_HBA_FILTER = "(devtype='Host' & device='v_device' & hbatype='Physical' & parttype='Port' & name='Availability')";
    public static final String AIX_VIO_SERVER_FILTER = "(devtype='Host' & clustid='v_clustid' & lpartype='Server')";
    public static final String HBA_PORT_FILTER_1 = "((devtype='Host' | devtype='Hypervisor') & parttype='Port' & partsn='v_partsn')";
    public static final String ALL_HOST_PORTS_FILTER = "(devtype='Host' | devtype='Hypervisor') & parttype='Port' & device='v_device'";
    public static final String ALL_HOST_FILTER = "(devtype='Host' | devtype='Hypervisor') & device='v_device'";
    public static final String HBA_DETAILS_FILTER = "parttype='Port' & device='v_device' & ((devtype='Host' & part='v_part') | (devtype='Hypervisor' & fcbus='v_fcbus'))";
    public static final String HOST_HBA_PORT_FILTER_1 = "((devtype='Host' | devtype='Hypervisor') & parttype='Port' & partsn='v_partsn' & device='v_device')";
    public static final String HOST_HBA_PORT_FILTER_2 = "((devtype='Host' | devtype='Hypervisor') & parttype='Port' & partsn='v_partsn')";
    public static final String VPLEX_FRONT_END_PORT_FILTER = "(devtype='VirtualStorage' & parttype='Port' & iftype='front-end' & portwwn='v_portwwn')";
    public static final String VPLEX_BACK_END_PORT_FILTER = "(devtype='VirtualStorage' & parttype='Port' & iftype='back-end' & portwwn='v_portwwn')";
    public static final String VIRTUAL_STORAGE_PORT_FILTER = "(devtype='VirtualStorage' & parttype='Port' & serialnb='v_serialnb')";
    public static final String VIRTUAL_STORAGE_FE_ADAPTER_FILTER = "(devtype='VirtualStorage' & parttype='Port' & portwwn='v_portwwn')";

    public static final String VIRTUALMACHINE_NPIV_PORT_FILTER = "(devtype='VirtualMachine' & parttype='Interface' & npivport='%v_npivport%')";
    public static final String SVC_FRONT_END_PORT_FILTER = "(source='SVC%' & devtype='VirtualStorage' & parttype='Port' & iftype='%front-end%' & portwwn='v_portwwn')";
    public static final String SVC_BACK_END_PORT_FILTER = "(source='SVC%' & devtype='VirtualStorage' & parttype='Port' & iftype='%back-end%' & portwwn='v_portwwn')";

    public static final String INITIATOR_PORT_FILTER = HBA_PORT_FILTER_1
            + SRMViprConstants.PIPE + VPLEX_BACK_END_PORT_FILTER
            + SRMViprConstants.PIPE + VIRTUALMACHINE_NPIV_PORT_FILTER
            + SRMViprConstants.PIPE + SVC_BACK_END_PORT_FILTER;
    public static final String TARGET_PORT_FILTER = ARRAY_PORT_FILTER_1
            + SRMViprConstants.PIPE + VPLEX_FRONT_END_PORT_FILTER
            + SRMViprConstants.PIPE + SVC_FRONT_END_PORT_FILTER;
    public static final String SWITCH_PORT_FILTER_4 = INITIATOR_PORT_FILTER
            + SRMViprConstants.PIPE + TARGET_PORT_FILTER
            + SRMViprConstants.PIPE + "(" + SWITCH_PORT_FILTER_3 + ")";

    public static final String REMOTE_HOSTDISKS_PORT_FILTER = "(devtype='Host' & parttype='Disk' & isremote='true' & portwwn='v_portwwn')";
    public static final String REMOTE_ESXDISKS_PORT_FILTER = "(devtype='Hypervisor' & parttype='DiskPath' & portwwn='v_portwwn')";
    public static final String ALL_PHYSICAL_OR_ESX_HOST_REMOTEDISK_FILTER = REMOTE_HOSTDISKS_PORT_FILTER
            + SRMViprConstants.PIPE + REMOTE_ESXDISKS_PORT_FILTER;
    public static final String HOST_DISKS_FOR_STORAGE_PORT_FILTER = "(devtype='Host' & parttype='Disk' & isremote='true' & tgtwwn='v_tgtwwn')";
    public static final String ESX_DISKS_FOR_STORAGE_PORT_FILTER = "(devtype='Hypervisor' & parttype='DiskPath' & tgtwwn='v_tgtwwn')";

    public static final String REMOTE_HOSTDISK_FILTER = "((devtype='Host' | devtype='Hypervisor') & parttype='Disk' & isremote='true' & partsn='v_partsn')";
    public static final String LUNS_CONNECTED_TO_HOST_FILTER = "(devtype='Host' | devtype='Hypervisor')& parttype='Disk' & isremote='true'& device='v_device'";
    public static final String LUNS_CONNECTED_TO_ESX_FILTER = "devtype='Hypervisor' & parttype='DiskPath' & device='v_device'";
    public static final String SPECIFIC_HOST_DISK_FILTER = "(device='v_device' & partsn='v_partsn' & "
            + "((devtype='Host' & parttype='Disk' & isremote='true') | (devtype='Hypervisor' & parttype='DiskPath')))";
    public static final String ALL_HOST_REMOTE_DISKS_FILTER_1 = "((devtype='Host' & parttype='Disk' & isremote='true' & device='v_device') | (devtype='Hypervisor' & parttype='DiskPath' & device='v_device') )";
    public static final String ALL_HOST_REMOTE_DISKS_FILTER_2 = "device='v_device' & ((devtype='Host' & parttype='Disk' & isremote='true') | (devtype='Hypervisor' & parttype='DiskPath'))";
    public static final String PASSIVE_HOSTDISK_FILTER = "(devtype='PassiveHost' & parttype='Disk' & hostwwn='v_hostwwn')";
    public static final String ALL_HOST_REMOTE_DISKS_FILTER_3 = REMOTE_HOSTDISKS_PORT_FILTER
            + SRMViprConstants.PIPE + PASSIVE_HOSTDISK_FILTER
            + SRMViprConstants.PIPE + REMOTE_ESXDISKS_PORT_FILTER
            + SRMViprConstants.PIPE
            + "(devtype='VirtualMachine' & parttype='Disk' & npivport='%v_npivport%')"
            + SRMViprConstants.PIPE
            + "(devtype='VirtualStorage' & name='VirtualDiskBackendPath' & portwwn='v_portwwn')";
    public static final String ALL_MULTIPLE_PATHS_FOR_HOST_FILTER = "devtype='Host' & parttype='Path' & !(inwwn='N/A' & tgtwwn='N/A') & pathstat='alive' & device='v_device' & name='Availability'";
    public static final String VPLEX_VIRTUAL_DISK_FILTER_1 = "(devtype='VirtualStorage' & parttype='VirtualDisk' & partsn='v_partsn')";
    public static final String VPLEX_VIRTUAL_DISK_FILTER_2 = "(devtype='VirtualStorage' & name='VirtualDiskBackendPath' & serialnb='v_serialnb')";
    public static final String VPLEX_VIRTUAL_DISK_FILTER_3 = "(devtype='VirtualStorage' & parttype='VirtualDisk' & serialnb='v_serialnb')";
    public static final String VPLEX_DEVICE_FILTER = "(devtype='VirtualStorage' & serialnb='v_serialnb' & name='Availability')";
    public static final String ALL_VPLEX_CLUSTER_LOGGING_FILTER = "(devtype='VirtualStorage' & serialnb='v_serialnb' & isloggin='1')";
    public static final String VPLEX_CLUSTER_1 = "(devtype='VirtualStorage' & serialnb='v_serialnb')";

    public static final String PASSIVE_HOSTDISK_PART_FILTER = "(devtype='PassiveHost' & parttype='Disk' & partsn='v_partsn')";
    public static final String INITIATOR_DISK_FILTER = REMOTE_HOSTDISK_FILTER
            + SRMViprConstants.PIPE + VPLEX_VIRTUAL_DISK_FILTER_1
            + SRMViprConstants.PIPE + PASSIVE_HOSTDISK_PART_FILTER;

    public static final String VIRTUALMACHINE_FILTER = "devtype='VirtualMachine' & device='v_device'";
    public static final String AIX_VIO_HOSTS_FILTER = "(devtype='Host' & device='v_device' & name='Availability' & (lpartype='Client' | lpartype='Server'))";
    public static final String VIPR_VOLUME_FILTER = "devtype='Array' & device='v_device' & parttype='LUN' & name='Availability'";
    public static final String VIPR_FILE_SYSTEM_FILTER = "devtype='Array' & device='v_device' & parttype='FileSystem' & name='Availability'";
    /**
     * Filter used for getting the scope/top-level objects.
     */
    public static final String ALL_FABRIC_SCOPE_FILTER = "devtype='FabricSwitch' & (parttype='Fabric' | parttype='VSAN')";
    public static final String ALL_PHYSICAL_HOSTS_SCOPE_FILTER = "(devtype='Host' | devtype='Hypervisor') & !(source='ViPR')";
    public static final String ALL_PHYSICAL_HOSTS_EXCLUDING_VIOS_SCOPE_FILTER = "((devtype='Host' & !(lpartype='Server')) | devtype='Hypervisor') & name='Availability'";
    public static final String ALL_ARRAY_SCOPE_FILTER = "(devtype='Array' | devtype='UnifiedArray')";
    public static final String ALL_EMC_ARRAYS_SCOPE_FILTER = "((devtype='Array' | devtype='UnifiedArray') & vendor='%EMC%') & !(source='ViPR')";
    public static final String ALL_SYMMETRIX_ARRAY_SCOPE_FILTER = "devtype='Array' & datagrp='VMAX-ARRAYS'";
    public static final String ALL_CLARIION_ARRAY_SCOPE_FILTER = "devtype='Array' & datagrp='VNXBlock-%'";
    public static final String ALL_HOSTS_SCOPE_FILTER = "devtype='Host' | devtype='Hypervisor'";
    public static final String ALL_HOSTS_EXCLUDING_VIOS_SCOPE_FILTER = "((devtype='Host' & !(lpartype='Server')) | devtype='Hypervisor' | devtype='VirtualMachine') & name='Availability'";
    public static final String ALL_SWITCH_SCOPE_FILTER = "devtype='FabricSwitch' & parttype='Port'";
    public static final String ALL_VPLEX_CLUSTER_SCOPE_FILTER = "(devtype='VirtualStorage' & source='VPLEX%')";
    public static final String ALL_SVC_CLUSTER_SCOPE_FILTER = "(devtype='VirtualStorage' & source='SVC%')";
    public static final String ALL_VIPR_HOSTS_SCOPE = "devtype='Array' & parttype ='Tenant'";
    public static final String ALL_HOSTS_EXCLUDING_AIX_SCOPE_FILTER = "(devtype='Host' | devtype='Hypervisor')"
            + " & !(lpartype='Server') & !(lpartype='Client')";
    public static final String ALL_AIX_HOSTS_SCOPE_FILTER = "(devtype='Host' & (lpartype='Server' | lpartype='Client') & name='Availability')";
    public static final String ALL_HOSTS_EXCLUDING_WINDOWS_SCOPE_FILTER = "(devtype='Host' | devtype='Hypervisor' | devtype='VirtualMachine') & !(devdesc='%Windows%')";
    public static final String ALL_HOSTS_EXCLUDING_AIX_AND_WINDOWS_SCOPE_FILTER = "((devtype='Host' | devtype='Hypervisor' | devtype='VirtualMachine') & !(lpartype='Server') & !(lpartype='Client') & !(devdesc='%Windows%'))";
    /**
     * Filters used for change event processing
     */
    public static final String ALL_DISKS_FOR_STORAGE_PORT_FILTER = HOST_DISKS_FOR_STORAGE_PORT_FILTER
            + SRMViprConstants.PIPE + ESX_DISKS_FOR_STORAGE_PORT_FILTER;
    public static final String ALL_PORTS_FILTER = ARRAY_PORT_FILTER_1
            + SRMViprConstants.PIPE + SWITCH_PORT_FILTER_3
            + SRMViprConstants.PIPE + HBA_PORT_FILTER_1;
    public static final String HOST_AND_ARRAY_PORTS_FILTER = ARRAY_PORT_FILTER_1
            + SRMViprConstants.PIPE + HBA_PORT_FILTER_1;

    public static final String VPLEX_SOE_FILTER = "(devtype='VirtualStorage' & name='Availability' & serialnb='v_serialnb')";
    public static final String ALL_ESM_DEVICES = "devtype='Host' | devtype='Hypervisor' | devtype='FabricSwitch' | devtype='Array'";

}
