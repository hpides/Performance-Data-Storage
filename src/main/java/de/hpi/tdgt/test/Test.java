package de.hpi.tdgt.test;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    //do not export them as JSON, else there is a cycle
    //workaround for multiplebagfetchexception, see https://stackoverflow.com/questions/17566304/multiple-fetches-with-eager-type-in-hibernate-with-jpa
    //lists should not be settable,they are managed by hibernate
    @Fetch(FetchMode.SELECT)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "test")
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private List<ReportedTime> times;

    @Fetch(FetchMode.SELECT)
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "test")
    @JsonIgnore
    @Setter(AccessLevel.NONE)
    private List<ReportedAssertion> assertions;
}
