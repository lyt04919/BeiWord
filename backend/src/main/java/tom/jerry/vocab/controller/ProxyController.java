package tom.jerry.vocab.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/proxy")
@CrossOrigin(origins = "*")
public class ProxyController {

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/youdao")
    public ResponseEntity<String> getYoudaoData(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try {
            String url = "https://dict.youdao.com/jsonapi?q=" + word.trim();
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, org.springframework.http.HttpMethod.GET, entity, String.class);
            return ResponseEntity.ok().header("Content-Type", "application/json").body(response.getBody());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
