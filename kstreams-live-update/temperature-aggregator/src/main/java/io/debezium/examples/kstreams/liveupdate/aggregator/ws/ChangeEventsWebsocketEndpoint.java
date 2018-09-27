/**
 *  Copyright 2018 Gunnar Morling (http://www.gunnarmorling.de/). See
 *  the copyright.txt file in the distribution for a full listing of all
 *  contributors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package io.debezium.examples.kstreams.liveupdate.aggregator.ws;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.websocket.CloseReason;
import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Consumed;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.kstream.Joined;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Serialized;

import io.debezium.examples.kstreams.liveupdate.aggregator.model.CountAndSum;
import io.debezium.examples.kstreams.liveupdate.aggregator.model.Station;
import io.debezium.examples.kstreams.liveupdate.aggregator.model.TemperatureMeasurement;
import io.debezium.examples.kstreams.liveupdate.aggregator.serdes.CountAndSumSerde;
import io.debezium.examples.kstreams.liveupdate.aggregator.serdes.LongKeySerde;
import io.debezium.examples.kstreams.liveupdate.aggregator.serdes.StationSerde;
import io.debezium.examples.kstreams.liveupdate.aggregator.serdes.TemperatureMeasurementSerde;

@ServerEndpoint("/example")
@ApplicationScoped
public class ChangeEventsWebsocketEndpoint {

    Logger log = Logger.getLogger( this.getClass().getName() );

    private final Set<Session> sessions = Collections.newSetFromMap( new ConcurrentHashMap<>() );

    private KafkaStreams streams;

    @PostConstruct
    public void startKStreams() {
        final String bootstrapServers = "kafka:9092";

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "streaming-aggregates-ddd");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.CACHE_MAX_BYTES_BUFFERING_CONFIG, 10*1024);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 1000);
        props.put(CommonClientConfigs.METADATA_MAX_AGE_CONFIG, 500);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        StreamsBuilder builder = new StreamsBuilder();

        Serde<Long> longKeySerde = new LongKeySerde();

        Serde<TemperatureMeasurement> temperatureMeasurementsSerde = new TemperatureMeasurementSerde();
        Serde<Station> stationSerde = new StationSerde();
        Serde<CountAndSum> countAndSumSerde = new CountAndSumSerde();

        KTable<Long, Station> stations = builder.table("dbserver1.inventory.stations", Consumed.with(longKeySerde, stationSerde));

        KStream<String, String> maxTemperaturesByStation = builder.stream(
                "dbserver1.inventory.temperature_measurements",
                Consumed.with(longKeySerde, temperatureMeasurementsSerde)
            )
            .selectKey((k, v) -> v.stationId)
            .join(
                    stations,
                    (value1, value2) -> {
                        value1.stationName = value2.name;
                        return value1;
                    },
                    Joined.with(Serdes.Long(), temperatureMeasurementsSerde, null)
             )
            .selectKey((k, v) -> v.stationName)
            .groupByKey(Serialized.with(Serdes.String(), temperatureMeasurementsSerde))
            .aggregate(
                    () -> new CountAndSum(), /* initializer */
                    (aggKey, newValue, aggValue) -> {
                        aggValue.count++;
                        aggValue.sum += newValue.value;
                        return aggValue;
                    },
                    Materialized.with(Serdes.String(), countAndSumSerde)
            )
            .mapValues(v -> {
                BigDecimal avg = BigDecimal.valueOf(v.sum / (double) v.count);
                avg = avg.setScale(1, RoundingMode.HALF_UP);
                return avg.doubleValue();
            })
            .mapValues(String::valueOf)
            .toStream()
            .peek((k, v) -> sessions.forEach(s -> {
                try {
                    s.getBasicRemote().sendText( "{ \"station\" : \"" + k + "\", \"average-temperature\" : " + v + " }" );
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }));

        maxTemperaturesByStation.to("average_temperatures_by_station", Produced.with(Serdes.String(), Serdes.String()));
//        maxTemperaturesByStation.print(Printed.toSysOut());

        streams = new KafkaStreams(builder.build(), props);
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));

        streams.start();
    }

    @PreDestroy
    public void closeKStreams() {
        streams.close();
    }

    @OnOpen
    public void open(Session session) {
        log.info( "Opening session: " + session.getId() );
        sessions.add(session);
    }

    @OnClose
    public void close(Session session, CloseReason c) {
        sessions.remove( session );
        log.info( "Closing: " + session.getId() );
    }
}