#!/bin/sh
# Save the command arguments
ARGC=$#
[ $ARGC -eq 0 ] && {
    echo "usage: sanzoning_test [addvolumezonecheck|sanzonereuse|cleanup]*"
    exit 2;
}
ARGV=$*
CWD=$(pwd)
export PATH=$CWD:$CWD/..:$PATH
echo "PATH: " $PATH

# Configuration file to be used  
source conf/sanity.conf

SAN_VA=san-va
SAN_NETWORK_losam82=$SAN_VA/$SAN_ZONE_losam82
COS_NAME=""
COS_VMAXBLOCK_THIN=cosvmaxb_thin
COS2_VMAXBLOCK_THIN=cosvmaxb_thin2
COS_VNXBLOCK_THIN=cosvnxb_thin
COS2_VNXBLOCK_THIN=cosvnxb_thin2
BLK_SIZE=1073741824

ZONE_NAME=""
ZONE_ADDR=""
ZONE1_NAME_VMAX=VMAXsanityzonetest1
ZONE2_NAME_VMAX=VMAXsanityzonetest2
ZONE3_NAME_VMAX=VMAXsanityzonetest3
ZONE4_NAME_VMAX=VMAXsanityzonetest4
ZONE1_NAME_VNX=VNXsanityzonetest1
ZONE2_NAME_VNX=VNXsanityzonetest2
ZONE3_NAME_VNX=VNXsanityzonetest3
ZONE4_NAME_VNX=VNXsanityzonetest4

tenant=${tenant:-standalone}
[ "$tenant" ] || {
    tenant="standalone"
}
[ "$project" ] || {
    project="sanity"
}
[ "$macaddr" ] || {
    macaddr=`/sbin/ifconfig eth0 | /usr/bin/awk '/HWaddr/ { print $5 }'`
}
[ "$hostseed" ] || {
    hostseed=`echo ${macaddr} | awk -F: '{print $5$6}'`
    hostbase=host${hostseed}
    echo "hostbase $hostbase"
}

VNX_VOLUME1_NAME=VnxSanityVol1-${HOST_NAME}-${hostseed}
VNX_VOLUME2_NAME=VnxSanityVol2-${HOST_NAME}-${hostseed}
vnx_vol1=$project/$VNX_VOLUME1_NAME
vnx_vol2=$project/$VNX_VOLUME2_NAME
VNX_EXPORT_NAME=VnxExp${hostseed}
vnx_exp=$project/$VNX_EXPORT_NAME

VMAX_VOLUME1_NAME=VmaxSanityVol1-${HOST_NAME}-${hostseed}
VMAX_VOLUME2_NAME=VmaxSanityVol2-${HOST_NAME}-${hostseed}
vmax_vol1=$project/$VMAX_VOLUME1_NAME
vmax_vol2=$project/$VMAX_VOLUME2_NAME
VMAX_EXPORT_NAME=VmaxExp${hostseed}
vmax_exp=$project/$VMAX_EXPORT_NAME

run() {
    cmd=$*
    date
    echo $cmd
    $cmd 2>&1
    status=$?
    date
    if [ $status -ne 0 ]; then
        echo $cmd failed
        date
        exit $status
    fi
}

# $1 = failure message
fail() {
    [ "$1" = "" ] && {
        $1="Failed- please see previous messages"
    }
    echo fail: $1
    date
    exit 2
}

setup_switches() {
    # Do once
    nsys=`networksystem list | wc -l`
    [ "$nsys" -gt 0 ] && return;

    #Discover the Brocade SAN switch.
    echo "Discovering brocade ..."
    networksystem create $BROCADE_NETWORK brocade --smisip $BROCADE_IP --smisport 5988 --smisuser $BROCADE_USER --smispw $BROCADE_PW --smisssl false
    sleep 30

    # Do once - Discover Cisco MDS simulator switch
#    echo "Discover Cisco MDS simulator switch"
#    networksystem create CiscoMdsSimulator  mds --devip $SIMULATOR_CISCO_MDS --devport 22 --username $SIMULATOR_CISCO_MDS_USER --password $SIMULATOR_CISCO_MDS_PW
}

