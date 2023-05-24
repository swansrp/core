basepath=$(cd "$(dirname "$0")"; pwd)
mvn dbdeploy:update -f $basepath/pom.xml -Ddb.url=jdbc:mysql://10.21.200.102:3306/customer?characterEncoding=utf8\&useSSL=false  -Ddb.usr=dbAdmin -Ddb.pwd=bhfae@Adm!n123 | tee run.log
