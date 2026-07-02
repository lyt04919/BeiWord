async function test() {
  const w = 'apple';
  const fetchRes = await fetch(`http://localhost:8080/api/vocabularies/fetch-info?word=${w}`);
  const data = await fetchRes.json();
  const payload = {
    word: w,
    vocabType: 'RECOGNITION',
    phoneticUk: data.phoneticUk,
    phoneticUs: data.phoneticUs,
    translation: JSON.stringify({ translations: data.translations || [] })
  };
  const saveRes = await fetch('http://localhost:8080/api/vocabularies', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  });
  console.log("Save status:", saveRes.status);
}
test();
