#
# Updates the local instance to the copy of a remote one
#
DIR=`dirname $0`

# Default to the Downloads directory, allowing it to be overriden in setEnv.sh
DOWNLOAD_DIR=~/Downloads

. ${DIR}/setEnv.sh

FETCH=true

#
# USAGE : Normally the file will be backed and fetched, but by specifying the --nofetch option, an existing
# /$DOWNLOAD_DIR/storageos.tar.gz file will be used instead
#

case $1 in
--nofetch)
    FETCH=false
    ;;
esac

echo SERVER=$OVF_SERVER
echo DOWNLOAD_DIR=$DOWNLOAD_DIR
echo NILE_HOME=$NILE_HOME

FILES=('nile-fabricapisvc.tar' 'nile-javaws-lib.tar'  'nile-zk-client.tar')

# Make sure target directory does not exist, is empty, or a previous installation
# (to stop potentially deleting something like the users HOME directory!
if [ -e $NILE_HOME ] ; then
    if [ ! -z "$(ls  $NILE_HOME)" ] && [ ! -e "$NILE_HOME/opt/nile" ]; then
        echo
        echo "ERROR : NILE_HOME directory is not empty, and doesn't contain nile files"
        echo
        exit 2
    fi
fi

if $FETCH ; then
    for FILE in "${FILES[@]}"; do
        echo "** Downloading $FILE to $DOWNLOAD_DIR/$FILE"
        rm $DOWNLOAD_DIR/$FILE
        curl http://cds-jenkins.isus.emc.com:8080/job/Nile_Fabric_Master_CI_Builds/lastSuccessfulBuild/artifact/build/ARTIFACTS/$FILE -o $DOWNLOAD_DIR/$FILE
    done
else
    for FILE in "${FILES[@]}"; do
        if [ ! -f $DOWNLOAD_DIR/$FILE ]; then
            echo "--nofetch specified, but no $DOWNLOAD_DIR/$FILE file found to unpack!"
        fi
    done
fi

echo
echo "** Removing current contents from $NILE_HOME"
if [ -h $NILE_HOME ]; then
    # NILE_HOME is a symbolic link, just remove the link
    rm $NILE_HOME
else
    # Recursively delete NILE_HOME
    rm -rf $NILE_HOME
fi
mkdir -p $NILE_HOME

echo
for FILE in "${FILES[@]}"; do
    echo "** Unpacking $FILE"
    tar xf $DOWNLOAD_DIR/$FILE -C $NILE_HOME
done;

if [ ! -z "${VIPR_SRC}" ]; then
    echo
    echo "** Copy models jar"
    cp $NILE_HOME/opt/nile/lib/nile-fabricapisvc_models.jar $VIPR_SRC/repo/nile-models-1.0.jar
fi

echo
echo "** Copying Extras"
cp $DIR/../extra/conf/* $NILE_HOME/opt/nile/conf

echo
echo "** Converting scripts/config"
${DIR}/convert.sh

echo
echo Update Complete!
