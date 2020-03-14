#!/bin/bash

if [[ -n $(java -version &> /dev/null) ]]; then
  echo "Java installation required"
  exit 1
fi

#cd "${0%/*}" || exit 1
SCRIPT_DIR=$(cd "${0%/*}" || exit 1 && realpath .)
WORKSPACE_DIR=$(realpath $SCRIPT_DIR/..)
JAR=$WORKSPACE_DIR/jvm-demo/build/libs/combo-jvm-demo-0.1-SNAPSHOT.jar
[ -f $JAR ] || (cd $WORKSPACE_DIR && $WORKSPACE_DIR/gradlew assemble)


re='^[0-9]+$'
if [[ $1 =~ $re ]] ; then
    for i in $(seq 1 $1)
    do
        java -jar $JAR "${@:2}"
    done
else
    java -jar $JAR "$@"
fi

