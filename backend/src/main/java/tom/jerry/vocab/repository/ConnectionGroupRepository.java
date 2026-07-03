package tom.jerry.vocab.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tom.jerry.vocab.model.ConnectionGroup;

public interface ConnectionGroupRepository extends JpaRepository<ConnectionGroup, Long> {
    java.util.Optional<ConnectionGroup> findByName(String name);
}
