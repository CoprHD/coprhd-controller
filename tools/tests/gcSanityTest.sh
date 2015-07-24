#!/bin/sh  
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

# Note: this test should work on single node 

declare Password=${Password:-ChangeMe}
# this value will aplly on both repo chunk & journal chunk
declare ChunkRotationSize=80960
# chunk size
declare ChunkSize=2097152
# this should be the same with data client settings
declare WriterNumber=4
# key number should set with ChunkRotationSize, we need ensure journal chunk be sealed under ChunkRotationSize
declare KeysPerChunk=25

declare WaitInitializeSeconds=300

declare WaitSealedSeconds=300

declare WaitPositiveSeconds=600

declare WaitRecycleSeconds=600

declare user

declare cred

declare bucket

declare namespace

# compression is on, estimate compression rate at most is 1.5 
testKeyNumber=$(((KeysPerChunk * $WriterNumber * 15 + 9) / 10 ))
valueSize=$((($ChunkRotationSize + $KeysPerChunk - 1) / $KeysPerChunk)) 
deployOutput='./DeploymentOutput'
ctFile='/tmp/ct'
rrFile='/tmp/rr'

Usage()
{
    echo "-------------------------------------------------------"
    echo "Test repo GC recycle one repo chunk. Pls. run it at a freshly installed single node testbed."
    echo "gcSanityTest cmd dataNodeIp defaultUserCredential"
    echo "You can choose Test to run full steps or just choose one step to run."
    echo "Test                    : Run full folowing test steps"
    echo "ChangeConfigs           : Change config to GC testing mode config"
    echo "Restart                 : Restart data service"
    echo "InsertKvs               : Insert key values to fill at least one repo chunk"
    echo "InsertMpuKvs            : Insert mpu kvs to fill at least one repo chunk"
    echo "CheckChunkSealed        : Query and wait first repo chunk to be sealed"
    echo "CheckPositiveRef        : Check all positive references have been added"
    echo "DeleteKvs               : Delete previously inserted keys to recycle chunk"
    echo "CheckChunkRecycled      : Query and wait first repo chunk to be recycled" 
    echo "RestoreConfigs          : Restore configs before changed"
    echo "-------------------------------------------------------"
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
    SSH $ip 'sed -i "s/^object.EnableRepoGc=.*/object.EnableRepoGc=true/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.RepoGcJournalParserIdleMillis=.*/object.RepoGcJournalParserIdleMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.RepoGcNtpSafeGuardMillis=.*/object.RepoGcNtpSafeGuardMillis=0/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.RepoGcDeleteJobScannerIdleMillis=.*/object.RepoGcDeleteJobScannerIdleMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.RepoGcReclaimerIdleMillis=.*/object.RepoGcReclaimerIdleMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.GcProgressRefreshIntervalMillis=.*/object.GcProgressRefreshIntervalMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.GcProgressRefreshIntervalMillis=.*/object.GcProgressRefreshIntervalMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip 'sed -i "s/^object.GcProgressRefreshIntervalMillis=.*/object.GcProgressRefreshIntervalMillis=3000/g" /opt/storageos/conf/shared.object.properties'
    SSH $ip "sed -i "s/^object.ChunkSize=.*/object.ChunkSize=$ChunkSize/g" /opt/storageos/conf/shared.object.properties"
    SSH $ip "sed -i "s/^object.GcTestChunkRotationSize=.*/object.GcTestChunkRotationSize=$ChunkRotationSize/g" /opt/storageos/conf/shared.object.properties"
    SSH $ip 'sed -i "s/^object.CleanupJobDelayMillis=.*/object.CleanupJobDelayMillis=3000/g" /opt/storageos/conf/shared.object.properties'

    SSH $ip 'cp /opt/storageos/conf/common.object.properties /opt/storageos/conf/common.object.properties.bak'
    SSH $ip 'sed -i "s/^object.NumDirectoriesPerCoSForSystemDT=.*/object.NumDirectoriesPerCoSForSystemDT=2/g" /opt/storageos/conf/common.object.properties'
    SSH $ip 'sed -i "s/^object.NumDirectoriesPerCoSForUserDT=.*/object.NumDirectoriesPerCoSForUserDT=2/g" /opt/storageos/conf/common.object.properties'
    
    SSH $ip 'cp /opt/storageos/conf/blobsvc-log4j.properties /opt/storageos/conf/blobsvc-log4j.properties.bak'
    SSH $ip 'sed -i "$ a log4j.logger.com.emc.storageos.data.object.impl.gc=DEBUG" /opt/storageos/conf/blobsvc-log4j.properties'
    SSH $ip 'sed -i "$ a log4j.logger.com.emc.storageos.data.object.impl.file=DEBUG" /opt/storageos/conf/blobsvc-log4j.properties'

    SSH $ip 'cp /opt/storageos/conf/cm-log4j.properties /opt/storageos/conf/cm-log4j.properties.bak'
    SSH $ip 'sed -i "$ a log4j.logger.com.emc.storageos.data.object.impl.gc=DEBUG" /opt/storageos/conf/cm-log4j.properties'

    echo "node $ip has changed configs for gc testing"
}

