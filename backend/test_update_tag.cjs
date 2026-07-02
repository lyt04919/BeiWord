async function test() {
  const w = 'vibe';
  // 1. Fetch info
  const fetchRes = await fetch(`http://localhost:8080/api/vocabularies`);
  const data = await fetchRes.json();
  const wordToUpdate = data[0];
  console.log("Fetched word:", wordToUpdate.word);
  
  // 2. Fetch tags
  const tagsRes = await fetch(`http://localhost:8080/api/tags`);
  const tags = await tagsRes.json();
  const firstTag = tags[0];
  console.log("Fetched tag to add:", firstTag.name);
  
  // 3. Update word
  wordToUpdate.tags = [firstTag];
  const saveRes = await fetch(`http://localhost:8080/api/vocabularies/${wordToUpdate.id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(wordToUpdate)
  });
  
  console.log("Save status:", saveRes.status);
  const saved = await saveRes.json();
  console.log("Saved word tags:", saved.tags);
}
test();
