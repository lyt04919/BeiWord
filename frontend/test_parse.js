const parseTranslation = (raw) => {
  if (!raw) return ''
  try {
    const firstParse = JSON.parse(raw)
    const secondParse = typeof firstParse === 'string' ? JSON.parse(firstParse) : firstParse
    return secondParse.translations?.join(', ') || raw
  } catch (e) {
    return raw
  }
}

const raw = '"{\\"translations\\":[\\"a. 复杂的, 久经世故的\\",\\"[法] 尖端的, 高级的, 非常有经验的\\"]}"';
console.log(parseTranslation(raw));
