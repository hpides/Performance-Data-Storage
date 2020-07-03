/*
 * WALT - A realistic load generator for web applications.
 *
 * Copyright 2020 Eric Ackermann <eric.ackermann@student.hpi.de>, Hendrik Bomhardt
 * <hendrik.bomhardt@student.hpi.de>, Benito Buchheim
 * <benito.buchheim@student.hpi.de>, Juergen Schlossbauer
 * <juergen.schlossbauer@student.hpi.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.hpi.tdgt.controller;

import de.hpi.tdgt.test.ReportedTime;
import de.hpi.tdgt.test.ReportedTimeRepository;
import de.hpi.tdgt.test.TestRepository;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;


@RestController
@Log4j2
public class TimeController extends MqttController {

    public static final String MQTT_TIME_TOPIC ="de.hpi.tdgt.times";
    public TimeController(TestRepository repository, ReportedTimeRepository timeRepository, @Value("${mqtt.host}") String mqtt_host) throws MqttException {
        //only way to autowire it, field injection would not be working because evaluated after creation
        this.mqtt_host = mqtt_host;
        this.mqtt_topic = MQTT_TIME_TOPIC;
        this.testRepository = repository;
        this.timeRepository = timeRepository;
        prepareClient((s, message) -> {
            receivedTimeMessage(s, message.toString());
        });
    }


    private final TestRepository testRepository;
    private final ReportedTimeRepository timeRepository;

    private final ObjectMapper mapper = new ObjectMapper();
    private void receivedTimeMessage(String topic, String message) {
        //just resetting persisted messages, ignore
        if(message.isEmpty()){
            return;
        }
        try {
            val time = mapper.readValue(message, Map.class);
            val testId = time.getOrDefault("testId", null);
            if(testId == null){
                log.error("Time has invalid format: "+message);
                return;
            }
            val test = testRepository.findById(Long.valueOf(testId.toString())).orElse(null);
            if(test == null){
                log.error("Message refers to non-existant test: "+testId+": "+message);
                return;
            }
            val timeRepr = new ReportedTime();
            timeRepr.setTest(test);
            timeRepr.setFullEntry(message);
            timeRepository.save(timeRepr);
        } catch (IOException e) {
            log.error("Unable to parse time", e);
        }

    }
}
