basepath=$(cd "$(dirname "$0")"; pwd)
mvn dbdeploy:update -f $basepath/pom.xml -Ddb.url=jdbc:mysql://localhost:3306/customer?characterEncoding=utf8  -Ddb.usr=dbAdmin -Ddb.pwd= |tee run.log