# restart and wait all DTs on
Restart() {
    ../../webstorage/./deployment.sh ResetDb
    sleep 120
    ../../webstorage/./deployment.sh Provision
}

# insert keys
InsertKvs() 
{
    local ip=$1
    local insertKeyNumber=$((100 + $testKeyNumber))
    
    InsertKvsInternal $ip 1 $insertKeyNumber $valueSize
} 

# insert keys
InsertKvsInternal() 
{
    local ip=$1
    local start=$2
    local end=$3 

    for ((j=$start; j<=$end; j++)) 
    do 
        # using pseudo random value to reduce compaction
        value=`cat /dev/urandom | tr -cd 'a-f0-9' | head -c $valueSize`
        response=`./bucketkey create $namespace $bucket --ip $ip --uid $user --secret $cred key$j $value`
        if [[ "$response" =~ "bucket_key_create failed with code:" ]] 
            then 
                echo "failed to insert key$j : $value, $response"
                exit
        fi
        echo "inseted key$j : $value"
    done    
}

# insert mpu kvs
InsertMpuKvs() 
{
    local ip=$1   
    local insertKeyNumber=$((100 + $testKeyNumber))
    InsertMpuKvsInternal $ip 1 $insertKeyNumber
}

InsertMpuKvsInternal() 
{
    local ip=$1   
    local start=$2
    local end=$3 

    for ((j=$start; j<=$end; j++)) 
    do 
        # this cmd will insert 3.6k random data each part
        response=`./s3_multipart_upload.py insertmpukv $namespace $bucket --ip $ip --uid $user --secret $cred key$j --partnum 20`
        if [[ "$response" =~ "fail" ]] 
            then 
                echo "failed to insert mpu key$j : $value, $response"
                exit
        fi
        echo "inseted mpu key$j"
    done
}

PrintRecycleProgress()
{
    local ip=$1
    local chunkId=$2
    #dump rr
    rm -f /tmp/rrkeys
    for rr in `cat $rrFile`
    do
        curl http://$ip:9101/$rr/R$chunkId.RR.  -X GET >> /tmp/rrkeys 2>/dev/null
        if [ $? -ne 0 ]
        then 
            echo "fail to list references of chunk:$chunkId"
            exit
        fi
    done
    local positive=`cat /tmp/rrkeys|grep POSITIVE|wc -l`
    local negative=`cat /tmp/rrkeys|grep NEGATIVE|wc -l`
    echo "Journal parsing progress for $chunkId, positive:$positive, negative:$negative"
}

QueryWaitChunkStatus()
{
    local ip=$1
    local chunkId=$2
    local expect=$3
    local timeout=$4
    #dump ct 
    local waitTime=0
    while [ $waitTime -lt $timeout ]
    do 
        rm -f /tmp/ctkeys
        for ct in `cat $ctFile`
        do  
            echo "CT : $ct" >> /tmp/ctkeys
            curl http://$ip:9101/$ct/.CE. -H x-emc-show-value:gpb -X GET >> /tmp/ctkeys 2>/dev/null
            if [ $? -ne 0 ]
            then 
                echo "fail to list chunk table:$ct"
                exit
            fi
        done     
        local status=`grep $chunkId /tmp/ctkeys -A 10|grep status|cut -f 2 -d ' '`
        if [ $status == $expect ]
        then
            break
        fi 

        if [ $expect == 'DELETING' ]
        then
            PrintRecycleProgress $ip $chunkId
        fi
	    echo "chunk $chunkId has not reached $expect, sleep 30 seconds..."
	    sleep 30 
        waitTime=$(($waitTime + 30))
    done

    if [ $waitTime -ge $timeout ]
    then
        echo "chunk:$chunkId timeout to be $expect!"
        exit
    fi
}

