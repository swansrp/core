#!/bin/bash
basepath=$(cd "$(dirname "$0")"; pwd)  

#======================= 部署模式配置 ============================
DEPLOY_MODE=("docker" "jar" "jar" "jar" "jar")

# JAR部署相关配置
RestartCmd='start.sh restart'
StatusCmd='start.sh status'
TraceCmd='tail -f -n 1000'
APICmd='tail -f -n 1000'
ServerNum=5
remoteJarName='app.jar'
remoteLogName='spring.log'

localModuleDir='tanya'
localJarName='*-exec.jar'
remoteModuleDir='root/tanya'
remoteLogDir="root/tanya"

DevSSH=tanya
StgSSH=
TestSSH=
PRD1SSH=
PRD2SSH=

DOCKER_COMPOSE_FILE='docker-compose.yml'
DOCKER_START_SCRIPT='docker-start.sh'
DOCKER_CONTAINER_NAME='tanya-server'

localJarPath=$basepath/$localModuleDir/target/$localJarName
DevTargetPath="~/$remoteModuleDir"
StgTargetPath="~/$remoteModuleDir"
TestTargetPath="~/$remoteModuleDir"
PRD1TargetPath="~/$remoteModuleDir"
PRD2TargetPath="~/$remoteModuleDir"

DevLogPath="~/$remoteLogDir"
StgLogPath="~/$remoteLogDir"
TestLogPath="~/$remoteLogDir"
PRD1LogPath="~/$remoteLogDir"
PRD2LogPath="~/$remoteLogDir"

ServerSSH=("$DevSSH" "$StgSSH" "$TestSSH" "$PRD1SSH" "$PRD2SSH")
ServerTargetPath=("$DevTargetPath" "$StgTargetPath" "$TestTargetPath" "$PRD1TargetPath" "$PRD2TargetPath")
ServerLogPath=("$DevLogPath" "$StgLogPath" "$TestLogPath" "$PRD1LogPath" "$PRD2LogPath")

#======================= 功能函数 ============================

get_deploy_mode() {
    echo "${DEPLOY_MODE[$1]}"
}

# JAR部署上传
upload(){
    ssh ${ServerSSH[$1]} "mkdir -p ${ServerTargetPath[$1]}" # 确保远程目录存在
    scp ${localJarPath} ${ServerSSH[$1]}:${ServerTargetPath[$1]}/$remoteJarName
	return 0
}

# Docker部署上传
upload_docker(){
    echo "Uploading Docker deployment files to ${ServerSSH[$1]}..."
    ssh ${ServerSSH[$1]} "mkdir -p ${ServerTargetPath[$1]}"  # 确保远程目录存在
    ssh ${ServerSSH[$1]} "mkdir -p ${ServerTargetPath[$1]}/logs" # Docker日志目录

    scp ${localJarPath} ${ServerSSH[$1]}:${ServerTargetPath[$1]}/$remoteJarName
    scp ${basepath}/${DOCKER_COMPOSE_FILE} ${ServerSSH[$1]}:${ServerTargetPath[$1]}/
    scp ${basepath}/${DOCKER_START_SCRIPT} ${ServerSSH[$1]}:${ServerTargetPath[$1]}/
    scp ${basepath}/Dockerfile ${ServerSSH[$1]}:${ServerTargetPath[$1]}/
    ssh ${ServerSSH[$1]} "chmod +x ${ServerTargetPath[$1]}/${DOCKER_START_SCRIPT}"
	return 0
}

init() {
	if dirExsit "${ServerTargetPath[$1]}"; then
		send "mkdir ${ServerTargetPath[$1]}"
	fi
	if dirExsit "${ServerTargetPath[$1]}/pre/"; then
		send "mkdir ${ServerTargetPath[$1]}/pre/"
	fi
	if dirExsit "${ServerTargetPath[$1]}/backup/"; then
		send "mkdir ${ServerTargetPath[$1]}/backup/"
	fi
}
send() {
	ssh ${ServerSSH[$serverName]} $1
	return $?
}
dirExsit() {
	if send "test -d $1"; then
		return 1
	else
		return 0
	fi
}

enter() {
	ssh ${ServerSSH[$1]}
	return 0
}

restart() {
    mode=$(get_deploy_mode $1)
    if [ "$mode" = "docker" ]; then
        echo "Restarting Docker container on ${ServerSSH[$1]}..."
        ssh ${ServerSSH[$1]} "cd ${ServerTargetPath[$1]} && sh ${DOCKER_START_SCRIPT} restart"
    else
        echo "Restarting JAR application on ${ServerSSH[$1]}..."
        ssh ${ServerSSH[$1]} sh ${ServerTargetPath[$1]}/$RestartCmd
    fi
    if test -z $2
    then
        trace $1
    fi
    return 0
}

stop() {
	ssh ${ServerSSH[$1]} sh ${ServerTargetPath[$1]}/$StopCmd
	return 0
}

