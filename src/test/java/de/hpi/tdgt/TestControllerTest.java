package de.hpi.tdgt;

import com.google.protobuf.InvalidProtocolBufferException;
import de.hpi.tdgt.controller.TestController;
import de.hpi.tdgt.stats.StatisticProtos;
import de.hpi.tdgt.test.TestData;
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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

import static de.hpi.tdgt.controller.TestController.MQTT_TIME_TOPIC;
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

    @Test
    public void canGetATest() throws MalformedURLException, URISyntaxException {
        val test = new TestData(System.currentTimeMillis(), "TestConfig", new byte[0]);
        testRepository.save(test);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test.id).toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTest = testRestTemplate.exchange(entity, TestData.class).getBody();
        assertThat(returnedTest, equalTo(test));
    }

    @Test
    public void canCreateATestWithMqtt() throws MqttException, InterruptedException {
        val test = new TestData(System.currentTimeMillis(), "TestConfig", new byte[0]);
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.id).getBytes(StandardCharsets.UTF_8),2,true);
        Thread.sleep(200);
        assertThat(testRepository.findById(test.id).orElse(null), notNullValue());
    }

    @Test
    public void canCreateATestWithMqttIncludingTestConfig() throws MqttException, InterruptedException {
        val config = "{ a=\" b\"}";
        val test = new TestData(System.currentTimeMillis(), config, new byte[0]);
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.id+" "+test.testConfig).getBytes(StandardCharsets.UTF_8),2,false);
        Thread.sleep(200);
        assertThat(testRepository.findById(test.id).orElse(null).testConfig, equalTo(config));
    }

    @Test
    public void canAcceptStopMessages() throws MqttException, InterruptedException {
        val config = "{ a=\" b\"}";
        val test = new TestData(System.currentTimeMillis(), config, new byte[0]);
        testRepository.save(test);
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testEnd "+test.id).getBytes(StandardCharsets.UTF_8),2,true);
        Thread.sleep(200);
        assertThat(testRepository.findById(test.id).orElse(null).isActive, equalTo(false));
    }

    @Test
    public void canRetrieveFinishedTestIDs() throws MalformedURLException, URISyntaxException {
        val config = "{ a=\" b\"}";
        val test1 = new TestData(System.currentTimeMillis(), config, new byte[0]);
        val test2 = new TestData(System.currentTimeMillis() + 10, config, new byte[0]);
        test2.isActive = false;
        testRepository.save(test1);
        testRepository.save(test2);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/tests/finished").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTests = testRestTemplate.exchange(entity, Long[].class).getBody();
        assertThat(returnedTests, notNullValue());
        assertThat(Arrays.asList(returnedTests), containsInRelativeOrder(test2.id));
        assertThat(Arrays.asList(returnedTests), not(containsInRelativeOrder(test1.id)));
    }

    @Test
    public void canRetrieveRunningTestIDs() throws MalformedURLException, URISyntaxException {
        val config = "{ a=\" b\"}";
        val test1 = new TestData(System.currentTimeMillis(), config, new byte[0]);
        val test2 = new TestData(System.currentTimeMillis() + 10, config, new byte[0]);
        test2.isActive = false;
        testRepository.save(test1);
        testRepository.save(test2);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/tests/running").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTests = testRestTemplate.exchange(entity, Long[].class).getBody();
        assertThat(returnedTests, notNullValue());
        assertThat(Arrays.asList(returnedTests), containsInRelativeOrder(test1.id));
        assertThat(Arrays.asList(returnedTests), not(containsInRelativeOrder(test2.id)));
    }

    @Test
    public void canGetATimeOfATest() throws MalformedURLException, URISyntaxException {
        val test1 = new TestData(System.currentTimeMillis(), "railgun", new byte[] {42});
        testRepository.save(test1);

        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test1.id+"/times").toURI()).accept(MediaType.APPLICATION_OCTET_STREAM).build();
        val returnedTest = testRestTemplate.exchange(entity, byte[].class).getBody();
        assertThat(returnedTest, notNullValue());
        assertThat(returnedTest[0], equalTo(42));
    }

    //when a static ID was used, other tests failed.
    @Test
    public void canCreateATimeEntryWithMqtt() throws MqttException, InterruptedException, InvalidProtocolBufferException {
        val test = new TestData(66, "TestConfig", new byte[0]);

        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.id).getBytes(StandardCharsets.UTF_8),2,true);


        val payload = StatisticProtos.Statistic.newBuilder()
                .setId(test.id)
                .setTotal(StatisticProtos.Population.newBuilder()
                        .setEp(StatisticProtos.Endpoint.newBuilder().setMethod(StatisticProtos.Endpoint.Method.POST).setUrl("colorblind").build())
                        .setLatestRequestTime(420)
                        .setMaxResponseTime(4269)
                        .setMinResponseTime(360)
                        .setNumFailures(0)
                        .setNumRequests(9001)
                        .setStartTime(-1)
                        .setTotalContentLength(1234)
                        .setTotalResponseTime(0xBADC0DED)
                ).build().toByteArray();

        client.publish(MQTT_TIME_TOPIC, payload,2,false);
        Thread.sleep(1000);

        val retrievedTest = testRepository.findById(test.id).orElse(null);
        assertThat(retrievedTest, notNullValue());


        assertThat(retrievedTest.serializedStatistic, equalTo(payload));
    }
}
