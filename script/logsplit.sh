this_path=$(cd `dirname $0`;pwd)  
  
cd $this_path  
echo $this_path  
current_date=`date -d "-1 day" "+%Y%m%d"`
delete_date=`date -d "-30 day" "+%Y%m%d"`  
echo $current_date  
split -b 32768000 -d -a 4 $this_path/spring.log   $this_path/log/spring_${current_date}_  
  
cat /dev/null > $this_path/spring.log
rm $this_path/log/spring_${delete_date}_*