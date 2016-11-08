#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
# set -x

test_host_1() {
    echot "Test 4 Add Initiator test"
    expname=${EXPORT_GROUP_NAME}t1
    item=${RANDOM}
    cfs="ExportGroup ExportMask Initiator"
    mkdir -p results/${item}

    smisprovider list | grep SIM > /dev/null
    if [ $? -eq 0 ]; then
        FC_ZONE_A=${CLUSTER1NET_SIM_NAME}
    fi

    verify_export ${expname}1 ${HOST1} gone

    # Perform any DB validation in here
    #snap_db 1 ${cfs}

    # Run the export group command
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec ${PROJECT}/${VOLNAME}-1 --hosts ${HOST1}

    # Add initiator to network
    test_pwwn=`randwwn`
    test_nwwn=`randwwn`
    runcmd run transportzone add ${FC_ZONE_A} ${test_pwwn}

    # Add initiator to host cluster
    runcmd initiator create ${HOST1} FC ${test_pwwn} --node ${test_nwwn}

    # Remove the shared export
    runcmd export_group delete $PROJECT/${expname}1

    # Snap the DB again
    #snap_db 2 ${cfs}

    # Validate nothing was left behind
    #validate_db 1 2 ${cfs}

    verify_export ${expname}1 ${HOST1} gone
}

