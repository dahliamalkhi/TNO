#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

usage() {
  echo Usage: start_server.sh \<TNO\|vanilla\>
}

if [ "$#" -ne 1 ]; then
  usage
  exit 1
fi

SOLR_VARIANT=$1
if [ "${SOLR_VARIANT}" != "TNO" ] && [ "${SOLR_VARIANT}" != "vanilla" ]; then
  echo Unknown Solr variant ${SOLR_VARIANT}
  usage
  exit 1
fi

COLLECTION_NAME=collection1
SOLR_DEPLOYMENT_PATH=solr_deployment

SOLR_DIST_PATH=../../tno_build/solr/dist
if [ "${SOLR_VARIANT}" == "vanilla" ]; then
  SOLR_DIST_PATH=../../solr-4.6.1/solr/dist
fi

# solrconfig.xml is dependent of whether TNO is present or not.
SOLRCONFIG_FILE=solrconfig.${SOLR_VARIANT}.xml

# Schema file is now independent of whether TNO is present or not.
SCHEMA_FILE=schema.advisor.xml

# # Two (optional) arguments
# # TODO: Make it so you can use them both at once
# DEBUG_FLAGS=
# if [ "$#" -eq 1 ]; then
#   if [ "$1" == "debug" ]; then
#     # 'debug' enables the Java debugger.
#     DEBUG_FLAGS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5901"
#   elif [ "$1" == "vanilla" ]; then
#     # 'vanilla' uses the vanilla Solr distribution, rather than the TNO version.
#     SOLR_DIST_PATH=../../solr-4.6.1/solr/dist
#   else
#     echo "Only (optional) arguments are 'debug' and 'vanilla'. Exiting ..."
# 	exit 1
#   fi
# fi

# Copy selected solrconfig XML file to conf/solrconfig.xml
rm -rf solr_deployment/solr/collection1/conf/solrconfig.xml
cp solr_deployment/solr/collection1/conf/solrconfigs/${SOLRCONFIG_FILE} solr_deployment/solr/collection1/conf/solrconfig.xml

# Copy selected schema XML file to conf/schema.xml
rm -rf solr_deployment/solr/collection1/conf/schema.xml
cp solr_deployment/solr/collection1/conf/schemas/${SCHEMA_FILE} solr_deployment/solr/collection1/conf/schema.xml


echo
echo "**** Trust No One demo v1.1 ****"
echo

echo Updating Solr WAR file...
[ ! -d ${SOLR_DEPLOYMENT_PATH}/webapps ] && mkdir ${SOLR_DEPLOYMENT_PATH}/webapps
echo Using Solr dist from ${SOLR_DIST_PATH}
echo cp ${SOLR_DIST_PATH}/solr-4.6-SNAPSHOT.war ${SOLR_DEPLOYMENT_PATH}/webapps/solr.war
cp ${SOLR_DIST_PATH}/solr-4.6-SNAPSHOT.war ${SOLR_DEPLOYMENT_PATH}/webapps/solr.war

echo Deleting log files in example\logs...
rm -f ${SOLR_DEPLOYMENT_PATH}/logs/*

echo
echo Starting Solr...
( pushd ${SOLR_DEPLOYMENT_PATH} && java ${DEBUG_FLAGS} -XX:+PrintHeapAtGC -jar start.jar )
