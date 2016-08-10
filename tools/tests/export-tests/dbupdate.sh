#!/bin/sh -x
#
# A script to modify an entry in the ViPR DB
#
# Usage: ./dbupdate.sh <CF> <field> <URI> <new-value>
#
# Restrictions:
# - works with strings only
# - the field must already exist in the URI object
# - the original value in the field must be the only occurrence of that value (for now) in the XML (yuck).
#   TODO: Need to improve this to replace only the field that was requested.
# 

DUMPFILE=/tmp/dumpcakes.xml

if [ "$4" = "" ]; then
   echo "Usage: ./dbupdate.sh <CF> <field> <URI> <new-value>"
   exit 1;
fi

# Fill in values
CF=$1
FIELD=$2
URI=$3
VALUE=$4

# dump out the field
/opt/storageos/bin/dbcli dump -i "${URI}" -f ${DUMPFILE} ${CF}

originalfieldentry=`grep "name=\"${FIELD}\"" ${DUMPFILE}`
originalfieldvalue=`echo ${originalfieldentry} | awk -F\" '{print $6}'`

replacestring="s/${originalfieldvalue}/${VALUE}/g"
sed ${replacestring} ${DUMPFILE} > ${DUMPFILE}-new
/opt/storageos/bin/dbcli load -f ${DUMPFILE}-new
