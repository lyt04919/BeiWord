async function test() {
  const w = 'happy';
  // 1. Fetch info
  const fetchRes = await fetch(`http://localhost:8080/api/vocabularies/fetch-info?word=${w}`);
  const data = await fetchRes.json();
  console.log("Fetched info:", data);
  
  // 2. Save word
  const payload = {
    word: w,
    vocabType: 'RECOGNITION',
    phoneticUk: data.phoneticUk,
    phoneticUs: data.phoneticUs,
    translation: JSON.stringify({ translations: data.translations })
  };
  
  const saveRes = await fetch('http://localhost:8080/api/vocabularies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  
  console.log("Save status:", saveRes.status);
  const saved = await saveRes.json();
  console.log("Saved word:", saved);
}
test();
