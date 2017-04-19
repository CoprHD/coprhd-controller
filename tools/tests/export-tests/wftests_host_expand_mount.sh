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

test_expand_host_filesystem() {
    test_name="test_expand_host_filesystem"
    echot "Test ${test_name} Begins"

    failure_injections="${HAPPY_PATH_TEST_INJECTION}"

    supported_os="hpux"

    for os in ${supported_os}
    do
	volume="FS-${BASENUM}"

	if [ "${os}" = "hpux" ]
	then
	    hostname=hpuxhost1
	fi
	
	unix_create_volume_and_mount $TENANT ${hostname} "${volume}" "/${volume}" ${NH} ${VPOOL_BASE} ${PROJECT} ${os}

	for failure in ${failure_injections}
	do
            secho "Running ${test_name} with failure scenario: ${failure}..."
            TEST_OUTPUT_FILE=test_output_${RANDOM}.log
            reset_counts
            column_family="Host Volume ExportGroup ExportMask Cluster"
            random_number=${RANDOM}
            mkdir -p results/${random_number}

            # Snap DB
            snap_db 1 "${column_family[@]}"
            # Turn on failure at a specific point
            set_artificial_failure ${failure}

	    # Run expand filesystem
            unix_expand_volume $TENANT ${hostname} ${volume} ${PROJECT} "5" ${os}

            # Verify injected failures were hit
            # verify_failures ${failure}

            # Snap DB
            snap_db 2 "${column_family[@]}"

            # Validate DB
            validate_db 1 2 "${column_family[@]}"
            # Report results
            report_results ${test_name} ${failure}

            # Add a break in the output
            echo " "
	done

	# Turn off failure
	set_artificial_failure none

	unix_unmount_and_delete_volume $TENANT ${hostname} "${volume}" ${PROJECT} ${os}
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

unix_create_volume_and_mount() {
    # host, virtualArray, virtualPool, project, name, consistencyGroup, size, mountPoint, hlu
    tenant_arg=$1
    hostname_arg=$2
    volname_arg=$3
    mountpoint_arg=$4

    virtualarray_id=`neighborhood list | grep "${5} " | awk '{print $3}'`
    virtualpool_id=`cos list block | grep "${6} " | grep -v description | awk '{print $3}'`
    project_id=`project list --tenant ${tenant_arg} | grep -v owner | grep "${7} " | awk '{print $4}'`

    os=$8

    # All OS's share the same service catalog name in this operation.
    service_catalog=CreateAndMountBlockVolume
    if [ "${os}" = "hpux" ]
    then
	service_category=BlockServicesforHP-UX
    fi

    host_id=`hosts list ${tenant_arg} | grep ${hostname_arg} | awk '{print $4}'`

    echo "=== catalog order ${service_catalog} ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,mountPoint=${mountpoint_arg} ${service_category}"
    echo `catalog order ${service_catalog} ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,mountPoint=${mountpoint_arg} ${service_category}`
}

unix_expand_volume() {
    # tenant hostname volname project size
    tenant_arg=$1
    host_id=`hosts list ${tenant_arg} | grep "${2} " | awk '{print $4}'`
    volume_id=`volume list ${4} | grep "${3}" | awk '{print $7}'`
    size=$5
    os=$6

    service_category=NoneSet
    service_catalog=NoneSet

    if [ "${os}" = "hpux" ]
    then
	service_category=BlockServicesforHP-UX
        service_catalog=ExpandVolumeonHPUX
    fi

    # unix_expand_volume $TENANT ${hostname} ${volume} ${PROJECT} "5" ${os}

    echo "=== catalog order ${service_catalog} ${tenant_arg} host=${host_id},size=${size},volume=${volume_id} ${service_category}"
    echo `catalog order ${service_catalog} ${tenant_arg} host=${host_id},size=${size},volume=${volume_id} ${service_category}`
}

unix_unmount_and_delete_volume() {
    # host, name
    tenant_arg=$1
    hostname_arg=$2
    volname_arg=$3
    project_arg=$4
    os=$5

    service_category=NoneSet
    service_catalog=NoneSet
    if [ "${os}" = "hpux" ]
    then
	service_category=BlockServicesforHP-UX
        service_catalog=UnmountandDeleteVolumeonHPUX
    fi

    host_id=`hosts list ${tenant_arg} | grep ${hostname_arg} | awk '{print $4}'`
    volume_id=`volume list ${PROJECT} | grep "${volname_arg}" | awk '{print $7}'`

    echo "=== catalog order ${service_catalog} ${tenant_arg} volumes=${volname_id},host=${host_id} ${service_category}"
    echo `catalog order ${service_catalog} ${tenant_arg} volumes=${volname_id},host=${host_id} ${service_category}`
}
