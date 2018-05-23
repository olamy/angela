#!/bin/bash
cwd=`dirname $0`
user=`whoami`
cd $cwd
mkdir -p /data/${user}/angela/ignite
export IGNITE_HOME=/data/${user}/angela/ignite
export JAVA_HOME=/nfs00/perf/java/jdk1.8.0_65
export KITS_DIR=/data/${user}/angela
export MAVEN_OPTS=-Xms4g -Xmx4g -XX:MaxPermSize=512m -XX:MaxDirectMemorySize=120g

killall -9 java
clear


exec mvn exec:java -DkitsDir=$KITS_DIR $@
