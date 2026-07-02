package tom.jerry.vocab.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tom.jerry.vocab.model.Vocabulary;
import tom.jerry.vocab.repository.VocabularyRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    @Autowired
    private VocabularyRepository vocabularyRepository;

    // In-memory pool for today's review
    private List<Vocabulary> dailyReviewPool;

    /**
     * Initializes the daily review pool. Called by the scheduler at 06:00.
     */
    @Transactional(readOnly = true)
    public void initializeDailyPool() {
        LocalDate today = LocalDate.now();
        dailyReviewPool = vocabularyRepository.findDailyReviewPool(today);
    }

    /**
     * Gets the full daily review pool.
     */
    public List<Vocabulary> getDailyReviewPool() {
        if (dailyReviewPool == null) {
            initializeDailyPool();
        }
        return dailyReviewPool;
    }

    /**
     * Gets the next batch of up to 10 words for review.
     */
    public List<Vocabulary> getNextBatch() {
        if (dailyReviewPool == null) {
            initializeDailyPool();
        }
        if (dailyReviewPool.isEmpty()) {
            return List.of();
        }
        return dailyReviewPool.stream().limit(10).collect(Collectors.toList());
    }

    /**
     * Gets the next batch of up to 10 words for learning.
     * Prioritizes words that have not been learned yet (currentStage = 0) and are not mastered.
     * If there are no stage 0 words, fetches other unmastered words.
     */
    @Transactional(readOnly = true)
    public List<Vocabulary> getLearnBatch() {
        List<Vocabulary> list = vocabularyRepository.findByCurrentStageAndIsMasteredFalse(0);
        return list.stream().limit(10).collect(Collectors.toList());
    }

    @Autowired
    private tom.jerry.vocab.repository.DailyStatsRepository dailyStatsRepository;

    /**
     * Handles the review result for a specific word.
     */
    @Transactional
    public void submitReviewResult(Long vocabularyId, boolean passed) {
        Optional<Vocabulary> optVocab = vocabularyRepository.findById(vocabularyId);
        if (optVocab.isEmpty()) {
            return;
        }

        Vocabulary vocab = optVocab.get();

        if (passed) {
            vocab.setCurrentStage(vocab.getCurrentStage() + 1);
            vocab.setIsFailedYesterday(false);
            
            if (vocab.getCurrentStage() >= 6) {
                vocab.setIsMastered(true);
            }
            
            vocab.setNextReviewDate(calculateNextReviewDate(vocab.getCurrentStage()));
            
            // Remove from pool if it was successful
            if (dailyReviewPool != null) {
                dailyReviewPool.removeIf(v -> v.getId().equals(vocabularyId));
            }
            
            recordDailyReview();
        } else {
            // Failed
            vocab.setIsFailedYesterday(true);
            vocab.setIsMastered(false); // Reset mastery if failed
            vocab.setCurrentStage(1); // Restart curve from stage 1 so it's reviewed tomorrow
            vocab.setNextReviewDate(LocalDate.now().plusDays(1)); // Review it tomorrow
            // It remains in the dailyReviewPool to be reviewed again within the inner loop TODAY
        }

        vocabularyRepository.save(vocab);
    }
    
    private void recordDailyReview() {
        LocalDate today = LocalDate.now();
        tom.jerry.vocab.model.DailyStats stats = dailyStatsRepository.findByDate(today)
                .orElse(new tom.jerry.vocab.model.DailyStats(null, today, 0, 0));
        stats.setWordsReviewed(stats.getWordsReviewed() + 1);
        dailyStatsRepository.save(stats);
    }

    /**
     * Ebbinghaus forgetting curve simulation.
     * Simple implementation mapping stages to days.
     */
    private LocalDate calculateNextReviewDate(int currentStage) {
        int[] intervals = {0, 1, 2, 4, 7, 15, 30}; // Days
        int daysToAdd = 30; // Default to 30 days if stage is high
        if (currentStage >= 0 && currentStage < intervals.length) {
            daysToAdd = intervals[currentStage];
        }
        return LocalDate.now().plusDays(daysToAdd);
    }
}
