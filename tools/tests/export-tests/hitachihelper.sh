#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# 
# Quick verification script
#
# Usage: ./HiCommandCli.sh <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#

SG_PATTERN=$1
HOST_NAME=$2.lss.emc.com
NUM_INITIATORS=$3
NUM_LUNS=$4

SYM=ARRAY.D800S.83041093
/opt/CLI/HiCommandCLI.sh GetStorageArray subtarget=HostStorageDomain serialnum=83041093 model=D800S hsdsubinfo=wwn,path > /tmp/maskingview_verify.txt 2> /tmp/whocares1
/opt/CLI/HiCommandCLI.sh GetHost hostname=${HOST_NAME} subtarget=LogicalUnit lusubinfo=Path > /tmp/lun_verify.txt 2> /tmp/whocares2

grep -n ${SG_PATTERN} /tmp/maskingview_verify.txt > /dev/null
if [ $? -ne 0 ]
then
   if [ "$3" = "gone" ]
   then
      echo "PASSED: Verified MaskingView with pattern ${SG_PATTERN} doesn't exist."
      exit 0;
   fi
   echo "ERROR: Expected MaskingView ${SG_PATTERN}, but could not find it";
   exit 1;
else
   if [ "$3" = "gone" ]
   then
      echo "ERROR: Expected MaskingView ${SG_PATTERN} to be gone, but it was found"
      exit;
   fi
fi

num_inits=`grep 'An instance of WWN' /tmp/lun_verify.txt | wc -l`
num_luns=`grep 'An instance of Path' /tmp/lun_verify.txt  | wc -l`
failed=false

if [ ${num_inits} -ne ${NUM_INITIATORS} ]
then
   echo "FAILED: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
   echo "FAILED: Masking view dump:"
   grep "Masking View Name" /tmp/initiator_verify.txt
   grep "Group Name" /tmp/initiator_verify.txt
   grep "WWN" /tmp/initiator_verify.txt
   grep "Not Visible" /tmp/initiator_verify.txt | sort -u
   failed=true
fi

if [ ${num_luns} -ne ${NUM_LUNS} ]
then
   echo "FAILED: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
   echo "FAILED: Masking view dump:"
   grep "Masking View Name" /tmp/lun_verify.txt
   grep "Group Name" /tmp/lun_verify.txt
   grep "WWN" /tmp/lun_verify.txt
   grep "Not Visible" /tmp/lun_verify.txt | sort -u
   failed=true
fi

if [ "${failed}" = "true" ]
then
   exit 1;
fi

echo "PASSED: MaskingView '$1' contained $2 initiators and $3 luns"
exit 0;
