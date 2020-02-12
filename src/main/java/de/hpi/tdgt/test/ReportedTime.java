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
public class ReportedTime {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne()
    private Test test;

    private String fullEntry;

    public ReportedTime(Test test, String fullEntry) {
        this.test = test;
        this.fullEntry = fullEntry;
    }
}
