package de.hpi.tdgt.test;

import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import javax.persistence.*;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Table
public class Test {
    @Id
    private long createdAt;
    @Lob
    private String testConfig;

    private boolean isActive = true;
    //workaround for multiplebagfetchexception, see https://stackoverflow.com/questions/17566304/multiple-fetches-with-eager-type-in-hibernate-with-jpa
    @Fetch(FetchMode.SELECT)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "test")
    private List<ReportedTime> times;

    @Fetch(FetchMode.SELECT)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "test")
    private List<ReportedAssertion> assertions;
}
