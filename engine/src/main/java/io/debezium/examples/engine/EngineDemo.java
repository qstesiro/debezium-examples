package io.debezium.examples.engine;

import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.debezium.config.Configuration;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.format.Json;
import  io.debezium.engine.ChangeEvent;

public final class EngineDemo {

  public static void main(String[] args) throws Exception {
    new EngineDemo().run();
  }

  private void run() throws Exception {
    // Define the configuration for the Debezium Engine with MySQL connector...
    final Properties props = Configuration.create().build().asProperties();
    props.setProperty("name", "dbz-demo");
    props.setProperty("connector.class", "io.debezium.connector.mysql.MySqlConnector");
    props.setProperty("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore");
    props.setProperty("offset.storage.file.filename", "/tmp/offset");
    props.setProperty("offset.flush.interval.ms", "1000");
    /* begin connector properties */
    props.setProperty("database.history", "io.debezium.relational.history.FileDatabaseHistory");
    props.setProperty("database.history.file.filename", "/tmp/dbhistory");
    props.setProperty("database.hostname", "localhost");
    props.setProperty("database.port", "3306");
    props.setProperty("database.user", "debezium");
    props.setProperty("database.password", "dbz");
    props.setProperty("database.server.id", "123456");
    props.setProperty("database.server.name", "dbz-demo-123456");
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
