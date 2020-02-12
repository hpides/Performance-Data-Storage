package de.hpi.tdgt;

import de.hpi.tdgt.controller.AssertionController;
import de.hpi.tdgt.controller.TestController;
import de.hpi.tdgt.controller.TimeController;
import de.hpi.tdgt.test.*;
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
import java.util.LinkedList;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
@Log4j2
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BootstrapUserTest.Initializer.class)
public class AssertionControllerTest {

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
    private ReportedAssertionRepository assertionRepository;
    @Test
    public void canGetAnAssertionOfATest() throws MalformedURLException, URISyntaxException {
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>(), new LinkedList<>());
        testRepository.save(test);
        val assertion = new ReportedAssertion();
        assertion.setFullEntry("{}");
        assertion.setTest(test);
        assertionRepository.save(assertion);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test.getCreatedAt()+"/assertions").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTest = testRestTemplate.exchange(entity, String[].class).getBody();
        assertThat(returnedTest, notNullValue());
        assertThat(returnedTest[0], equalTo(assertion.getFullEntry()));
    }

    private final String exampleAssertion = "{ \n" +
            "  \"testId\":30847872,\n" +
            "    \"actuals\":{ \n" +
            "      \"postWithBody returns JSON\":{ \n" +
            "        \"key\":1,\n" +
            "        \"value\":[ \n" +
            "          \"application/json\"\n" +
            "        ]\n" +
            "      }\n" +
            "    }\n" +
            "}\n";

    @Test
    public void canGetTwoAssertionsOfATest() throws MalformedURLException, URISyntaxException {
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>(), new LinkedList<>());
        testRepository.save(test);
        val assertion = new ReportedAssertion();
        val assertion2 = new ReportedAssertion();
        assertion.setFullEntry("{}");
        assertion2.setFullEntry("{\"a\"=0}");
        assertion.setTest(test);
        assertion2.setTest(test);
        assertionRepository.save(assertion);
        assertionRepository.save(assertion2);
        val entity = RequestEntity.get(new URL("http://localhost:"+localPort+"/test/"+test.getCreatedAt()+"/assertions").toURI()).accept(MediaType.APPLICATION_JSON).build();
        val returnedTest = testRestTemplate.exchange(entity, String[].class).getBody();
        assertThat(returnedTest, notNullValue());
        assertThat(returnedTest[0], equalTo(assertion.getFullEntry()));
        assertThat(returnedTest[1], equalTo(assertion2.getFullEntry()));
    }

    @Test
    public void canCreateATimeEntryWithMqtt() throws MalformedURLException, URISyntaxException, JsonProcessingException, MqttException, InterruptedException {
        val test = new de.hpi.tdgt.test.Test(30847872, "TestConfig", true, new LinkedList<>(), new LinkedList<>());
        client.publish(TestController.MQTT_CONTROL_TOPIC, ("testStart "+test.getCreatedAt()).getBytes(StandardCharsets.UTF_8),2,false);
        Thread.sleep(200);
        client.publish(AssertionController.MQTT_ASSERTION_TOPIC, exampleAssertion.getBytes(StandardCharsets.UTF_8),2,false);
        Thread.sleep(1000);
        val retrievedTest = testRepository.findById(test.getCreatedAt()).orElse(null);
        assertThat(retrievedTest, notNullValue());
        assertThat(retrievedTest.getAssertions(), notNullValue());
        assertThat(retrievedTest.getAssertions(), not(emptyIterable()));
        assertThat(retrievedTest.getAssertions().size(), is(1));
        assertThat(retrievedTest.getAssertions().get(0).getFullEntry(), equalTo(exampleAssertion));
    }

}
