<template>
  <div class="flex flex-col items-center justify-center min-h-[500px] p-4 w-full">
    <div class="glass-panel w-full max-w-2xl rounded-3xl p-8 flex flex-col gap-6 relative overflow-hidden transition-all duration-500">
      
      <!-- Progress and Status -->
      <div class="flex justify-between items-center text-sm font-medium opacity-70">
        <div class="flex items-center gap-2">
          <button @click="router.push('/')" class="p-2 -ml-2 rounded-full hover:bg-black/5 dark:hover:bg-white/10 transition-colors" title="Exit Review">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M9.707 16.707a1 1 0 01-1.414 0l-6-6a1 1 0 010-1.414l6-6a1 1 0 011.414 1.414L5.414 9H17a1 1 0 110 2H5.414l4.293 4.293a1 1 0 010 1.414z" clip-rule="evenodd" />
            </svg>
          </button>
          <span class="text-lg font-bold tracking-wider">{{ currentIndex + 1 }}/{{ batchSize }}</span>
        </div>
        <span class="px-3 py-1 bg-zinc-200 dark:bg-zinc-700 rounded-full font-bold">{{ word.vocabType || 'RECOGNITION' }}</span>
      </div>

      <!-- Main Word Area -->
      <div class="flex flex-col items-center py-10 gap-2">
        <h1 v-if="(word.vocabType !== 'SPELLING') || isAnswered" 
            class="text-6xl font-bold tracking-tight transition-colors"
            :class="spellingResult === 'incorrect' ? 'text-red-500' : (spellingResult === 'correct' ? 'text-green-500' : '')">
          {{ word.word }}
        </h1>
        <h1 v-else class="text-6xl font-bold tracking-tight text-zinc-300 dark:text-zinc-600">
          {{ '_'.repeat(word.word?.length || 5) }}
        </h1>
        
        <div class="flex gap-4 text-zinc-500 dark:text-zinc-400 mt-2">
          <span>UK /{{ word.phoneticUk }}/</span>
          <span>US /{{ word.phoneticUs }}/</span>
        </div>
      </div>

      <!-- SPELLING Input -->
      <div v-if="word.vocabType === 'SPELLING' && !isAnswered" class="w-full flex justify-center mt-2">
        <input 
          ref="spellInputRef"
          v-model="spellingInput" 
          @keyup.enter="submitSpelling" 
          class="w-full max-w-sm bg-black/5 dark:bg-white/10 border-2 border-zinc-200 dark:border-white/20 rounded-xl p-4 text-center text-2xl outline-none focus:border-primary dark:focus:border-primary transition-colors text-zinc-900 dark:text-white placeholder:text-zinc-400"
          placeholder="Type here and press Enter"
        />
      </div>

      <!-- Translation Area -->
      <div 
        v-show="isAnswered || word.vocabType === 'SPELLING'"
        class="flex flex-col gap-6 items-center transition-all duration-300 w-full animate-fade-in-up"
      >
        <div class="w-full bg-white/40 dark:bg-zinc-800/60 rounded-xl p-6 flex flex-col gap-4 text-left">
          <div class="text-xl font-medium leading-relaxed whitespace-pre-wrap">{{ youdaoTranslation || parsedTranslation }}</div>
          
          <div v-if="word.phrases" class="mt-2 pt-4 border-t border-zinc-200 dark:border-white/10">
            <h4 class="text-xs font-bold text-zinc-400 uppercase mb-2">Custom Phrases</h4>
            <div class="whitespace-pre-wrap text-zinc-600 dark:text-zinc-300">{{ word.phrases }}</div>
          </div>
          
          <div v-if="word.tags && word.tags.length > 0" class="flex flex-wrap gap-2 mt-2">
            <span v-for="tag in word.tags" :key="tag.id" class="px-2 py-0.5 rounded text-xs font-semibold text-white shadow-sm" :style="{ backgroundColor: tag.color }">
              #{{ tag.name }}
            </span>
          </div>
          
          <div v-if="word.connectionGroups && word.connectionGroups.length > 0" class="mt-2 flex flex-wrap gap-2">
            <div v-for="group in word.connectionGroups" :key="group.id" class="flex items-center gap-1.5 px-2.5 py-1 bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400 rounded-md text-xs font-bold border border-blue-100 dark:border-blue-800/50">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M12.586 4.586a2 2 0 112.828 2.828l-3 3a2 2 0 01-2.828 0 1 1 0 00-1.414 1.414 4 4 0 005.656 0l3-3a4 4 0 00-5.656-5.656l-1.5 1.5a1 1 0 101.414 1.414l1.5-1.5zm-5 5a2 2 0 012.828 0 1 1 0 101.414-1.414 4 4 0 00-5.656 0l-3 3a4 4 0 105.656 5.656l1.5-1.5a1 1 0 10-1.414-1.414l-1.5 1.5a2 2 0 11-2.828-2.828l3-3z" clip-rule="evenodd" />
              </svg>
              {{ group.name }}
            </div>
          </div>
          <div v-if="exampleSentences.length > 0" class="mt-2 pt-4 border-t border-zinc-200 dark:border-white/10">
            <h4 class="text-xs font-bold text-zinc-400 uppercase mb-3 flex items-center gap-2">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
              </svg>
              Example Sentences (来自有道)
            </h4>
            <div class="flex flex-col gap-3">
              <div v-for="(sentence, index) in exampleSentences" :key="index" class="text-sm">
                <div class="text-zinc-800 dark:text-zinc-200 font-medium leading-relaxed" v-html="sentence['sentence-eng']"></div>
                <div class="text-zinc-500 dark:text-zinc-400 mt-1">{{ sentence['sentence-translation'] }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Controls -->
      <div v-if="!isAnswered && word.vocabType !== 'SPELLING'" class="mt-4 grid grid-cols-3 gap-4">
        <button @click="mark('Q')" class="interactive-btn p-3 rounded-xl bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 font-semibold shadow-sm hover:shadow-md transition-shadow">
          <div class="text-xs opacity-70 mb-1">Q</div>
          Know
        </button>
        <button @click="mark('W')" class="interactive-btn p-3 rounded-xl bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400 font-semibold shadow-sm hover:shadow-md transition-shadow">
          <div class="text-xs opacity-70 mb-1">W</div>
          Blurry
        </button>
        <button @click="mark('E')" class="interactive-btn p-3 rounded-xl bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400 font-semibold shadow-sm hover:shadow-md transition-shadow">
          <div class="text-xs opacity-70 mb-1">E</div>
          Forget
        </button>
      </div>

      <div v-if="isAnswered" class="mt-4 flex justify-center gap-4 w-full">
        <button @click="nextWord" ref="nextBtnRef" class="flex-[2] interactive-btn px-8 py-3 bg-primary text-white rounded-xl shadow-lg shadow-primary/30 font-bold flex justify-center items-center gap-2 focus:ring-4 ring-primary/50">
          Next [Space]
        </button>
        <button v-if="selectedType === 'Q' || selectedType === 'W'" @click="markWrong" class="flex-1 interactive-btn px-6 py-3 bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 rounded-xl font-semibold transition-all">
          Mark Wrong [E]
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const props = defineProps({
  word: {
    type: Object,
    required: true
  },
  batchSize: {
    type: Number,
    default: 1
  },
  currentIndex: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['result'])

const isAnswered = ref(false)
const selectedType = ref(null)

const spellingInput = ref('')
const spellingResult = ref(null)
const spellInputRef = ref(null)
const nextBtnRef = ref(null)
const exampleSentences = ref([])
const youdaoTranslation = ref('')

const playAudio = () => {
  if (!props.word || !props.word.word) return
  const audioUrl = `https://dict.youdao.com/dictvoice?audio=${encodeURIComponent(props.word.word)}&type=1`
  const audio = new Audio(audioUrl)
  audio.play().catch(err => console.error("Audio play failed:", err))
}

const parsedTranslation = computed(() => {
  if (!props.word || !props.word.translation) return ''
  let current = props.word.translation
  try {
    while (typeof current === 'string' && (current.startsWith('"') || current.startsWith('{') || current.startsWith('['))) {
      const parsed = JSON.parse(current)
      if (typeof parsed === 'string' && parsed === current) break
      current = parsed
    }
  } catch (e) {}

  if (current && current.translations) {
    const cleanTranslations = current.translations.map(t => {
      let inner = t
      try {
        while (typeof inner === 'string' && (inner.startsWith('"') || inner.startsWith('{') || inner.startsWith('['))) {
          const p = JSON.parse(inner)
          if (typeof p === 'string' && p === inner) break
          inner = p
        }
      } catch (e) {}
      return inner && inner.translations ? inner.translations.join('\n') : inner
    })
    return cleanTranslations.join('\n')
  }
  return typeof current === 'string' ? current : JSON.stringify(current)
})

const mark = async (type) => {
  if (isAnswered.value) return
  selectedType.value = type
  isAnswered.value = true
  await nextTick()
  if (nextBtnRef.value) nextBtnRef.value.focus()
}

const submitSpelling = () => {
  if (isAnswered.value) return
  const correct = spellingInput.value.trim().toLowerCase() === props.word.word.toLowerCase()
  if (correct) {
    spellingResult.value = 'correct'
    mark('Q')
  } else {
    spellingResult.value = 'incorrect'
    mark('E')
  }
}

const nextWord = () => {
  if (!isAnswered.value) return
  // If they selected Q (Know), they pass.
  // If they selected W (Blurry) or E (Forget), they fail.
  const passed = selectedType.value === 'Q'
  emit('result', { id: props.word.id, passed })
  isAnswered.value = false
  selectedType.value = null
}

const markWrong = () => {
  if (!isAnswered.value) return
  // "Remembered Wrong" forces passed to false, regardless of what they originally picked.
  emit('result', { id: props.word.id, passed: false })
  isAnswered.value = false
  selectedType.value = null
}

const handleKeydown = (e) => {
  if (e.target.tagName === 'INPUT') return // Don't intercept if user is typing
  
  const key = e.key.toUpperCase()
  
  // R key for pronunciation
  if (key === 'R') {
    playAudio()
    return
  }

  if (!isAnswered.value && props.word.vocabType !== 'SPELLING') {
    if (['Q', 'W', 'E'].includes(key)) {
      mark(key)
    }
  } else if (isAnswered.value) {
    if (key === ' ') {
      e.preventDefault()
      nextWord()
    } else if (key === 'E' && (selectedType.value === 'Q' || selectedType.value === 'W')) {
      e.preventDefault()
      markWrong()
    }
  }
}

// Auto-play audio when word appears
watch(() => props.word, async () => {
  isAnswered.value = false
  selectedType.value = null
  spellingInput.value = ''
  spellingResult.value = null
  exampleSentences.value = []
  youdaoTranslation.value = ''
  
  // Check if we have examples in the database
  if (props.word && props.word.examples) {
    try {
      let ex = props.word.examples
      while (typeof ex === 'string') ex = JSON.parse(ex)
      if (Array.isArray(ex) && ex.length > 0) {
        exampleSentences.value = ex.map(item => ({
          'sentence-eng': item.en,
          'sentence-translation': item.zh
        }))
        return // Skip API fetch! We have data locally!
      }
    } catch(e) {}
  }

  // Fallback: Fetch example sentences and translation from Youdao proxy
  if (props.word && props.word.word) {
    fetch(`/api/proxy/youdao?word=${encodeURIComponent(props.word.word)}`)
      .then(res => res.json())
      .then(data => {
        // Parse Translation
        if (data && data.ec && data.ec.word && data.ec.word[0] && data.ec.word[0].trs) {
           youdaoTranslation.value = data.ec.word[0].trs.map(t => {
             if (t && t.tr && t.tr[0] && t.tr[0].l && t.tr[0].l.i) {
               return t.tr[0].l.i.join(', ')
             }
             return ''
           }).filter(Boolean).join('\n')
        }
        // Parse Examples
        if (data && data.blng_sents_part && data['blng_sents_part']['sentence-pair']) {
           exampleSentences.value = data['blng_sents_part']['sentence-pair'].slice(0, 2)
        }
      }).catch(e => console.error("Failed to fetch examples:", e))
  }

  // Auto-play pronunciation
  playAudio()

  if (props.word.vocabType === 'SPELLING') {
    await nextTick()
    if (spellInputRef.value) spellInputRef.value.focus()
  }
}, { immediate: true })

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.bg-primary {
  background-color: var(--color-primary);
}
.shadow-primary\/30 {
  box-shadow: 0 10px 15px -3px rgb(0 122 255 / 0.3);
}
</style>
