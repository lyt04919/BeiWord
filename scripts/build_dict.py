import urllib.request
import zipfile
import csv
import os
import shutil

url = "https://raw.githubusercontent.com/skywind3000/ECDICT/master/ecdict.csv"
csv_name = "ecdict.csv"
mini_csv_path = "backend/src/main/resources/ecdict_mini.csv"

# Target tags to keep
target_tags = {"zk", "gk", "cet4", "cet6", "ky", "ielts", "toefl", "gre"}

def download_and_extract():
    print("Downloading ECDICT CSV directly from GitHub (65MB)...")
    if not os.path.exists(csv_name):
        urllib.request.urlretrieve(url, csv_name)

def filter_csv():
    print("Filtering ECDICT CSV...")
    count = 0
    with open(csv_name, 'r', encoding='utf-8') as infile, \
         open(mini_csv_path, 'w', encoding='utf-8', newline='') as outfile:
        
        reader = csv.DictReader(infile)
        
        # We only need specific columns to save space: word, phonetic, translation, tag
        fieldnames = ['word', 'phonetic', 'translation', 'tag']
        writer = csv.DictWriter(outfile, fieldnames=fieldnames)
        writer.writeheader()
        
        for row in reader:
            tags = row.get('tag', '')
            if tags:
                tag_set = set(t.strip().lower() for t in tags.split(' ') if t.strip())
                if not tag_set.isdisjoint(target_tags):
                    # Write to mini CSV
                    writer.writerow({
                        'word': row['word'],
                        'phonetic': row['phonetic'],
                        'translation': row['translation'].replace('\n', '\\n'), # escape newlines
                        'tag': row['tag']
                    })
                    count += 1
    
    print(f"Filtered {count} words. Saved to {mini_csv_path}.")

if __name__ == "__main__":
    download_and_extract()
    filter_csv()
    # Cleanup
    if os.path.exists(csv_name):
        os.remove(csv_name)
    print("Done!")
