# 编译
{
    # 正常运行
    alias mvn='mvn compile exec:java -Dexec.mainClass=io.debezium.examples.engine.EngineDemo'
    # 调试运行
    alias mvn='set -m; mvnDebug compile exec:java -Dexec.mainClass=io.debezium.examples.engine.EngineDemo & pid=$!; jdb -connect com.sun.jdi.SocketAttach:hostname=localhost,port=1025; set +m; kill -SIGTERM -- -$pid'
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
