basepath=$(cd "$(dirname "$0")"; pwd)  

RestartCmd='start.sh restart'
StatusCmd='start.sh status'
StopCmd='start.sh stop'
TraceCmd='tail -f -n 1000'
APICmd='tail -f -n 1000'
ServerNum=4

remoteLogName='spring.log'
#======================= 修改这里 ============================
localModuleDir='tanya'
localJarName='*.jar'
remoteModuleDir='root/tanya'
remoteLogDir="root/tanya"

DevSSH=tanya
StgSSH=
TestSSH=
PRD1SSH=
PRD2SSH=
#======================= 修改这里 ============================

localJarPath=$basepath/$localModuleDir/target/$localJarName
DevTargetPath="/$remoteModuleDir"
StgTargetPath="/$remoteModuleDir"
TestTargetPath="/$remoteModuleDir"
PRD1TargetPath="/$remoteModuleDir"
PRD2TargetPath="/$remoteModuleDir"

DevLogPath="/$remoteLogDir"
StgLogPath="/$remoteLogDir"
TestLogPath="/$remoteLogDir"
PRD1LogPath="/$remoteLogDir"
PRD2LogPath="/$remoteLogDir"

ServerSSH=("$DevSSH" "$StgSSH" "$TestSSH" "$PRD1SSH" "$PRD2SSH")
ServerTargetPath=("$DevTargetPath" "$StgTargetPath" "$TestTargetPath" "$PRD1TargetPath" "$PRD2TargetPath")
ServerLogPath=("$DevLogPath" "$StgLogPath" "$TestLogPath" "$PRD1LogPath" "$PRD2LogPath")

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
upload(){
    scp ${localJarPath} ${ServerSSH[$1]}:${ServerTargetPath[$1]}/pre/
	return 0
}
enter() {
	ssh ${ServerSSH[$1]}
	return 0
}
restart() {
	ssh ${ServerSSH[$1]} sh ${ServerTargetPath[$1]}/$RestartCmd
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
	ssh ${ServerSSH[$1]} $TraceCmd ${ServerLogPath[$1]}/$remoteLogName
	return 0
}
debug() {
	ssh ${ServerSSH[$1]} $TraceCmd ${ServerLogPath[$1]}/$remoteLogName | grep -iE -a5 "Exception"
	return 0
}
deploy() {
	upload $1
	stop
	ssh ${ServerSSH[$1]} mv ${ServerTargetPath[$1]}/$localJarName ${ServerTargetPath[$1]}/backup/
	ssh ${ServerSSH[$1]} mv ${ServerTargetPath[$1]}/pre/$localJarName ${ServerTargetPath[$1]}/
	restart $1 $2
	return 0
}
status() {
	ssh ${ServerSSH[$1]} sh ${ServerLogPath[$1]}/$StatusCmd
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
	if [ ! -d "$basepath/log$1" ]; then
		mkdir "$basepath/log$1"
	fi
	scp ${ServerSSH[$1]}:${ServerLogPath[$1]}/$remoteLogName "$basepath/log$1/$remoteLogName"
}

allLog() {
	if [ ! -d "$basepath/log$1" ]; then
		mkdir "$basepath/log$1"
	fi
    scp -r ${ServerSSH[$1]}:${ServerLogPath[$1]}/log/ "$basepath/log$1/"
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

serverName=$1
option=$2

init $serverName
if [ -z "$serverName" -a -z "$option" ]
then


echo "┌------------------------------┐"
echo "|----0. 14 Server--------------|"
if [ -n "$StgSSH" ];
then
echo "|----1. 15 Server--------------|"
fi
if [ -n "$TestSSH" ];
then
echo "|----2. TEST Server------------|"
fi
if [ -n "$PRD1SSH" ];
then
echo "|----3. PRD1 Server------------|"
fi
if [ -n "$PRD2SSH" ];
then
echo "|----4. PRD2 Server------------|"
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
echo "|----7. Debug-----------|"
echo "└-----------------------┘"
echo ""
echo "input your operation"
read option
fi
else
noTrace="noTrace"
fi

case $option in
0)
	init $serverName
	;;
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
7)
	debug $serverName
	;;
esac

