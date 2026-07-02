package tom.jerry.vocab.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tom.jerry.vocab.model.DailyStats;
import tom.jerry.vocab.repository.DailyStatsRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
@CrossOrigin(origins = "*")
public class StatsController {

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private tom.jerry.vocab.repository.VocabularyRepository vocabularyRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> response = new HashMap<>();

        LocalDate today = LocalDate.now();
        DailyStats todayStats = dailyStatsRepository.findByDate(today)
                .orElse(new DailyStats(null, today, 0, 0));

        response.put("todayWords", todayStats.getWordsReviewed());
        response.put("todayTime", todayStats.getStudyTimeSeconds() / 60);

        List<DailyStats> allStats = dailyStatsRepository.findAll();
        int cumulativeWords = allStats.stream().mapToInt(DailyStats::getWordsReviewed).sum();
        int cumulativeTime = allStats.stream().mapToInt(DailyStats::getStudyTimeSeconds).sum() / 60;

        response.put("cumulativeWords", cumulativeWords);
        response.put("cumulativeTime", cumulativeTime);

        long totalWords = vocabularyRepository.count();
        long learnedWords = vocabularyRepository.findAll().stream().filter(v -> v.getCurrentStage() > 0).count();

        response.put("totalWords", totalWords);
        response.put("learnedWords", learnedWords);

        // Fetch last 7 days for the calendar
        LocalDate sevenDaysAgo = today.minusDays(6);
        List<DailyStats> weeklyStats = dailyStatsRepository.findByDateBetweenOrderByDateAsc(sevenDaysAgo, today);
        response.put("weeklyStats", weeklyStats);
        response.put("allStats", allStats); // Return all stats for monthly charting and total sign-in computation

        return ResponseEntity.ok(response);
    }

    @PostMapping("/time")
    public ResponseEntity<Void> updateTime(@RequestBody Map<String, Integer> payload) {
        if (!payload.containsKey("seconds")) {
            return ResponseEntity.badRequest().build();
        }
        int seconds = payload.get("seconds");
        LocalDate today = LocalDate.now();
        DailyStats stats = dailyStatsRepository.findByDate(today)
                .orElse(new DailyStats(null, today, 0, 0));
        stats.setStudyTimeSeconds(stats.getStudyTimeSeconds() + seconds);
        dailyStatsRepository.save(stats);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/supplement")
    public ResponseEntity<Void> supplementSign(@RequestBody Map<String, String> payload) {
        if (!payload.containsKey("date")) {
            return ResponseEntity.badRequest().build();
        }
        try {
            LocalDate supplementDate = LocalDate.parse(payload.get("date"));
            DailyStats stats = dailyStatsRepository.findByDate(supplementDate)
                    .orElse(new DailyStats(null, supplementDate, 0, 0));
            if (stats.getWordsReviewed() == 0) {
                stats.setWordsReviewed(1);
            }
            if (stats.getStudyTimeSeconds() == 0) {
                stats.setStudyTimeSeconds(300); // 5 minutes mock
            }
            dailyStatsRepository.save(stats);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
