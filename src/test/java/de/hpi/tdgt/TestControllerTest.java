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

package de.hpi.tdgt;

import de.hpi.tdgt.controller.TestController;
import de.hpi.tdgt.controller.TimeController;
import de.hpi.tdgt.test.ReportedTime;
import de.hpi.tdgt.test.ReportedTimeRepository;
import de.hpi.tdgt.test.TestRepository;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonProcessingException;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
@Log4j2
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BootstrapUserTest.Initializer.class)
public class TestControllerTest {

    @ClassRule
    public static PostgreSQLContainer postgreSQLContainer = new PostgreSQLContainer().withPassword("inmemory")
            .withUsername("inmemory");

    @LocalServerPort
    private int localPort;


    public TestRestTemplate testRestTemplate = new TestRestTemplate();

    public static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(@NotNull ConfigurableApplicationContext configurableApplicationContext) {
            postgreSQLContainer.start();
            TestPropertyValues values = TestPropertyValues.of(
                    "spring.datasource.url=" + postgreSQLContainer.getJdbcUrl(),
                    "spring.datasource.password=" + postgreSQLContainer.getPassword(),
                    "spring.datasource.username=" + postgreSQLContainer.getUsername()
            );
            values.applyTo(configurableApplicationContext);
        }
    }
    @Value("${mqtt.host}")
    private String mqtt_host;
    private MqttClient client;
    @BeforeEach
    public void prepareMqttClient() {
        if (client == null) {
            try {
                String publisherId = UUID.randomUUID().toString();
                //use memory persistence because it is not important that all packets are transferred and we do not want to spam the file system
                client = new MqttClient(mqtt_host, publisherId, new MemoryPersistence());
            } catch (MqttException e) {
                log.error("Error creating mqttclient in AssertionStorage: ", e);
            }
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            try {
                client.connect(options);
            } catch (MqttException e) {
                log.error("Could not connect to mqtt broker in AssertionStorage: ", e);
            }
        }
    }
    @AfterEach
    public void closeMqttClient() throws MqttException {
        client.disconnect();
        client = null;
    }

    @Autowired
    private TestRepository testRepository;

    @Autowired
    private ReportedTimeRepository reportedTimeRepository;

    @Test
    public void canGetATest() throws MalformedURLException, URISyntaxException {
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, null, null);
        testRepository.save(test);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test.getCreatedAt()).toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTest = testRestTemplate.exchange(entity, de.hpi.tdgt.test.Test.class).getBody();
        assertThat(returnedTest, equalTo(test));
    }

    @Test
    public void canCreateATestWithMqtt() throws MalformedURLException, URISyntaxException, JsonProcessingException, MqttException, InterruptedException {
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>(), new LinkedList<>());
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.getCreatedAt()).getBytes(StandardCharsets.UTF_8),2,true);
        Thread.sleep(200);
        assertThat(testRepository.findById(test.getCreatedAt()).orElse(null), notNullValue());
    }

    @Test
    public void canCreateATestWithMqttIncludingTestConfig() throws MalformedURLException, URISyntaxException, JsonProcessingException, MqttException, InterruptedException {
        val config = "{ a=\" b\"}";
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), config, true, new LinkedList<>(), new LinkedList<>());
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.getCreatedAt()+" "+test.getTestConfig()).getBytes(StandardCharsets.UTF_8),2,false);
        Thread.sleep(200);
        assertThat(testRepository.findById(test.getCreatedAt()).orElse(null).getTestConfig(), equalTo(config));
    }

    @Test
    public void canAcceptStopMessages() throws MalformedURLException, URISyntaxException, JsonProcessingException, MqttException, InterruptedException {
        val config = "{ a=\" b\"}";
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), config, true, new LinkedList<>(), new LinkedList<>());
        testRepository.save(test);
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testEnd "+test.getCreatedAt()).getBytes(StandardCharsets.UTF_8),2,true);
        Thread.sleep(200);
        assertThat(testRepository.findById(test.getCreatedAt()).orElse(null).isActive(), equalTo(false));
    }

    @Test
    public void canRetrieveFinishedTestIDs() throws InterruptedException, MalformedURLException, URISyntaxException {
        val config = "{ a=\" b\"}";
        val test1 = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), config, true, new LinkedList<>(), new LinkedList<>());
        val test2 = new de.hpi.tdgt.test.Test(System.currentTimeMillis() + 10, config, false, new LinkedList<>(), new LinkedList<>());
        testRepository.save(test1);
        testRepository.save(test2);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/tests/finished").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTests = testRestTemplate.exchange(entity, Long[].class).getBody();
        assertThat(returnedTests, notNullValue());
        assertThat(Arrays.asList(returnedTests), containsInRelativeOrder(test2.getCreatedAt()));
        assertThat(Arrays.asList(returnedTests), not(containsInRelativeOrder(test1.getCreatedAt())));
    }

    @Test
    public void canRetrieveRunningTestIDs() throws InterruptedException, MalformedURLException, URISyntaxException {
        val config = "{ a=\" b\"}";
        val test1 = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), config, true, new LinkedList<>(), new LinkedList<>());
        val test2 = new de.hpi.tdgt.test.Test(System.currentTimeMillis() + 10, config, false, new LinkedList<>(), new LinkedList<>());
        testRepository.save(test1);
        testRepository.save(test2);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/tests/running").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTests = testRestTemplate.exchange(entity, Long[].class).getBody();
        assertThat(returnedTests, notNullValue());
        assertThat(Arrays.asList(returnedTests), containsInRelativeOrder(test1.getCreatedAt()));
        assertThat(Arrays.asList(returnedTests), not(containsInRelativeOrder(test2.getCreatedAt())));
    }

}