# get first key's repo chunk and ensure it's sealed
QueryFirstChunkSealed()
{
    echo "query and wait until first repo chunk is sealed."
    local ip=$1
    QueryKeyRepoChunkSealed $ip key1 
}

# get key's repo chunk and ensure it's sealed
QueryKeyRepoChunkSealed()
{
    echo "query and wait until first repo chunk is sealed."
    local ip=$1
    local key=$2
    #get first keys' repo chunk
    local chunkId=`curl http://$ip:9101/gc/repoChunks/wuser1@SANITY.LOCAL/s3/$bucket/$key -X GET 2>/dev/null|cut -d ';' -f 1`
    if [ $? -ne 0 -o -z "$chunkId" -o "$chunkId" == " " ]
    then
        echo "fail to get first repo chunkId for $key" 
        exit
    fi
    QueryWaitChunkStatus $ip $chunkId "SEALED" $WaitSealedSeconds
    echo "chunk $chunkId sealed"
}

QueryPerPositiveReference()
{
    local ip=$1
    local key=$2
    local objectId=`curl http://$ip:9101/gc/getObjectId/wuser1@SANITY.LOCAL/s3/$bucket/$key -X GET 2>/dev/null`
    if [ $? -ne 0 ]
    then 
        echo "fail to get objectId for $key"
        exit
    fi
	local chunkId=`curl http://$ip:9101/gc/repoChunks/wuser1@SANITY.LOCAL/s3/$bucket/$key -X GET 2>/dev/null|cut -d ';' -f 1`
	if [ $? -ne 0 ]
    then 
        echo "fail to get first repo chunkId for $key"
        exit
    fi
    local waitTime=0
	while [ $waitTime -lt $WaitPositiveSeconds ]
	do
	    rm -f /tmp/rrkeys
	    for rr in `cat $rrFile`
	    do  
		    curl http://$1:9101/$rr/R$chunkId.RR.  -X GET >> /tmp/rrkeys 2>/dev/null
            if [ $? -ne 0 ]
            then 
                echo "fail to list references of chunk:$chunkId"
                exit
            fi
	    done     
	    
	    local positiveRef=`grep "$objectId" /tmp/rrkeys|grep POSITIVE`
	    if [ ! -z "$positiveRef" -a "$positiveRef" != " " ]
	    then
            echo "postive ref of $key has added, ref:$positiveRef"
            break
	    fi
	    echo "positive reference of $key hasn't been added, sleep 30 seconds..."
	    sleep 30
	    waitTime=$(($waitTime + 30))
	done
    
    if [ $waitTime -ge $WaitPositiveSeconds ]
    then
        echo "positvie reference of $key timed out!"
        exit
    fi
}

# check if positive references have been fully inserted
QueryPostiveReferencesFullyAdded()
{
    local ip=$1
    QueryPostiveReferencesFullyAddedInternal $ip 1 $testKeyNumber
}

QueryPostiveReferencesFullyAddedInternal()
{
    local ip=$1
    local start=$2
    local end=$3
 
    for ((i=$start; i<=$end; i++))
    do
        QueryPerPositiveReference $ip key$i
    done
}

# delete keys
DeleteKvs()
{
    local ip=$1
    local deleteKeyNumber=$((100 + $testKeyNumber))
    DeleteKvsInternal $ip 1 $deleteKeyNumber
}

DeleteKvsInternal()
{
    local ip=$1
    local start=$2
    local end=$3

    for ((j=$start; j<=$end; j++))
    do 
        # using pseudo random value to reduce compaction
        response=`./bucketkey delete $namespace $bucket --ip $ip --uid $user --secret $cred key$j` 
        if [[ "$response" =~ "bucket_key_delete failed with code:" ]] 
            then 
                echo "failed to delete key$j, $response"
                exit
        fi
        echo "deleted key$j"
    done    
}

# check negative progress until chunk enter into deleting 
QueryFirstChunkRecycled()
{
    local ip=$1
    QueryKeyRepoChunkRecycled $ip key1
}

