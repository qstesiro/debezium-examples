# 编译
{
    # 正常运行
    alias mvn='mvn compile exec:java -Dexec.mainClass=io.debezium.examples.engine.EngineDemo'
    # 调试运行
    alias mvn='rm -f /tmp/dbz-demo-81002127.offset; rm -f /tmp/dbz-demo-81002127.dbhistory; set -m; mvnDebug compile exec:java -Dexec.mainClass=io.debezium.examples.engine.EngineDemo & pid=$!; jdb -connect com.sun.jdi.SocketAttach:hostname=localhost,port=1025; set +m; kill -SIGTERM -- -$pid'
}

# mysql
{
    mysql -h127.0.0.1 \
          -P3306 \
          -uroot \
          -pdebezium \
          -Dinventory \
          --prompt 'mysqluser> '

    set session lock_wait_timeout = 10;
    set session innodb_lock_wait_timeout = 10;
    set transaction isolation level repeatable read;

    show global variables like 'innodb_rollback_on_timeout';
    set session innodb_rollback_on_timeout = on;
}

# kafka
{
    kafka-console-consumer.sh --bootstrap-server localhost:9092 \
                              --topic dbz-demo-81002127.dbhistory \
                              --from-beginning

    kafka-console-consumer.sh --bootstrap-server localhost:9092 \
                              --topic dbz-demo-81002127.offset \
                              --from-beginning
}
