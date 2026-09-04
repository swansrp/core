#!/bin/bash
#===============================================================================
# zz 服务器中间件一键部署脚本（CentOS 7 实测可用）
#
# 部署内容：
#   Docker 26.x + MySQL 8.0 + Redis 7.2 + Kafka 3.7(KRaft) + Elasticsearch 8.13
#   + Kafka UI + Kibana + XXL-JOB 2.4.2 + Nginx
#
# 镜像源踩坑记录（2026-09 实测）：
#   - yum 装 docker：用阿里云源 mirrors.aliyun.com/docker-ce（官方源国内不通）
#   - docker hub 加速：写入 daemon.json 的 registry-mirrors 对拉镜像生效
#     * docker.m.daocloud.io   ✅ 最稳，mysql/redis/es/kibana/xxl-job/nginx 都成功
#     * docker.1ms.run         ⚠️ 部分镜像 manifest 找不到
#     * docker.xuanyuan.me       ⚠️ 免费节点经常 toomanyrequests
#     * hub.rat.dev            ⚠️ 不稳定，下载中途会断
#     * dockerproxy.net        ✅ apache/kafka 从这里拉成功过
#   - bitnami/kafka 各镜像站都不收（denied/manifest unknown），
#     改用官方 apache/kafka:3.7.0（KRaft 原生，体积小）
#   - xxl-job 建表 SQL 必须用 2.4.2 tag 版本（gitee master 是新表结构，
#     与 2.4.2 镜像不兼容，报 Unknown column 't.title'）
#   - Kibana 8.x 不允许用 elastic 超管账号连接，必须用服务账号 token
#
# 网络要点：
#   - 云服务器无法访问自己的公网 IP（hairpin NAT），所以 Kafka 用双监听器：
#     9092 广播公网 IP（外部客户端）、29092 广播 localhost（本机客户端）
#   - 管理界面容器统一用 host 网络，直连 127.0.0.1 各端口，避免 DNS/广播地址问题
#
# 用法（在目标服务器上以 root 执行）：
#   bash install-middleware.sh
#   ※ 从 Windows 传过去的脚本先执行：sed -i 's/\r$//' install-middleware.sh
#   ※ 脚本幂等：已运行的容器会跳过，可重复执行
#===============================================================================
set -u

#----------------------- 可修改参数 -----------------------
MYSQL_PASSWORD='plsintec@mysql'
REDIS_PASSWORD='plsintec@redis'
ES_PASSWORD='plsintec@es'
XXLJOB_ACCESS_TOKEN='plsintec-token'
PUBLIC_IP=$(curl -s -m 10 ifconfig.me || curl -s -m 10 ip.sb)
MIRRORS="docker.m.daocloud.io dockerproxy.net docker.1ms.run hub.rat.dev docker.xuanyuan.me"

echo "==================================================="
echo "公网 IP: $PUBLIC_IP （Kafka 外部广播地址将使用它）"
echo "==================================================="

log() { echo -e "\n\033[32m=====> $1\033[0m"; }

# 带镜像源回退的拉取：pull <镜像名:tag>
pull() {
  local img=$1
  docker image inspect "$img" >/dev/null 2>&1 && { echo "已存在: $img"; return 0; }
  for m in $MIRRORS; do
    echo "尝试 $m/$img ..."
    if timeout 600 docker pull "$m/$img"; then
      docker tag "$m/$img" "$img"
      return 0
    fi
  done
  echo "!! 拉取失败: $img"
  return 1
}

running() { [ "$(docker inspect -f '{{.State.Running}}' "$1" 2>/dev/null)" = "true" ]; }

#----------------------- 1. 安装 Docker（阿里云源） -----------------------
if ! command -v docker >/dev/null 2>&1; then
  log "安装 Docker（阿里云 yum 源）"
  yum install -y yum-utils >/dev/null 2>&1
  yum-config-manager --add-repo https://mirrors.aliyun.com/docker-ce/linux/centos/docker-ce.repo
  sed -i 's|https://download.docker.com|https://mirrors.aliyun.com/docker-ce|' /etc/yum.repos.d/docker-ce.repo
  yum install -y docker-ce docker-ce-cli containerd.io
fi

log "配置镜像加速器并启动 Docker"
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<'EOF'
{"registry-mirrors":["https://docker.m.daocloud.io","https://docker.1ms.run","https://dockerproxy.net"]}
EOF
systemctl reset-failed docker.service 2>/dev/null
systemctl enable docker >/dev/null 2>&1
systemctl restart docker
docker info 2>/dev/null | grep -A3 "Registry Mirrors"

