#!/bin/sh
#
# Copyright (c) 2017 Dell EMC Corporation
# All Rights Reserved
# set -x

HAPPY_PATH_TEST_INJECTION="happy_path_test_injection"

test_expand_host_filesystem() {
    test_name="test_expand_host_filesystem"
    echot "Test ${test_name} Begins"

    failure_injections="${HAPPY_PATH_TEST_INJECTION}"
    supported_os="windows linux hpux" 

    for os in ${supported_os[@]}
    do
        echo "Running test for ${os}"

	random_number=${RANDOM}
	volume="FS-${random_number}"

	if [ "${os}" = "hpux" ]
	then
	    hostname=hpuxhost1
	elif [ "${os}" = "linux" ]
	then
	    hostname=linuxhost1
	elif [ "${os}" = "windows" ]
	then
	    hostname=winhost1
	fi

        if [ "${os}" = "windows" ]
        then
            windows_create_and_mount_volume $TENANT ${hostname} "${volume}" ${NH} ${VPOOL_BASE} ${PROJECT}
        else
            unix_create_volume_and_mount $TENANT ${hostname} "${volume}" "/${volume}" ${NH} ${VPOOL_BASE} ${PROJECT} ${os}
        fi

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
            expand_volume $TENANT ${hostname} ${volume} ${PROJECT} "5" ${os}

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

	unmount_and_delete_volume $TENANT ${hostname} "${volume}" ${PROJECT} ${os}
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
    
    runcmd catalog order CreateandMountVolume ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,fileSystemType=ntfs,partitionType=GPT,blockSize=DEFAULT,mountPoint=,label= BlockServicesforWindows
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
 
    file_system_type=""

    # All OS's share the same service catalog name in this operation.
    service_catalog=CreateAndMountBlockVolume
    if [ "${os}" = "hpux" ]
    then
	service_category=BlockServicesforHP-UX
    elif [ "${os}" = "linux" ]
    then
        service_category=BlockServicesforLinux
        file_system_type=",fileSystemType=ext3"
    fi

    host_id=`hosts list ${tenant_arg} | grep ${hostname_arg} | awk '{print $4}'`

    runcmd catalog order ${service_catalog} ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,mountPoint=${mountpoint_arg}${file_system_type} ${service_category}
}

expand_volume() {
    # tenant hostname volname project size
    tenant_arg=$1
    host_id=`hosts list ${tenant_arg} | grep "${2} " | awk '{print $4}'`
    volume_id=`volume list ${4} | grep "${3}" | awk '{print $7}'`
    size=$5
    os=$6

    service_category=NoneSet
    service_catalog=NoneSet
    volume_parameter_name="volume"

    if [ "${os}" = "hpux" ]
    then
	service_category=BlockServicesforHP-UX
        service_catalog=ExpandVolumeonHPUX
    elif [ "${os}" = "linux" ]
    then
        service_category=BlockServicesforLinux
        service_catalog=ExpandLinuxMount
    elif [ "${os}" = "windows" ]
    then
        service_category=BlockServicesforWindows
        service_catalog=ExpandVolumeonWindows
        volume_parameter_name="volumes"
    fi

    # expand_volume $TENANT ${hostname} ${volume} ${PROJECT} "5" ${os}

    runcmd catalog order ${service_catalog} ${tenant_arg} host=${host_id},size=${size},${volume_parameter_name}=${volume_id} ${service_category}
}

unmount_and_delete_volume() {
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
    elif [ "${os}" = "linux" ]
    then
        service_category=BlockServicesforLinux
        service_catalog=UnmountandDeleteVolume
    elif [ "${os}" = "windows" ]
    then
        service_category=BlockServicesforWindows
        service_catalog=UnmountandDeleteVolume
    fi

    host_id=`hosts list ${tenant_arg} | grep ${hostname_arg} | awk '{print $4}'`
    volume_id=`volume list ${PROJECT} | grep "${volname_arg}" | awk '{print $7}'`

    runcmd catalog order ${service_catalog} ${tenant_arg} volumes=${volume_id},host=${host_id} ${service_category}
}
