#!/bin/bash
(set -o igncr) 2>/dev/null && set -o igncr; # this comment is required. this should be 2nd line of script.
# Above line makes this script ignore Windows line endings, to avoid this error without having to run dos2unix: 
#    $'\r': command not found
# As per http://stackoverflow.com/questions/14598753/running-bash-script-in-cygwin-on-windows-7

# Configure environment variables etc
source ../../scripts/env.sh

# Note that each file contains one Solr document per row (after first row which contains column field names)
# The present Advisor files have 500 Solr documents per file.

#DOCUMENT_SOURCE now defined in env.sh

NUM_DOCS_PER_FILE=500
echo "${NUM_DOCS_PER_FILE} Solr docs per file"

# There should be one argument
if [ "$#" -ne 1 ]; then
  echo "Usage:"
  echo "  ./bulk_post.sh <num docs to ingest>"
  echo
  echo "   Use -1 to ingest all available documents. (Will run for 24 hours or more.)"
  echo "Exiting..."
  exit 1
fi

NUM_DOCS_TO_INGEST=$1

# Ingest either specified number of docs (rounded up modulo NUM_DOCS_PER_FILE)
# or all available docs, if NUM_DOCS_TO_INGEST is -1
FULL_FILE_LIST=${DOCUMENT_SOURCE}/files_ordered_by_time.txt
FILES_TO_INGEST=./temp_files_to_ingest.txt
if [ "${NUM_DOCS_TO_INGEST}" -ne -1 ]; then
  NUM_FILES_TO_INGEST=$(( ( ${NUM_DOCS_TO_INGEST} + ${NUM_DOCS_PER_FILE} - 1 ) / ${NUM_DOCS_PER_FILE} ))
  echo "Ingesting ${NUM_FILES_TO_INGEST} files for ${NUM_DOCS_TO_INGEST} docs"
  head -n ${NUM_FILES_TO_INGEST} ${FULL_FILE_LIST} > ${FILES_TO_INGEST}
else
  echo "Ingesting all available files..."
  cp ${FULL_FILE_LIST} ${FILES_TO_INGEST}
fi

START_TIME=`date +%s`
START_TIME_NICE=`date -d @${START_TIME}`
# run for 8 hours max
#MAX_RUNTIME=28800
#JC
MAX_RUNTIME=100000
END_TIME=$(( ${START_TIME} + ${MAX_RUNTIME} ))
END_TIME_NICE=`date -d @${END_TIME}`
echo Currently ${START_TIME_NICE}. Will end at ${END_TIME_NICE}

FILE_COUNT=1
while read INPUT_FILE; do
  TIMESTAMP=`date`
  POST_COMMENT="Post: ${FILE_COUNT} at ${TIMESTAMP}"
  ./post_csv_file.sh ${DOCUMENT_SOURCE}/${INPUT_FILE} "${POST_COMMENT}"
  (( FILE_COUNT += 1 ))
#JC
  sleep 1
  if [[ $(date +%s) -ge ${END_TIME} ]]; then
    echo Exiting at ${END_TIME_NICE} - after ${MAX_RUNTIME} seconds.
    break
  fi  
done <${FILES_TO_INGEST}
