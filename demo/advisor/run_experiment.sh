#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

EXPERIMENT_NAME=$1

if [ "x${EXPERIMENT_NAME}x" == "xx" ]; then
  echo Must provide an experiment name... Exiting.
  exit 1
fi

# # Curl output goes into ./query_output
rm -rf ./query_output
mkdir ./query_output

START_TIME=`date +%Y-%m-%d__%H_%M_%S`
EXPERIMENT_OUTPUT_DIR=results/${START_TIME}_${EXPERIMENT_NAME}
if [ -d "${EXPERIMENT_OUTPUT_DIR}" ]; then
  echo ${EXPERIMENT_OUTPUT_DIR} already exists! Exiting... 
  exit 1
fi
mkdir "${EXPERIMENT_OUTPUT_DIR}"

# kill Solr server from previous run if still present...
echo Killing any old Solr server instance...
kill `ps | grep java | awk '{ print $1 }'`

./reset_index.sh
if [ -d "solr_deployment/solr/collection1/data" ]; then
  echo Can\'t delete old index. Exiting...
  exit 1
fi

sleep 1
echo Starting server ...
./start_server.sh > stdout.server.txt &
sleep 10
./post_and_query.sh > stdout.client.txt
cp stdout.* ${EXPERIMENT_OUTPUT_DIR}
echo Results copied to ${EXPERIMENT_OUTPUT_DIR}
echo Cleaning up ...
kill $!
