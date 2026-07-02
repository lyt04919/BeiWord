package tom.jerry.vocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tom.jerry.vocab.model.DailyStats;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, Long> {
    Optional<DailyStats> findByDate(LocalDate date);
    List<DailyStats> findByDateBetweenOrderByDateAsc(LocalDate startDate, LocalDate endDate);
}
