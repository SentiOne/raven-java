#pamietaj o zmianie application.properties recznie w grailsowych appkac
#juz nie musisz pamietac :p - zolw
NEW=2.0.3-ZOLW-CUSTOM
OLD=2.0.2-ZOLW-CUSTOM
mvn versions:set -DoldVersion=$OLD -DnewVersion=$NEW
#sed -i "s/app.version=$OLD/app.version=$NEW/g" chimeo-web/application.properties
#sed -i "s/app.version=$OLD/app.version=$NEW/g" chimeo-web-admin/application.properties
mvn versions:commit
