package de.hpi.tdgt.test;

import javax.persistence.*;

@Entity
public class TestData {
    @Id
    public long id;
    public int lastChange = 0;
    @Lob
    public String testConfig;
    public boolean isActive = true;

    public byte[] serializedStatistic;

    public TestData()
    {
    }

    public TestData(long id, String testConfig, byte[] serializedStatistic)
    {
        this.id = id;
        this.testConfig = testConfig;
        this.serializedStatistic = serializedStatistic;
    }
}
