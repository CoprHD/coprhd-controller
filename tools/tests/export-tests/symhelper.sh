#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#
# 
# Quick verification script
#
# Usage: ./symhelper.sh <SID> <NAME_PATTERN> <NUMBER_OF_INITIATORS_EXPECTED> <NUMBER_OF_LUNS_EXPECTED>
#
# set -x

# First parameter is the Symm ID
SID=$1
shift
# Subsequent parameters: MaskingView Name, Number of Initiators, Number of Luns
# If checking if the MaskingView does not exist, then parameter $2 should be "gone"
SG_PATTERN=$1
NUM_INITIATORS=$2
NUM_LUNS=$3
TMPFILE1=/tmp/verify-${RANDOM}
TMPFILE2=/dev/null

SYM=${SID:-000198700406}
/opt/emc/SYMCLI/bin/symaccess -sid ${SYM} list view -name ${SG_PATTERN} -detail > ${TMPFILE1} 2> ${TMPFILE2}

grep -n ${SG_PATTERN} ${TMPFILE1} > /dev/null
if [ $? -ne 0 ]
then
   if [ "$2" = "gone" ]
   then
      echo "PASSED: Verified MaskingView with pattern ${SG_PATTERN} doesn't exist."
      exit 0;
   fi
   echo "ERROR: Expected MaskingView ${SG_PATTERN}, but could not find it";
   exit 1;
else
   if [ "$2" = "gone" ]
   then
      echo "ERROR: Expected MaskingView ${SG_PATTERN} to be gone, but it was found"
      exit 1;
   fi
fi

num_inits=`grep "WWN.*:" ${TMPFILE1} | wc -l`
num_luns=`perl -nle 'print $1 if(m#(\S+)\s+\S+\s+Not Visible\s+#);' ${TMPFILE1} | sort -u | wc -l`
failed=false

if [ ${num_inits} -ne ${NUM_INITIATORS} ]
then
   echo "FAILED: Export group initiators: Expected: ${NUM_INITIATORS}, Retrieved: ${num_inits}";
   echo "FAILED: Masking view dump:"
   grep "Masking View Name" ${TMPFILE1}
   grep "Group Name" ${TMPFILE1}
   grep "WWN.*:" ${TMPFILE1}
   grep "Not Visible" ${TMPFILE1} | sort -u
   failed=true
fi

if [ ${num_luns} -ne ${NUM_LUNS} ]
then
   echo "FAILED: Export group luns: Expected: ${NUM_LUNS}, Retrieved: ${num_luns}";
   echo "FAILED: Masking view dump:"
   grep "Masking View Name" ${TMPFILE1}
   grep "Group Name" ${TMPFILE1}
   grep "WWN" ${TMPFILE1}
   grep "Not Visible" ${TMPFILE1} | sort -u
   failed=true
fi

if [ "${failed}" = "true" ]
then
   exit 1;
fi

echo "PASSED: MaskingView '$1' contained $2 initiators and $3 luns"
exit 0;
