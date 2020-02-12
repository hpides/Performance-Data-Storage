package de.hpi.tdgt.test;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface TestRepository extends CrudRepository<Test, Long> {
    public List<Test> findAllByIsActiveEquals(boolean active);
}
