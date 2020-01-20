package de.hpi.tdgt.controller;

import de.hpi.tdgt.test.Test;
import de.hpi.tdgt.test.TestRepository;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

@RestController
@Log4j2
public class TestController {
    public TestController(TestRepository repository, @Value("${mqtt.host}") String mqtt_host) throws MqttException {
        //only way to autowire it, field injection would not be working because evaluated after creation
        this.mqtt_host = mqtt_host;
        this.repository = repository;
        controlClient = prepareClient(MQTT_CONTROL_TOPIC, (s, message) -> {
            receivedControlMessage(s, message.toString());
        });
    }
    private final TestRepository repository;
    public static final String MQTT_CONTROL_TOPIC ="de.hpi.tdgt.control";
    @GetMapping(path="/test/{id}")
    public Test getTest(@PathVariable long id){
        return repository.findById(id).orElse(null);
    }


    private final String mqtt_host;
    private MqttClient controlClient;


    private MqttClient prepareClient(final String topic, IMqttMessageListener callback) throws MqttException {
        String publisherId = UUID.randomUUID().toString();
        MqttClient client = new MqttClient(mqtt_host,publisherId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        client.connect(options);
        client.subscribe(topic, callback);

        return client;
    }

    /**
     * Detects if the received control message is a test start or a test stop and updates the test accordingly.
     * @param topic MQTT Topic this message has been received on. Only messages from topic de.hpi.tdgt.control will be processed.
     * @param message The actual message. See Wiki of RequestGenerator for allowed values.
     */
    private void receivedControlMessage(String topic, String message){
        //double-check this is correct topic, else remaining code will fail
        if(!topic.equals(MQTT_CONTROL_TOPIC)){
            return;
        }
        String[] messageParts = message.split(" ");
        if(messageParts.length < 2){
            return;
        }
        if(messageParts[0].equals("testStart")){
            val test = new Test();
            test.setCreatedAt(Long.parseLong(messageParts[1]));

            //test config follows after start keyword and id, and might contain spaces --> collect back together
            if(messageParts.length > 2){
                val sb = new StringBuilder();
                var first = true;
                for(int i = 2; i < messageParts.length; i++){
                    //whitespace has been filtered out, re-append it
                    if(!first){
                        sb.append(' ');
                    }
                    first = false;
                    sb.append(messageParts[i]);
                }
                test.setTestConfig(sb.toString());
            }
            log.info("Test "+test.getCreatedAt() + " with description "+test.getTestConfig()+" started!");
            repository.save(test);
        }
        if(messageParts[0].equals("testEnd")){
            //assume that the
            Long testId;
            try {
                 testId = Long.parseLong(messageParts[1]);
            } catch (NumberFormatException e){
                log.warn("Received invalid test end: Could not parse test id \""+messageParts[1]+"\".", e);
                return;
            }
            val test = repository.findById(testId).orElse(null);
            if(test == null){
                log.warn("Received invalid test end: Could not find test with id \""+messageParts[1]+"\".");
                return;
            }
            test.setActive(false);
            repository.save(test);
        }
    }
}
