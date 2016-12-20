#!/bin/sh
#
# Copyright (c) 2016 EMC Corporation
# All Rights Reserved
#
# run these by executing dutests.sh with one of the test function names below as the test arg
#


# Export Test VPLEX_ORCH_1
#
# Summary: Remove Initiator should not remove volumes unless validation_check is off.
#
# 1. ViPR creates 2 volumes, 1 cluster export to two hosts.
# 2. turn on validation_check flag
# 3. remove all initiators for host1
#     -- expected result: exception with error message about how volumes would be removed
# 4. ? verify no zones removed
# 5. turn off validation_check flag
# 6. remove all initiators for host1, again
#     -- expected result: host is removed
#
test_VPLEX_ORCH_1() {
    echot "Test VPLEX_ORCH_1: Remove Initiator should not remove volumes unless validation_check is off."
    expname=${EXPORT_GROUP_NAME}tvo1

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone

    set_validation_check true

    # Create the cluster export and masks with the 2 volumes
    runcmd export_group create $PROJECT ${expname}1 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-1,${PROJECT}/${VOLNAME}-2" --clusters "${TENANT}/${CLUSTER}"

    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2

    # Run the export group command.  Expect it to fail with validation
    fail export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1},${HOST1}/${H1PI2}

    # Verify exports still exist
    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 2

    # Verify the zone names, as we know them, are on the switch
    load_zones ${HOST1} 
    verify_zones ${FC_ZONE_A:7} exists

    set_validation_check false

    # Run the export group command.  It should succeed since there is no validation
    runcmd export_group update $PROJECT/${expname}1 --remInits ${HOST1}/${H1PI1},${HOST1}/${H1PI2}

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure the mask is gone
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}1 ${HOST2} gone

    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
    
    set_validation_check true
}

# Export Test VPLEX_ORCH_2
#
# Summary: Remove Volume should not remove initiators unless validation_check is off.
#
# 1. ViPR creates 1 volume, 1 host export
# 2. turn on validation_check flag
# 3. remove volume
#     -- expected result: exception with error message about how initiators would be removed
# 4. verify no zones removed
# 5. turn off validation_check flag
# 6. remove volume, again
#     -- expected result: volume is removed successfully
#
test_VPLEX_ORCH_2() {
    echot "Test VPLEX_ORCH_2: Remove Volume should not remove initiators unless validation_check is off."
    expname=${EXPORT_GROUP_NAME}tvo2

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone

    set_validation_check true

    # Create the mask with the 1 volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1}"

    verify_export ${expname}1 ${HOST1} 2 1

    # Run the export group command.  Expect it to fail with validation
    fail export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"

    # Verify exports still exist
    verify_export ${expname}1 ${HOST1} 2 1

    # Verify the zone names, as we know them, are on the switch
    load_zones ${HOST1} 
    verify_zones ${FC_ZONE_A:7} exists

    set_validation_check false

    # Run the export group command.  It should succeed since there is no validation
    runcmd export_group update $PROJECT/${expname}1 --remVols "${PROJECT}/${VOLNAME}-1"

    # Delete the export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure the mask is gone
    verify_export ${expname}1 ${HOST1} gone

    verify_no_zones ${FC_ZONE_A:7} ${HOST1}
    
    set_validation_check true
}

# Export Test VPLEX_ORCH_3
#
# Summary: Delete Export Group should not remove Initiators.
#
# 1. ViPR creates 2 volumes
# 2. Export one volume to a cluster of two hosts
# 3. export other volume to just host1 in the cluster
# 4. delete the cluster export group
#     -- expected result: host1's storage view should have no initiators removed from it
# 5. verify no zones removed
#
test_VPLEX_ORCH_3() {
    echot "Test VPLEX_ORCH_3: Delete Export Group should not remove Initiators."
    expname=${EXPORT_GROUP_NAME}tvo3

    # Make sure we start clean; no masking view on the array
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone

    # Create the host export and mask with the 1st volume
    runcmd export_group create $PROJECT ${expname}1 $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${HOST1}"

    # Create the cluster export and masks with the 2nd volume
    runcmd export_group create $PROJECT ${expname}2 $NH --type Cluster --volspec "${PROJECT}/${VOLNAME}-2" --clusters "${TENANT}/${CLUSTER}"

    verify_export ${expname}1 ${HOST1} 2 2
    verify_export ${expname}1 ${HOST2} 2 1

    set_validation_check true

    # Delete the cluster export group
    fail export_group delete $PROJECT/${expname}2

    set_validation_check false

    # Delete the cluster export group
    runcmd export_group delete $PROJECT/${expname}2

    # Verify exports still exist
    verify_export ${expname}1 ${HOST1} 2 1
    verify_export ${expname}1 ${HOST2} gone
    
    # Verify the zone names, as we know them, are on the switch
    load_zones ${HOST1} 
    verify_zones ${FC_ZONE_A:7} exists

    # Delete the host export group
    runcmd export_group delete $PROJECT/${expname}1

    # Make sure the masks are gone
    verify_export ${expname}1 ${HOST1} gone
    verify_export ${expname}2 ${HOST1} gone
    verify_export ${expname}2 ${HOST2} gone

    verify_no_zones ${FC_ZONE_A:7} ${HOST1}

    set_validation_check true
}