setup_virtual_arrays() {
    existingVAs=$(neighborhood list | awk ' { print $1; }')
    # Ensure the virtual arrays are present
    $(echo $existingVAs | grep -q $SAN_VA ) || {
        neighborhood create $SAN_VA
    }
    existingVAs=$(neighborhood list | awk ' { print $1; }')
    echo "Virtual arrays: $existingVAs"
}

setup_hosts() {
    # Create host and initiators
    echo "Creating hosts and initiators"
    hosts create $HOST_NAME $tenant Other $HOST --port 22
    initiator create $HOST_NAME FC $ZONE1_ADDR1 --node $INITIATOR1_NODE
}

delete_initiators() {
    # Delete initiators
    initiator delete $HOST_NAME/$ZONE1_ADDR1
    initiator delete $HOST_NAME/$ZONE2_ADDR1
    initiator delete $HOST_NAME/$ZONE3_ADDR1
    initiator delete $HOST_NAME/$ZONE4_ADDR1
}

delete_hosts() {
    # Delete host
    hosts delete $HOST_NAME
}

delete_virtual_pool() {
    cos delete $COS_NAME block
}

add_initiator_to_host() {
    # Add host initiators to network vplex154nbr2
    transportzone add $SAN_NETWORK_losam82 $ZONE2_ADDR1
    initiator create $HOST_NAME FC $ZONE2_ADDR1 --node $INITIATOR2_NODE
}

setup_initiators() {
    # Add host initiators to network vplex154nbr2
    transportzone add $SAN_NETWORK_losam82 $ZONE1_ADDR1
}

setup_arrayports() {
    storageport update $VNXB_NATIVEGUID FC --addvarrays ${SAN_VA}
    storageport update $VMAX_NATIVEGUID FC --addvarrays ${SAN_VA}
}

add_networks_to_varrays() {
    # Assign networks to virtual array san-va
    transportzone assign $SAN_ZONE_losam82 $SAN_VA
}

setup_zones() {
    echo "Creating zone " $ZONE_NAME
    zone create $BROCADE_NETWORK --fabricid $FABRIC_ID --zones $ZONE_NAME,$ZONE_ADDR
    zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE_NAME --exclude_members false
    zone activate $BROCADE_NETWORK --fabricid $FABRIC_ID
}

cleanup_zones() {
    echo "Deleting zone " $ZONE_NAME
    zone delete $BROCADE_NETWORK --fabricid $FABRIC_ID --zones $ZONE_NAME,$ZONE_ADDR
    zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE_NAME --exclude_members false
    zone activate $BROCADE_NETWORK --fabricid $FABRIC_ID
}

sanzoning_setup() {
    # Check if the environment has already been setup.
    # This is detected because array ports are assigned to each Varray.
    setup_switches
    setup_hosts
    CC1COUNT=$(neighborhood storageports san-va | grep -c CLAR )
    CC2COUNT=$(neighborhood storageports san-va | grep -c SYMM )
    echo array counts: $CC1COUNT
    if [ $CC1COUNT -gt 0 -a $CC2COUNT -gt 0 ]; then
        echo "Skipping sanzoning setup; varrays are already populated."
        return;
    fi

    setup_virtual_arrays
    setup_initiators
    setup_arrayports
    add_networks_to_varrays
}

setup_virtual_pools() {
    #Create Virtual Pool for VMAX
    cos create block $COS_VMAXBLOCK_THIN                                \
                         --description 'VMAX thin storage' true      \
                         --protocols FC               \
                         --numpaths 1 \
                         --maxpaths 2 \
                         --max_snapshots 10 \
                         --system_type vmax \
                         --provisionType 'Thin' \
                         --expandable true \
                         --neighborhoods $SAN_VA

    #Create Virtual Pool for VNX
    cos create block $COS_VNXBLOCK_THIN                            \
                       --description 'VNX thin storage' true         \
                       --protocols FC                    \
                       --numpaths 1 \
                       --maxpaths 2 \
                       --max_snapshots 10 \
                       --system_type vnxblock \
                       --provisionType 'Thin' \
                       --neighborhoods $SAN_VA
}

