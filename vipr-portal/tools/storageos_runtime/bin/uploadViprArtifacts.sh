#
# Uploads ViPR artifacts to the ASD Maven Repository
#
# NOTE : In order for this to work, you will need the correct settings file in ~/.m2/settings.xml
#        otherwise you'll get 401 (Access Denied)

DIR=`dirname $0`
. ${DIR}/setEnv.sh

# NOTE : This is not the URL the build uses, this is a URL to the Artifactory LOCAL repository
MAVEN_REPO=http://pld-imgapprd01.isus.emc.com:8081/artifactory/iwave-prerelease-local/

VERSION=`cat $INSTALL_HOME/storageos/version.txt`
VERSION=${VERSION:5}

FILES=( "storageos-dbsvc" "storageos-dbclient" "storageos-errorhandling" "storageos-coordinatorsvc" "storageos-syssvc" "storageos-security" "storageos-apisvc" "storageos-models" "storageos-jmx")

for file in "${FILES[@]}"
do :
    mvn deploy:deploy-file -Durl=${MAVEN_REPO} -DgroupId=storageos -DartifactId=$file -Dversion=${VERSION} -Dfile=$INSTALL_HOME/storageos/lib/${file}.jar -Dpackaging=jar -DrepositoryId=ASD_Artifactory
done


