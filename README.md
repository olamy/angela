Distributed control framework to handle a Terracotta cluster and tests clients
a.k.a. TsaManager 2

## How to build

    mvn clean install -DskipTests

## Run specific tests

    mvn test -f integration-test/pom.xml -Dtest=TmsSecurityTest*

Be careful not to cd direclty into the module, you would not use the right kit version !

## Use specific location for Angela kits

    -DkitsDir=/Users/adah/kits/

## Do not delete after run

    -Dtc.qa.angela.skipUninstall=true

## Specify JVM vendor

    -Djava.build.vendor=zulu