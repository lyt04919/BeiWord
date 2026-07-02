async function test() {
  let wRes = await fetch(`http://localhost:8080/api/vocabularies/1`);
  let word = await wRes.json();
  console.log("Original tags length:", word.tags ? word.tags.length : 0);
  
  if (!word.tags) word.tags = [];
  word.tags.push({id: 1, name: 'CET4', color: '#1f8242'});
  
  let res = await fetch(`http://localhost:8080/api/vocabularies/1`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(word)
  });
  console.log("Save status:", res.status);
  
  wRes = await fetch(`http://localhost:8080/api/vocabularies/1`);
  word = await wRes.json();
  console.log("New tags length:", word.tags ? word.tags.length : 0);
}
test();
