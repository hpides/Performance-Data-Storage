package de.hpi.tdgt.controller;

import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.UUID;

public class MqttController {
    protected String mqtt_host;
    protected String mqtt_topic;
    protected MqttClient prepareClient(IMqttMessageListener callback) throws MqttException {
        String publisherId = UUID.randomUUID().toString();
        MqttClient client = new MqttClient(mqtt_host,publisherId, new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        client.connect(options);
        client.subscribe(mqtt_topic, callback);
        return client;
    }
}
