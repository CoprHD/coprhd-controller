#!/bin/bash

workspace=$1
sanity_conf=$2
test_type=$3

if [[ -z "$workspace" || -z "$sanity_conf" || -z "$test_type" ]]; then
   echo "Usage: $0 <location of workspace> <location of sanity.conf> <type of test = all | catalog | block | protection | vmware>"
   exit 1
fi

cd ${workspace}

if [[ "$test_type" = "all" || "$test_type" = "catalog" ]]; then
    echo "Adding SampleService to the catalog"
    mkdir ${workspace}/vipr-portal/com.iwave.isa.content/src/java/com/emc/sa/service/sample/
    cp ${workspace}/vipr-portal/com.iwave.isa.content/src/test/com/emc/sa/service/sample/SampleService.j* ${workspace}/vipr-portal/com.iwave.isa.content/src/java/com/emc/sa/service/sample/
    echo "Building sasvc"
    ${workspace}/gradlew :com.iwave.isa.content:jar
    cp ${workspace}/build/gradle/vipr-portal/com.iwave.isa.content/libs/com.iwave.isa.content.jar /opt/storageos/lib/
    echo "Stopping sasvc"
    /usr/bin/systemctl stop storageos-sa.service
    echo "Starting sasvc"
    /usr/bin/systemctl start storageos-sa.service
fi

set > setvars.log
source ${sanity_conf} 
set > setvars2.log
for line in `diff setvars.log setvars2.log | cut -c3-1000 | egrep "$[A-Z]*"`; do export ${line} > /dev/null 2>&1; done
source ${sanity_conf} 

rm setvars.log setvars2.log

export CatalogTest=${test_type}
${workspace}/gradlew catalogSanity