add_initiators_to_host() {
    # Add host initiators to network vplex154nbr2
    transportzone add $SAN_NETWORK_losam82 $ZONE3_ADDR1
    initiator create $HOST_NAME FC $ZONE3_ADDR1 --node $INITIATOR3_NODE
    transportzone add $SAN_NETWORK_losam82 $ZONE4_ADDR1
    initiator create $HOST_NAME FC $ZONE4_ADDR1 --node $INITIATOR4_NODE
}

add_path_to_vpool_vmax() {
    add_initiators_to_host
    #Create Virtual Pool for VMAX
    cos create block $COS2_VMAXBLOCK_THIN                                \
                         --description 'VMAX thin storage' true      \
                         --protocols FC               \
                         --numpaths 1 \
                         --maxpaths 4 \
                         --max_snapshots 10 \
                         --system_type vmax \
                         --provisionType 'Thin' \
                         --expandable true \
                         --neighborhoods $SAN_VA
    volume change_cos $vmax_vol1 $COS2_VMAXBLOCK_THIN 
}

add_path_to_vpool_vnx() {
    add_initiators_to_host
    #Create Virtual Pool for VNX
    cos create block $COS2_VNXBLOCK_THIN                                \
                         --description 'VNX thin storage' true      \
                         --protocols FC               \
                         --numpaths 1 \
                         --maxpaths 4 \
                         --max_snapshots 10 \
                         --system_type vnxblock \
                         --provisionType 'Thin' \
                         --neighborhoods $SAN_VA
    volume change_cos $vnx_vol1 $COS2_VNXBLOCK_THIN
}

cleanup() {
    echo "Cleaning up export groups on VNX"
    run export_group delete $vnx_exp
    echo "Cleaning up volumes on VNX"
    run volume delete $project/$VNX_VOLUME1_NAME --wait
    run volume delete $project/$VNX_VOLUME2_NAME --wait

    echo "Cleaning up export groups on VMAX"
    run export_group delete $vmax_exp
    echo "Cleaning up volumes on VMAX"
    run volume delete $project/$VMAX_VOLUME1_NAME --wait
    run volume delete $project/$VMAX_VOLUME2_NAME --wait

    echo "Cleaning up zones created"
    ZONE_NAME=$ZONE1_NAME_VNX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE2_NAME_VNX
    ZONE_ADDR=$ZONE2_ADDR1+$ZONE2_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE3_NAME_VNX
    ZONE_ADDR=$ZONE3_ADDR1+$ZONE3_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE4_NAME_VNX
    ZONE_ADDR=$ZONE4_ADDR1+$ZONE4_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE1_NAME_VMAX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VMAX
    cleanup_zones
    ZONE_NAME=$ZONE2_NAME_VMAX
    ZONE_ADDR=$ZONE2_ADDR1+$ZONE2_ADDR2_VMAX
    cleanup_zones
    ZONE_NAME=$ZONE3_NAME_VMAX
    ZONE_ADDR=$ZONE3_ADDR1+$ZONE3_ADDR2_VMAX
    cleanup_zones
    ZONE_NAME=$ZONE4_NAME_VMAX
    ZONE_ADDR=$ZONE4_ADDR1+$ZONE4_ADDR2_VMAX
    cleanup_zones
}

