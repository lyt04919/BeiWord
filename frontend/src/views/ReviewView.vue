<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import ReviewCard from '../components/ReviewCard.vue'

const router = useRouter()
const route = useRoute()
const isLearnMode = computed(() => route.query.mode === 'learn')
const reviewBatch = ref([])
const currentIndex = ref(0)
const initialBatchSize = ref(0)
const loading = ref(true)

// Study time tracking inside active review session
const studySeconds = ref(0)
const batchTotalSeconds = ref(0)
const batchTimeSpent = ref(0)
const showBatchSummary = ref(false)
let timerInterval = null

const completedWordIds = ref([])
const completedWordsSummary = ref([])
const todayReviewedCount = ref(0)
const remainingCount = ref(0)
const showSummaryMeanings = ref(false)

const getCleanTranslation = (translation) => {
  if (!translation) return ''
  let current = translation
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
      return inner && inner.translations ? inner.translations.join(' ') : inner
    })
    return cleanTranslations.join(' ')
  }
  return typeof current === 'string' ? current : JSON.stringify(current)
}

const formatDuration = (seconds) => {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  if (m > 0) {
    return `${m}分${s}秒`
  }
  return `${s}秒`
}

const getNextReviewIntervalText = (wordObj) => {
  if (!wordObj) return ""
  if (wordObj.isMastered) {
    return "复习完成"
  }
  if (!wordObj.nextReviewDate) {
    return "今天复习"
  }
  const nextDate = new Date(wordObj.nextReviewDate)
  const today = new Date()
  
  nextDate.setHours(0, 0, 0, 0)
  today.setHours(0, 0, 0, 0)
  
  const diffTime = nextDate - today
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24))
  if (diffDays <= 0) {
    return "今天复习"
  }
  return `${diffDays}天后复习`
}

const fetchStatsAndPool = async () => {
  try {
    const isLearn = route.query.mode === 'learn'
    const statsRes = await fetch(window.API_BASE_URL + '/api/stats')
    if (statsRes.ok) {
      const statsData = await statsRes.json()
      todayReviewedCount.value = statsData.todayWords || 0
    }

    if (isLearn) {
      const vocabRes = await fetch(window.API_BASE_URL + '/api/vocabularies')
      if (vocabRes.ok) {
        const vocabData = await vocabRes.json()
        remainingCount.value = vocabData.filter(v => v.currentStage === 0 && !v.isMastered).length
      }
    } else {
      const poolRes = await fetch(window.API_BASE_URL + '/api/review/pool')
      if (poolRes.ok) {
        const poolData = await poolRes.json()
        remainingCount.value = poolData.length
      }
    }
  } catch (e) {
    console.error("Failed to fetch stats and pool details", e)
  }
}

const startNextBatch = async () => {
  showBatchSummary.value = false
  batchTotalSeconds.value = 0
  await fetchBatch()
}

const reportStudyTime = async (seconds) => {
  try {
    await fetch(window.API_BASE_URL + '/api/stats/time', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ seconds })
    })
  } catch (e) {
    console.error('Failed to report study time', e)
  }
}

const currentWord = computed(() => {
  if (reviewBatch.value.length === 0) return null
  return reviewBatch.value[currentIndex.value]
})

const fetchBatch = async () => {
  loading.value = true
  try {
    const mode = route.query.mode || 'review'
    const res = await fetch(`${window.API_BASE_URL}/api/review/batch?mode=${mode}`)
    if (res.ok) {
      reviewBatch.value = await res.json()
      initialBatchSize.value = reviewBatch.value.length
      currentIndex.value = 0
      batchTotalSeconds.value = 0
      completedWordIds.value = []
      completedWordsSummary.value = []
    }
  } catch (e) {
    console.error('Failed to fetch review batch', e)
  } finally {
    loading.value = false
  }
}

const handleResult = async ({ id, passed }) => {
  try {
    await fetch(`${window.API_BASE_URL}/api/review/${id}/result?passed=${passed}`, {
      method: 'POST'
    })
  } catch (e) {
    console.error('Failed to submit result', e)
  }

  if (passed) {
    completedWordIds.value.push(id)
    reviewBatch.value.splice(currentIndex.value, 1)
  } else {
    currentIndex.value = (currentIndex.value + 1) % reviewBatch.value.length
  }

  if (reviewBatch.value.length === 0) {
    // Flush remaining study time when finishing the batch
    if (studySeconds.value > 0) {
      reportStudyTime(studySeconds.value)
      studySeconds.value = 0
    }
    batchTimeSpent.value = batchTotalSeconds.value

    try {
      const vocabRes = await fetch(window.API_BASE_URL + '/api/vocabularies')
      if (vocabRes.ok) {
        const allVocabs = await vocabRes.json()
        completedWordsSummary.value = completedWordIds.value.map(cid => allVocabs.find(v => v.id === cid)).filter(Boolean)
      }
    } catch (e) {
      console.error("Failed to fetch vocabulary details for summary", e)
    }

    await fetchStatsAndPool()
    showBatchSummary.value = true
  } else {
    if (currentIndex.value >= reviewBatch.value.length) {
      currentIndex.value = 0
    }
  }
}

const handleGlobalKeydown = (e) => {
  if (e.key === 'Escape') {
    router.push('/')
  }
  if (e.key === 'Enter' && showBatchSummary.value) {
    startNextBatch()
  }
}

