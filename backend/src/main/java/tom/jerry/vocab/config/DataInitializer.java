package tom.jerry.vocab.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import tom.jerry.vocab.model.VocabType;
import tom.jerry.vocab.model.Vocabulary;
import tom.jerry.vocab.repository.VocabularyRepository;

import java.time.LocalDate;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private VocabularyRepository vocabularyRepository;

    @Override
    public void run(String... args) throws Exception {
        if (vocabularyRepository.count() == 0) {
            System.out.println("Database is empty. Initializing sample vocabulary data...");

            LocalDate today = LocalDate.now();

            Vocabulary v1 = new Vocabulary();
            v1.setWord("important");
            v1.setPhoneticUk("/ɪmˈpɔːtnt/");
            v1.setPhoneticUs("/ɪmˈpɔːrtnt/");
            v1.setTranslation("{\"translations\":[\"adj. 重要的，重大的；有权力的，有地位的\"]}");
            v1.setCurrentStage(0);
            v1.setNextReviewDate(LocalDate.now());
            v1.setIsFailedYesterday(false);
            v1.setVocabType(VocabType.RECOGNITION);

            Vocabulary v2 = new Vocabulary();
            v2.setWord("sophisticated");
            v2.setPhoneticUk("/səˈfɪstɪkeɪtɪd/");
            v2.setPhoneticUs("/səˈfɪstɪkeɪtɪd/");
            v2.setTranslation("{\"translations\":[\"adj. 复杂的；精致的；久经世故的；富有经验的\"]}");
            v2.setCurrentStage(0);
            v2.setNextReviewDate(LocalDate.now());
            v2.setIsFailedYesterday(false);
            v2.setVocabType(VocabType.RECOGNITION);

            Vocabulary v3 = new Vocabulary();
            v3.setWord("ubiquitous");
            v3.setPhoneticUk("/juːˈbɪkwɪtəs/");
            v3.setPhoneticUs("/juːˈbɪkwɪtəs/");
            v3.setTranslation("{\"translations\":[\"adj. 普遍存在的；无所不在的\"]}");
            v3.setCurrentStage(0);
            v3.setNextReviewDate(LocalDate.now());
            v3.setIsFailedYesterday(false);
            v3.setVocabType(VocabType.RECOGNITION);

            Vocabulary v4 = new Vocabulary();
            v4.setWord("ephemeral");
            v4.setPhoneticUk("/ɪˈfemərəl/");
            v4.setPhoneticUs("/ɪˈfemərəl/");
            v4.setTranslation("{\"translations\":[\"adj. 短暂的；朝生暮死的\"]}");
            v4.setCurrentStage(0);
            v4.setNextReviewDate(LocalDate.now());
            v4.setIsFailedYesterday(false);
            v4.setVocabType(VocabType.RECOGNITION);

            Vocabulary v5 = new Vocabulary();
            v5.setWord("serendipity");
            v5.setPhoneticUk("/ˌserənˈdɪpəti/");
            v5.setPhoneticUs("/ˌserənˈdɪpəti/");
            v5.setTranslation("{\"translations\":[\"n. 意外发现珍奇事物的本领；机缘凑巧\"]}");
            v5.setCurrentStage(0);
            v5.setNextReviewDate(LocalDate.now());
            v5.setIsFailedYesterday(false);
            v5.setVocabType(VocabType.RECOGNITION);            
            vocabularyRepository.saveAll(List.of(v1, v2, v3, v4, v5));
            
            System.out.println("Sample data injected.");
        }
    }
}
