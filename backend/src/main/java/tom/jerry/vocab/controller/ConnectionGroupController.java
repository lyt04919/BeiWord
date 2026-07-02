package tom.jerry.vocab.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tom.jerry.vocab.model.ConnectionGroup;
import tom.jerry.vocab.model.Vocabulary;
import tom.jerry.vocab.repository.ConnectionGroupRepository;
import tom.jerry.vocab.repository.VocabularyRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/connection-groups")
@CrossOrigin
public class ConnectionGroupController {

    @Autowired
    private ConnectionGroupRepository connectionGroupRepository;

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @GetMapping
    public List<ConnectionGroup> getAllGroups() {
        return connectionGroupRepository.findAll();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ConnectionGroup> createGroup(@RequestBody Map<String, Object> payload) {
        ConnectionGroup group = new ConnectionGroup();
        
        if (payload.containsKey("name") && payload.get("name") != null) {
            String name = payload.get("name").toString().trim();
            group.setName(name);
        } else {
            group.setName("");
        }
        
        // Save the group first to get an ID
        ConnectionGroup savedGroup = connectionGroupRepository.save(group);
        
        if (payload.containsKey("vocabularyIds")) {
            List<Integer> ids = (List<Integer>) payload.get("vocabularyIds");
            List<Long> longIds = ids.stream().map(Integer::longValue).collect(Collectors.toList());
            List<Vocabulary> vocabularies = vocabularyRepository.findAllById(longIds);
            
            for (Vocabulary v : vocabularies) {
                savedGroup.getVocabularies().add(v);
                v.getConnectionGroups().add(savedGroup); // assuming connectionGroups is added to Vocabulary
            }
            vocabularyRepository.saveAll(vocabularies);
            savedGroup = connectionGroupRepository.save(savedGroup);
        }
        
        return ResponseEntity.ok(savedGroup);
    }
    
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ConnectionGroup> updateGroup(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        Optional<ConnectionGroup> opt = connectionGroupRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        
        ConnectionGroup group = opt.get();
        if (payload.containsKey("name")) {
            Object nameObj = payload.get("name");
            if (nameObj == null || nameObj.toString().trim().isEmpty()) {
                group.setName("");
            } else {
                group.setName(nameObj.toString().trim());
            }
        }
        return ResponseEntity.ok(connectionGroupRepository.save(group));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
        Optional<ConnectionGroup> opt = connectionGroupRepository.findById(id);
        if (opt.isPresent()) {
            ConnectionGroup group = opt.get();
            // Need to remove from all vocabularies first to clean join table
            for (Vocabulary v : group.getVocabularies()) {
                v.getConnectionGroups().remove(group);
                vocabularyRepository.save(v);
            }
            group.getVocabularies().clear();
            connectionGroupRepository.delete(group);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/{groupId}/vocabularies/{vocabId}")
    @Transactional
    public ResponseEntity<ConnectionGroup> addVocabularyToGroup(@PathVariable Long groupId, @PathVariable Long vocabId) {
        Optional<ConnectionGroup> groupOpt = connectionGroupRepository.findById(groupId);
        Optional<Vocabulary> vocabOpt = vocabularyRepository.findById(vocabId);
        
        if (groupOpt.isPresent() && vocabOpt.isPresent()) {
            ConnectionGroup group = groupOpt.get();
            Vocabulary vocab = vocabOpt.get();
            
            if (!group.getVocabularies().contains(vocab)) {
                group.getVocabularies().add(vocab);
                vocab.getConnectionGroups().add(group);
                vocabularyRepository.save(vocab);
                connectionGroupRepository.save(group);
            }
            return ResponseEntity.ok(group);
        }
        return ResponseEntity.notFound().build();
    }
    
    @PostMapping("/{groupId}/vocabularies")
    @Transactional
    public ResponseEntity<ConnectionGroup> addVocabulariesToGroup(@PathVariable Long groupId, @RequestBody List<Long> vocabIds) {
        Optional<ConnectionGroup> groupOpt = connectionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        ConnectionGroup group = groupOpt.get();
        List<Vocabulary> vocabularies = vocabularyRepository.findAllById(vocabIds);
        
        for (Vocabulary vocab : vocabularies) {
            if (!group.getVocabularies().contains(vocab)) {
                group.getVocabularies().add(vocab);
                vocab.getConnectionGroups().add(group);
            }
        }
        
        vocabularyRepository.saveAll(vocabularies);
        connectionGroupRepository.save(group);
        
        return ResponseEntity.ok(group);
    }
    
    @PutMapping("/{groupId}/vocabularies")
    @Transactional
    public ResponseEntity<ConnectionGroup> updateVocabulariesInGroup(@PathVariable Long groupId, @RequestBody List<Long> vocabIds) {
        Optional<ConnectionGroup> groupOpt = connectionGroupRepository.findById(groupId);
        if (groupOpt.isEmpty()) return ResponseEntity.notFound().build();
        
        ConnectionGroup group = groupOpt.get();
        List<Vocabulary> newVocabularies = vocabularyRepository.findAllById(vocabIds);
        List<Long> newIds = newVocabularies.stream().map(Vocabulary::getId).collect(Collectors.toList());
        
        // Remove old vocabularies that are not in the new list
        for (Vocabulary vocab : new java.util.ArrayList<>(group.getVocabularies())) {
            if (!newIds.contains(vocab.getId())) {
                group.getVocabularies().remove(vocab);
                vocab.getConnectionGroups().remove(group);
                vocabularyRepository.save(vocab);
            }
        }
        
        // Add new vocabularies
        List<Long> currentIds = group.getVocabularies().stream().map(Vocabulary::getId).collect(Collectors.toList());
        for (Vocabulary vocab : newVocabularies) {
            if (!currentIds.contains(vocab.getId())) {
                group.getVocabularies().add(vocab);
                vocab.getConnectionGroups().add(group);
            }
        }
        
        vocabularyRepository.saveAll(newVocabularies);
        connectionGroupRepository.save(group);
        
        return ResponseEntity.ok(group);
    }
    
    @DeleteMapping("/{groupId}/vocabularies/{vocabId}")
    @Transactional
    public ResponseEntity<ConnectionGroup> removeVocabularyFromGroup(@PathVariable Long groupId, @PathVariable Long vocabId) {
        Optional<ConnectionGroup> groupOpt = connectionGroupRepository.findById(groupId);
        Optional<Vocabulary> vocabOpt = vocabularyRepository.findById(vocabId);
        
        if (groupOpt.isPresent() && vocabOpt.isPresent()) {
            ConnectionGroup group = groupOpt.get();
            Vocabulary vocab = vocabOpt.get();
            
            group.getVocabularies().remove(vocab);
            vocab.getConnectionGroups().remove(group);
            
            vocabularyRepository.save(vocab);
            connectionGroupRepository.save(group);
            
            return ResponseEntity.ok(group);
        }
        return ResponseEntity.notFound().build();
    }
}
