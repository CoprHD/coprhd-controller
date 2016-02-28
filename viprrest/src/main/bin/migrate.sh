#!/bin/sh
#
# migrate.sh - Script to migrate data from one lun to another given the WWNs of the LUNs involved.
#
#  ./migrate.sh <source_luns_wwn> <target_luns_wwn>
#
#

SOURCE_WWN=$1
TARGET_WWN=$2

# Wait for the target LUN to be added to the host.  Keep scanning
# till we see the target LUN's WWN in powermig display dev=all ouput
while true
do
    /etc/opt/emcpower/emcplun_linux s hba -noprompt > /dev/null 2>&1

    /sbin/powermt display dev=all | grep ${TARGET_WWN} > /dev/null 2>&1

    if [ $? -eq 0 ]
    then
        break 
    fi
    
    sleep 5
done

# Find the emcpower names of the two LUNs from their WWNs
/sbin/powermt display dev=all > /tmp/pdall
for i in `awk '/Pseudo name=[a-zA-Z]/ {print $2}' /tmp/pdall | sed -e 's/name=//'`
do
    /sbin/powermt display dev=$i | grep ${SOURCE_WWN} > /dev/null 2>&1
    
    if [ $? -eq 0 ]
    then
        SOURCE_DEV=$i
    fi

    /sbin/powermt display dev=$i | grep ${TARGET_WWN} > /dev/null 2>&1
    
    if [ $? -eq 0 ]
    then
        TARGET_DEV=$i
    fi
done
rm -f /tmp/pdall

#echo SOURCE_DEV=${SOURCE_DEV}
#echo TARGET_DEV=${TARGET_DEV}

# Setup migration
out=`/sbin/powermig setup -tt hostcopy -src $SOURCE_DEV -tgt $TARGET_DEV -no`

if [ $? -ne 0 ]
then
    echo "Error happened while setting up the migration from ${SOURCE_DEV} to ${TARGET_DEV}"
    /bin/false
    exit
fi

HANDLE=`echo ${out} | grep Migration | sed -e 's/Migration Handle = \([0-9]*\)/\1/'`

#echo HANDLE=${HANDLE}

# Start syncing the migration
/sbin/powermig sync -hd ${HANDLE} -no > /dev/null 2>&1

if [ $? -ne 0 ]
then
    echo "Error happened while syncing the migration from ${SOURCE_DEV} to ${TARGET_DEV} with handle ${HANDLE}"

    # Cleanup the migration
    /sbin/powermig cleanup -hd ${HANDLE} -no > /dev/null 2>&1

    /bin/false
    exit
fi

# Loop till the sync is complete.  Just wait till you get sourceSelected state
# in powermig info output.
while true
do
    /sbin/powermig info -hd ${HANDLE} -query  | grep sourceSelected > /dev/null 2>&1

    if [ $? -eq 0 ]
    then
        break
    fi

    sleep 10
done

# Move the migration to targetSelected state
/sbin/powermig selectTarget -hd ${HANDLE} -no > /dev/null 2>&1

if [ $? -ne 0 ]
then
    echo "Error happened while moving the migration from ${SOURCE_DEV} to ${TARGET_DEV} with handle ${HANDLE} to targetSelected state"

    # Abort and cleanup the migration
    /sbin/powermig abort -hd ${HANDLE} -no > /dev/null 2>&1
    /sbin/powermig cleanup -hd ${HANDLE} -no > /dev/null 2>&1

    /bin/false
    exit
fi

# Commit the migration
/sbin/powermig commit -hd ${HANDLE} -no > /dev/null 2>&1

if [ $? -ne 0 ]
then
    echo "Error happened while cleaning up the migration from ${SOURCE_DEV} to ${TARGET_DEV} with handle ${HANDLE}"

    # Abort and cleanup the migration
    /sbin/powermig abort -hd ${HANDLE} -no > /dev/null 2>&1
    /sbin/powermig cleanup -hd ${HANDLE} -no > /dev/null 2>&1

    /bin/false
    exit
fi

# Clean up the migration
/sbin/powermig cleanup -hd ${HANDLE} -no > /dev/null 2>&1

/bin/true