trace() {
    mode=$(get_deploy_mode $1)
    if [ "$mode" = "docker" ]; then
        echo "Viewing Docker container logs on ${ServerSSH[$1]}..."
        ssh ${ServerSSH[$1]} "cd ${ServerTargetPath[$1]} && sh ${DOCKER_START_SCRIPT} log"
    else
        ssh ${ServerSSH[$1]} $TraceCmd ${ServerLogPath[$1]}/$remoteLogName
    fi
    return 0
}

debug() {
	ssh ${ServerSSH[$1]} $TraceCmd ${ServerLogPath[$1]}/$remoteLogName | grep -iE -a5 "Exception"
	return 0
}

deploy() {
    mode=$(get_deploy_mode $1)
    echo "Deploy mode: $mode"
    if [ "$mode" = "docker" ]; then
        upload_docker $1
    else
        upload $1
    fi
    restart $1 $2
    return 0
}

status() {
    mode=$(get_deploy_mode $1)
    if [ "$mode" = "docker" ]; then
        echo "Checking Docker container status on ${ServerSSH[$1]}..."
        ssh ${ServerSSH[$1]} "cd ${ServerTargetPath[$1]} && sh ${DOCKER_START_SCRIPT} status"
    else
        ssh ${ServerSSH[$1]} sh ${ServerLogPath[$1]}/$StatusCmd
    fi
    return 0
}

allstatus() {
	echo ---------------------------
	for((i=0;i<$ServerNum;i++));
	do
	if [ -n "${ServerSSH[$i]}" ]; 
	then
		echo ${ServerSSH[$i]} 
		status i;
		echo ---------------------------
	fi
	done
	return 0
}

log() {
    mode=$(get_deploy_mode $1)
    mkdir -p "$basepath/log$1"  # 确保本地目录存在

    if [ "$mode" = "docker" ]; then
        echo "Downloading Docker container logs from ${ServerSSH[$1]}..."
        ssh ${ServerSSH[$1]} "mkdir -p ${ServerTargetPath[$1]}/logs"
        ssh ${ServerSSH[$1]} "docker logs ${DOCKER_CONTAINER_NAME} > ${ServerTargetPath[$1]}/logs/${remoteLogName} 2>&1"
        scp ${ServerSSH[$1]}:${ServerTargetPath[$1]}/logs/${remoteLogName} "$basepath/log$1/${remoteLogName}"
    else
        scp ${ServerSSH[$1]}:${ServerLogPath[$1]}/$remoteLogName "$basepath/log$1/$remoteLogName"
    fi
}

allLog() {
    mode=$(get_deploy_mode $1)
    mkdir -p "$basepath/log$1"  # 确保本地目录存在

    if [ "$mode" = "docker" ]; then
        ssh ${ServerSSH[$1]} "mkdir -p ${ServerTargetPath[$1]}/logs"
        scp -r ${ServerSSH[$1]}:${ServerTargetPath[$1]}/logs/*.log "$basepath/log$1/" 2>/dev/null || echo "No additional log files found"
    else
        scp -r ${ServerSSH[$1]}:${ServerLogPath[$1]}/log/ "$basepath/log$1/"
    fi
}

deployAll() {
	for((i=1;i<$ServerNum;i++));
	do
		if [ -n "${ServerSSH[$i]}" ]; 
		then
			echo "===== Start  to deploy: " ${ServerSSH[$i]} ...  ======
			deploy i "noTrace";
			echo "===== Finish to deploy: " ${ServerSSH[$i]} ...  ======
		fi
	done
	allstatus
	return 0
}

#======================= 主程序 ============================

serverName=$1
option=$2
if [ -z "$serverName" -a -z "$option" ]
then
echo "┌------------------------------┐"
echo "|----0. DEV Server ($(get_deploy_mode 0))--|"
if [ -n "$StgSSH" ];
then
echo "|----1. STG Server ($(get_deploy_mode 1))--|"
fi
if [ -n "$TestSSH" ];
then
echo "|----2. TEST Server ($(get_deploy_mode 2))-|"
fi
if [ -n "$PRD1SSH" ];
then
echo "|----3. PRD1 Server ($(get_deploy_mode 3))-|"
fi
if [ -n "$PRD2SSH" ];
then
echo "|----4. PRD2 Server ($(get_deploy_mode 4))-|"
fi
echo "|----5. All deploy-------------|"
echo "|----6. Status-----------------|"
echo "└------------------------------┘"
echo ""
echo "input your option: "
read serverName

if test $serverName -eq 5
then
deployAll
elif test $serverName -eq 6
then 
allstatus
else
echo "┌-----------------------┐"
echo "|----1. Deploy----------|"
echo "|----2. Trace log-------|"
echo "|----3. Restart---------|"
echo "|----4. Enter-----------|"
echo "|----5. Get Log---------|"
echo "|----6. All Log---------|"
echo "└-----------------------┘"
echo ""
echo "input your operation"
read option
fi
else
noTrace="noTrace"
fi

case $option in
1)
	deploy $serverName $noTrace
    ;;
2)
	trace $serverName
    ;;
3)
	restart $serverName
    ;;
4)
	enter $serverName
    ;;
5)
	log $serverName
	;;
6)
	allLog $serverName
	;;
esac

