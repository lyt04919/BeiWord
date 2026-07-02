import csv

words = [
    {"word": "apple", "phonetic": "ˈæpl", "translation": "n. 苹果; 苹果公司", "tag": "zk gk cet4"},
    {"word": "software", "phonetic": "ˈsɒftweə", "translation": "n. 软件", "tag": "cet4 cet6"},
    {"word": "engineer", "phonetic": "ˌendʒɪˈnɪə", "translation": "n. 工程师 v. 设计", "tag": "cet4 ielts"},
    {"word": "mock", "phonetic": "mɒk", "translation": "v. 嘲笑; 模仿 n. 模拟考试", "tag": "toefl gre"},
    {"word": "vibe", "phonetic": "vaɪb", "translation": "n. 氛围，感觉", "tag": ""},
    {"word": "hello", "phonetic": "həˈləʊ", "translation": "int. 喂；哈罗", "tag": "zk gk"},
    {"word": "terminal", "phonetic": "ˈtɜːmɪnl", "translation": "n. 终端；航站楼 adj. 晚期的", "tag": "cet4 cet6"},
    {"word": "architecture", "phonetic": "ˈɑːkɪtektʃə", "translation": "n. 建筑学；架构", "tag": "cet4 toefl"},
    {"word": "reinforcement", "phonetic": "ˌriːɪnˈfɔːsmənt", "translation": "n. 加固；强化；增援", "tag": "gre ielts"},
    {"word": "resilience", "phonetic": "rɪˈzɪliəns", "translation": "n. 恢复力；弹力", "tag": "gre toefl"},
    {"word": "integration", "phonetic": "ˌɪntɪˈɡreɪʃn", "translation": "n. 集成；结合", "tag": "cet6 ielts"},
    {"word": "crust", "phonetic": "krʌst", "translation": "n. 地壳；外壳", "tag": "toefl"}
]

csv_path = "backend/src/main/resources/ecdict_mini.csv"

with open(csv_path, 'w', encoding='utf-8', newline='') as f:
    writer = csv.DictWriter(f, fieldnames=['word', 'phonetic', 'translation', 'tag'])
    writer.writeheader()
    writer.writerows(words)

print("Generated dummy ecdict_mini.csv for development.")
