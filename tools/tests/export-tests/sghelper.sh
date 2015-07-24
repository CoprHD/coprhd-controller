#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# 
# Quick verification script
#
# Usage: ./sghelper.sh <SID> <NAME_PATTERN> <NUMBER_OF_MASKING_VIEWS> <NUMBER_OF_LUNS_EXPECTED> <POLICY_NAME>
#
# set -x

# First parameter is the Symm ID
SID=$1
shift
# Subsequent parameters: MaskingView Name, Number of Initiators, Number of Luns
# If checking if the MaskingView does not exist, then parameter $2 should be "gone"
SG_PATTERN=$1
POLICYNAME=$2
NUM_MVS=$3
NUM_LUNS=$4
TMPFILE1=/tmp/verify-${RANDOM}
TMPFILE2=/dev/null

SYM=${SID:-000198700406}
/opt/emc/SYMCLI/bin/symaccess -sid ${SYM} list -type storage -name ${SG_PATTERN} -detail > ${TMPFILE1} 2> ${TMPFILE2}
/opt/emc/SYMCLI/bin/symfast -sid ${SYM} show -association -sg ${SG_PATTERN} >> ${TMPFILE1} 2> ${TMPFILE2}

grep -n ${SG_PATTERN} ${TMPFILE1} > ${TMPFILE2}
if [ $? -ne 0 ]
then
   if [ "$2" = "gone" ]
   then
      echo "PASSED: Verified Storage Group with pattern ${SG_PATTERN} doesn't exist."
      exit 0;
   fi
   echo "ERROR: Expected Storage Group ${SG_PATTERN}, but could not find it";
   exit 1;
else
   if [ "$2" = "gone" ]
   then
      echo "ERROR: Expected Storage Group ${SG_PATTERN} to be gone, but it was found"
      exit;
   fi
fi

num_luns=`grep ${SG_PATTERN} ${TMPFILE1} | head -1 | awk '{print $2}'`
num_mvs=`grep ${SG_PATTERN} ${TMPFILE1} | head -1 | awk '{print $4}'`
child_sg=`grep ${SG_PATTERN} ${TMPFILE1} | head -1 | awk '{print $5}'`

#
# Output from /opt/emc/SYMCLI/bin/symaccess -sid ${SYM} list -type storage -name ${SG_PATTERN} -detail
# looks like:
#
# Symmetrix ID          : 000195701573
#
#                                  Dev   SG    View  FLG
#Storage Group Name                Count Count Count  S
#--------------------------------  ----- ----- ----- ---
#ingesthost-03_ChSG1                   1     1     1  C
#
#Legend:
#    (S)tatus, P = Parent SG, C = Child SG, . = N/A
#
if [ "$child_sg"x = "C"x ]
then
    if [ $NUM_MVS -eq 0 ]
    then
        num_mvs=0
    elif [ $NUM_MVS -eq 1 -a $num_mvs -eq 1 ]
    then
        num_mvs=1
    fi
fi

policy_name=None
grep "Policy Name" ${TMPFILE1} > ${TMPFILE2}
if [ $? -eq 0 ]
then
   policy_name=`grep "Policy Name" ${TMPFILE1} | awk '{print $4}'`
fi

failed=false

if [ "${policy_name}" != "${POLICYNAME}" ]
then
   echo "FAILED: Storage group policy name: Expected: ${POLICYNAME}, Retrieved: ${policy_name}";
   echo "FAILED: storage group dump:"
   echo "============================================================="
   echo -n "= "
   grep "Dev" ${TMPFILE1} | head -1
   echo -n "= "
   grep "Storage Group Name" ${TMPFILE1} 
   echo -n "= "
   grep "${SG_PATTERN}" ${TMPFILE1} | head -1
   echo -n "= "
   grep "Policy Name" ${TMPFILE1}
   echo "============================================================="
   failed=true
fi

if [ ${num_mvs} -ne ${NUM_MVS} ]
then
   echo "FAILED: Storage group masking views: Expected: ${NUM_MVS}, Retrieved: ${num_mvs}";
   echo "FAILED: storage group dump:"
   echo "============================================================="
   echo -n "= "
   grep "Dev" ${TMPFILE1} | head -1
   echo -n "= "
   grep "Storage Group Name" ${TMPFILE1} 
   echo -n "= "
   grep "${SG_PATTERN}" ${TMPFILE1} | head -1
   echo -n "= "
   grep "Policy Name" ${TMPFILE1}
   echo "============================================================="
   failed=true
fi

if [ ${num_luns} -ne ${NUM_LUNS} ]
then
   echo "FAILED: Storage group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
   echo "FAILED: Storage group dump:"
   echo "============================================================="
   echo -n "= "
   grep "Dev" ${TMPFILE1} | head -1
   echo -n "= "
   grep "Storage Group Name" ${TMPFILE1} 
   echo -n "= "
   grep "${SG_PATTERN}" ${TMPFILE1} | head -1
   echo -n "= "
   grep "Policy Name" ${TMPFILE1}
   echo "============================================================="
   failed=true
fi

if [ "${failed}" = "true" ]
then
   exit 1;
fi

echo "PASSED: Storage Group '$1' with Policy $2 is associated with $3 masking views and $4 luns"
exit 0;
