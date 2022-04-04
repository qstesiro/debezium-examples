package io.debezium.examples.engine;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.debezium.config.Configuration;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import  io.debezium.engine.ChangeEvent;

/*
  docker run       \
      --name mysql \
      --network dbz \
      --network-alias mysql \
      -p 3306:3306 \
      -e MYSQL_ROOT_PASSWORD=debezium \
      -e MYSQL_USER=mysqluser \
      -e MYSQL_PASSWORD=mysqluser \
      --rm -d \
      debezium/example-mysql:1.7

   # 客户端
   docker run \
       --net host \
       -it --rm \
       debezium/example-mysql:1.7 \
       mysql -h127.0.0.1 \
       -P3306 \
       -uroot \
       -pdebezium \
       -Dinventory \
       --prompt 'mysqluser> '
 */

public final class EngineDemo {

    public static void main(String[] args) throws Exception {
        new EngineDemo().run();
    }

    /*
      rm -f /tmp/dbz-demo-81002127.offset
      rm -f /tmp/dbz-demo-81002127.dbhistory
     */
    private void run() throws Exception {
        // Define the configuration for the Debezium Engine with MySQL connector...
        final Properties props = Configuration.create().build().asProperties();
        props.setProperty("name", "dbz-demo");
        props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
        props.setProperty("key.converter.schemas.enable", "false");
        props.setProperty("value.converter.schemas.enable", "false");
        props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
        {
            // 文件
            props.setProperty("offset.storage.file.filename", "/tmp/dbz-demo-81002127.offset");
            props.setProperty("offset.flush.interval.ms", "1000");
            // Kafka
            // props.setProperty("bootstrap.servers", "127.0.0.1:9092");
            // props.setProperty("offset.storage", "org.apache.kafka.connect.storage.KafkaOffsetBackingStore");
            // props.setProperty("offset.storage.topic", "dbz-demo-81002127.offset");
            // props.setProperty("offset.storage.partitions", "1");
            // props.setProperty("offset.storage.replication.factor", "1");
            // props.setProperty("offset.flush.interval.ms", "1000");
        }
        /* begin connector properties */
        {
            // 文件
            props.setProperty("database.history", "io.debezium.relational.history.FileDatabaseHistory");
            props.setProperty("database.history.file.filename", "/tmp/dbz-demo-81002127.dbhistory");
            // Kafka
            // props.setProperty("database.history.kafka.bootstrap.servers", "127.0.0.1:9092");
            // props.setProperty("database.history.kafka.topic", "dbz-demo-81002127.dbhistory");
            // props.setProperty("database.history", "io.debezium.relational.history.KafkaDatabaseHistory");
        }
        // mysql properties
        props.setProperty("database.server.id", "81002127");
        props.setProperty("tombstones.on.delete", "false");
        props.setProperty("database.server.name", "dbz-demo-81002127");
        // 锁模式
        // props.setProperty("snapshot.locking.mode", "minimal");
        props.setProperty("snapshot.locking.mode", "none");
        // 全局锁
        props.setProperty("database.hostname", "localhost");
        props.setProperty("database.port", "3306");
        props.setProperty("database.user", "root");
        props.setProperty("database.password", "debezium");
        props.setProperty("database.include.list", "inventory");
        props.setProperty("table.include.list", "inventory.customers");
        props.setProperty("snapshot.include.collection.list", "inventory.customers");
        // 表锁
        // props.setProperty("database.hostname", "10.138.228.243");
        // props.setProperty("database.port", "3306");
        // props.setProperty("database.user", "debezium");
        // props.setProperty("database.password", "vWrqedsPyIxll1A1yL");
        // props.setProperty("database.include.list", "console");
        // props.setProperty("table.include.list", "console.rule_account");
        // props.setProperty("snapshot.include.collection.list", "console.rule_account");
        //
        props.setProperty("include.query", "true");
        props.setProperty("tombstones.on.delete", "false");
        for (String k : props.stringPropertyNames()) {
            System.out.printf("%s: %s\n", k, props.getProperty(k));
        }
        // Create the engine with this configuration ...
        try (DebeziumEngine<ChangeEvent<String, String>> engine = DebeziumEngine.create(Json.class)
             .using(props)
             .notifying(record -> {
                     System.out.println(record);
                 })
             .build()) {
            // Run the engine asynchronously ...
            ExecutorService exec = Executors.newSingleThreadExecutor();
            exec.execute(engine);
            exec.awaitTermination(1000, TimeUnit.SECONDS);
            // Do something else or wait for a signal or an event
        }
        // Engine is stopped when the main code is finished
    }
}
