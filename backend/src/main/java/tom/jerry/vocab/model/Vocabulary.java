package tom.jerry.vocab.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Table(name = "vocabularies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Vocabulary {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String word;

    @Column(name = "phonetic_uk")
    private String phoneticUk;

    @Column(name = "phonetic_us")
    private String phoneticUs;

    @Column(columnDefinition = "TEXT")
    private String translation; // Stores JSON representation of translations

    @Column(name = "current_stage", nullable = false)
    private Integer currentStage = 0; // Ebbinghaus stage

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "is_failed_yesterday", nullable = false)
    private Boolean isFailedYesterday = false;

    @Column(name = "is_mastered", nullable = false)
    private Boolean isMastered = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "vocab_type", nullable = false)
    private VocabType vocabType = VocabType.RECOGNITION;

    @ManyToMany(fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @JoinTable(
        name = "vocabulary_tag_relation",
        joinColumns = @JoinColumn(name = "vocabulary_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Tag> tags = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String phrases; // Stores JSON or plain text for custom phrases

    @Column(columnDefinition = "TEXT")
    private String examples; // Stores JSON representation of example sentences

    @ManyToMany(mappedBy = "vocabularies", fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @JsonIgnoreProperties({"vocabularies"})
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<ConnectionGroup> connectionGroups = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @JoinTable(
        name = "vocabulary_links",
        joinColumns = @JoinColumn(name = "vocab_id"),
        inverseJoinColumns = @JoinColumn(name = "linked_vocab_id")
    )
    @JsonIgnoreProperties({"linkedWords", "tags", "connectionGroups"}) // Prevent infinite recursion
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Vocabulary> linkedWords = new HashSet<>();
}
