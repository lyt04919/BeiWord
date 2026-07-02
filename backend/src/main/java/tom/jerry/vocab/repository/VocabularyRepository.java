package tom.jerry.vocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tom.jerry.vocab.model.Vocabulary;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface VocabularyRepository extends JpaRepository<Vocabulary, Long> {
    // Fetch words that need to be reviewed today or failed yesterday, strictly excluding unlearned words (currentStage > 0)
    @org.springframework.data.jpa.repository.Query("SELECT v FROM Vocabulary v WHERE v.currentStage > 0 AND (v.nextReviewDate <= :date OR v.isFailedYesterday = true)")
    List<Vocabulary> findDailyReviewPool(@org.springframework.data.repository.query.Param("date") LocalDate date);

    java.util.Optional<Vocabulary> findByWord(String word);

    List<Vocabulary> findByCurrentStageAndIsMasteredFalse(Integer currentStage);

    List<Vocabulary> findByIsMasteredFalse();
}
