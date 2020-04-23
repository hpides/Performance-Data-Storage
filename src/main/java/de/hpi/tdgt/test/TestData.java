package de.hpi.tdgt.test;

import javax.persistence.*;

@Entity
@Table
public class TestData {
    @Id
    public long id;
    @Lob
    public String testConfig;
    public boolean isActive = true;

    public byte[] serializedStatistic;

    public TestData(long id, String testConfig)
    {
        this.id = id;
        this.testConfig = testConfig;
    }
}