addvolumezonecheck() {
    echo "Testing zoning check while adding volumes to export on VNX"
    echo "Creating volume1"
    run volume create $VNX_VOLUME1_NAME $project $SAN_VA $COS_VNXBLOCK_THIN $BLK_SIZE --thinVolume true
    echo "Exporting volume "
    echo "Creating export group"
    run export_group create --type Host $project $VNX_EXPORT_NAME $SAN_VA --volspec "$vnx_vol1+1" --hosts $HOST_NAME
    run export_group show $vnx_exp
    echo "Creating volume2"
    run volume create $VNX_VOLUME2_NAME $project $SAN_VA $COS_VNXBLOCK_THIN $BLK_SIZE --thinVolume true
    START=$(date +%s.%N)
    echo "Updating export group -add volume"
    run export_group update $vnx_exp --addVolspec $vnx_vol2+2
    END=$(date +%s.%N)
    DIFF=$(echo "$END - $START" | bc )
    echo "Time taken to run addvolumezonecheck Test on VNX is " $DIFF " seconds"
    run export_group show $vnx_exp
    echo "Deleting export group"
    run export_group delete $vnx_exp
    echo "Deleting volumes "
    run volume delete $project/$VNX_VOLUME1_NAME --wait
    run volume delete $project/$VNX_VOLUME2_NAME --wait

    echo "Testing zoning check while adding volumes to export on VMAX"
    echo "Creating volume1"
    run volume create $VMAX_VOLUME1_NAME $project $SAN_VA $COS_VMAXBLOCK_THIN $BLK_SIZE --thinVolume true
    echo "Exporting volume "
    echo "Creating export group"
    run export_group create --type Host $project $VMAX_EXPORT_NAME $SAN_VA --volspec "$vmax_vol1+3" --hosts $HOST_NAME
    run export_group show $vmax_exp
    echo "Creating volume2"
    run volume create $VMAX_VOLUME2_NAME $project $SAN_VA $COS_VMAXBLOCK_THIN $BLK_SIZE --thinVolume true
    START=$(date +%s.%N)
    echo "Updating export group -add volume"
    run export_group update $vmax_exp --addVolspec $vmax_vol2+4
    END=$(date +%s.%N)
    DIFF=$(echo "$END - $START" | bc )
    echo "Time taken to run addvolumezonecheck Test on VMAX is " $DIFF " seconds"
    run export_group show $vmax_exp
    echo "Deleting export group"
    run export_group delete $vmax_exp
    echo "Deleting volumes "
    run volume delete $project/$VMAX_VOLUME1_NAME --wait
    run volume delete $project/$VMAX_VOLUME2_NAME --wait
}

compare_ports_zones() {
    if [ -f "/tmp/_prezone" ]
    then  
        while read LINE  
        do
            if [ $LINE -eq 1 ]
            then
                echo "Passed!! Picked the prezoned port"
            else
                echo "Failed!! Did not pick prezoned port"
            fi
        done <"/tmp/_prezone"
        rm -f "/tmp/_prezone"
    fi
    if [ -f "/tmp/_fczoneref" ]
    then
        while read LINE  
        do
            if [ $LINE -eq 1 ]
            then
                echo "Passed!! Deleted FCZoneReferences"
            else
                echo "Failed!! Did not delete FCZoneReferences"
            fi
        done <"/tmp/_fczoneref"
        rm -f "/tmp/_fczoneref"
    fi
    if [ -f "/tmp/_portsused" ]
    then
        while read LINE  
        do
            if [ $LINE -eq 1 ]
            then
                echo "All zoned ports are used"
            else
                echo "All zoned ports are not used"
            fi
        done <"/tmp/_portsused"
        rm -f "/tmp/_portsused"
    fi
}

sanzonereuse() {
    portzonedmatchvpoolpath
    portzonedmorethanvpoolpath
}

portzonedmatchvpoolpath() {
#   Zone Reuse - Ports zoned match vpool paths requirements - VNX
#   Create export group test
    ZONE_NAME=$ZONE1_NAME_VNX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VNX
    setup_zones
    echo "Testing reusing zones on VNX"
    echo "Creating volume "
    run volume create $VNX_VOLUME1_NAME $project $SAN_VA $COS_VNXBLOCK_THIN $BLK_SIZE --thinVolume true

#   Create export group test
    echo "Exporting volume "
    echo "Creating export group"
    run export_group create --type Host $project $VNX_EXPORT_NAME $SAN_VA --volspec "$vnx_vol1+1" --hosts $HOST_NAME
    run export_group show $vnx_exp    
    run dumpexport $VNX_EXPORT_NAME $ZONE1_ADDR2_VNX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE1_ADDR1 $ZONE1_ADDR2_VNX | grep -c $ZONE1_NAME_VNX )
    echo "Matched " $checkzone " zone with name " $ZONE1_NAME_VNX " and endpoints " $ZONE1_ADDR1 " and " $ZONE1_ADDR2_VNX

