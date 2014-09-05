#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

COLLECTION_NAME=collection1
SOLR_DEPLOYMENT_PATH=solr_deployment

DATA_PATH=${SOLR_DEPLOYMENT_PATH}/solr/${COLLECTION_NAME}/data

# (Unsuccessful) attempt to avoid Device or Resource Busy errors:
# echo Deleting old indexes...
# rm -rf ${DATA_PATH}.old
# echo Moving existing indexes...
# mv ${DATA_PATH} ${DATA_PATH}.old

echo Deleting existing indexes...
rm -rf ${DATA_PATH}

#echo Deleting TNO keys file...
#rm -f ${SOLR_DEPLOYMENT_PATH}/SecureCipherUtil.Keys.txt
#echo For now do NOT delete TNO keys file. Using well-known keys for simplicity of testing...
