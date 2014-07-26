#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

COLLECTION_NAME=collection1
SOLR_DIST_PATH=../../solr-4.6.1-tno/solr/dist
SOLR_DEPLOYMENT_PATH=solr_deployment

echo Deleting existing indexes...
rm -rf ${SOLR_DEPLOYMENT_PATH}/solr/${COLLECTION_NAME}/data

echo Deleting TNO keys file...
rm -f ${SOLR_DEPLOYMENT_PATH}/SecureCipherUtil.Keys.txt