#   Add initiator to host test
    ZONE_NAME=$ZONE2_NAME_VNX
    ZONE_ADDR=$ZONE2_ADDR1+$ZONE2_ADDR2_VNX
    setup_zones
    echo "Adding initiator to host"
    add_initiator_to_host
    run dumpexport $VNX_EXPORT_NAME $ZONE1_ADDR2_VNX,$ZONE2_ADDR2_VNX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE2_ADDR1 $ZONE2_ADDR2_VNX | grep -c $ZONE2_NAME_VNX )
    echo "Matched " $checkzone " zone with name " $ZONE2_NAME_VNX " and endpoints " $ZONE2_ADDR1 " and " $ZONE2_ADDR2_VNX

#   Change Vpool to add paths test
    ZONE_NAME=$ZONE3_NAME_VNX
    ZONE_ADDR=$ZONE3_ADDR1+$ZONE3_ADDR2_VNX
    setup_zones
    ZONE_NAME=$ZONE4_NAME_VNX
    ZONE_ADDR=$ZONE4_ADDR1+$ZONE4_ADDR2_VNX
    setup_zones
    echo "Change vpool to add paths"
    add_path_to_vpool_vnx
    run dumpexport $VNX_EXPORT_NAME $ZONE1_ADDR2_VNX,$ZONE2_ADDR2_VNX,$ZONE3_ADDR2_VNX,$ZONE4_ADDR2_VNX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE3_ADDR1 $ZONE3_ADDR2_VNX | grep -c $ZONE3_NAME_VNX )
    echo "Matched " $checkzone " zone with name " $ZONE3_NAME_VNX " and endpoints " $ZONE3_ADDR1 " and " $ZONE3_ADDR2_VNX
    checkzone=$( networksystem zonereferences $ZONE4_ADDR1 $ZONE4_ADDR2_VNX | grep -c $ZONE4_NAME_VNX )
    echo "Matched " $checkzone " zone with name " $ZONE4_NAME_VNX " and endpoints " $ZONE4_ADDR1 " and " $ZONE4_ADDR2_VNX

#   Delete export group test 
    echo "Deleting export group"
    run export_group delete $vnx_exp
    zone1count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE1_NAME_VNX --exclude_members false | grep -c "*sanityzonetest*" )
    zone2count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE2_NAME_VNX --exclude_members false | grep -c "*sanityzonetest*" )
    zone3count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE3_NAME_VNX --exclude_members false | grep -c "*sanityzonetest*" )
    zone4count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE4_NAME_VNX --exclude_members false | grep -c "*sanityzonetest*" )
    if [ $zone1count -eq 0 -a $zone2count -eq 0 -a $zone3count -eq 0 -a $zone4count -eq 0 ]; then
        echo "zone(s) don't exist in ViPR after deleting export group "
    else
        echo "zone(s) exist in ViPR after deleting export group "
    fi
    run dumpexport $VNX_EXPORT_NAME $ZONE1_ADDR2_VNX checkfczone
    compare_ports_zones

#   Delete volume test
    echo "Deleting volume "
    run volume delete $project/$VNX_VOLUME1_NAME --wait

#   Clean up Zones
    ZONE_NAME=$ZONE1_NAME_VNX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE2_NAME_VNX
    ZONE_ADDR=$ZONE2_ADDR1+$ZONE2_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE3_NAME_VNX
    ZONE_ADDR=$ZONE3_ADDR1+$ZONE3_ADDR2_VNX
    cleanup_zones
    ZONE_NAME=$ZONE4_NAME_VNX
    ZONE_ADDR=$ZONE4_ADDR1+$ZONE4_ADDR2_VNX
    cleanup_zones

#   Clean up hosts and initiators
    delete_initiators
    delete_hosts

#   Set up hosts and initiators
    setup_hosts
    setup_initiators

#   Zone Reuse - Ports zoned match vpool paths requirements - VMAX
#   Create export group test
    ZONE_NAME=$ZONE1_NAME_VMAX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VMAX
    setup_zones
    echo "Testing reusing zones on VMAX"
    echo "Creating volume "
    run volume create $VMAX_VOLUME1_NAME $project $SAN_VA $COS_VMAXBLOCK_THIN $BLK_SIZE --thinVolume true

