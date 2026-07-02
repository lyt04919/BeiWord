<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const dueTodayCount = ref(0)
const totalWordsCount = ref(0)
const newWordsCount = ref(0)
const totalGroupsCount = ref(0)
const loading = ref(true)

// Advanced Features State
const allVocabularies = ref([])
const showSearch = ref(false)
const searchInputRef = ref(null)
const searchQuery = ref('')
const wallpaperUrl = ref('')
const initialLearnCount = ref(0)
const initialReviewCount = ref(0)
const learnProgress = ref(0)
const reviewProgress = ref(0)

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

const featuredWord = ref({
  word: 'Blossom',
  phoneticUk: 'ˈblɒsəm',
  phoneticUs: 'ˈblɑːsəm'
})

const speakFeaturedWord = () => {
  const audioUrl = `https://dict.youdao.com/dictvoice?audio=${encodeURIComponent(featuredWord.value.word)}&type=1`
  const audio = new Audio(audioUrl)
  audio.play().catch(err => console.error("Audio play failed:", err))
}

const fetchDashboardData = async () => {
  loading.value = true
  try {
    // 1. Fetch Review Pool Count
    const reviewRes = await fetch(window.API_BASE_URL + '/api/review/pool')
    if (reviewRes.ok) {
      const data = await reviewRes.json()
      dueTodayCount.value = data.length
    }

    // 2. Fetch All Vocabularies to get count and random word
    const vocabRes = await fetch(window.API_BASE_URL + '/api/vocabularies')
    if (vocabRes.ok) {
      const data = await vocabRes.json()
      allVocabularies.value = data
      totalWordsCount.value = data.length
      newWordsCount.value = data.filter(v => v.currentStage === 0 && !v.isMastered).length
      
      if (data.length > 0) {
        // Core Logic: Pick a random word from the dictionary
        const todayStr = new Date().toISOString().split('T')[0]
        const cachedDate = localStorage.getItem('featured_word_date')
        const cachedWord = localStorage.getItem('featured_word')
        
        if (cachedDate === todayStr && cachedWord) {
          try {
            featuredWord.value = JSON.parse(cachedWord)
          } catch(e) {
            pickRandomWord(data, todayStr)
          }
        } else {
          pickRandomWord(data, todayStr)
        }
      }
    }

    // 3. Fetch All Connection Groups to get count
    const groupsRes = await fetch(window.API_BASE_URL + '/api/connection-groups')
    if (groupsRes.ok) {
      const groupsData = await groupsRes.json()
      totalGroupsCount.value = groupsData.length
    }
    
    // 4. Calculate Daily Progress
    const todayStr = new Date().toISOString().split('T')[0]
    const statsDate = localStorage.getItem('stats_date')

    if (statsDate !== todayStr) {
      localStorage.setItem('stats_date', todayStr)
      localStorage.setItem('initial_learn', newWordsCount.value)
      localStorage.setItem('initial_review', dueTodayCount.value)
    }

    initialLearnCount.value = parseInt(localStorage.getItem('initial_learn') || newWordsCount.value)
    initialReviewCount.value = parseInt(localStorage.getItem('initial_review') || dueTodayCount.value)

    if (initialLearnCount.value < newWordsCount.value) {
      initialLearnCount.value = newWordsCount.value
      localStorage.setItem('initial_learn', initialLearnCount.value)
    }
    if (initialReviewCount.value < dueTodayCount.value) {
      initialReviewCount.value = dueTodayCount.value
      localStorage.setItem('initial_review', initialReviewCount.value)
    }

    learnProgress.value = initialLearnCount.value > 0 
      ? Math.max(0, (initialLearnCount.value - newWordsCount.value) / initialLearnCount.value * 100) 
      : 100

    reviewProgress.value = initialReviewCount.value > 0 
      ? Math.max(0, (initialReviewCount.value - dueTodayCount.value) / initialReviewCount.value * 100) 
      : 100

  } catch (e) {
    console.error('Failed to fetch dashboard data', e)
  } finally {
    loading.value = false
  }
}

const fetchWallpaper = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/wallpapers/random')
    if (res.ok && res.status === 200) {
      const blob = await res.blob()
      wallpaperUrl.value = URL.createObjectURL(blob)
    }
  } catch(e) {
    console.error("No custom wallpaper found", e)
  }
}

