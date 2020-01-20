package de.hpi.tdgt.test;

import lombok.*;

import javax.persistence.*;
import java.util.Date;

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

    @Lob
    private String fullEntry;

    private Date recordingTime;

    public ReportedTime(Test test, String fullEntry, Date recordingTime) {
        this.test = test;
        this.fullEntry = fullEntry;
        this.recordingTime = recordingTime;
    }
}
