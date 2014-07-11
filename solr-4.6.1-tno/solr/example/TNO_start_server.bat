@echo off

echo.
echo *** Trust No One demo v1.0 ***
echo.

echo Updating Solr WAR file...
if not exist webapps\nul ( mkdir webapps )
copy /y ..\dist\solr-4.6-SNAPSHOT.war webapps\solr.war

echo Deleting existing indexes...
rmdir /s/q solr\collection1\data > nul

echo Deleting TNO keys file...
del /q SecureCipherUtil.Keys.txt > nul

echo Deleting log files in example\logs...
del /q logs\* > nul

echo.
echo Starting Solr...
java -jar start.jar
