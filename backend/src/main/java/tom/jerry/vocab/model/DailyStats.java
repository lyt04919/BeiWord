package tom.jerry.vocab.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDate;

@Entity
@Table(name = "daily_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyStats {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(name = "words_reviewed", nullable = false)
    private Integer wordsReviewed = 0;

    @Column(name = "study_time_seconds", nullable = false)
    private Integer studyTimeSeconds = 0;
}
