package tom.jerry.vocab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
@EnableScheduling
public class VocabApplication {

	public static void main(String[] args) {
		SpringApplication.run(VocabApplication.class, args);
	}

	@Bean
	public CommandLineRunner fixSchema(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE vocabularies ADD COLUMN is_mastered BOOLEAN DEFAULT FALSE");
				System.out.println("Added is_mastered column successfully.");
			} catch (Exception e) {
				System.out.println("is_mastered column might already exist: " + e.getMessage());
			}

			// Delete all daily_stats except for yesterday and today to reset stats
			try {
				java.time.LocalDate today = java.time.LocalDate.now();
				java.time.LocalDate yesterday = today.minusDays(1);
				int deleted = jdbcTemplate.update(
					"DELETE FROM daily_stats WHERE date NOT IN (?, ?)",
					java.sql.Date.valueOf(yesterday),
					java.sql.Date.valueOf(today)
				);
				System.out.println("Cleaned up " + deleted + " old daily stats records.");
			} catch (Exception e) {
				System.out.println("Failed to clean up stats: " + e.getMessage());
			}

			// Seed IELTS words if they don't exist
			try {
				System.out.println("Seeding database with IELTS vocabulary if missing...");
				// Ensure IELTS tag exists
				jdbcTemplate.execute("MERGE INTO tags (name, color) KEY(name) VALUES ('IELTS', '#EC4899')");
				Long tagId = jdbcTemplate.queryForObject("SELECT id FROM tags WHERE name = 'IELTS'", Long.class);

				String[][] ieltsWords = {
					{"abandon", "əˈbændən", "{\"translations\":[\"vt. 放弃，抛弃；放纵\"]}", "[{\"en\":\"He decided to abandon his search.\",\"zh\":\"他决定放弃搜寻。\"}]"},
					{"accumulate", "əˈkjuːmjəleɪt", "{\"translations\":[\"v. 积累，堆积\"]}", "[{\"en\":\"Dust began to accumulate on the tables.\",\"zh\":\"桌上开始积起灰尘。\"}]"},
					{"beneficial", "ˌbenɪˈfɪʃl", "{\"translations\":[\"adj. 有益的，有利的\"]}", "[{\"en\":\"Regular exercise is beneficial to health.\",\"zh\":\"规律运动对身体有益。\"}]"},
					{"collaborate", "kəˈlæbəreɪt", "{\"translations\":[\"vi. 合作，协作\"]}", "[{\"en\":\"Researchers collaborate on various projects.\",\"zh\":\"研究人员在各种项目上进行合作。\"}]"},
					{"diverse", "daɪˈvɜːs", "{\"translations\":[\"adj. 多样的，不同的\"]}", "[{\"en\":\"The city has a diverse population.\",\"zh\":\"这个城市有不同的人口构成。\"}]"},
					{"evaluate", "ɪˈvæljueɪt", "{\"translations\":[\"vt. 评估，评价\"]}", "[{\"en\":\"We need to evaluate the results carefully.\",\"zh\":\"我们需要仔细评估结果。\"}]"},
					{"fluctuate", "ˈflʌktʃueɪt", "{\"translations\":[\"vi. 波动，起伏\"]}", "[{\"en\":\"Prices fluctuate according to supply and demand.\",\"zh\":\"价格随供求关系变化而波动。\"}]"},
					{"guarantee", "ˌgærənˈtiː", "{\"translations\":[\"vt. 保证，担保；n. 保证书\"]}", "[{\"en\":\"We guarantee the quality of our products.\",\"zh\":\"我们保证我们产品的质量。\"}]"},
					{"hypothesis", "haɪˈpɒθəsɪs", "{\"translations\":[\"n. 假设，假说\"]}", "[{\"en\":\"The scientists tested the hypothesis.\",\"zh\":\"科学家们验证了这个假设。\"}]"},
					{"justify", "ˈdʒʌstɪfaɪ", "{\"translations\":[\"vt. 证明……有理，为……辩护\"]}", "[{\"en\":\"You don't need to justify your decision.\",\"zh\":\"你不需要为你的决定辩解。\"}]"}
				};

				int addedCount = 0;
				for (String[] wordData : ieltsWords) {
					String word = wordData[0];
					String phonetic = wordData[1];
					String trans = wordData[2];
					String examples = wordData[3];

					Integer exists = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vocabularies WHERE word = ?", Integer.class, word);
					if (exists != null && exists == 0) {
						jdbcTemplate.update(
							"INSERT INTO vocabularies (word, phonetic_uk, phonetic_us, translation, current_stage, next_review_date, is_failed_yesterday, is_mastered, vocab_type, examples) VALUES (?, ?, ?, ?, 0, ?, false, false, 'RECOGNITION', ?)",
							word, phonetic, phonetic, trans, java.sql.Date.valueOf(java.time.LocalDate.now()), examples
						);

						Long vocabId = jdbcTemplate.queryForObject("SELECT id FROM vocabularies WHERE word = ?", Long.class, word);
						jdbcTemplate.update("INSERT INTO vocabulary_tag_relation (vocabulary_id, tag_id) VALUES (?, ?)", vocabId, tagId);
						addedCount++;
					}
				}
				System.out.println("Seeded " + addedCount + " new IELTS words.");
			} catch (Exception e) {
				System.out.println("Failed to seed IELTS words: " + e.getMessage());
				e.printStackTrace();
			}
		};
	}
}
