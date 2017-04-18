#!/bin/sh
#
# Copyright (c) 2017 Dell EMC Corporation
# All Rights Reserved
# set -x

HAPPY_PATH_TEST_INJECTION="happy_path_test_injection"

test_windows_expand_mount() {
    echot "Test test_windows_expand_mount"

    cfs=("ExportGroup ExportMask Volume Host")
    test_name="test_windows_expand_mount"
    random_number=${RANDOM}
    volume=${VOLNAME}-1-${random_number}
    winhost=winhost1

    failure_injections="${HAPPY_PATH_TEST_INJECTION}"

    for failure in ${failure_injections}
    do
        TEST_OUTPUT_FILE=test_output_${RANDOM}.log
        reset_counts
        item=${RANDOM}
        mkdir -p results/${item}

        # Snap the DB
        snap_db 1 "${cfs[@]}"

        windows_create_and_mount_volume $TENANT ${winhost} ${volume} ${NH} ${VPOOL_BASE} ${PROJECT}

        windows_expand_volume $TENANT ${winhost} ${volume} ${PROJECT} "5"

        windows_unmount_and_delete_volume $TENANT ${winhost} ${volume} ${PROJECT}

        snap_db 2 "${cfs[@]}"

        # Validate that nothing was left behind
        validate_db 1 2 "${cfs[@]}"

        # Report results
        report_results ${test_name} ${failure}

        echo " "
    done
}



#### Service Catalog methods are below ####


windows_create_and_mount_volume() {
    # tenant hostname volname varray vpool project
    tenant_arg=$1
    host_id=`hosts list ${tenant_arg} | grep "${2} " | awk '{print $4}'`
    volname_arg=$3

    virtualarray_id=`neighborhood list | grep "${4} " | awk '{print $3}'`
    virtualpool_id=`cos list block | grep "${5} " | awk '{print $3}'`
    project_id=`project list --tenant ${tenant_arg} | grep "${6} " | awk '{print $4}'`
    
    echo "=== catalog order CreateandMountVolume ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,fileSystemType=ntfs,partitionType=GPT,blockSize=DEFAULT,mountPoint=,label="
    echo `catalog order CreateandMountVolume ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,fileSystemType=ntfs,partitionType=GPT,blockSize=DEFAULT,mountPoint=,label= BlockServicesforWindows`
}

windows_expand_volume() {
    # tenant hostname volname project size
    tenant_arg=$1
    host_id=`hosts list ${tenant_arg} | grep "${2} " | awk '{print $4}'`
    volume_id=`volume list ${4} | grep "${3}" | awk '{print $7}'`
    size=$5

    echo "=== catalog order ExpandVolumeonWindows ${tenant_arg} host=${host_id},size=${size},volumes=${volume_id}"
    echo `catalog order ExpandVolumeonWindows ${tenant_arg} host=${host_id},size=${size},volumes=${volume_id} BlockServicesforWindows`
}

windows_unmount_and_delete_volume() {
    # tenant hostname volname project
    tenant_arg=$1
    host_id=`hosts list ${tenant_arg} | grep "${2} " | awk '{print $4}'`
    volume_id=`volume list ${4} | grep "${3}" | awk '{print $7}'`

    echo "=== catalog order UnmountandDeleteVolume ${tenant_arg} host=${host_id},volumes=${volume_id}"    
    echo `catalog order UnmountandDeleteVolume ${tenant_arg} host=${host_id},volumes=${volume_id} BlockServicesforWindows`
}