onMounted(() => {
  fetchBatch()
  window.addEventListener('keydown', handleGlobalKeydown)

  // Start tracking review time
  timerInterval = setInterval(() => {
    if (reviewBatch.value.length > 0 && !loading.value) {
      studySeconds.value += 10
      batchTotalSeconds.value += 10
      if (studySeconds.value >= 60) {
        reportStudyTime(studySeconds.value)
        studySeconds.value = 0
      }
    }
  }, 10000)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  if (timerInterval) clearInterval(timerInterval)
  if (studySeconds.value > 0) {
    reportStudyTime(studySeconds.value)
  }
})
</script>

<template>
  <div class="w-full flex-1 flex flex-col justify-center items-center">
    <div v-if="loading" class="flex-1 flex justify-center items-center h-96">
      <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
    </div>
    <template v-else>
      <!-- Batch Completed Summary Screen -->
      <div v-if="showBatchSummary" class="glass-panel w-full max-w-2xl rounded-3xl p-8 flex flex-col gap-6 relative overflow-hidden transition-all duration-500 border border-white/60 dark:border-zinc-800/60 shadow-[0_12px_40px_-8px_rgba(0,0,0,0.12)]">
        
        <!-- Header -->
        <div class="flex justify-between items-center w-full pb-2 border-b border-zinc-200/30 dark:border-zinc-700/30">
          <button @click="router.push('/')" class="p-2 -ml-2 rounded-full hover:bg-black/5 dark:hover:bg-white/10 transition-colors" title="Back to Dashboard">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6 text-zinc-500 dark:text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <span class="text-xl font-bold text-zinc-900 dark:text-white">小结</span>
          <button @click="showSummaryMeanings = !showSummaryMeanings" class="p-2 rounded-full hover:bg-black/5 dark:hover:bg-white/10 transition-colors" :class="showSummaryMeanings ? 'text-primary' : 'text-zinc-500 dark:text-zinc-400'" title="Toggle Visibility">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
          </button>
        </div>

        <!-- Banner -->
        <div class="w-full bg-white/40 dark:bg-zinc-800/40 py-3 px-4 rounded-xl text-center text-sm font-semibold text-zinc-500 dark:text-zinc-400 border border-white/20 dark:border-zinc-700/20">
          💡 快速回顾本组单词吧~
        </div>

        <!-- Word List -->
        <div class="flex-1 overflow-y-auto custom-scrollbar max-h-96 pr-2 flex flex-col gap-2">
          <div v-for="word in completedWordsSummary" :key="word.id" class="flex flex-col py-3.5 px-4 bg-white/30 dark:bg-zinc-800/20 rounded-xl border border-white/20 dark:border-zinc-800/30 hover:bg-white/50 dark:hover:bg-zinc-800/30 transition-colors">
            <div class="flex justify-between items-center w-full">
              <span class="font-bold text-zinc-800 dark:text-zinc-200 text-md">{{ word.word }}</span>
              <span class="text-sm font-semibold" :class="word.isMastered ? 'text-green-600 dark:text-green-400' : 'text-zinc-500 dark:text-zinc-400'">
                {{ getNextReviewIntervalText(word) }}
              </span>
            </div>
            <div v-if="showSummaryMeanings" class="mt-1.5 text-sm font-medium text-zinc-500 dark:text-zinc-400">
              {{ getCleanTranslation(word.translation) }}
            </div>
          </div>
        </div>

        <!-- Footer Stats & Buttons -->
        <div class="flex flex-col items-center gap-4 w-full pt-4 border-t border-zinc-200/30 dark:border-zinc-700/30">
          <div class="text-sm font-semibold text-zinc-500 dark:text-zinc-400">
            已{{ isLearnMode ? '学习' : '复习' }} {{ todayReviewedCount }} 词，还剩 {{ remainingCount }} 词
          </div>
          <div class="flex gap-4 w-full justify-center">
            <button @click="router.push('/')" class="flex-1 px-6 py-3 bg-zinc-200/60 dark:bg-zinc-800/60 hover:bg-zinc-300/60 dark:hover:bg-zinc-700/60 text-zinc-700 dark:text-zinc-300 rounded-xl font-bold transition-all">
              返回主页
            </button>
            <button @click="startNextBatch" class="flex-[1.5] px-6 py-3 bg-primary text-white rounded-xl shadow-lg shadow-primary/30 font-bold hover:scale-[1.02] active:scale-[0.97] transition-all flex items-center justify-center gap-1.5">
              继续{{ isLearnMode ? '学习' : '复习' }} ↵
            </button>
          </div>
        </div>
      </div>

      <!-- No words to study or review state -->
      <div v-else-if="reviewBatch.length === 0" class="flex flex-col items-center justify-center text-center p-12 glass-panel rounded-3xl max-w-2xl w-full">
        <div class="text-6xl mb-6">🎉</div>
        <h2 class="text-3xl font-bold mb-4">
          {{ isLearnMode ? "You've Learned All Words!" : "You're All Caught Up!" }}
        </h2>
        <p class="text-zinc-500 dark:text-zinc-400 mb-8">
          {{ isLearnMode ? "There are no new words to learn. Try adding more words!" : "There are no more words to review today." }}
        </p>
        <button @click="router.push('/')" class="px-6 py-3 bg-primary text-white rounded-xl font-semibold hover:scale-[1.02] active:scale-[0.97] transition-all">
          Back to Dashboard
        </button>
      </div>

      <ReviewCard v-else class="w-full" :word="currentWord" :batchSize="initialBatchSize" :currentIndex="initialBatchSize - reviewBatch.length" @result="handleResult" />
    </template>
  </div>
</template>
