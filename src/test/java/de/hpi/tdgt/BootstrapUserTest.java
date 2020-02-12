package de.hpi.tdgt;

import de.hpi.tdgt.test.ReportedTime;
import de.hpi.tdgt.test.ReportedTimeRepository;
import de.hpi.tdgt.test.TestRepository;
import lombok.extern.log4j.Log4j2;
import lombok.val;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.junit.ClassRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@ExtendWith(SpringExtension.class)
@Log4j2
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(initializers = BootstrapUserTest.Initializer.class)
public class BootstrapUserTest {
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
    @Autowired
    private TestRepository testRepository;

    @Autowired
    private ReportedTimeRepository reportedTimeRepository;

    @Test
    public void injectionWorks(){
        assertThat(testRepository, notNullValue());
        assertThat(reportedTimeRepository, notNullValue());
    }

    @Test
    public void AddingOfTestsWorks(){
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig",true, new LinkedList<>(), new LinkedList<>());
        assertThat(testRepository.save(test), notNullValue());
    }

    @Test
    public void QueryingOfTestsWorks(){
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>(), new LinkedList<>());
        testRepository.save(test);
        assertThat(testRepository.existsById(test.getCreatedAt()), is(true));
    }

    @Test
    public void AddingReportedTimesWorks(){
        val test = new de.hpi.tdgt.test.Test(System.currentTimeMillis(), "TestConfig", true, new LinkedList<>(), new LinkedList<>());
        val time = new ReportedTime(test, "{}");
        testRepository.save(test);
        reportedTimeRepository.save(time);
        assertThat(testRepository.findById(test.getCreatedAt()).get().getTimes(), not(empty()));
    }

}
