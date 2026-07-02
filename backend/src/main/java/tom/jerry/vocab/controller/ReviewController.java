package tom.jerry.vocab.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tom.jerry.vocab.model.Vocabulary;
import tom.jerry.vocab.service.ReviewService;

import java.util.List;

@RestController
@RequestMapping("/api/review")
@CrossOrigin(origins = "*") // Allow frontend to call
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/batch")
    public ResponseEntity<List<Vocabulary>> getNextBatch(@RequestParam(required = false, defaultValue = "review") String mode) {
        if ("learn".equalsIgnoreCase(mode)) {
            return ResponseEntity.ok(reviewService.getLearnBatch());
        }
        return ResponseEntity.ok(reviewService.getNextBatch());
    }

    @GetMapping("/pool")
    public ResponseEntity<List<Vocabulary>> getDailyPool() {
        return ResponseEntity.ok(reviewService.getDailyReviewPool());
    }

    @PostMapping("/{id}/result")
    public ResponseEntity<Void> submitResult(@PathVariable Long id, @RequestParam boolean passed) {
        reviewService.submitReviewResult(id, passed);
        return ResponseEntity.ok().build();
    }
}
