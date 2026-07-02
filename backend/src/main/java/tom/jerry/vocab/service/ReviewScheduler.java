package tom.jerry.vocab.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReviewScheduler {

    @Autowired
    private ReviewService reviewService;

    /**
     * Runs every day at 06:00 AM.
     */
    @Scheduled(cron = "0 0 6 * * ?")
    public void scheduleDailyReviewPool() {
        reviewService.initializeDailyPool();
        System.out.println("Daily review pool initialized at 06:00 AM.");
    }
}
