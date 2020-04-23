package de.hpi.tdgt.test;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TestRepository extends CrudRepository<TestData, Long> {
    public List<TestData> findAllByIsActiveEquals(boolean active);
}
