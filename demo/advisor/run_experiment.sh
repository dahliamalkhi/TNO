#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

usage() {
  echo Usage: run_experiment.sh \<TNO\|vanilla\> \<num terms\>
}

if [ "$#" -ne 2 ]; then
  usage
  exit 1
fi

SOLR_VARIANT=$1
if [ "${SOLR_VARIANT}" != "TNO" ] && [ "${SOLR_VARIANT}" != "vanilla" ]; then
  echo Unknown Solr variant ${SOLR_VARIANT}
  usage
  exit 1
fi

NUM_TERMS_STRING=$2
NUM_TERMS=
if [ "${NUM_TERMS_STRING}" == "1" ]; then
  NUM_TERMS=1
elif [ "${NUM_TERMS_STRING}" == "10" ]; then
  NUM_TERMS=10
elif [ "${NUM_TERMS_STRING}" == "100" ]; then
  NUM_TERMS=100
elif [ "${NUM_TERMS_STRING}" == "1K" ]; then
  NUM_TERMS=1000
elif [ "${NUM_TERMS_STRING}" == "10K" ]; then
  NUM_TERMS=10000
elif [ "${NUM_TERMS_STRING}" == "100K" ]; then
  NUM_TERMS=100000
elif [ "${NUM_TERMS_STRING}" == "1M" ]; then
  NUM_TERMS=1000000
elif [ "${NUM_TERMS_STRING}" == "10M" ]; then
  NUM_TERMS=10000000
else
  echo Unsupported number of terms ${NUM_TERMS_STRING}
  usage
  exit 1
fi
echo Running with ${NUM_TERMS} terms

# Check that Solr server is running and warn if it has more than 0 docs in collection1 index...

# -s -S : Don't show progress info, but do show errors.
# echo Checking committed doc count ...
curl -s -S -o temp_stats.txt "http://${SOLR_SERVER_HOSTNAME}${SOLR_SERVER_PORT}/solr/collection1/admin/mbeans?stats=true"

# Using XMLStarlet for XML processing
# http://xmlstar.sourceforge.net/doc/UG/xmlstarlet-ug.html
#
# Get the value of numDocs
# <int name="numDocs">0</int>
# Path determined using: $ xmlstarlet.exe el -v temp_stats.txt | grep numDocs
COMMITTED_DOCS=`xmlstarlet.exe sel --text --template --value-of "(response/lst/lst/lst/lst/int[@name='numDocs'])[1]" temp_stats.txt`

if [ ${COMMITTED_DOCS} -ne 0 ]; then
  echo Non-zero number of docs in collection1. Please kill the server and run reset_index_and_start_server.sh
  exit 1
fi

# # Curl output goes into ./query_output
rm -rf ./query_output
mkdir ./query_output

START_TIME=`date +%Y-%m-%d__%H_%M_%S`
EXPERIMENT_OUTPUT_DIR=results/${START_TIME}_${SOLR_VARIANT}_${NUM_TERMS_STRING}
if [ -d "${EXPERIMENT_OUTPUT_DIR}" ]; then
  echo ${EXPERIMENT_OUTPUT_DIR} already exists! Exiting... 
  exit 1
fi
mkdir "${EXPERIMENT_OUTPUT_DIR}"

./post_and_query.sh ${NUM_TERMS} > stdout.client.txt
cp stdout.server.txt ${EXPERIMENT_OUTPUT_DIR}
cp stdout.client.txt ${EXPERIMENT_OUTPUT_DIR}
echo Results copied to ${EXPERIMENT_OUTPUT_DIR}
