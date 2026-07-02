package tom.jerry.vocab.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.CSVParser;

@Service
public class LocalDictService {

    private final Map<String, Map<String, Object>> dictionary = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        System.out.println("Loading ECDICT mini dataset into memory using Commons CSV...");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("ecdict_mini.csv").getInputStream(), StandardCharsets.UTF_8))) {
            
            CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(br);
            
            for (CSVRecord record : parser) {
                if (record.size() < 4) continue;
                
                String word = record.get("word");
                String phonetic = record.get("phonetic");
                String translation = record.get("translation").replace("\\n", "\n");
                String tag = record.get("tag");
                
                Map<String, Object> data = new HashMap<>();
                data.put("word", word);
                data.put("phoneticUk", phonetic);
                data.put("phoneticUs", phonetic);
                data.put("translations", Arrays.asList(translation.split("\n")));
                
                if (tag != null && !tag.isEmpty()) {
                    List<String> exams = new ArrayList<>();
                    List<String> allowedTags = Arrays.asList("IELTS", "TOEFL", "CET4", "CET6", "GRE");
                    for (String t : tag.split(" ")) {
                        String upper = t.trim().toUpperCase();
                        if (allowedTags.contains(upper)) {
                            exams.add(upper);
                        }
                    }
                    data.put("exam_type", exams);
                }
                
                dictionary.put(word.toLowerCase(), data);
            }
            System.out.println("Loaded " + dictionary.size() + " words from ECDICT offline database.");
        } catch (Exception e) {
            System.err.println("Failed to load ECDICT offline database.");
            e.printStackTrace();
        }
    }

    public Map<String, Object> fetchWordInfo(String word) {
        if (word == null) return null;
        return dictionary.get(word.trim().toLowerCase());
    }
}
