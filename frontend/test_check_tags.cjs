async function test() {
  let res = await fetch(`http://localhost:8080/api/vocabularies`);
  let words = await res.json();
  let w = words.find(v => v.id == 1);
  console.log("Word 1 tags:", w.tags);
}
test();
