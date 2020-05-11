package de.hpi.tdgt.controller;

import com.google.protobuf.InvalidProtocolBufferException;
import de.hpi.tdgt.stats.Statistic;
import de.hpi.tdgt.stats.StatisticProtos;
import de.hpi.tdgt.test.StrippedTest;
import de.hpi.tdgt.test.TestData;
import de.hpi.tdgt.test.TestRepository;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@Log4j2
public class TestController {

    public static final String MQTT_CONTROL_TOPIC = "de.hpi.tdgt.control";
    public static final String MQTT_TIME_TOPIC = "de.hpi.tdgt.times";

    private final TestRepository repository;

    public TestController(TestRepository repository, @Value("${mqtt.host}") String mqtt_host) throws MqttException {
        setupMqttClient(mqtt_host);
        this.repository = repository;
    }

    @RequestMapping(path = "/test/{id}", method = RequestMethod.GET)
    public ResponseEntity<TestData> getTest(@PathVariable long id) {
        TestData data = repository.findById(id).orElse(null);
        if(data == null)
            return new ResponseEntity<TestData>(HttpStatus.NO_CONTENT);
        return new ResponseEntity<TestData>(data, HttpStatus.OK);
    }

    @RequestMapping(path = "/tests", method = RequestMethod.GET)
    public List<StrippedTest> getTests() {
        List<StrippedTest> tests = new ArrayList<StrippedTest>(100);
        repository.findAll().forEach(t -> tests.add(new StrippedTest(t.id, t.lastChange)));
        return tests;
    }

    private void setupMqttClient(String mqtt_host) throws MqttException {
        String publisherId = UUID.randomUUID().toString();
        MqttClient client = new MqttClient(mqtt_host, publisherId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        client.connect(options);
        client.subscribe(MQTT_CONTROL_TOPIC, this::receivedControlMessage);
        client.subscribe(MQTT_TIME_TOPIC, this::receivedTimeMessage);
    }

    /**
     * Detects if the received control message is a test start or a test stop and updates the test accordingly.
     *
     * @param topic   MQTT Topic this message has been received on. Only messages from topic de.hpi.tdgt.control will be processed.
     * @param message The actual message. See Wiki of RequestGenerator for allowed values.
     */
    private void receivedControlMessage(String topic, MqttMessage message) {
        String[] messageParts = message.toString().split(" ");
        if (messageParts.length == 0) {
            return;
        }
        if (messageParts.length < 2) {
            log.error("Control message has too few parts! "+message.toString());
            return;
        }

        if (messageParts[0].equals("testStart")) {

            long testId = 0;
            try {
                testId = Long.parseLong(messageParts[1]);
            } catch (NumberFormatException e) {
                log.warn("Received invalid control message: Could not parse test id \"" + messageParts[1] + "\".", e);
                return;
            }
            var testConfig = "";

            //test config follows after start keyword and id, and might contain spaces --> collect back together
            if (messageParts.length > 2) {
                val sb = new StringBuilder();
                var first = true;
                for (int i = 2; i < messageParts.length; i++) {
                    //whitespace has been filtered out, re-append it
                    if (!first) {
                        sb.append(' ');
                    }
                    first = false;
                    sb.append(messageParts[i]);
                }
                testConfig = sb.toString();
            }
            try {
                val emptyStat = new Statistic(testId);

                repository.save(new TestData(testId, testConfig, emptyStat.Serialize(0).toByteArray()));
                log.info("Test " + testId + " started!");
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }
        else if (messageParts[0].equals("testEnd")) {
            long testId = 0;
            try {
                testId = Long.parseLong(messageParts[1]);
            } catch (NumberFormatException e) {
                log.warn("Received invalid control message: Could not parse test id \"" + messageParts[1] + "\".", e);
                return;
            }

            val test = repository.findById(testId).orElse(null);
            if (test == null) {
                log.warn("Received invalid test end: Could not find test with id \"" + messageParts[1] + "\".");
                return;
            }
            test.isActive = false;
            test.lastChange++;
            repository.save(test);
            log.info("Test " + testId + " ended!");
        }else
        {
            log.info("Unknown control msg: " + message.toString());
        }
    }

    private void receivedTimeMessage(String topic, MqttMessage message) {
        try {
            StatisticProtos.Statistic stats = StatisticProtos.Statistic.parseFrom(message.getPayload());
            val test = repository.findById(stats.getId()).orElse(null);
            if (test == null) {
                log.error("Received statistic for non existing test. Data will be lost");
                return;
            } else if (test.serializedStatistic == null || test.serializedStatistic.length == 0) {
                test.serializedStatistic = message.getPayload();
                repository.save(test);
            }else
            {
                //merge
                StatisticProtos.Statistic protoSA = StatisticProtos.Statistic.parseFrom(test.serializedStatistic);
                Statistic sa = new Statistic(protoSA);
                Statistic sb = new Statistic(stats);

                sa.Merge(sb);
                test.serializedStatistic = sa.Serialize(Math.max(protoSA.getSequenceNr(), stats.getSequenceNr())).toByteArray();
                repository.save(test);
            }
            log.info("Received statistic for Test " + stats.getId());

        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
}