const searchResult = computed(() => {
  if (!searchQuery.value.trim()) return null
  const q = searchQuery.value.trim().toLowerCase()
  return allVocabularies.value.find(v => v.word.toLowerCase().startsWith(q) || v.word.toLowerCase() === q)
})

const greetingMessage = computed(() => {
  const hour = new Date().getHours()
  if (hour >= 5 && hour < 12) return 'Good morning! 🌅'
  if (hour >= 12 && hour < 17) return 'Good afternoon! ☀️'
  if (hour >= 17 && hour < 21) return 'Good evening! 🌌'
  return 'Good night! 🌙'
})

const pickRandomWord = (wordList, todayStr) => {
  const randomIndex = Math.floor(Math.random() * wordList.length)
  const selected = wordList[randomIndex]
  const picked = {
    word: selected.word,
    phoneticUk: selected.phoneticUk || '',
    phoneticUs: selected.phoneticUs || ''
  }
  featuredWord.value = picked
  localStorage.setItem('featured_word', JSON.stringify(picked))
  localStorage.setItem('featured_word_date', todayStr)
}

// Keyboard shortcuts (L for Learn, R for Review, Cmd+K for Search)
const handleKeydown = (e) => {
  const key = e.key.toLowerCase()
  if (key === 'escape' && showSearch.value) {
    showSearch.value = false
    searchQuery.value = ''
    return
  }

  if (key === 'k' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    showSearch.value = !showSearch.value
    if (showSearch.value) {
      nextTick(() => {
        searchInputRef.value?.focus()
      })
    } else {
      searchQuery.value = ''
    }
    return
  }
  
  if (key === 'l') {
    router.push('/review?mode=learn')
  } else if (key === 'r') {
    router.push('/review?mode=review')
  }
}