#   Create export group test
    echo "Exporting volume "
    echo "Creating export group"
    run export_group create --type Host $project $VMAX_EXPORT_NAME $SAN_VA --volspec "$vmax_vol1+1" --hosts $HOST_NAME    
    run export_group show $vmax_exp
    run dumpexport $VMAX_EXPORT_NAME $ZONE1_ADDR2_VMAX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE1_ADDR1 $ZONE1_ADDR2_VMAX | grep -c $ZONE1_NAME_VMAX )
    echo "Matched " $checkzone " zone with name " $ZONE1_NAME_VMAX " and endpoints " $ZONE1_ADDR1 " and " $ZONE1_ADDR2_VMAX

#   Add initiator to host test
    ZONE_NAME=$ZONE2_NAME_VMAX
    ZONE_ADDR=$ZONE2_ADDR1+$ZONE2_ADDR2_VMAX
    setup_zones
    echo "Adding initiator to host"
    add_initiator_to_host
    run dumpexport $VMAX_EXPORT_NAME $ZONE1_ADDR2_VMAX,$ZONE2_ADDR2_VMAX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE2_ADDR1 $ZONE2_ADDR2_VMAX | grep -c $ZONE2_NAME_VMAX )
    echo "Matched " $checkzone " zone with name " $ZONE2_NAME_VMAX " and endpoints " $ZONE2_ADDR1 " and " $ZONE2_ADDR2_VMAX

#   Change Vpool to add paths test
    ZONE_NAME=$ZONE3_NAME_VMAX
    ZONE_ADDR=$ZONE3_ADDR1+$ZONE3_ADDR2_VMAX
    setup_zones
    ZONE_NAME=$ZONE4_NAME_VMAX
    ZONE_ADDR=$ZONE4_ADDR1+$ZONE4_ADDR2_VMAX
    setup_zones
    echo "Change vpool to add paths"
    add_path_to_vpool_vmax
    run dumpexport $VMAX_EXPORT_NAME $ZONE1_ADDR2_VMAX,$ZONE2_ADDR2_VMAX,$ZONE3_ADDR2_VMAX,$ZONE4_ADDR2_VMAX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE3_ADDR1 $ZONE3_ADDR2_VMAX | grep -c $ZONE3_NAME_VMAX )
    echo "Matched " $checkzone " zone with name " $ZONE3_NAME_VMAX " and endpoints " $ZONE3_ADDR1 " and " $ZONE3_ADDR2_VMAX
    checkzone=$( networksystem zonereferences $ZONE4_ADDR1 $ZONE4_ADDR2_VMAX | grep -c $ZONE4_NAME_VMAX )
    echo "Matched " $checkzone " zone with name " $ZONE4_NAME_VMAX " and endpoints " $ZONE4_ADDR1 " and " $ZONE4_ADDR2_VMAX
 
#   Delete export group test   
    echo "Deleting export group"
    run export_group delete $vmax_exp
    zone1count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE1_NAME_VMAX --exclude_members false | grep -c "*sanityzonetest*" )
    zone2count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE2_NAME_VMAX --exclude_members false | grep -c "*sanityzonetest*" )
    zone3count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE3_NAME_VMAX --exclude_members false | grep -c "*sanityzonetest*" )
    zone4count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE4_NAME_VMAX --exclude_members false | grep -c "*sanityzonetest*" )
    if [ $zone1count -eq 0 -a $zone2count -eq 0 -a $zone3count -eq 0 -a $zone4count -eq 0 ]; then
        echo "zone(s) don't exist in ViPR after deleting export group "
    else
        echo "zone(s) exist in ViPR after deleting export group "
    fi
    run dumpexport $VMAX_EXPORT_NAME $ZONE1_ADDR2_VMAX checkfczone
    compare_ports_zones

#   Delete volume test
    echo "Deleting volume "
    run volume delete $project/$VMAX_VOLUME1_NAME --wait

#   Clean up Zones
    ZONE_NAME=$ZONE1_NAME_VMAX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VMAX
    cleanup_zones
    ZONE_NAME=$ZONE2_NAME_VMAX
    ZONE_ADDR=$ZONE2_ADDR1+$ZONE2_ADDR2_VMAX
    cleanup_zones
    ZONE_NAME=$ZONE3_NAME_VMAX
    ZONE_ADDR=$ZONE3_ADDR1+$ZONE3_ADDR2_VMAX
    cleanup_zones
    ZONE_NAME=$ZONE4_NAME_VMAX
    ZONE_ADDR=$ZONE4_ADDR1+$ZONE4_ADDR2_VMAX
    cleanup_zones

