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

# Export Test EXISITING_USERADDED_INITS

# Conversion of Existing Initiators to User Added initiators if they are ViPR managed within an export Mask
# 1. Create and Export a Volume- V1 to a Host H1 with two initiators I1 and I2
# 2. Using VPLEX add Initiator I3, I3 to the Initiator Group Associated with the Storage View.
# 3. Create and Export a Volume- V2 to this Host. Verify that the Storage View contains 4 initators and 2 Volumes
# 4. Add I3, I4 to Host H1. The export Mask needs to be updated accordingly as part of the Export Group Update.
# 5. Remove I3 and Verify that the Storage View contains 3 initators and 2 Volumes
# 6. Delete Export Group and verify that the Storage View is gone..

test_EXISITING_USERADDED_INITS() {
    echot "Existing Initiators to User Added Initiators Test Begins"

    #Prepare Host, Initiators and zones
    BASENUM=${BASENUM:=$RANDOM}
    EXISTINGINITTEST=exinittest${BASENUM}
    USERADDEDINIT1=10:00:00:DE:AD:BE:EF:01
    USERADDEDINIT2=10:00:00:DE:AD:BE:EF:02
    EXISTINGINIT3=10:00:00:DE:AD:BE:EF:03
    EXISTINGINIT4=10:00:00:DE:AD:BE:EF:04
    PWWN3=100000DEADBEEF03
    PWWN4=100000DEADBEEF04

    runcmd hosts create ${EXISTINGINITTEST} $TENANT Other ${EXISTINGINITTEST} --port 8111
    runcmd initiator create ${EXISTINGINITTEST} FC $USERADDEDINIT1 --node $USERADDEDINIT1
    runcmd initiator create ${EXISTINGINITTEST} FC $USERADDEDINIT2 --node $USERADDEDINIT2
    runcmd transportzone add $NH/${FC_ZONE_A} $USERADDEDINIT1
    runcmd transportzone add $NH/${FC_ZONE_A} $USERADDEDINIT2

    echot "Creating an export Group and exporting the first volume to initiators 10:00:00:DE:AD:BE:EF:01 and 10:00:00:DE:AD:BE:EF:02"
    EXISTINGINITEGTEST=exinitegtest${BASENUM}
    #verify_export $EXISTINGINITEGTEST ${EXISTINGINITTEST} gone

    runcmd export_group create $PROJECT $EXISTINGINITEGTEST $NH --type Host --volspec "${PROJECT}/${VOLNAME}-1" --hosts "${EXISTINGINITTEST}"
    #verify_export $EXISTINGINITEGTEST ${EXISTINGINITTEST} 2 1

    echot "Adding initiators 10:00:00:DE:AD:BE:EF:03 and 10:00:00:DE:AD:BE:EF:04 to the Masking View using the CLI"
    VPLEXADDINIT=add_initiator_to_mask
    # Add another initiator to the mask (done differently per array type)
    #runcmd vplexhelper.sh $VPLEXADDINIT ${PWWN3} ${$EXISTINGINITEGTEST}
    #runcmd vplexhelper.sh $VPLEXADDINIT ${PWWN4} ${$EXISTINGINITEGTEST}
    runcmd export_group update $PROJECT/$EXISTINGINITEGTEST --addVols "${PROJECT}/${VOLNAME}-2"
    #verify_export $EXISTINGINITEGTEST ${EXISTINGINITTEST} 4 2


    echot "Adding existing initiators 10:00:00:DE:AD:BE:EF:03 and 10:00:00:DE:AD:BE:EF:04 to the Host"
    runcmd transportzone add $NH/${FC_ZONE_A} $EXISTINGINIT3
    runcmd transportzone add $NH/${FC_ZONE_A} $EXISTINGINIT4
    runcmd initiator create ${EXISTINGINITTEST} FC $EXISTINGINIT3 --node $EXISTINGINIT3
    runcmd initiator create ${EXISTINGINITTEST} FC $EXISTINGINIT4 --node $EXISTINGINIT4

    echot "Deleting existing initiators 10:00:00:DE:AD:BE:EF:03"
    runcmd initiator delete $EXISTINGINITTEST/$EXISTINGINIT3
    #verify_export $EXISTINGINITEGTEST ${EXISTINGINITTEST} 3 2

    echot "Deleting Export Group"
    runcmd export_group delete $PROJECT/$EXISTINGINITEGTEST
    #verify_export $EXISTINGINITEGTEST ${EXISTINGINITTEST} gone

    runcmd initiator delete $EXISTINGINITTEST/$USERADDEDINIT1
    runcmd initiator delete $EXISTINGINITTEST/$USERADDEDINIT2
    runcmd initiator delete $EXISTINGINITTEST/$EXISTINGINIT4
    runcmd hosts delete $EXISTINGINITTEST

    echoit "Existing Initiators to User Added Initiators Test C"
}
