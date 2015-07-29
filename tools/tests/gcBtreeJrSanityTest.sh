#!/bin/sh
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# Note: this test should work on single node

declare ScriptDir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

declare Password=${Password:-ChangeMe}

declare WaitInitializeSeconds=300

declare WaitRecycleSeconds=600

declare user

declare cred

declare bucket

declare namespace

# compression is on, estimate compression rate at most is 1.5
deployOutput='./DeploymentOutput'
ctFile='/tmp/ct'
rrFile='/tmp/rr'

Usage()
{
    echo "----------------------------------------------------------------------------------------------"
    echo "Test BTREE & JOURNAL chunk CG. Pls. run it at a freshly installed single node testbed."
    echo "Test BTREE & JOURNAL chunk GC: gcBtreeJrSanityTest [BTREE|JOURNAL] cmd dataNodeIp"
    echo "You can choose Test to run full steps or just choose one step to run."
    echo "Test BTREE                    : Run following BTREE test steps for BTREE GC"
    echo "Test JOURNAL                  : Run following JOURNAL test steps for JOURNAL GC"
    echo "ChangeConfigs                 : Change config to GC testing mode config"
    echo "Restart                       : Restart data service"
    echo "InsertKvs                     : Insert key values to get number of unused BTREE/JOURNAL chunks"
    echo "WaitForDeletingChunks BTREE   : Wait for BTREE chunks to be recycled"
    echo "WaitForDeletingChunks JOURNAL : Wait for JOURNAL chunks to be recycled"
    echo "RestoreConfigs                : Restore configs before changed"
    echo "-----------------------------------------------------------------------------------------------"
    exit 1
}

SSH(){
    local ip=$1
    shift
    local cmd="$@"
    sshpass -p ${Password} ssh -q -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null root@${ip} ${cmd}
}

# change config for gc test and restart services
ChangeConfigs()
{
    local ip=$1
    SSH $ip 'cp /opt/storageos/conf/shared.object.properties /opt/storageos/conf/shared.object.properties.bak'
    SSH $ip 'sed -i "s/^object.RepoGcNtpSafeGuardMillis=.*/object.RepoGcNtpSafeGuardMillis=0/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.RepoGcDeleteJobScannerIdleMillis=.*/object.RepoGcDeleteJobScannerIdleMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.GcProgressRefreshIntervalMillis=.*/object.GcProgressRefreshIntervalMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.ChunkSize=.*/object.ChunkSize=65536/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.DTTableCapacity=.*/object.DTTableCapacity=100/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.BPlusTreeChunkReclaimDelay=.*/object.BPlusTreeChunkReclaimDelay=6000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.BPlusTreeOccupancyScanIntervalSec=.*/object.BPlusTreeOccupancyScanIntervalSec=30/g" /opt/storageos/conf/shared.object.properties'

    SSH $ip 'cp /opt/storageos/conf/common.object.properties /opt/storageos/conf/common.object.properties.bak'
    SSH $ip 'sed -i "s/^object.NumDirectoriesPerCoSForSystemDT=.*/object.NumDirectoriesPerCoSForSystemDT=2/g" /opt/storageos/conf/common.object.properties'
    SSH $ip 'sed -i "s/^object.NumDirectoriesPerCoSForUserDT=.*/object.NumDirectoriesPerCoSForUserDT=2/g" /opt/storageos/conf/common.object.properties'
    echo "node $ip has changed configs for gc testing"
}

# restart and wait all DTs on
Restart() {
    pushd ${ScriptDir}/../../webstorage
    ./deployment.sh ResetDb
    ./deployment.sh Provision
    popd
    sleep 300
}


# insert keys
InsertKvs()
{
    local ip=$1
    local insertKeyNumber=1000
    local valueSize=1024

    for j in `seq ${insertKeyNumber}`
    do
        # using pseudo random value to reduce compaction
        value=`cat /dev/urandom | tr -cd 'a-f0-9' | head -c ${valueSize}`
        randomKey=`cat /dev/urandom | tr -cd 'a-f0-9' | head -c 10`
        response=`./bucketkey create ${namespace} ${bucket} --ip ${ip} --uid ${user} --secret ${cred} key-${randomKey}-${j} ${value}`
        if [[ "${response}" =~ "bucket_key_create failed with code:" ]]
            then
                echo "failed to insert key-${randomKey}-${j} : ${value}, ${response}"
                exit
        fi
        echo "inseted key-${randomKey}-${j} : ${value}"
    done
}


