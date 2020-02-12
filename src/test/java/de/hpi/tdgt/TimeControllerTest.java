package de.hpi.tdgt;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedList;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
@Log4j2
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BootstrapUserTest.Initializer.class)
public class TimeControllerTest {

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
                //clear retained messages from last test
                client.publish(TestController.MQTT_CONTROL_TOPIC, new byte[0],0,true);
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
    public void canGetATimeOfATest() throws MalformedURLException, URISyntaxException {
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>());
        testRepository.save(test);
        val time = new ReportedTime();
        time.setFullEntry("{}");
        time.setTest(test);
        reportedTimeRepository.save(time);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test.getCreatedAt()+"/times").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTest = testRestTemplate.exchange(entity, String[].class).getBody();
        assertThat(returnedTest, notNullValue());
        assertThat(returnedTest[0], equalTo(time.getFullEntry()));
    }

    private final String exampleTime = "{\n" +
            "    \"testId\": 30847872,\n" +
            "    \"creationTime\": 76363421,\n" +
            "    \"times\":{\n" +
            "\t\"http://localhost:9000/\":{\n" +
            "\t\t\"POST\":{\n" +
            "\t\t\t\"story1\":{\n" +
            "\t\t\t\t\"minLatency\":\"10\",\n" +
            "\t\t\t\t\"avgLatency\":\"10\",\n" +
            "\t\t\t\t\"maxLatency\":\"10\",\n" +
            "\t\t\t\t\"throughput\":\"1\"\n" +
            "\t\t\t},\n" +
            "\t\t\t\"story2\":{\n" +
            "\t\t\t\t\"minLatency\":\"20\",\n" +
            "\t\t\t\t\"avgLatency\":\"20\",\n" +
            "\t\t\t\t\"maxLatency\":\"20\",\n" +
            "\t\t\t\t\"throughput\":\"1\"\n" +
            "\t\t\t}\n" +
            "\t\t}\n" +
            "\t}\n" +
            "    }\n" +
            "}\n";

    @Test
    public void canGetTwoTimesOfATest() throws MalformedURLException, URISyntaxException {
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>());
        testRepository.save(test);
        val time = new ReportedTime();
        time.setFullEntry("{}");
        time.setTest(test);
        val time2 = new ReportedTime();
        time2.setFullEntry("{\"testId\":12}");
        time2.setTest(test);
        reportedTimeRepository.save(time);
        reportedTimeRepository.save(time2);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test.getCreatedAt()+"/times").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTest = testRestTemplate.exchange(entity, String[].class).getBody();
        assertThat(returnedTest, notNullValue());
        assertThat(returnedTest[0], equalTo(time.getFullEntry()));
        assertThat(returnedTest[1], equalTo(time2.getFullEntry()));
    }

    @Test
    public void canCreateATestWithMqtt() throws MalformedURLException, URISyntaxException, JsonProcessingException, MqttException, InterruptedException {
        val test = new de.hpi.tdgt.test.Test(30847872, "TestConfig", true, new LinkedList<>());
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.getCreatedAt()).getBytes(StandardCharsets.UTF_8),2,false);
        Thread.sleep(200);
        client.publish(TimeController.MQTT_TIME_TOPIC, exampleTime.getBytes(StandardCharsets.UTF_8),2,false);
        Thread.sleep(1000);
        val retrievedTest = testRepository.findById(test.getCreatedAt()).orElse(null);
        assertThat(retrievedTest, notNullValue());
        assertThat(retrievedTest.getTimes(), notNullValue());
        assertThat(retrievedTest.getTimes(), not(emptyIterable()));
        assertThat(retrievedTest.getTimes().size(), is(1));
        assertThat(retrievedTest.getTimes().get(0).getFullEntry(), equalTo(exampleTime));
    }

}
