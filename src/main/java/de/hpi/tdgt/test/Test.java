package de.hpi.tdgt.test;

import lombok.*;

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

    private String testConfig;

    private boolean isActive = true;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true, mappedBy = "test")
    private List<ReportedTime> times;
}