log "内核参数：vm.max_map_count（ES 需要 >= 262144）"
sysctl -w vm.max_map_count=262144
grep -q 'vm.max_map_count' /etc/sysctl.conf \
  && sed -i 's/vm.max_map_count.*/vm.max_map_count=262144/' /etc/sysctl.conf \
  || echo 'vm.max_map_count=262144' >> /etc/sysctl.conf

#----------------------- 2. MySQL 8.0 -----------------------
if ! running mysql; then
  log "部署 MySQL 8.0"
  pull mysql:8.0
  docker rm -f mysql 2>/dev/null
  docker run -d --name mysql --restart=always \
    -p 3306:3306 \
    -e MYSQL_ROOT_PASSWORD="$MYSQL_PASSWORD" \
    -e TZ=Asia/Shanghai \
    -v /data/mysql:/var/lib/mysql \
    mysql:8.0 \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_general_ci \
    --default-authentication-plugin=mysql_native_password
fi

#----------------------- 3. Redis 7.2 -----------------------
if ! running redis; then
  log "部署 Redis 7.2"
  pull redis:7.2
  docker rm -f redis 2>/dev/null
  docker run -d --name redis --restart=always \
    -p 6379:6379 \
    -v /data/redis:/data \
    redis:7.2 \
    redis-server --requirepass "$REDIS_PASSWORD" --appendonly yes
fi

#----------------------- 4. Kafka 3.7 KRaft -----------------------
# 注意：bitnami/kafka 各镜像源都拉不到，用官方 apache/kafka
if ! running kafka; then
  log "部署 Kafka 3.7.0（KRaft 单节点，双监听器）"
  pull apache/kafka:3.7.0
  docker rm -f kafka 2>/dev/null
  docker run -d --name kafka --restart=always \
    -p 9092:9092 -p 29092:29092 -p 9093:9093 \
    -e KAFKA_NODE_ID=1 \
    -e KAFKA_PROCESS_ROLES=broker,controller \
    -e KAFKA_LISTENERS=INTERNAL://0.0.0.0:29092,EXTERNAL://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
    -e KAFKA_ADVERTISED_LISTENERS=INTERNAL://localhost:29092,EXTERNAL://$PUBLIC_IP:9092 \
    -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
    -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT \
    -e KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL \
    -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 \
    -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
    -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
    -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
    -e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
    -v /data/kafka:/var/lib/kafka/data \
    apache/kafka:3.7.0
fi

#----------------------- 5. Elasticsearch 8.13 -----------------------
if ! running es; then
  log "部署 Elasticsearch 8.13.4"
  pull elasticsearch:8.13.4
  mkdir -p /data/es
  chown -R 1000:0 /data/es   # 容器内 uid=1000，不授权会 node.lock AccessDenied
  docker rm -f es 2>/dev/null
  docker run -d --name es --restart=always \
    -p 9200:9200 -p 9300:9300 \
    -e discovery.type=single-node \
    -e xpack.security.enabled=true \
    -e xpack.security.http.ssl.enabled=false \
    -e ES_JAVA_OPTS="-Xms512m -Xmx512m" \
    -e ELASTIC_PASSWORD="$ES_PASSWORD" \
    -v /data/es:/usr/share/elasticsearch/data \
    elasticsearch:8.13.4
fi

#----------------------- 6. Kafka UI -----------------------
if ! running kafka-ui; then
  log "部署 Kafka UI（:8080）"
  pull provectuslabs/kafka-ui:latest
  docker rm -f kafka-ui 2>/dev/null
  docker run -d --name kafka-ui --restart=always \
    --network host \
    -e KAFKA_CLUSTERS_0_NAME=local \
    -e KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS=127.0.0.1:29092 \
    -e DYNAMIC_CONFIG_ENABLED=true \
    provectuslabs/kafka-ui
fi

#----------------------- 7. Kibana -----------------------
# 坑：8.x 禁止 elastic 超管直连，需先创建服务账号 token
if ! running kibana; then
  log "部署 Kibana 8.13.4（:5601）"
  pull kibana:8.13.4
  echo "等待 ES 就绪..."
  for i in $(seq 1 30); do
    curl -s -u elastic:"$ES_PASSWORD" http://127.0.0.1:9200 >/dev/null && break
    sleep 5
  done
  TOKEN=$(curl -s -u elastic:"$ES_PASSWORD" -X POST \
    http://127.0.0.1:9200/_security/service/elastic/kibana/credential/token/kibana-token \
    | python -c "import sys,json;print(json.load(sys.stdin).get('token',{}).get('value',''))" 2>/dev/null)
  if [ -z "$TOKEN" ]; then
    TOKEN=$(curl -s -u elastic:"$ES_PASSWORD" \
      http://127.0.0.1:9200/_security/service/elastic/kibana/credential/token/kibana-token \
      | python -c "import sys,json;print(json.load(sys.stdin)['token']['value'])")
  fi
  docker rm -f kibana 2>/dev/null
  docker run -d --name kibana --restart=always \
    --network host \
    -e ELASTICSEARCH_HOSTS=http://127.0.0.1:9200 \
    -e ELASTICSEARCH_SERVICEACCOUNTTOKEN="$TOKEN" \
    -e I18N_LOCALE=zh-CN \
    kibana:8.13.4
