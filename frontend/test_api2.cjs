async function test() {
  const createRes = await fetch('http://localhost:8080/api/connection-groups', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: "API Test Group 2" })
  });
  const newGroup = await createRes.json();
  
  await fetch(`http://localhost:8080/api/connection-groups/${newGroup.id}/vocabularies/6`, { method: 'POST' });
  
  const vocabRes = await fetch('http://localhost:8080/api/vocabularies');
  const allVocabs = await vocabRes.json();
  const vocab6 = allVocabs.find(v => v.id === 6);
  console.log("Vocabulary 6 connectionGroups:", vocab6.connectionGroups);
}
test();