QueryKeyRepoChunkRecycled()
{
    local ip=$1
    local key=$2
    echo "query and wait until first repo chunk is recycled."
    #get first keys' repo chunk
    local chunkId=`curl http://$ip:9101/gc/repoChunks/$user/$namespace/$bucket/$key -X GET 2>/dev/null|cut -d ';' -f 1`
    if [ $? -ne 0 -o -z "$chunkId" -o "$chunkId" == " " ]
    then 
        echo "fail to get first repo chunkId for $key"
        exit
    fi
    QueryWaitChunkStatus $ip $chunkId "DELETING" $WaitRecycleSeconds 
    echo "chunk $chunkId has been recycled!"
}

RestoreConfigs() 
{
    local ip=$1
    SSH $ip 'mv /opt/storageos/conf/blobsvc.object.properties.bak /opt/storageos/conf/blobsvc.object.properties'
    SSH $ip 'mv /opt/storageos/conf/common.object.properties.bak /opt/storageos/conf/common.object.properties'
    SSH $ip 'mv /opt/storageos/conf/cm-log4j.properties.bak /opt/storageos/conf/cm-log4j.properties'
    SSH $ip 'mv /opt/storageos/conf/cm-log4j.properties.bak /opt/storageos/conf/cm-log4j.properties'
    echo "node $ip has restored configs"
}

ParseDeployOutput()
{
    user=`grep user $deployOutput|cut -f 2 -d '='` 
    cred=`grep secretkey $deployOutput|cut -f 2 -d '='`
    namespace=`grep namespace $deployOutput|cut -f 2 -d '='`
    bucket=`grep bucket $deployOutput|cut -f 2 -d '='|cut -f 1 -d ' '`
}

ListCTs() 
{
    local ip=$1
    rm -f $ctFile
    curl http://$ip:9101/diagnostic/CT/1/ -H x-emc-show-value:gpb -X GET 2>/dev/null|grep id|cut -f 2 -d '>'|cut -f 1 -d '<'>>$ctFile
    curl http://$ip:9101/diagnostic/CT/2/ -H x-emc-show-value:gpb -X GET 2>/dev/null|grep id|cut -f 2 -d '>'|cut -f 1 -d '<'>>$ctFile
}

ListRRs()
{
    local ip=$1
    rm -f $rrFile
    curl http://$ip:9101/diagnostic/RR/0/ -H x-emc-show-value:gpb -X GET 2>/dev/null|grep id|cut -f 2 -d '>'|cut -f 1 -d '<'>>$rrFile
}

if [[ $# == 0 ]]
then
    Usage
fi

if [[ $1 == 'Test' ]]
then
    ChangeConfigs $2
    Restart $2
    ParseDeployOutput
    ListCTs $2  
    ListRRs $2
    
    # test normal keys gc
    InsertKvsInternal $2 1 250 
    QueryKeyRepoChunkSealed $2 key1
    QueryPostiveReferencesFullyAdded $2
    DeleteKvsInternal $2 1 250
    QueryKeyRepoChunkRecycled $2 key1 
    # test mpu keys gc
    InsertMpuKvsInternal $2 251 500
    QueryKeyRepoChunkSealed $2 key251
    DeleteKvsInternal $2 251 500
    QueryKeyRepoChunkRecycled $2 key251
 
    RestoreConfigs $2
elif [[ $1 == 'ChangeConfigs' ]]
then
    ChangeConfigs $2
elif [[ $1 == 'Restart' ]]
then 
    Restart $2
elif [[ $1 == 'InsertKvs' ]]
then
    ParseDeployOutput
    ListCTs $2  
    ListRRs $2
    #InsertKvs $2 
    InsertKvsInternal $2 1 250 
elif [[ $1 == 'InsertMpuKvs' ]]
then
    ParseDeployOutput
    ListCTs $2  
    ListRRs $2
    InsertMpuKvs $2 
elif [[ $1 == 'CheckChunkSealed' ]]    
then
    ParseDeployOutput
    ListCTs $2  
    ListRRs $2
    QueryFirstChunkSealed $2
elif [[ $1 == 'CheckPositiveRef' ]] 
then 
    ParseDeployOutput
    ListCTs $2  
    ListRRs $2
    QueryPostiveReferencesFullyAdded $2
elif [[ $1 == 'DeleteKvs' ]]
then
    ParseDeployOutput
    ListCTs $2
    ListRRs $2
    DeleteKvs $2 
elif [[ $1 == 'CheckChunkRecycled' ]]
then 
    ParseDeployOutput
    ListCTs $2
    ListRRs $2
    QueryFirstChunkRecycled $2
elif [[ $1 == 'RestoreConfigs' ]]
then
    RestoreConfigs $2
fi
