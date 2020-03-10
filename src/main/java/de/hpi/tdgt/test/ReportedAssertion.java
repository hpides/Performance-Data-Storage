package de.hpi.tdgt.test;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
@Table
@ToString
public class ReportedAssertion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    private Test test;
    @Lob
    private String fullEntry;

    public ReportedAssertion(Test test, String fullEntry) {
        this.test = test;
        this.fullEntry = fullEntry;
    }
}
