package tom.jerry.vocab.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "connection_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Optional name
    @Column(nullable = true)
    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "group_vocabularies",
        joinColumns = @JoinColumn(name = "group_id"),
        inverseJoinColumns = @JoinColumn(name = "vocabulary_id")
    )
    @JsonIgnoreProperties({"connectionGroups", "linkedWords", "tags"}) // Prevent infinite recursion
    private List<Vocabulary> vocabularies = new ArrayList<>();
}