QueryTables()
{
    local ip=${1}
    local tableName=${2}

    echo "$(curl -s http://${ip}:9101/diagnostic/${tableName}/1/ | grep table_detail | sed -e 's/<[^>]*>//g')"
}

DumpTables()
{
    local ip=${1}
    local tableName=${2}

    local tables=$(QueryTables ${ip} ${tableName})

    for table in ${tables}; do
        curl -s ${table};
    done;
}

SelectChunks()
{
    local ip=${1}
    local ctDump=$(DumpTables ${ip} CT)
    echo "${ctDump}" | sed -ne '/schemaType CHUNK /p; /sequenceNumber/p; /^status/p; /dataType/p' \
    | perl -pe 's/status:\s([A-Z]*)\n/ \1/' \
    | perl -pe 's/dataType:\s([A-Z]*)\n/ \1/' \
    | perl -pe 's/schemaType CHUNK chunkId\s([\-0-9a-z]*)\s?\n/ \1/' \
    | perl -pe 's/sequenceNumber: / /' \
    | awk '{ print $4 " " $3 " " $2 " " $1 }' | sort -g
    echo "finish selecting chunks"
}

SelectDeletingChunks()
{
    local chunkType=$1
    local ip=${2}
    local chunkTable=$(SelectChunks ${ip})
    echo "${chunkTable}" | grep $chunkType | grep DELETING | cut -d ' ' -f 4
}

VerifyReferencesDeleted()
{
    local ip=${1}
    local chunks="${2}"

    local references=$(DumpTables ${ip} BR)

    for chunk in ${chunks} ; do
        echo "${references}" | grep ${chunk} && echo "ERROR: Found reference from deleted chunk ${chunk}" && exit 1
    done

    echo "All references are deleted"
}
WaitForDeletingChunks()
{
    echo "Looking for $1 chunks in DELETING state"

    local ip=${2}
    local chunkList=$(SelectDeletingChunks ${ip})

#    echo "${chunkList}"

    local waitTime=0

    while [[ X"" == X"${chunkList}" ]]
    do
        if [[ "${waitTime}" -gt "${WaitRecycleSeconds}" ]]; then
            echo "Failed to get deleting chunks in ${WaitRecycleSeconds} seconds" && exit 1;
        fi

        echo "Still waiting for chunks in DELETING state to appear"
        sleep 30
        waitTime=$(($waitTime + 30))
        chunkList=$(SelectDeletingChunks $1 ${ip})
    done

    local chunkCount=$(echo "${chunkList}" | wc -l)

    echo "Found ${chunkCount} $1  chunks in DELETING state"

    VerifyReferencesDeleted ${ip} "${chunkList}"
}


RestoreConfigs()
{
    local ip=$1
    SSH $ip 'mv /opt/storageos/conf/shared.object.properties.bak /opt/storageos/conf/shared.object.properties'
    SSH $ip 'mv /opt/storageos/conf/common.object.properties.bak /opt/storageos/conf/common.object.properties'
    echo "node $ip has restored configs"
}

ParseDeployOutput()
{
    user=`grep user $deployOutput|cut -f 2 -d '='`
    cred=`grep secretkey $deployOutput|cut -f 2 -d '='`
    namespace=`grep namespace $deployOutput|cut -f 2 -d '='`
    bucket=`grep bucket $deployOutput|cut -f 2 -d '='|cut -f 1 -d ' '`
}

if [[ $# == 0 ]]
then
    Usage
fi

if [[ $1 == 'Test' ]]
then
    ChangeConfigs $3
    Restart $3
    ParseDeployOutput    
    InsertKvs $3
    WaitForDeletingChunks $2 ${3}
    RestoreConfigs $3
elif [[ $1 == 'ChangeConfigs' ]]
then
    ChangeConfigs $2
elif [[ $1 == 'Restart' ]]
then
    Restart $2
elif [[ $1 == 'InsertKvs' ]]
then
    ParseDeployOutput
    InsertKvs $2
elif [[ $1 == 'WaitForDeletingChunks' ]]
then
    ParseDeployOutput
    WaitForDeletingChunks $2 $3
elif [[ $1 == 'RestoreConfigs' ]]
then
    RestoreConfigs $2
elif [[ $1 == 'ShowChunks' ]]
then
    SelectChunks $2
fi
