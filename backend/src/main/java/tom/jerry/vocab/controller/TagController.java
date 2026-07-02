package tom.jerry.vocab.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tom.jerry.vocab.model.Tag;
import tom.jerry.vocab.repository.TagRepository;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@CrossOrigin(origins = "*")
public class TagController {

    @Autowired
    private TagRepository tagRepository;

    @GetMapping
    public List<Tag> getAllTags() {
        return tagRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<Tag> createTag(@RequestBody Tag tag) {
        if (tag.getName() == null || tag.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Tag savedTag = tagRepository.save(tag);
        return ResponseEntity.ok(savedTag);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Tag> updateTag(@PathVariable Long id, @RequestBody Tag tagDetails) {
        java.util.Optional<Tag> tagOpt = tagRepository.findById(id);
        if (!tagOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Tag tag = tagOpt.get();
        if (tagDetails.getName() != null && !tagDetails.getName().trim().isEmpty()) {
            tag.setName(tagDetails.getName().trim());
        }
        if (tagDetails.getColor() != null && !tagDetails.getColor().trim().isEmpty()) {
            tag.setColor(tagDetails.getColor().trim());
        }
        return ResponseEntity.ok(tagRepository.save(tag));
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> deleteTag(@PathVariable Long id) {
        java.util.Optional<Tag> tagOpt = tagRepository.findById(id);
        if (!tagOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Tag tag = tagOpt.get();
        // Remove tag from all vocabularies
        for (tom.jerry.vocab.model.Vocabulary vocab : tag.getVocabularies()) {
            vocab.getTags().remove(tag);
        }
        tagRepository.delete(tag);
        return ResponseEntity.ok().build();
    }
}
