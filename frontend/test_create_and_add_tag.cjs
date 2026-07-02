async function test() {
  // 1. Create a new tag
  let res = await fetch('http://localhost:8080/api/tags', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: 'MyNewTag', color: '#ff0000' })
  });
  let newTag = await res.json();
  console.log("Created Tag:", newTag);
  
  // 2. Add to word 1
  let wRes = await fetch(`http://localhost:8080/api/vocabularies/1`);
  let word = await wRes.json();
  if (!word.tags) word.tags = [];
  word.tags.push(newTag);
  
  res = await fetch(`http://localhost:8080/api/vocabularies/1`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(word)
  });
  console.log("Save status:", res.status);
  
  // 3. Fetch word again
  wRes = await fetch(`http://localhost:8080/api/vocabularies/1`);
  word = await wRes.json();
  console.log("New tags length:", word.tags ? word.tags.length : 0);
  if (word.tags && word.tags.length > 0) {
      console.log("Word tag:", word.tags[0]);
  }
}
test();