onMounted(() => {
  fetchDashboardData()
  fetchWallpaper()
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<template>
  <div class="flex flex-col items-center justify-between min-h-screen w-full relative pt-4 pb-12 select-none overflow-hidden bg-cover bg-center transition-all duration-700"
       :class="wallpaperUrl ? 'bg-zinc-900' : 'bg-gradient-to-b from-rose-50/30 via-zinc-100/20 to-teal-50/20 dark:from-zinc-950 dark:via-zinc-900 dark:to-zinc-950'"
       :style="wallpaperUrl ? { backgroundImage: `url(${wallpaperUrl})` } : {}">
    
    <div v-if="wallpaperUrl" class="absolute inset-0 bg-black/20 z-0 pointer-events-none"></div>

    <!-- Top Stats Bar -->
    <div class="w-full max-w-2xl px-6 py-2 flex justify-between items-center z-10 select-none">
      <div class="flex items-center gap-2 text-sm font-black" :class="wallpaperUrl ? 'text-white/80 drop-shadow-md' : 'text-zinc-600 dark:text-zinc-400'">
        <span>{{ greetingMessage }}</span>
      </div>
      <div class="flex items-center gap-3 text-xs font-bold" :class="wallpaperUrl ? 'text-white/80 drop-shadow-md' : 'text-zinc-400 dark:text-zinc-500'">
        <span class="flex items-center gap-1"><span class="opacity-70">📖</span> {{ totalWordsCount }} words</span>
        <span class="flex items-center gap-1"><span class="opacity-70">🔗</span> {{ totalGroupsCount }} groups</span>
      </div>
    </div>


    <!-- Center featured word area -->
    <div class="flex flex-col items-center justify-center flex-1 py-12 px-4 z-10 text-center w-full max-w-lg">
      <h1 
        @click="speakFeaturedWord" 
        class="text-6xl md:text-8xl font-black tracking-tight cursor-pointer hover:scale-[1.02] active:scale-[0.98] transition-transform select-none font-sans"
        :class="wallpaperUrl ? 'text-white drop-shadow-[0_2px_15px_rgba(0,0,0,0.3)]' : 'text-zinc-900 dark:text-white drop-shadow-sm'"
      >
        {{ featuredWord.word }}
      </h1>
      
      <div 
        @click="speakFeaturedWord" 
        class="flex items-center justify-center gap-1.5 mt-4 cursor-pointer transition-colors px-4 py-1.5 rounded-full"
        :class="wallpaperUrl ? 'bg-black/30 backdrop-blur-sm text-white/90 hover:bg-black/40 border border-white/10' : 'text-zinc-500 hover:text-zinc-700 dark:text-zinc-400 dark:hover:text-zinc-300'"
      >
        <span class="text-sm font-bold tracking-wide">/{{ featuredWord.phoneticUs || featuredWord.phoneticUk }}/</span>
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
        </svg>
      </div>
    </div>

    <!-- Quick Search Bar Backdrop -->
    <transition enter-active-class="transition duration-200 ease-out" enter-from-class="opacity-0" enter-to-class="opacity-100" leave-active-class="transition duration-150 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
      <div v-if="showSearch" class="fixed inset-0 bg-black/20 backdrop-blur-sm z-[90]" @click="showSearch = false; searchQuery = ''"></div>
    </transition>

    <!-- Quick Search Bar (Overlay at top) -->
    <transition enter-active-class="transition duration-200 ease-out" enter-from-class="transform -translate-y-4 opacity-0 scale-95" enter-to-class="transform translate-y-0 opacity-100 scale-100" leave-active-class="transition duration-150 ease-in" leave-from-class="transform translate-y-0 opacity-100 scale-100" leave-to-class="transform -translate-y-4 opacity-0 scale-95">
      <div v-if="showSearch" class="fixed top-24 left-1/2 -translate-x-1/2 w-full max-w-xl z-[100] px-4 pointer-events-none">
        <div class="relative group pointer-events-auto">
          <div class="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
            <svg class="h-5 w-5 text-zinc-400" :class="wallpaperUrl ? 'text-white/60' : ''" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>
          <input 
            ref="searchInputRef"
            v-model="searchQuery"
            type="text" 
            placeholder="Quick search a word... (Press Esc to close)" 
            @keydown.esc="showSearch = false; searchQuery = ''"
            class="w-full pl-11 pr-4 py-3.5 bg-white/40 dark:bg-zinc-900/40 backdrop-blur-md border outline-none rounded-2xl shadow-[0_8px_30px_rgba(0,0,0,0.04)] font-bold transition-all focus:bg-white/60 dark:focus:bg-zinc-800/60"
          :class="wallpaperUrl ? 'border-white/20 text-white placeholder:text-white/50 focus:border-white/40 bg-zinc-900/60' : 'border-zinc-200 dark:border-zinc-800 text-zinc-900 dark:text-white focus:border-indigo-500/50 focus:ring-4 focus:ring-indigo-500/10 bg-white/80 dark:bg-zinc-900/80'"
        />
        
        <!-- Search Quick Result Popup -->
        <div v-if="searchQuery && searchResult" 
             @click="router.push('/dictionary/' + searchResult.id)"
             class="absolute top-full left-0 right-0 mt-3 p-4 backdrop-blur-2xl border shadow-2xl rounded-2xl flex items-center justify-between cursor-pointer transition-colors z-20"
             :class="wallpaperUrl ? 'border-white/20 text-white bg-zinc-900/90 hover:bg-zinc-800/95' : 'bg-white dark:bg-zinc-900 hover:bg-zinc-50 dark:hover:bg-zinc-800 border-zinc-200 dark:border-zinc-800'">
          <div class="flex flex-col text-left w-full pr-4">
            <div class="flex items-center gap-2">
              <span class="font-black text-lg" :class="wallpaperUrl ? 'text-white' : 'text-zinc-900 dark:text-white'">{{ searchResult.word }}</span>
              <span class="text-xs font-bold px-1.5 py-0.5 rounded border" :class="wallpaperUrl ? 'bg-white/10 border-white/20 text-white/70' : 'bg-zinc-100 dark:bg-zinc-800 border-zinc-200 dark:border-zinc-700 text-zinc-500'">/{{ searchResult.phoneticUs || searchResult.phoneticUk || '---' }}/</span>
            </div>
            <span class="text-sm font-semibold truncate mt-1.5 w-full" :class="wallpaperUrl ? 'text-white/80' : 'text-zinc-600 dark:text-zinc-300'">{{ getCleanTranslation(searchResult.translation) || 'No translation available' }}</span>
          </div>
          <svg class="w-5 h-5 opacity-50 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
          </svg>
        </div>
        <div v-else-if="searchQuery && !searchResult" class="absolute top-full left-0 right-0 mt-3 p-4 backdrop-blur-2xl border shadow-2xl rounded-2xl text-center text-sm font-bold z-20"
             :class="wallpaperUrl ? 'border-white/20 text-white/80 bg-zinc-900/90' : 'bg-white dark:bg-zinc-900 border-zinc-200 dark:border-zinc-800 text-zinc-500'">
          No word found matching "{{ searchQuery }}"
        </div>
        </div>
      </div>
    </transition>

    <!-- Bottom Module Pill Cards -->
    <div class="grid grid-cols-2 gap-4 w-full max-w-2xl px-6 z-10">
      
      <!-- 1. Learn Card (Left Pill) -->
      <div 
        @click="router.push('/review?mode=learn')"
        class="glass-card group flex flex-col justify-between p-5 h-32 rounded-3xl cursor-pointer hover:-translate-y-1 hover:shadow-lg transition-all duration-300 border relative overflow-hidden"
        :class="wallpaperUrl ? 'bg-black/30 border-white/20' : 'border-white/60 dark:border-zinc-800/60'"
      >
        <div class="flex justify-between items-center z-10">
          <span class="font-black text-sm" :class="wallpaperUrl ? 'text-white/90' : 'text-zinc-700 dark:text-zinc-300'">Learn New</span>
          <span class="text-[9px] font-black px-1.5 py-0.5 rounded border select-none" :class="wallpaperUrl ? 'bg-white/10 border-white/20 text-white/70' : 'border-zinc-200/50 dark:border-zinc-700/50 bg-zinc-50/50 dark:bg-zinc-800/50 text-zinc-400'">L</span>
        </div>
        <div class="flex items-end justify-between z-10 mt-auto">
          <div class="text-4xl font-black tracking-tight" :class="wallpaperUrl ? 'text-white' : 'text-orange-500'">
            {{ newWordsCount }}
          </div>
          <div class="text-xs font-bold mb-1" :class="wallpaperUrl ? 'text-white/60' : 'text-zinc-400'">
            {{ learnProgress === 100 ? 'All done! 🎉' : Math.round(learnProgress) + '% done' }}
          </div>
        </div>
        
        <!-- Progress Bar Background -->
        <div class="absolute bottom-0 left-0 h-1.5 bg-black/10 dark:bg-white/5 w-full">
          <div class="h-full transition-all duration-1000 ease-out" 
               :class="wallpaperUrl ? 'bg-white' : 'bg-orange-500'" 
               :style="{ width: `${learnProgress}%` }"></div>
        </div>
      </div>

      <!-- 2. Review Card (Right Pill) -->
      <div 
        @click="router.push('/review?mode=review')"
        class="glass-card group flex flex-col justify-between p-5 h-32 rounded-3xl cursor-pointer hover:-translate-y-1 hover:shadow-lg transition-all duration-300 border relative overflow-hidden"
        :class="wallpaperUrl ? 'bg-black/30 border-white/20' : 'border-white/60 dark:border-zinc-800/60'"
      >
        <div class="flex justify-between items-center z-10">
          <span class="font-black text-sm" :class="wallpaperUrl ? 'text-white/90' : 'text-zinc-700 dark:text-zinc-300'">Review Due</span>
          <span class="text-[9px] font-black px-1.5 py-0.5 rounded border select-none" :class="wallpaperUrl ? 'bg-white/10 border-white/20 text-white/70' : 'border-zinc-200/50 dark:border-zinc-700/50 bg-zinc-50/50 dark:bg-zinc-800/50 text-zinc-400'">R</span>
        </div>
        <div class="flex items-end justify-between z-10 mt-auto">
          <div class="text-4xl font-black tracking-tight" :class="wallpaperUrl ? 'text-white' : 'text-orange-500'">
            {{ dueTodayCount }}
          </div>
          <div class="text-xs font-bold mb-1" :class="wallpaperUrl ? 'text-white/60' : 'text-zinc-400'">
            {{ reviewProgress === 100 ? 'All done! 🎉' : Math.round(reviewProgress) + '% done' }}
          </div>
        </div>
        
        <!-- Progress Bar Background -->
        <div class="absolute bottom-0 left-0 h-1.5 bg-black/10 dark:bg-white/5 w-full">
          <div class="h-full transition-all duration-1000 ease-out" 
               :class="wallpaperUrl ? 'bg-white' : 'bg-orange-500'" 
               :style="{ width: `${reviewProgress}%` }"></div>
        </div>
      </div>

    </div>

  </div>
</template>

<style scoped>
.glass-card {
  background-color: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(40px);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.015);
}
.dark .glass-card {
  background-color: rgba(24, 24, 27, 0.45);
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.05);
}
</style>
