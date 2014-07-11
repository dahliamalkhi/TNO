@echo off

set COLLECTION_NAME=collection1
set SOLR_DIST_PATH=..\..\solr-4.6.1-tno\solr\dist
set SOLR_DEPLOYMENT_PATH=solr_deployment

echo.
echo *** Trust No One demo v1.1 ***
echo.

echo Updating Solr WAR file...
if not exist %SOLR_DEPLOYMENT_PATH%\webapps\nul ( mkdir %SOLR_DEPLOYMENT_PATH%\webapps )
copy /y %SOLR_DIST_PATH%\solr-4.6-SNAPSHOT.war %SOLR_DEPLOYMENT_PATH%\webapps\solr.war

echo Deleting existing indexes...
rmdir /s/q %SOLR_DEPLOYMENT_PATH%\solr\%COLLECTION_NAME%\data > nul

echo Deleting TNO keys file...
del /q %SOLR_DEPLOYMENT_PATH%\SecureCipherUtil.Keys.txt > nul

echo Deleting log files in example\logs...
del /q %SOLR_DEPLOYMENT_PATH%\logs\* > nul

echo.
echo Starting Solr...
pushd %SOLR_DEPLOYMENT_PATH% && (java -jar start.jar & popd)
rem pushd %SOLR_DEPLOYMENT_PATH% && (java -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5901 -jar start.jar & popd)