fi

#----------------------- 8. XXL-JOB 2.4.2 -----------------------
if ! running xxl-job-admin; then
  log "部署 XXL-JOB 2.4.2（:8081）"
  pull xuxueli/xxl-job-admin:2.4.2
  # 建表 SQL 必须用 2.4.2 tag 版本（master 分支表结构不兼容）
  SQL=/root/tables_xxl_job_242.sql
  if [ ! -s "$SQL" ]; then
    curl -fsSL https://gitee.com/xuxueli0323/xxl-job/raw/2.4.2/doc/db/tables_xxl_job.sql -o $SQL || \
    curl -fsSL https://gh-proxy.com/https://raw.githubusercontent.com/xuxueli/xxl-job/2.4.2/doc/db/tables_xxl_job.sql -o $SQL
  fi
  echo "等待 MySQL 就绪..."
  for i in $(seq 1 30); do
    docker exec mysql mysql -uroot -p"$MYSQL_PASSWORD" -e "SELECT 1" >/dev/null 2>&1 && break
    sleep 5
  done
  docker exec mysql mysql -uroot -p"$MYSQL_PASSWORD" \
    -e "CREATE DATABASE IF NOT EXISTS xxl_job DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;" 2>/dev/null
  docker exec mysql mysql -uroot -p"$MYSQL_PASSWORD" -e "USE xxl_job; SHOW TABLES;" 2>/dev/null | grep -q xxl_job_info \
    || docker exec -i mysql mysql -uroot -p"$MYSQL_PASSWORD" xxl_job < $SQL
  docker rm -f xxl-job-admin 2>/dev/null
  docker run -d --name xxl-job-admin --restart=always \
    --network host \
    -e PARAMS="--spring.datasource.url=jdbc:mysql://127.0.0.1:3306/xxl_job?useUnicode=true&characterEncoding=UTF-8&autoReconnect=true&serverTimezone=Asia/Shanghai --spring.datasource.username=root --spring.datasource.password=$MYSQL_PASSWORD --server.port=8081 --xxl.job.accessToken=$XXLJOB_ACCESS_TOKEN" \
    xuxueli/xxl-job-admin:2.4.2
fi

#----------------------- 9. Nginx -----------------------
# 坑：空目录挂载会覆盖镜像内默认配置/页面，导致不监听 80 / 403
if ! running nginx; then
  log "部署 Nginx（:80/:443）"
  pull nginx:latest
  mkdir -p /data/nginx/{conf.d,html,logs}
  [ -f /data/nginx/conf.d/default.conf ] || \
    docker run --rm -v /data/nginx/conf.d:/dst nginx:latest cp /etc/nginx/conf.d/default.conf /dst/default.conf
  [ -f /data/nginx/html/index.html ] || \
    docker run --rm -v /data/nginx/html:/dst nginx:latest sh -c "cp -r /usr/share/nginx/html/* /dst/"
  docker rm -f nginx 2>/dev/null
  docker run -d --name nginx --restart=always \
    --network host \
    -v /data/nginx/html:/usr/share/nginx/html \
    -v /data/nginx/conf.d:/etc/nginx/conf.d \
    -v /data/nginx/logs:/var/log/nginx \
    nginx:latest
fi

#----------------------- 10. 验证 -----------------------
log "最终状态"
sleep 10
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
echo
echo "==================================================="
echo "  MySQL       $PUBLIC_IP:3306  root / $MYSQL_PASSWORD"
echo "  Redis       $PUBLIC_IP:6379  密码 $REDIS_PASSWORD"
echo "  Kafka       外部 $PUBLIC_IP:9092 / 本机 localhost:29092（无认证）"
echo "  ES          $PUBLIC_IP:9200  elastic / $ES_PASSWORD"
echo "  Kafka UI    http://$PUBLIC_IP:8080"
echo "  Kibana      http://$PUBLIC_IP:5601"
echo "  XXL-JOB     http://$PUBLIC_IP:8081/xxl-job-admin  (admin/123456, token: $XXLJOB_ACCESS_TOKEN)"
echo "  Nginx       http://$PUBLIC_IP:80"
echo "==================================================="
