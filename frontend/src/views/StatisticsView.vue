<script setup>
import { ref, onMounted, computed } from 'vue'

const currentTab = ref('main') // 'main' | 'overview' | 'calendar'

const stats = ref({
  todayWords: 0,
  cumulativeWords: 0,
  todayTime: 0,
  cumulativeTime: 0,
  totalWords: 0,
  learnedWords: 0,
  weeklyStats: [],
  allStats: []
})

const loading = ref(true)

const fetchStats = async () => {
  loading.value = true
  try {
    const res = await fetch(window.API_BASE_URL + '/api/stats')
    if (res.ok) {
      stats.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch stats', e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchStats()
})

// === OVERVIEW TAB CONTROLS ===
const vocabTab = ref('week') // 'week' | 'month'
const timeTab = ref('week') // 'week' | 'month'

// Selected data points for details display below charts
const selectedVocabIndex = ref(null)
const selectedTimeIndex = ref(null)

// Helper to get past dates
const getPastDates = (numDays) => {
  const result = []
  for (let i = numDays - 1; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    result.push(d.toISOString().split('T')[0])
  }
  return result
}

// Generate weekly stats data for chart
const weeklyChartData = computed(() => {
  const dates = getPastDates(7)
  const daysOfWeek = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']
  return dates.map((dateStr, idx) => {
    const d = new Date(dateStr)
    const label = idx === 6 ? '今日' : daysOfWeek[d.getDay() === 0 ? 6 : d.getDay() - 1]
    const dbStat = stats.value.allStats?.find(ws => ws.date === dateStr)
    
    // For mock display in chart, let's distinguish between "learned" and "reviewed" words
    // H2 only tracks total wordsReviewed, so we can split it dynamically for rendering
    const total = dbStat ? dbStat.wordsReviewed : 0
    const time = dbStat ? Math.floor(dbStat.studyTimeSeconds / 60) : 0
    
    return {
      dateStr,
      label,
      total,
      learned: total > 0 ? Math.floor(total * 0.1) : 0, // Mock 10% learned
      reviewed: total > 0 ? total - Math.floor(total * 0.1) : 0,
      time
    }
  })
})

// Generate monthly stats data for chart
const monthlyChartData = computed(() => {
  const result = []
  const now = new Date()
  for (let i = 5; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
    const year = d.getFullYear()
    const month = d.getMonth()
    const yearMonthStr = `${year}-${String(month + 1).padStart(2, '0')}`
    
    // Filter stats matching this year and month
    const monthStats = stats.value.allStats?.filter(ws => {
      const wsDate = new Date(ws.date)
      return wsDate.getFullYear() === year && wsDate.getMonth() === month
    }) || []
    
    const total = monthStats.reduce((sum, item) => sum + item.wordsReviewed, 0)
    const time = Math.floor(monthStats.reduce((sum, item) => sum + item.studyTimeSeconds, 0) / 60)
    
    result.push({
      yearMonthStr,
      label: `${month + 1}月`,
      total,
      learned: total > 0 ? Math.floor(total * 0.15) : 0,
      reviewed: total > 0 ? total - Math.floor(total * 0.15) : 0,
      time
    })
  }
  return result
})

// Active vocab chart dataset
const vocabChartData = computed(() => {
  return vocabTab.value === 'week' ? weeklyChartData.value : monthlyChartData.value
})

// Max vocab value for scaling
const vocabMaxVal = computed(() => {
  return Math.max(...vocabChartData.value.map(x => x.total)) || 1
})

// Active time chart dataset
const timeChartData = computed(() => {
  return timeTab.value === 'week' ? weeklyChartData.value : monthlyChartData.value
})

// Max time value for scaling
const timeMaxVal = computed(() => {
  return Math.max(...timeChartData.value.map(x => x.time)) || 1
})

// SVG path for study time line chart
const timeLinePath = computed(() => {
  const data = timeChartData.value
  const maxVal = timeMaxVal.value
  if (data.length === 0) return ''
  return data.reduce((path, item, idx) => {
    const x = (idx / (data.length - 1)) * 600
    const y = 100 - (item.time / maxVal) * 70
    return path + (idx === 0 ? `M ${x} ${y}` : ` L ${x} ${y}`)
  }, '')
})

// Active stats detail for display
const activeVocabDetail = computed(() => {
  const data = vocabChartData.value
  const idx = selectedVocabIndex.value !== null ? selectedVocabIndex.value : data.length - 1
  return data[idx] || { learned: 0, reviewed: 0 }
})

const activeTimeDetail = computed(() => {
  const data = timeChartData.value
  const idx = selectedTimeIndex.value !== null ? selectedTimeIndex.value : data.length - 1
  return data[idx] || { time: 0 }
})


// === CALENDAR TAB CONTROLS ===
const calendarYear = ref(new Date().getFullYear())
const calendarMonth = ref(new Date().getMonth()) // 0-indexed

const changeMonth = (direction) => {
  let m = calendarMonth.value + direction
  let y = calendarYear.value
  if (m < 0) {
    m = 11
    y--
  } else if (m > 11) {
    m = 0
    y++
  }
  calendarMonth.value = m
  calendarYear.value = y
}

// Generate calendar cells
const getCalendarCells = () => {
  const y = calendarYear.value
  const m = calendarMonth.value
  const totalDays = new Date(y, m + 1, 0).getDate()
  
  // Start day of the week (1st of month)
  // getDay(): 0 = Sun, 1 = Mon ... 6 = Sat
  // We want: Mon = 0, Tue = 1 ... Sun = 6
  let startDay = new Date(y, m, 1).getDay()
  startDay = startDay === 0 ? 6 : startDay - 1
  
  const cells = []
  // Pad empty days at start
  for (let i = 0; i < startDay; i++) {
    cells.push({ isPadding: true })
  }
  
  const todayStr = new Date().toISOString().split('T')[0]
  
  // Fill month days
  for (let d = 1; d <= totalDays; d++) {
    const cellDateStr = `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const dbStat = stats.value.allStats?.find(ws => ws.date === cellDateStr)
    const isToday = cellDateStr === todayStr
    
    cells.push({
      isPadding: false,
      dateNum: d,
      dateStr: cellDateStr,
      isToday,
      hasStudied: dbStat ? dbStat.wordsReviewed > 0 : false
    })
  }
  return cells
}

// Generate dates for calendar (7-day strip)
const getCalendarDays = () => {
  const days = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT']
  const result = []
  for (let i = 6; i >= 0; i--) {
    const d = new Date()
    d.setDate(d.getDate() - i)
    
    // Convert to MON=0, TUE=1 ... SUN=6 format for naming, matching getCalendarDays behavior
    const dayName = i === 0 ? '今' : days[d.getDay() === 0 ? 0 : d.getDay()]
    
    result.push({
      dayName,
      dateNum: d.getDate(),
      isToday: i === 0,
      hasStudied: stats.value.allStats?.some(ws => ws.date === d.toISOString().split('T')[0] && ws.wordsReviewed > 0)
    })
  }
  return result
}

// Calculations for Calendar Tab
const totalSignIns = computed(() => {
  // Count how many days in allStats have wordsReviewed > 0
  return stats.value.allStats?.filter(ws => ws.wordsReviewed > 0).length || 0
})

const continuousStreak = computed(() => {
  if (!stats.value.allStats || stats.value.allStats.length === 0) return 0
  
  const sortedStats = [...stats.value.allStats]
    .filter(ws => ws.wordsReviewed > 0)
    .map(ws => ws.date)
    .sort((a, b) => new Date(b) - new Date(a)) // Desending (newest first)
    
  if (sortedStats.length === 0) return 0
  
  const todayStr = new Date().toISOString().split('T')[0]
  const yesterday = new Date()
  yesterday.setDate(yesterday.getDate() - 1)
  const yesterdayStr = yesterday.toISOString().split('T')[0]
  
  // Check if user has signed in today or yesterday to continue streak
  const hasCheckedInLatest = sortedStats.includes(todayStr) || sortedStats.includes(yesterdayStr)
  if (!hasCheckedInLatest) return 0
  
  let streak = 0
  let checkDate = new Date(sortedStats.includes(todayStr) ? todayStr : yesterdayStr)
  
  while (true) {
    const checkStr = checkDate.toISOString().split('T')[0]
    if (sortedStats.includes(checkStr)) {
      streak++
      checkDate.setDate(checkDate.getDate() - 1) // Go to previous day
    } else {
      break
    }
  }
  return streak
})


</script>

<template>
  <div class="w-full max-w-4xl mx-auto flex flex-col gap-6 py-6 px-4 select-none">
    
    <!-- ============================================ -->
    <!-- 1. MAIN DASHBOARD VIEW                       -->
    <!-- ============================================ -->
    <div v-if="currentTab === 'main'" class="flex flex-col gap-8">
      
      <!-- Top Title -->
      <div class="flex justify-between items-center">
        <h2 class="text-2xl font-bold text-zinc-900 dark:text-white">我的数据</h2>
      </div>

      <!-- Overview Card (Pic 1 Style) -->
      <div class="bg-white dark:bg-zinc-900 rounded-3xl p-6 border border-zinc-100 dark:border-zinc-800 shadow-sm flex flex-col gap-6">
        <div @click="currentTab = 'overview'" class="flex justify-between items-center cursor-pointer group">
          <span class="text-lg font-bold text-zinc-800 dark:text-zinc-200">概览</span>
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-zinc-400 group-hover:text-zinc-700 dark:group-hover:text-zinc-200 transition-colors" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" />
          </svg>
        </div>
        
        <div class="grid grid-cols-2 gap-y-8 gap-x-12">
          <!-- Item 1: Today Words -->
          <div class="flex flex-col gap-1.5">
            <span class="text-xs font-bold text-zinc-400 flex items-center gap-1.5">
              <span class="w-1.5 h-3 bg-amber-500 rounded-full"></span>
              今日学习&复习
            </span>
            <div class="flex items-baseline gap-1">
              <span class="text-3xl font-black text-zinc-900 dark:text-white tracking-tight">{{ stats.todayWords }}</span>
              <span class="text-xs font-bold text-zinc-400">词</span>
            </div>
          </div>
          
          <!-- Item 2: Cumulative Words -->
          <div class="flex flex-col gap-1.5">
            <span class="text-xs font-bold text-zinc-400 flex items-center gap-1.5">
              <span class="w-1.5 h-3 bg-red-400 rounded-full"></span>
              累计学习
            </span>
            <div class="flex items-baseline gap-1">
              <span class="text-3xl font-black text-zinc-900 dark:text-white tracking-tight">{{ stats.cumulativeWords }}</span>
              <span class="text-xs font-bold text-zinc-400">词</span>
            </div>
          </div>

          <!-- Item 3: Today Time -->
          <div class="flex flex-col gap-1.5">
            <span class="text-xs font-bold text-zinc-400 flex items-center gap-1.5">
              <span class="w-1.5 h-3 bg-amber-500 rounded-full"></span>
              今日总时长
            </span>
            <div class="flex items-baseline gap-1">
              <span class="text-3xl font-black text-zinc-900 dark:text-white tracking-tight">{{ stats.todayTime }}</span>
              <span class="text-xs font-bold text-zinc-400">分钟</span>
            </div>
          </div>

          <!-- Item 4: Cumulative Time -->
          <div class="flex flex-col gap-1.5">
            <span class="text-xs font-bold text-zinc-400 flex items-center gap-1.5">
              <span class="w-1.5 h-3 bg-red-400 rounded-full"></span>
              累计时长
            </span>
            <div class="flex items-baseline gap-1">
              <span class="text-3xl font-black text-zinc-900 dark:text-white tracking-tight">{{ stats.cumulativeTime }}</span>
              <span class="text-xs font-bold text-zinc-400">分钟</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Calendar Card (Pic 1 Style) -->
      <div class="bg-white dark:bg-zinc-900 rounded-3xl p-6 border border-zinc-100 dark:border-zinc-800 shadow-sm flex flex-col gap-6">
        <div @click="currentTab = 'calendar'" class="flex justify-between items-center cursor-pointer group">
          <span class="text-lg font-bold text-zinc-800 dark:text-zinc-200">日历</span>
          <span class="text-xs font-bold text-zinc-400 flex items-center gap-1 group-hover:text-zinc-700 dark:group-hover:text-zinc-200 transition-colors">
            连续签到 {{ continuousStreak }} 天
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" />
            </svg>
          </span>
        </div>
        
        <!-- 7-day horizontal strip (exact recreation of Pic 1) -->
        <div class="flex justify-between items-center w-full px-2">
          <div v-for="(day, index) in getCalendarDays()" :key="index" class="flex flex-col items-center gap-4">
            <span class="text-[10px] font-bold text-zinc-400 uppercase">{{ day.dayName }}</span>
            <div 
              class="w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs"
              :class="day.hasStudied || (day.isToday && day.hasStudied) ? 'bg-orange-500 text-white shadow' : (day.isToday ? 'bg-zinc-100 dark:bg-zinc-800 text-orange-500 font-bold' : 'text-zinc-800 dark:text-zinc-300')"
            >
              <span v-if="day.hasStudied && day.isToday" class="text-white">今</span>
              <span v-else-if="day.hasStudied" class="text-white">{{ day.dateNum }}</span>
              <span v-else-if="day.isToday" class="text-orange-500">今</span>
              <span v-else class="text-zinc-700 dark:text-zinc-400">{{ day.dateNum }}</span>
            </div>
          </div>
        </div>
      </div>
      
    </div>

    <!-- ============================================ -->
    <!-- 2. OVERVIEW DETAIL VIEW                      -->
    <!-- ============================================ -->
    <div v-if="currentTab === 'overview'" class="flex flex-col gap-6">
      <!-- Back Header -->
      <div class="flex justify-between items-center py-2">
        <button @click="currentTab = 'main'" class="p-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-full transition-colors text-zinc-600 dark:text-zinc-300">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <span class="text-lg font-black text-zinc-900 dark:text-white">概览</span>
        <button @click="fetchStats" class="p-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-full transition-colors text-zinc-600 dark:text-zinc-300">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M4 2a1 1 0 011 1v2.101a7.002 7.002 0 0111.601 2.566 1 1 0 11-1.885.666A5.002 5.002 0 005.999 7H9a1 1 0 110 2H4a1 1 0 01-1-1V3a1 1 0 011-1zm.008 9.005a1 1 0 01.937.712A5.002 5.002 0 0014.001 13H11a1 1 0 110-2h5a1 1 0 011 1v5a1 1 0 11-2 0v-2.101a7.002 7.002 0 01-11.601-2.566 1 1 0 01.61-1.277z" clip-rule="evenodd" />
          </svg>
        </button>
      </div>

      <!-- Card 1: 单词输入量 (Pic 2 style) -->
      <div class="bg-white dark:bg-zinc-900 rounded-3xl p-6 border border-zinc-100 dark:border-zinc-800 shadow-sm flex flex-col gap-6">
        <div class="flex justify-between items-center">
          <span class="text-sm font-bold text-zinc-700 dark:text-zinc-300 flex items-center gap-1">
            <span class="w-1 h-3.5 bg-orange-500 rounded-full"></span>
            单词输入量
          </span>
          <!-- Tab toggles -->
          <div class="flex bg-zinc-100 dark:bg-zinc-800 p-0.5 rounded-full text-xs font-bold text-zinc-500">
            <button @click="vocabTab = 'week'; selectedVocabIndex = null" :class="vocabTab === 'week' ? 'bg-white dark:bg-zinc-700 text-zinc-900 dark:text-white shadow-sm' : ''" class="px-3 py-1 rounded-full transition-all">最近一周</button>
            <button @click="vocabTab = 'month'; selectedVocabIndex = null" :class="vocabTab === 'month' ? 'bg-white dark:bg-zinc-700 text-zinc-900 dark:text-white shadow-sm' : ''" class="px-3 py-1 rounded-full transition-all">按月查看</button>
          </div>
        </div>

        <!-- Bar Chart SVG Container -->
        <div class="h-44 w-full flex items-end justify-between px-2 pt-6 relative border-b border-zinc-100 dark:border-zinc-800">
          <div v-for="(item, idx) in vocabChartData" :key="idx" 
               @click="selectedVocabIndex = idx"
               class="flex flex-col items-center flex-1 group cursor-pointer h-full justify-end relative">
            
            <!-- Value Popover & Purple Bar Wrapper -->
            <div class="relative w-3 flex flex-col items-center justify-end"
                 :style="{ 
                   height: `${Math.max(10, Math.min(100, (item.total / vocabMaxVal) * 90))}%`
                 }">
              
              <!-- Value Popover (Positioned relative to the bar's top) -->
              <div class="absolute -top-6 bg-zinc-900 text-white dark:bg-white dark:text-zinc-950 font-black text-[9px] px-1 py-0.5 rounded shadow-md pointer-events-none transition-all z-10"
                   :class="(selectedVocabIndex === idx || (selectedVocabIndex === null && idx === vocabChartData.length - 1)) ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'">
                {{ item.total }}
              </div>

              <!-- Purple Bar -->
              <div class="w-full h-full rounded-t-full transition-all duration-300 overflow-hidden"
                   :class="(selectedVocabIndex === idx || (selectedVocabIndex === null && idx === vocabChartData.length - 1)) ? 'bg-purple-600' : 'bg-purple-300 dark:bg-purple-700 opacity-60'">
              </div>
            </div>

            <!-- X label -->
            <span class="text-[9px] font-bold text-zinc-400 mt-2 h-4">{{ item.label }}</span>
          </div>
        </div>

        <!-- Detail summary below chart -->
        <div class="grid grid-cols-2 gap-4 text-center mt-2">
          <div class="flex flex-col">
            <span class="text-xs font-bold text-zinc-400">当日学习</span>
            <span class="text-xl font-black text-zinc-800 dark:text-white mt-1">{{ activeVocabDetail.learned }} <span class="text-xs font-bold text-zinc-400">词</span></span>
          </div>
          <div class="flex flex-col border-l border-zinc-100 dark:border-zinc-800">
            <span class="text-xs font-bold text-zinc-400">当日复习</span>
            <span class="text-xl font-black text-zinc-800 dark:text-white mt-1">{{ activeVocabDetail.reviewed }} <span class="text-xs font-bold text-zinc-400">词</span></span>
          </div>
        </div>
      </div>

      <!-- Card 2: 学习时长 (Pic 2 style) -->
      <div class="bg-white dark:bg-zinc-900 rounded-3xl p-6 border border-zinc-100 dark:border-zinc-800 shadow-sm flex flex-col gap-6">
        <div class="flex justify-between items-center">
          <span class="text-sm font-bold text-zinc-700 dark:text-zinc-300 flex items-center gap-1">
            <span class="w-1 h-3.5 bg-orange-500 rounded-full"></span>
            学习时长
          </span>
          <!-- Tab toggles -->
          <div class="flex bg-zinc-100 dark:bg-zinc-800 p-0.5 rounded-full text-xs font-bold text-zinc-500">
            <button @click="timeTab = 'week'; selectedTimeIndex = null" :class="timeTab === 'week' ? 'bg-white dark:bg-zinc-700 text-zinc-900 dark:text-white shadow-sm' : ''" class="px-3 py-1 rounded-full transition-all">最近一周</button>
            <button @click="timeTab = 'month'; selectedTimeIndex = null" :class="timeTab === 'month' ? 'bg-white dark:bg-zinc-700 text-zinc-900 dark:text-white shadow-sm' : ''" class="px-3 py-1 rounded-full transition-all">按月查看</button>
          </div>
        </div>

        <!-- Line Chart using SVG -->
        <div class="h-44 w-full relative px-6 border-b border-zinc-100 dark:border-zinc-800 flex flex-col justify-end pb-4 pt-6">
          <svg class="w-full h-full" viewBox="0 0 600 120">
            <!-- Grid Lines -->
            <line x1="0" y1="30" x2="600" y2="30" stroke="#f4f4f5" stroke-dasharray="2" />
            <line x1="0" y1="75" x2="600" y2="75" stroke="#f4f4f5" stroke-dasharray="2" />
            
            <!-- Generate SVG Path coordinates -->
            <path 
              :d="timeLinePath"
              fill="none" 
              stroke="#e4e4e7" 
              stroke-width="2" 
            />

            <!-- Circles for datapoints -->
            <g v-for="(item, idx) in timeChartData" :key="idx">
              <circle 
                :cx="`${(idx / (timeChartData.length - 1)) * 600}`"
                :cy="`${100 - (item.time / timeMaxVal) * 70}`"
                r="4.5"
                :fill="(selectedTimeIndex === idx || (selectedTimeIndex === null && idx === timeChartData.length - 1)) ? '#f97316' : '#d4d4d8'" 
                class="cursor-pointer"
                @click="selectedTimeIndex = idx"
              />
              
              <!-- Value text on top of points -->
              <text 
                :x="`${(idx / (timeChartData.length - 1)) * 600}`"
                :y="`${88 - (item.time / timeMaxVal) * 70}`"
                text-anchor="middle"
                font-size="8"
                font-weight="bold"
                :fill="(selectedTimeIndex === idx || (selectedTimeIndex === null && idx === timeChartData.length - 1)) ? '#f97316' : '#a1a1aa'"
              >
                {{ item.time }}
              </text>
            </g>
          </svg>

          <!-- X axis labels below SVG -->
          <div class="flex justify-between w-full mt-2 px-1 text-[9px] font-bold text-zinc-400">
            <span v-for="(item, idx) in timeChartData" :key="idx">{{ item.label }}</span>
          </div>
        </div>

        <!-- Detail summary below chart -->
        <div class="grid grid-cols-2 gap-4 text-center mt-2">
          <div class="flex flex-col">
            <span class="text-xs font-bold text-zinc-400">当日学习</span>
            <span class="text-xl font-black text-zinc-800 dark:text-white mt-1">{{ activeTimeDetail.time }} <span class="text-xs font-bold text-zinc-400">分钟</span></span>
          </div>
          <div class="flex flex-col border-l border-zinc-100 dark:border-zinc-800">
            <span class="text-xs font-bold text-zinc-400">总时长</span>
            <span class="text-xl font-black text-zinc-800 dark:text-white mt-1">{{ stats.cumulativeTime }} <span class="text-xs font-bold text-zinc-400">分钟</span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================================ -->
    <!-- 3. CALENDAR DETAIL VIEW                      -->
    <!-- ============================================ -->
    <div v-if="currentTab === 'calendar'" class="flex flex-col gap-6">
      
      <!-- Back Header -->
      <div class="flex justify-between items-center py-2">
        <button @click="currentTab = 'main'" class="p-2 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-full transition-colors text-zinc-600 dark:text-zinc-300">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <span class="text-lg font-black text-zinc-900 dark:text-white">日历</span>
        <div class="w-10"></div> <!-- Placeholder to center title -->
      </div>

      <!-- Calendar grid card (Pic 3 Style) -->
      <div class="bg-white dark:bg-zinc-900 rounded-3xl p-6 border border-zinc-100 dark:border-zinc-800 shadow-sm flex flex-col items-center gap-6">
        
        <!-- YYYY年MM月 Navigation Header -->
        <div class="flex items-center gap-6 justify-center">
          <button @click="changeMonth(-1)" class="p-1 text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M12.707 5.293a1 1 0 010 1.414L9.414 10l3.293 3.293a1 1 0 01-1.414 1.414l-4-4a1 1 0 010-1.414l4-4a1 1 0 011.414 0z" clip-rule="evenodd" />
            </svg>
          </button>
          <span class="font-black text-zinc-800 dark:text-white text-base">
            {{ calendarYear }}年{{ String(calendarMonth + 1).padStart(2, '0') }}月
          </span>
          <button @click="changeMonth(1)" class="p-1 text-zinc-400 hover:text-zinc-700 dark:hover:text-zinc-200">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
              <path fill-rule="evenodd" d="M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z" clip-rule="evenodd" />
            </svg>
          </button>
        </div>

        <!-- Month calendar grid -->
        <div class="w-full max-w-sm flex flex-col gap-4 mt-2">
          
          <!-- Days header -->
          <div class="grid grid-cols-7 text-center text-[10px] font-bold text-zinc-400 uppercase tracking-wide">
            <span>MON</span>
            <span>TUE</span>
            <span>WED</span>
            <span>THU</span>
            <span>FRI</span>
            <span>SAT</span>
            <span>SUN</span>
          </div>

          <!-- Cells -->
          <div class="grid grid-cols-7 gap-y-4 gap-x-2 text-center">
            <div v-for="(cell, index) in getCalendarCells()" :key="index" class="h-8 flex items-center justify-center">
              <template v-if="!cell.isPadding">
                <div 
                  class="w-7 h-7 rounded-full flex items-center justify-center font-bold text-xs"
                  :class="cell.hasStudied ? 'bg-orange-500 text-white shadow-sm' : (cell.isToday ? 'bg-zinc-100 dark:bg-zinc-800 text-orange-500' : 'text-zinc-800 dark:text-zinc-300')"
                >
                  <span v-if="cell.hasStudied && cell.isToday">今</span>
                  <span v-else-if="cell.hasStudied">{{ cell.dateNum }}</span>
                  <span v-else-if="cell.isToday">今</span>
                  <span v-else>{{ cell.dateNum }}</span>
                </div>
              </template>
            </div>
          </div>
        </div>



        <!-- Streak statistics at bottom (Pic 3 Style) -->
        <div class="grid grid-cols-2 gap-8 w-full border-t border-zinc-100 dark:border-zinc-800 pt-6 mt-2 text-center">
          <div class="flex flex-col">
            <span class="text-xs font-bold text-zinc-400">连续签到</span>
            <span class="text-xl font-black text-zinc-800 dark:text-white mt-1">{{ continuousStreak }} <span class="text-xs font-bold text-zinc-400">天</span></span>
          </div>
          <div class="flex flex-col border-l border-zinc-100 dark:border-zinc-800">
            <span class="text-xs font-bold text-zinc-400">累计签到</span>
            <span class="text-xl font-black text-zinc-800 dark:text-white mt-1">{{ totalSignIns }} <span class="text-xs font-bold text-zinc-400">天</span></span>
          </div>
        </div>
      </div>
      
    </div>

  </div>
</template>
