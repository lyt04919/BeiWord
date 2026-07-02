async function test() {
  const w = 'dog';
  const fetchRes = await fetch(`http://localhost:8080/api/vocabularies/fetch-info?word=${w}`);
  const data = await fetchRes.json();
  const payload = {
    word: w,
    vocabType: 'RECOGNITION',
    phoneticUk: data.phoneticUk,
    phoneticUs: data.phoneticUs,
    translation: JSON.stringify({ translations: ["Custom meaning from user"] })
  };
  const saveRes = await fetch('http://localhost:8080/api/vocabularies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  const saved = await saveRes.json();
  console.log("Saved word translation:", saved.translation);
}
test();
