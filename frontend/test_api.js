const fetch = require('node-fetch');
async function test() {
  const createRes = await fetch('http://localhost:8080/api/connection-groups', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: "API Test Group" })
  });
  const newGroup = await createRes.json();
  console.log("Created group:", newGroup);
  
  const joinRes = await fetch(`http://localhost:8080/api/connection-groups/${newGroup.id}/vocabularies/6`, { method: 'POST' });
  console.log("Join status:", joinRes.status, joinRes.statusText);
  if (!joinRes.ok) {
    const text = await joinRes.text();
    console.log("Join error body:", text);
  }
}
test();
