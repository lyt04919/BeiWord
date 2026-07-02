package tom.jerry.vocab.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tom.jerry.vocab.model.Vocabulary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class YoudaoService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public YoudaoService() {
    }

    public Map<String, Object> fetchWordInfo(String word) {
        try {
            String url = "https://dict.youdao.com/jsonapi?q=" + word.trim();
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            Map<String, Object> result = new HashMap<>();

            // Extract Phonetics
            if (root.has("ec") && root.get("ec").has("word") && root.get("ec").get("word").isArray() && root.get("ec").get("word").size() > 0) {
                JsonNode ecWord = root.get("ec").get("word").get(0);
                if (ecWord.has("ukphone")) {
                    result.put("phoneticUk", "/" + ecWord.get("ukphone").asText() + "/");
                }
                if (ecWord.has("usphone")) {
                    result.put("phoneticUs", "/" + ecWord.get("usphone").asText() + "/");
                }
                
                // Extract Translation
                if (ecWord.has("trs") && ecWord.get("trs").isArray()) {
                    List<String> translations = new ArrayList<>();
                    for (JsonNode trNode : ecWord.get("trs")) {
                        if (trNode.has("tr") && trNode.get("tr").isArray() && trNode.get("tr").size() > 0) {
                            JsonNode tr = trNode.get("tr").get(0);
                            if (tr.has("l") && tr.get("l").has("i") && tr.get("l").get("i").isArray()) {
                                translations.add(tr.get("l").get("i").get(0).asText());
                            }
                        }
                    }
                    if (!translations.isEmpty()) {
                        result.put("translations", translations);
                    }
                }
            }

            // Extract Exam Types
            if (root.has("ec") && root.get("ec").has("exam_type") && root.get("ec").get("exam_type").isArray()) {
                List<String> exams = new ArrayList<>();
                for (JsonNode examNode : root.get("ec").get("exam_type")) {
                    exams.add(examNode.asText());
                }
                if (!exams.isEmpty()) {
                    result.put("exam_type", exams);
                }
            }

            // Extract Example Sentences
            if (root.has("blng_sents_part") && root.get("blng_sents_part").has("sentence-pair") && root.get("blng_sents_part").get("sentence-pair").isArray()) {
                List<Map<String, String>> examples = new ArrayList<>();
                JsonNode sentencePairs = root.get("blng_sents_part").get("sentence-pair");
                for (int i = 0; i < Math.min(sentencePairs.size(), 3); i++) {
                    JsonNode pair = sentencePairs.get(i);
                    if (pair.has("sentence-eng") && pair.has("sentence-translation")) {
                        Map<String, String> ex = new HashMap<>();
                        ex.put("en", pair.get("sentence-eng").asText());
                        ex.put("zh", pair.get("sentence-translation").asText());
                        examples.add(ex);
                    }
                }
                if (!examples.isEmpty()) {
                    result.put("examples", examples);
                }
            }

            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