#   Clean up hosts and initiators
    delete_initiators
    delete_hosts
}

portzonedmorethanvpoolpath() {
#   Delete virtual Pool
    COS_NAME=$COS_VNXBLOCK_THIN
    delete_virtual_pool
    COS_NAME=$COS2_VNXBLOCK_THIN
    delete_virtual_pool
    COS_NAME=$COS_VMAXBLOCK_THIN
    delete_virtual_pool
    COS_NAME=$COS2_VMAXBLOCK_THIN
    delete_virtual_pool

#   Create virtual pool
    setup_virtual_pools

#   Set up hosts and initiators
    setup_hosts
    setup_initiators

#   Zone Reuse - More than needed ports pre-zoned - VNX
#   Create export group test
    ZONE_NAME=$ZONE1_NAME_VNX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VNX+$ZONE2_ADDR2_VNX
    setup_zones
    echo "Testing reusing zones on VNX"
    echo "Creating volume "
    run volume create $VNX_VOLUME1_NAME $project $SAN_VA $COS_VNXBLOCK_THIN $BLK_SIZE --thinVolume true

#   Create export group test
    echo "Exporting volume "
    echo "Creating export group"
    run export_group create --type Host $project $VNX_EXPORT_NAME $SAN_VA --volspec "$vnx_vol1+1" --hosts $HOST_NAME
    run export_group show $vnx_exp
    run dumpexport $VNX_EXPORT_NAME $ZONE1_ADDR2_VNX,$ZONE2_ADDR2_VNX portselection
    compare_ports_zones
    checkzone=$( networksystem zonereferences $ZONE1_ADDR1 $ZONE1_ADDR2_VNX | grep -c $ZONE1_NAME_VNX )
    echo "Matched " $checkzone " zone with name " $ZONE1_NAME_VNX " and endpoints " $ZONE1_ADDR1 " and " $ZONE1_ADDR2_VNX
    checkzone=$( networksystem zonereferences $ZONE1_ADDR1 $ZONE2_ADDR2_VNX | grep -c $ZONE1_NAME_VNX )
    echo "Matched " $checkzone " zone with name " $ZONE1_NAME_VNX " and endpoints " $ZONE1_ADDR1 " and " $ZONE2_ADDR2_VNX

#   Delete export group test 
    echo "Deleting export group"
    run export_group delete $vnx_exp
    zone1count=$( zone list $BROCADE_NETWORK --fabricid $FABRIC_ID --zone_name $ZONE1_NAME_VNX --exclude_members false | grep -c "*sanityzonetest*" )
    if [ $zone1count -eq 0 ]; then
        echo "zone(s) don't exist in ViPR after deleting export group "
    else
        echo "zone(s) exist in ViPR after deleting export group "
    fi
    run dumpexport $VNX_EXPORT_NAME $ZONE1_ADDR2_VNX checkfczone
    compare_ports_zones

#   Delete volume test
    echo "Deleting volume "
    run volume delete $project/$VNX_VOLUME1_NAME --wait

#   Clean up Zones
    ZONE_NAME=$ZONE1_NAME_VNX
    ZONE_ADDR=$ZONE1_ADDR1+$ZONE1_ADDR2_VNX+$ZONE2_ADDR2_VNX
    cleanup_zones
}

sanzoning_test() {
    didcleanup=0
    # Setup operations
    sanzoning_setup
    setup_virtual_pools
    # Create volumes 
    for cmd in ${ARGV}
    do
        [ "$cmd" == "addvolumezonecheck" ] && addvolumezonecheck;
        [ "$cmd" == "sanzonereuse" ] && sanzonereuse;
        if [ "$cmd" == "cleanup" ]
        then
            cleanup
            didcleanup=1
        fi
    done
    if [ $didcleanup -eq 0 ]
    then
        echo "No cleanup performed... remember to cleanup later or remove volumes/exports"
    fi
}
sanzoning_test
