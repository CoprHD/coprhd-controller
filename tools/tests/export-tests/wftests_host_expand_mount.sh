#!/bin/sh
#
# Copyright (c) 2017 Dell EMC Corporation
# All Rights Reserved
# set -x

HAPPY_PATH_TEST_INJECTION="happy_path_test_injection"

test_expand_host_filesystem() {
    test_name="test_expand_host_filesystem"
    echot "Test ${test_name} Begins"
    common_failure_injections="failure_004_final_step_in_workflow_complete \
                               failure_080_BlockDeviceController.expandVolume_before_device_expand"

    os_failure_injections=""

    supported_os="windows linux hpux" 

    run syssvc $SANITY_CONFIG_FILE localhost set_prop system_proxyuser_encpassword $SYSADMIN_PASSWORD

    for os in ${supported_os[@]}
    do
        echo "Running test for ${os}"

	random_number=${RANDOM}
	volume="FS-${random_number}"

	if [ "${os}" = "hpux" ]
	then
	    hostname=hpuxhost1
            os_failure_injections="hpux_expandVolume_after_unmount \
                                   hpux_expandVolume_after_remove_tag \
                                   hpux_expandVolume_after_volume_resize \
                                   hpux_expandVolume_after_mount"
	elif [ "${os}" = "linux" ]
	then
	    hostname=linuxhost1
            os_failure_injections="linux_expandVolume_after_unmount \
                                   linux_expandVolume_after_remove_tag \
                                   linux_expandVolume_after_volume_resize \
                                   linux_expandVolume_after_resize_partition \
                                   linux_expandVolume_after_resize_filesystem \
                                   linux_expandVolume_after_mount"
	elif [ "${os}" = "windows" ]
	then
	    hostname=winhost1
            os_failure_injections="windows_before_extendDrives \
                                   windows_after_extendDrives"
	fi

        failure_injections="${HAPPY_PATH_TEST_INJECTION} ${common_failure_injections} ${os_failure_injections}"

        if [ "${os}" = "windows" ]
        then
            run windows_create_and_mount_volume $TENANT ${hostname} "${volume}" ${NH} ${VPOOL_BASE} ${PROJECT}
        else
            run unix_create_volume_and_mount $TENANT ${hostname} "${volume}" "/${volume}" ${NH} ${VPOOL_BASE} ${PROJECT} ${os}
        fi
        # Initial size to be expanded to
        size=2
        wwn=`get_volume_wwn ${PROJECT}/${volume}`
        mountpoint=`get_volume_mount_point ${PROJECT}/${volume}`

        # Placeholder when a specific failure case is being worked...
        # failure_injections="windows_before_extendDrives"
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

            if [ ${failure} != ${HAPPY_PATH_TEST_INJECTION} ]; then
                fail expand_volume $TENANT ${hostname} ${volume} ${PROJECT} ${size} ${os} ${failure}

                # Verify injected failures were hit
                verify_failures ${failure}

                if [ ${failure} = "linux_expandVolume_after_mount" ]; then
                   volume_id=`volume list ${PROJECT} | grep "${volume} " | awk '{print $7}'`
                   host_id=`hosts list ${TENANT} | grep "${hostname} " | awk '{print $4}'`
                   volume_tag="vipr:mountPoint-${host_id}=/${volume}"
                   add_tag "volume" ${volume_id} ${volume_tag}
                fi

                # Snap DB
                snap_db 2 "${column_family[@]}"

                # Validate DB
                validate_db 1 2 "${column_family[@]}"

                # host tooling to verify that volume is remounted
                verify_mount_point ${os} ${mountpoint} ${size} ${wwn}

		# Rerun the expand operation
		set_artificial_failure none
                runcmd expand_volume $TENANT ${hostname} ${volume} ${PROJECT} ${size} ${os}

		else
	        runcmd expand_volume $TENANT ${hostname} ${volume} ${PROJECT} ${size} ${os} ${failure}
	    fi
		    # Verify that expand is successful on host side
 		    verify_mount_point ${os} ${mountpoint} ${size} ${wwn}

            # Report results
            report_results "${test_name}_${os}" ${failure}

            size=`expr $size + 1`

            # Add a break in the output
            echo " "
	done

	run unmount_and_delete_volume $TENANT ${hostname} "${volume}" ${PROJECT} ${os}
    done 
}

