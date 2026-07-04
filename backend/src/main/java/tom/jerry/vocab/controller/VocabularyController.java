package tom.jerry.vocab.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tom.jerry.vocab.model.Vocabulary;
import tom.jerry.vocab.repository.VocabularyRepository;
import tom.jerry.vocab.service.ReviewService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tom.jerry.vocab.service.LocalDictService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import tom.jerry.vocab.model.Tag;

@RestController
@RequestMapping("/api/vocabularies")
@CrossOrigin(origins = "*")
public class VocabularyController {

    @Autowired
    private VocabularyRepository vocabularyRepository;
    
    @Autowired
    private ReviewService reviewService;
    
    @Autowired
    private LocalDictService localDictService;
    
    @Autowired
    private tom.jerry.vocab.repository.TagRepository tagRepository;
    
    @Autowired
    private tom.jerry.vocab.service.YoudaoService youdaoService;
    
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @GetMapping
    public List<Vocabulary> getAllVocabularies() {
        return vocabularyRepository.findAll();
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportVocabularies() {
        List<Vocabulary> allVocabs = vocabularyRepository.findAll();
        StringWriter sw = new StringWriter();
        try (CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT.builder().setHeader("Word", "Translation", "Phonetic_UK", "Phonetic_US", "Tags").build())) {
            for (Vocabulary v : allVocabs) {
                String tagsStr = v.getTags() != null ? v.getTags().stream().map(Tag::getName).collect(Collectors.joining(";")) : "";
                printer.printRecord(v.getWord(), v.getTranslation(), v.getPhoneticUk(), v.getPhoneticUs(), tagsStr);
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv"));
        headers.setContentDispositionFormData("attachment", "beiword_export.csv");
        
        // Add BOM for Excel UTF-8 compatibility
        String csvContent = "\ufeff" + sw.toString();
        
        return new ResponseEntity<>(csvContent, headers, HttpStatus.OK);
    }

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importVocabularies(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().build();
        
        int successCount = 0;
        int duplicateCount = 0;
        
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreHeaderCase(true).setTrim(true).build())) {
             
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            boolean hasTranslation = headerMap != null && headerMap.containsKey("Translation");
            boolean hasTags = headerMap != null && headerMap.containsKey("Tags");
            boolean hasPhoneticUk = headerMap != null && headerMap.containsKey("Phonetic_UK");
            boolean hasPhoneticUs = headerMap != null && headerMap.containsKey("Phonetic_US");
            
            for (CSVRecord record : csvParser) {
                String word = headerMap != null && headerMap.containsKey("Word") ? record.get("Word") : (record.size() > 0 ? record.get(0) : null);
                if (word == null || word.trim().isEmpty()) continue;
                word = word.trim();
                
                if (vocabularyRepository.findByWord(word).isPresent()) {
                    duplicateCount++;
                    continue;
                }
                
                Vocabulary v = new Vocabulary();
                v.setWord(word);
                
                String translation = hasTranslation && record.isMapped("Translation") ? record.get("Translation") : null;
                
                if (translation == null || translation.trim().isEmpty()) {
                    Map<String, Object> dictData = localDictService.fetchWordInfo(word);
                    if (dictData != null) {
                        v.setPhoneticUk((String) dictData.get("phoneticUk"));
                        v.setPhoneticUs((String) dictData.get("phoneticUs"));
                        if (dictData.get("translations") != null) {
                            Map<String, Object> transMap = new HashMap<>();
                            transMap.put("translations", dictData.get("translations"));
                            v.setTranslation(objectMapper.writeValueAsString(transMap));
                        }
                    }
                } else {
                    v.setTranslation(translation);
                    if (hasPhoneticUk && record.isMapped("Phonetic_UK")) v.setPhoneticUk(record.get("Phonetic_UK"));
                    if (hasPhoneticUs && record.isMapped("Phonetic_US")) v.setPhoneticUs(record.get("Phonetic_US"));
                }
                
                v = vocabularyRepository.save(v); // Save to get ID
                
                if (hasTags && record.isMapped("Tags")) {
                    String tagsStr = record.get("Tags");
                    if (tagsStr != null && !tagsStr.trim().isEmpty()) {
                        String[] tagNames = tagsStr.split(";");
                        for (String tagName : tagNames) {
                            tagName = tagName.trim();
                            if (!tagName.isEmpty()) {
                                Optional<Tag> optTag = tagRepository.findByName(tagName);
                                Tag tag;
                                if (optTag.isPresent()) {
                                    tag = optTag.get();
                                } else {
                                    tag = new Tag();
                                    tag.setName(tagName);
                                    tag.setColor("#6366f1");
                                    tag = tagRepository.save(tag);
                                }
                                v.getTags().add(tag);
                            }
                        }
                        vocabularyRepository.save(v);
                    }
                }
                successCount++;
            }
        } catch (Exception e) {
            Map<String, Object> res = new HashMap<>();
            res.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(res);
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("successCount", successCount);
        response.put("duplicateCount", duplicateCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/fetch-info")
    public ResponseEntity<Map<String, Object>> fetchWordInfo(@RequestParam String word) {
        if (word == null || word.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Map<String, Object> dictData = localDictService.fetchWordInfo(word.trim());
        if (dictData == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dictData);
    }

    @PostMapping
    public ResponseEntity<Vocabulary> addVocabulary(@RequestBody Vocabulary vocabulary) {
        if (vocabulary.getWord() == null || vocabulary.getWord().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        // Check for duplicates
        if (vocabularyRepository.findByWord(vocabulary.getWord()).isPresent()) {
            return ResponseEntity.status(409).body(null);
        }

        // Priority 1: Fetch from Youdao
        Map<String, Object> dictData = youdaoService.fetchWordInfo(vocabulary.getWord());
        
        // Priority 2: Fallback to offline ECDICT if Youdao fails or returns empty
        if (dictData == null || dictData.isEmpty()) {
            dictData = localDictService.fetchWordInfo(vocabulary.getWord());
        }
        
        if (dictData != null) {
            if (vocabulary.getPhoneticUk() == null || vocabulary.getPhoneticUk().isEmpty()) {
                vocabulary.setPhoneticUk((String) dictData.get("phoneticUk"));
            }
            if (vocabulary.getPhoneticUs() == null || vocabulary.getPhoneticUs().isEmpty()) {
                vocabulary.setPhoneticUs((String) dictData.get("phoneticUs"));
            }
            
            // If translations from frontend are missing or just a plain string, use Dict's
            try {
                if (vocabulary.getTranslation() == null || vocabulary.getTranslation().isEmpty()) {
                    if (dictData.containsKey("translations")) {
                        Map<String, Object> transMap = new HashMap<>();
                        transMap.put("translations", dictData.get("translations"));
                        vocabulary.setTranslation(objectMapper.writeValueAsString(transMap));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Auto-tagging based on exam_type
            if (dictData.containsKey("exam_type")) {
                List<String> exams = (List<String>) dictData.get("exam_type");
                for (String exam : exams) {
                    // Check if tag exists, if not create
                    tom.jerry.vocab.model.Tag tag = tagRepository.findAll().stream()
                            .filter(t -> t.getName().equalsIgnoreCase(exam))
                            .findFirst()
                            .orElseGet(() -> {
                                tom.jerry.vocab.model.Tag newTag = new tom.jerry.vocab.model.Tag();
                                newTag.setName(exam);
                                String hex = String.format("%06x", Math.abs(exam.hashCode()) % 0xFFFFFF);
                                newTag.setColor("#" + (hex.length() < 6 ? "007AFF" : hex));
                                return tagRepository.save(newTag);
                            });
                    if (vocabulary.getTags() == null) {
                        vocabulary.setTags(new java.util.HashSet<>());
                    }
                    vocabulary.getTags().add(tag);
                }
            }
            
            // Save example sentences
            if (dictData.containsKey("examples")) {
                try {
                    vocabulary.setExamples(objectMapper.writeValueAsString(dictData.get("examples")));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (vocabulary.getNextReviewDate() == null) {
            vocabulary.setNextReviewDate(LocalDate.now());
        }
        if (vocabulary.getCurrentStage() == null) {
            vocabulary.setCurrentStage(0);
        }
        if (vocabulary.getIsFailedYesterday() == null) {
            vocabulary.setIsFailedYesterday(false);
        }
        if (vocabulary.getIsMastered() == null) {
            vocabulary.setIsMastered(false);
        }
        
        Vocabulary saved = vocabularyRepository.save(vocabulary);
        
        // Add to today's pool if next review is today or earlier
        if (!saved.getNextReviewDate().isAfter(LocalDate.now())) {
            // we should re-initialize the pool to pick it up, or just add it
            reviewService.initializeDailyPool();
        }

        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Vocabulary> updateVocabulary(@PathVariable Long id, @RequestBody Vocabulary vocabularyDetails) {
        Optional<Vocabulary> opt = vocabularyRepository.findById(id);
        if (!opt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        
        Vocabulary existing = opt.get();
        
        // Update fields safely
        if (vocabularyDetails.getWord() != null) existing.setWord(vocabularyDetails.getWord());
        if (vocabularyDetails.getPhoneticUk() != null) existing.setPhoneticUk(vocabularyDetails.getPhoneticUk());
        if (vocabularyDetails.getPhoneticUs() != null) existing.setPhoneticUs(vocabularyDetails.getPhoneticUs());
        if (vocabularyDetails.getTranslation() != null) existing.setTranslation(vocabularyDetails.getTranslation());
        if (vocabularyDetails.getPhrases() != null) existing.setPhrases(vocabularyDetails.getPhrases());
        if (vocabularyDetails.getVocabType() != null) existing.setVocabType(vocabularyDetails.getVocabType());
        
        if (vocabularyDetails.getCurrentStage() != null) {
            existing.setCurrentStage(vocabularyDetails.getCurrentStage());
            existing.setIsMastered(vocabularyDetails.getCurrentStage() >= 5);
        }
        if (vocabularyDetails.getIsMastered() != null) {
            existing.setIsMastered(vocabularyDetails.getIsMastered());
            if (vocabularyDetails.getIsMastered() && (existing.getCurrentStage() == null || existing.getCurrentStage() < 5)) {
                existing.setCurrentStage(5);
            } else if (!vocabularyDetails.getIsMastered() && existing.getCurrentStage() != null && existing.getCurrentStage() >= 5) {
                existing.setCurrentStage(4);
            }
        }
        
        // Update tags safely to avoid constraint violations
        if (vocabularyDetails.getTags() != null) {
            java.util.Set<tom.jerry.vocab.model.Tag> newTags = new java.util.HashSet<>();
            for (tom.jerry.vocab.model.Tag t : vocabularyDetails.getTags()) {
                tom.jerry.vocab.model.Tag persistentTag = tagRepository.findById(t.getId()).orElse(null);
                if (persistentTag != null) {
                    newTags.add(persistentTag);
                }
            }
            existing.getTags().retainAll(newTags);
            existing.getTags().addAll(newTags);
        }

        Vocabulary saved = vocabularyRepository.save(existing);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteVocabulary(@PathVariable Long id) {
        Optional<Vocabulary> opt = vocabularyRepository.findById(id);
        if (opt.isPresent()) {
            Vocabulary v = opt.get();
            v.getTags().clear();
            
            for (Vocabulary related : v.getLinkedWords()) {
                related.getLinkedWords().remove(v);
            }
            v.getLinkedWords().clear();
            vocabularyRepository.delete(v);
            
            reviewService.initializeDailyPool(); // refresh pool
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/batch/delete")
    @Transactional
    public ResponseEntity<Void> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        List<Vocabulary> vocabularies = vocabularyRepository.findAllById(ids);
        for (Vocabulary v : vocabularies) {
            v.getTags().clear();
            
            for (Vocabulary related : v.getLinkedWords()) {
                related.getLinkedWords().remove(v);
            }
            v.getLinkedWords().clear();
            vocabularyRepository.delete(v);
        }
        
        reviewService.initializeDailyPool(); // refresh pool
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/add-tag")
    @Transactional
    public ResponseEntity<Void> batchAddTag(@RequestBody Map<String, Object> payload) {
        List<Integer> vocabIdsInt = (List<Integer>) payload.get("ids");
        Integer tagIdInt = (Integer) payload.get("tagId");
        if (vocabIdsInt == null || vocabIdsInt.isEmpty() || tagIdInt == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Long tagId = tagIdInt.longValue();
        Optional<tom.jerry.vocab.model.Tag> tagOpt = tagRepository.findById(tagId);
        if (!tagOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        tom.jerry.vocab.model.Tag tag = tagOpt.get();
        
        for (Object idObj : vocabIdsInt) {
            Long id = ((Number) idObj).longValue();
            vocabularyRepository.findById(id).ifPresent(v -> {
                v.getTags().add(tag);
                vocabularyRepository.save(v);
            });
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/remove-tag")
    @Transactional
    public ResponseEntity<Void> batchRemoveTag(@RequestBody Map<String, Object> payload) {
        List<Integer> vocabIdsInt = (List<Integer>) payload.get("ids");
        Integer tagIdInt = (Integer) payload.get("tagId");
        if (vocabIdsInt == null || vocabIdsInt.isEmpty() || tagIdInt == null) {
            return ResponseEntity.badRequest().build();
        }
        
        Long tagId = tagIdInt.longValue();
        Optional<tom.jerry.vocab.model.Tag> tagOpt = tagRepository.findById(tagId);
        if (!tagOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        tom.jerry.vocab.model.Tag tag = tagOpt.get();
        
        for (Object idObj : vocabIdsInt) {
            Long id = ((Number) idObj).longValue();
            vocabularyRepository.findById(id).ifPresent(v -> {
                v.getTags().remove(tag);
                vocabularyRepository.save(v);
            });
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch/set-stage")
    @Transactional
    public ResponseEntity<Void> batchSetStage(@RequestBody Map<String, Object> payload) {
        List<Integer> vocabIdsInt = (List<Integer>) payload.get("ids");
        Integer stage = (Integer) payload.get("stage");
        if (vocabIdsInt == null || vocabIdsInt.isEmpty() || stage == null) {
            return ResponseEntity.badRequest().build();
        }
        
        for (Object idObj : vocabIdsInt) {
            Long id = ((Number) idObj).longValue();
            vocabularyRepository.findById(id).ifPresent(v -> {
                v.setCurrentStage(stage);
                v.setIsMastered(stage >= 5);
                vocabularyRepository.save(v);
            });
        }
        reviewService.initializeDailyPool(); // refresh pool
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/links/{linkedId}")
    @Transactional
    public ResponseEntity<Void> addLink(@PathVariable Long id, @PathVariable Long linkedId) {
        if (id.equals(linkedId)) return ResponseEntity.badRequest().build();
        
        Optional<Vocabulary> opt1 = vocabularyRepository.findById(id);
        Optional<Vocabulary> opt2 = vocabularyRepository.findById(linkedId);
        
        if (opt1.isPresent() && opt2.isPresent()) {
            Vocabulary v1 = opt1.get();
            Vocabulary v2 = opt2.get();
            
            v1.getLinkedWords().add(v2);
            v2.getLinkedWords().add(v1);
            
            vocabularyRepository.save(v1);
            vocabularyRepository.save(v2);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}/links/{linkedId}")
    @Transactional
    public ResponseEntity<Void> removeLink(@PathVariable Long id, @PathVariable Long linkedId) {
        Optional<Vocabulary> opt1 = vocabularyRepository.findById(id);
        Optional<Vocabulary> opt2 = vocabularyRepository.findById(linkedId);
        
        if (opt1.isPresent() && opt2.isPresent()) {
            Vocabulary v1 = opt1.get();
            Vocabulary v2 = opt2.get();
            
            v1.getLinkedWords().remove(v2);
            v2.getLinkedWords().remove(v1);
            
            vocabularyRepository.save(v1);
            vocabularyRepository.save(v2);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/tags/{tagId}")
    @Transactional
    public ResponseEntity<Vocabulary> addTagToVocabulary(@PathVariable Long id, @PathVariable Long tagId) {
        Optional<Vocabulary> vocabOpt = vocabularyRepository.findById(id);
        Optional<tom.jerry.vocab.model.Tag> tagOpt = tagRepository.findById(tagId);
        
        if (vocabOpt.isPresent() && tagOpt.isPresent()) {
            Vocabulary vocab = vocabOpt.get();
            tom.jerry.vocab.model.Tag tag = tagOpt.get();
            
            vocab.getTags().add(tag);
            vocabularyRepository.save(vocab);
            return ResponseEntity.ok(vocab);
        }
        return ResponseEntity.notFound().build();
    }

}