test_swap_mounted_volume() {
    test_name="test_swap_mounted_volume"
    echot "Test ${test_name} Begins"

    supported_os="windows linux"
    #supported_os="windows linux hpux" 

    run syssvc $SANITY_CONFIG_FILE localhost set_prop system_proxyuser_encpassword $SYSADMIN_PASSWORD

    for os in ${supported_os[@]}
    do
        echo "Running test for ${os}"

	random_number=${RANDOM}
	volume1="FS-${random_number}-1"
        volume2="FS-${random_number}-2"

        reset_counts

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
            run windows_create_and_mount_volume $TENANT ${hostname} "${volume1}" ${NH} ${VPOOL_BASE} ${PROJECT}
            run windows_create_and_mount_volume $TENANT ${hostname} "${volume2}" ${NH} ${VPOOL_BASE} ${PROJECT}
        else
            run unix_create_volume_and_mount $TENANT ${hostname} "${volume1}" "/${volume1}" ${NH} ${VPOOL_BASE} ${PROJECT} ${os}
            run unix_create_volume_and_mount $TENANT ${hostname} "${volume2}" "/${volume2}" ${NH} ${VPOOL_BASE} ${PROJECT} ${os}
        fi

        size=2

        mountpoint1=`get_volume_mount_point ${PROJECT}/${volume1}`
        mountpoint2=`get_volume_mount_point ${PROJECT}/${volume2}`
        volume1_id=`volume list ${PROJECT} | grep "${volume1} " | awk '{print $7}'`
        volume2_id=`volume list ${PROJECT} | grep "${volume2} " | awk '{print $7}'`
        host_id=`hosts list ${TENANT} | grep "${hostname} " | awk '{print $4}'`
        volume1_tag="vipr:mountPoint-${host_id}=${mountpoint1}"
        volume2_tag="vipr:mountPoint-${host_id}=${mountpoint2}"

        echo "Swapping tags" 
        remove_tag "volume" ${volume1_id} ${volume1_tag}
        remove_tag "volume" ${volume2_id} ${volume2_tag}
        add_tag "volume" ${volume1_id} ${volume2_tag}
        add_tag "volume" ${volume2_id} ${volume1_tag}

        fail expand_volume $TENANT ${hostname} ${volume1} ${PROJECT} ${size} ${os} ${failure}
        fail expand_volume $TENANT ${hostname} ${volume2} ${PROJECT} ${size} ${os} ${failure}

        echo "Fixing the tags"
        remove_tag "volume" ${volume1_id} ${volume2_tag}
        remove_tag "volume" ${volume2_id} ${volume1_tag}
        add_tag "volume" ${volume1_id} ${volume1_tag}
        add_tag "volume" ${volume2_id} ${volume2_tag}

        runcmd expand_volume $TENANT ${hostname} ${volume1} ${PROJECT} ${size} ${os} ${failure}
        runcmd expand_volume $TENANT ${hostname} ${volume2} ${PROJECT} ${size} ${os} ${failure}

        report_results "${test_name}_${os}" ${test_name}

	run unmount_and_delete_volume $TENANT ${hostname} "${volume1}" ${PROJECT} ${os}
	run unmount_and_delete_volume $TENANT ${hostname} "${volume2}" ${PROJECT} ${os}
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
    
    runcmd catalog order CreateandMountVolume ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,fileSystemType=ntfs,partitionType=GPT,blockSize=DEFAULT,mountPoint=,label= BlockServicesforWindows --failOnError true
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

    runcmd catalog order ${service_catalog} ${tenant_arg} project=${project_id},name=${volname_arg},virtualPool=${virtualpool_id},virtualArray=${virtualarray_id},host=${host_id},size=1,mountPoint=${mountpoint_arg}${file_system_type} ${service_category} --failOnError true
    return $?
}

expand_volume() {
    # tenant hostname volname project size
    tenant_arg=$1
    host_id=`hosts list ${tenant_arg} | grep "${2} " | awk '{print $4}'`
    volume_id=`volume list ${4} | grep "${3}" | awk '{print $7}'`
    size=$5
    os=$6
    catalog_failure=$7

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

    catalog order ${service_catalog} ${tenant_arg} host=${host_id},size=${size},${volume_parameter_name}=${volume_id},artificialFailure=${catalog_failure} ${service_category} --failOnError true
    return $?
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

    runcmd catalog order ${service_catalog} ${tenant_arg} volumes=${volume_id},host=${host_id} ${service_category} --failOnError true
}

get_volume_wwn() {
    label=$1
    volume show ${label} | grep wwn | awk '{print $2}' | cut -d '"' -f2
}

get_volume_mount_point() {
    label=$1
    volume show ${label} | grep mountPoint | cut -d '"' -f2 | cut -d '=' -f2
}
