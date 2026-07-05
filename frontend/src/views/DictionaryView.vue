<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToast } from '../composables/useToast'

const router = useRouter()
const { success, error } = useToast()

const viewMode = ref('card') // 'card' or 'list'
const vocabularies = ref([])
const allTags = ref([])
const selectedTagId = ref(null)

const loading = ref(true)
const showAddModal = ref(false)

const rawWordInput = ref('')
const isFetching = ref(false)
const isSaving = ref(false)
const fetchedData = ref(null)

const searchQuery = ref('')
const sortBy = ref('recent') // 'recent', 'az', 'mastery'
const currentPage = ref(1)
const pageSize = ref(12) // Perfect for 2/3/4-column grids

// New Word Creation State
const searchInputRef = ref(null)
const keepModalOpen = ref(false)
const allGroups = ref([])
const selectedNewTags = ref([])
const selectedNewGroups = ref([])

// Self-test mode state
const selfTestMode = ref(false)
const revealedWords = ref(new Set())

// Batch operation state
const batchMode = ref(false)
const selectedWordIds = ref([])
const showBatchTagsMenu = ref(false)
const showBatchStageMenu = ref(false)
const batchActionType = ref('addTag') // 'addTag' or 'removeTag'

// Batch import state
const showBatchImportModal = ref(false)
const importWordsText = ref('')
const selectedImportTags = ref([])
const selectedImportGroups = ref([])
const isImporting = ref(false)
const importProgress = ref(0)
const importTotal = ref(0)
const csvFileInput = ref(null)

const toggleReveal = (wordId) => {
  if (revealedWords.value.has(wordId)) {
    revealedWords.value.delete(wordId)
  } else {
    revealedWords.value.add(wordId)
  }
}

const toggleBatchMode = () => {
  batchMode.value = !batchMode.value
  selectedWordIds.value = []
  showBatchTagsMenu.value = false
  showBatchStageMenu.value = false
  showBatchGroupMenu.value = false
}

const toggleSelectWord = (wordId) => {
  const index = selectedWordIds.value.indexOf(wordId)
  if (index === -1) {
    selectedWordIds.value.push(wordId)
  } else {
    selectedWordIds.value.splice(index, 1)
  }
}

const isSelected = (wordId) => {
  return selectedWordIds.value.includes(wordId)
}

const selectedAllCurrentPage = computed(() => {
  const currentIds = paginatedVocabularies.value.map(v => v.id)
  if (currentIds.length === 0) return false
  return currentIds.every(id => selectedWordIds.value.includes(id))
})

const toggleSelectAllCurrentPage = () => {
  const currentIds = paginatedVocabularies.value.map(v => v.id)
  if (selectedAllCurrentPage.value) {
    // Deselect all on current page
    selectedWordIds.value = selectedWordIds.value.filter(id => !currentIds.includes(id))
  } else {
    // Select all on current page
    currentIds.forEach(id => {
      if (!selectedWordIds.value.includes(id)) {
        selectedWordIds.value.push(id)
      }
    })
  }
}

const executeBatchDelete = async () => {
  const count = selectedWordIds.value.length
  if (count === 0) return
  if (!confirm(`Are you sure you want to delete the ${count} selected words?`)) return
  
  try {
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies/batch/delete', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(selectedWordIds.value)
    })
    if (res.ok) {
      selectedWordIds.value = []
      batchMode.value = false
      await fetchVocabularies()
      success(`Successfully deleted ${count} words`)
    }
  } catch (e) {
    console.error("Batch delete failed", e)
    error('Batch delete failed')
  }
}

const executeBatchSetStage = async (stage) => {
  const count = selectedWordIds.value.length
  if (count === 0) return
  try {
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies/batch/set-stage', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ids: selectedWordIds.value,
        stage: stage
      })
    })
    if (res.ok) {
      selectedWordIds.value = []
      batchMode.value = false
      showBatchStageMenu.value = false
      await fetchVocabularies()
      success(`Successfully updated stage for ${count} words`)
    }
  } catch (e) {
    console.error("Batch set stage failed", e)
    error('Failed to update stage')
  }
}

const executeBatchTagAction = async (tagId, action) => {
  const count = selectedWordIds.value.length
  if (count === 0) return
  const url = action === 'addTag' 
    ? window.API_BASE_URL + '/api/vocabularies/batch/add-tag'
    : window.API_BASE_URL + '/api/vocabularies/batch/remove-tag'
    
  try {
    const res = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ids: selectedWordIds.value,
        tagId: tagId
      })
    })
    if (res.ok) {
      selectedWordIds.value = []
      batchMode.value = false
      showBatchTagsMenu.value = false
      await fetchVocabularies()
      success(`Successfully updated tags for ${count} words`)
    }
  } catch (e) {
    console.error("Batch tag action failed", e)
    error('Failed to update tags')
  }
}

const fetchVocabularies = async () => {
  loading.value = true
  try {
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies')
    if (res.ok) {
      vocabularies.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch vocabularies', e)
  } finally {
    loading.value = false
  }
}

const fetchTags = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/tags')
    if (res.ok) {
      allTags.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch tags', e)
  }
}

const fetchAllGroups = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/connection-groups')
    if (res.ok) {
      allGroups.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch groups', e)
  }
}

const openAddModal = async () => {
  showAddModal.value = true
  selectedNewTags.value = []
  selectedNewGroups.value = []
  rawWordInput.value = ''
  fetchedData.value = null
  
  if (allGroups.value.length === 0) {
    await fetchAllGroups()
  }
  
  // Auto-focus next tick
  setTimeout(() => {
    if (searchInputRef.value) searchInputRef.value.focus()
  }, 50)
}

const robustParseTranslation = (raw) => {
  if (!raw) return ''
  let current = raw
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
      return inner && inner.translations ? inner.translations.join(', ') : inner
    })
    return cleanTranslations.join(', ')
  }
  return typeof current === 'string' ? current : JSON.stringify(current)
}

const filteredAndSortedVocabularies = computed(() => {
  let result = vocabularies.value

  if (selectedTagId.value) {
    result = result.filter(v => v.tags && v.tags.some(t => t.id === selectedTagId.value))
  }

  if (searchQuery.value.trim()) {
    const q = searchQuery.value.trim().toLowerCase()
    result = result.filter(v => {
      const wMatch = v.word.toLowerCase().includes(q)
      const tMatch = robustParseTranslation(v.translation).toLowerCase().includes(q)
      return wMatch || tMatch
    })
  }

  result = [...result].sort((a, b) => {
    if (sortBy.value === 'az') {
      return a.word.localeCompare(b.word)
    } else if (sortBy.value === 'mastery') {
      return (a.isMastered === b.isMastered) ? 0 : a.isMastered ? -1 : 1
    } else if (sortBy.value === 'forget') {
      return (b.addCount || 1) - (a.addCount || 1)
    }
    return b.id - a.id
  })
  
  return result
})

const totalPages = computed(() => Math.ceil(filteredAndSortedVocabularies.value.length / pageSize.value) || 1)

const paginatedVocabularies = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredAndSortedVocabularies.value.slice(start, start + pageSize.value)
})

const parseTranslation = (raw) => robustParseTranslation(raw)

const fetchWordInfo = async () => {
  if (!rawWordInput.value.trim()) return
  
  isFetching.value = true
  fetchedData.value = null
  let w = rawWordInput.value.trim()
  const isSpelling = w.endsWith('*')
  if (isSpelling) w = w.slice(0, -1)

  try {
    const res = await fetch(`${window.API_BASE_URL}/api/vocabularies/fetch-info?word=${encodeURIComponent(w)}`)
    if (res.ok) {
      const data = await res.json()
      let trans = ''
      if (data.translations) {
        trans = data.translations.join('\n')
      }
      fetchedData.value = {
        word: w,
        vocabType: isSpelling ? 'SPELLING' : 'RECOGNITION',
        phoneticUk: data.phoneticUk || '',
        phoneticUs: data.phoneticUs || '',
        translation: trans,
        tags: data.exam_type ? data.exam_type.join(', ') : ''
      }
      
      // Auto-select tags parsed from Youdao
      if (data.exam_type) {
        data.exam_type.forEach(tName => {
          const found = allTags.value.find(t => t.name.toLowerCase() === tName.toLowerCase().trim())
          if (found && !selectedNewTags.value.includes(found.id)) {
            selectedNewTags.value.push(found.id)
          }
        })
      }
    } else {
      fetchedData.value = {
        word: w,
        vocabType: isSpelling ? 'SPELLING' : 'RECOGNITION',
        phoneticUk: '',
        phoneticUs: '',
        translation: '',
        tags: ''
      }
    }
  } catch (e) {
    console.error('Failed to fetch info', e)
    fetchedData.value = { word: w, vocabType: isSpelling ? 'SPELLING' : 'RECOGNITION', phoneticUk: '', phoneticUs: '', translation: '', tags: '' }
  } finally {
    isFetching.value = false
  }
}

const saveWord = async () => {
  if (!fetchedData.value) return
  isSaving.value = true
  try {
    const payload = {
      word: fetchedData.value.word,
      vocabType: fetchedData.value.vocabType,
      phoneticUk: fetchedData.value.phoneticUk,
      phoneticUs: fetchedData.value.phoneticUs,
      translation: JSON.stringify({ translations: fetchedData.value.translation.split('\n').filter(t => t.trim() !== '') })
    }
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    
    if (res.ok) {
      if (payload.vocabType === 'SPELLING') {
        const fetchRes = await fetch(`${window.API_BASE_URL}/api/vocabularies/fetch-info?word=${encodeURIComponent(payload.word)}`)
        if (fetchRes.ok) {
          const fetchedData = await fetchRes.json()
          payload.phoneticUk = fetchedData.phoneticUk || ''
          payload.phoneticUs = fetchedData.phoneticUs || ''
          if (fetchedData.translations) {
            payload.translation = JSON.stringify({ translations: fetchedData.translations })
          }
        }
      }
      
      const newWord = await res.json()
      
      // Assign selected tags
      for (const tagId of selectedNewTags.value) {
        await fetch(`${window.API_BASE_URL}/api/vocabularies/${newWord.id}/tags/${tagId}`, { method: 'POST' })
      }
      
      // Assign selected groups
      for (const groupId of selectedNewGroups.value) {
        await fetch(`${window.API_BASE_URL}/api/connection-groups/${groupId}/vocabularies/${newWord.id}`, { method: 'POST' })
      }
      
      if (newWord.addCount > 1) {
        success(`🔥 该单词已在词库中！这是你第 ${newWord.addCount} 次添加它，已为你重置记忆曲线！`)
      } else {
        success('Word saved successfully')
      }
      
      await fetchVocabularies()
      
      if (keepModalOpen.value) {
        // Reset state but keep open
        rawWordInput.value = ''
        fetchedData.value = null
        selectedNewTags.value = []
        selectedNewGroups.value = []
        
        // Auto-focus next tick
        setTimeout(() => {
          if (searchInputRef.value) searchInputRef.value.focus()
        }, 50)
      } else {
        showAddModal.value = false
        rawWordInput.value = ''
        fetchedData.value = null
        selectedNewTags.value = []
        selectedNewGroups.value = []
      }
    } else {
      const err = await res.json()
      error(err.message || 'Failed to save word')
    }
  } catch (e) {
    console.error('Failed to save word', e)
    error('Network error')
  } finally {
    isSaving.value = false
  }
}

const cancelAdd = () => {
  showAddModal.value = false
  rawWordInput.value = ''
  fetchedData.value = null
  selectedNewTags.value = []
  selectedNewGroups.value = []
}

const deleteWord = async (id) => {
  if (!confirm('Are you sure you want to delete this word?')) return
  try {
    await fetch(`${window.API_BASE_URL}/api/vocabularies/${id}`, { method: 'DELETE' })
    await fetchVocabularies()
    if (currentPage.value > totalPages.value) currentPage.value = totalPages.value
  } catch (e) {
    console.error('Failed to delete word', e)
  }
}

const speakWord = (word) => {
  const url = `https://dict.youdao.com/dictvoice?audio=${encodeURIComponent(word)}&type=1`
  new Audio(url).play().catch(e => console.error("Audio play failed:", e))
}

const getTagLightStyles = (colorStr, isActive) => {
  let mainColor = colorStr || '#6366f1'
  if (isActive) {
    return {
      backgroundColor: `${mainColor}1A`, // 10% opacity
      borderColor: mainColor,
      color: mainColor
    }
  }
  return {
    borderColor: 'rgba(120, 120, 120, 0.15)',
    color: 'rgba(120, 120, 120, 0.6)'
  }
}

const openBatchImportModal = async () => {
  importWordsText.value = ''
  selectedImportTags.value = []
  selectedImportGroups.value = []
  isImporting.value = false
  importProgress.value = 0
  importTotal.value = 0
  
  if (allGroups.value.length === 0) {
    try {
      const res = await fetch(window.API_BASE_URL + '/api/connection-groups')
      if (res.ok) allGroups.value = await res.json()
    } catch (e) {
      console.error('Failed to fetch groups', e)
    }
  }
  
  showBatchImportModal.value = true
}

const handleBatchImport = async () => {
  const words = importWordsText.value
    .split('\n')
    .map(w => w.trim())
    .filter(w => w.length > 0)
    
  if (words.length === 0) {
    error('Please enter at least one word')
    return
  }
  
  isImporting.value = true
  importTotal.value = words.length
  importProgress.value = 0
  
  let successCount = 0
  let failCount = 0
  
  for (let i = 0; i < words.length; i++) {
    const word = words[i]
    try {
      const payload = {
        word: word,
        vocabType: 'RECOGNITION'
      }
      
      const res = await fetch(window.API_BASE_URL + '/api/vocabularies', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      })
      
      if (res.ok) {
        const newWord = await res.json()
        
        for (const tagId of selectedImportTags.value) {
          await fetch(`${window.API_BASE_URL}/api/vocabularies/${newWord.id}/tags/${tagId}`, { method: 'POST' })
        }
        
        for (const groupId of selectedImportGroups.value) {
          await fetch(`${window.API_BASE_URL}/api/connection-groups/${groupId}/vocabularies/${newWord.id}`, { method: 'POST' })
        }
        
        successCount++
      } else {
        failCount++
      }
    } catch (e) {
      console.error(`Failed to import word: ${word}`, e)
      failCount++
    }
    importProgress.value = i + 1
  }
  
  isImporting.value = false
  showBatchImportModal.value = false
  success(`Successfully imported ${successCount} words. Failed: ${failCount}`)
  await fetchVocabularies()
}

const fetchGroups = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/connection-groups')
    if (res.ok) {
      allGroups.value = await res.json()
    }
  } catch (e) {
    console.error(e)
  }
}

const exportCsv = () => {
  window.location.href = window.API_BASE_URL + '/api/vocabularies/export'
}

const handleCsvImport = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  const formData = new FormData()
  formData.append('file', file)
  
  isImporting.value = true
  // Show a toast message to indicate import has started since it might take a few seconds
  success('Importing CSV... Please wait.')
  
  try {
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies/import', {
      method: 'POST',
      body: formData
    })
    
    if (res.ok) {
      const data = await res.json()
      success(`CSV Imported! Added ${data.successCount} words. Skipped ${data.duplicateCount} duplicates.`)
      await fetchVocabularies()
    } else {
      const err = await res.json()
      error(err.error || 'Failed to import CSV')
    }
  } catch (e) {
    console.error('CSV import failed', e)
    error('Network error during import')
  } finally {
    isImporting.value = false
    // Reset file input
    if (csvFileInput.value) {
      csvFileInput.value.value = ''
    }
  }
}

onMounted(() => {
  fetchVocabularies()
  fetchTags()
  fetchGroups()
})
</script>

<template>
  <div>
    <!-- Hidden file input for CSV -->
    <input type="file" ref="csvFileInput" accept=".csv" class="hidden" @change="handleCsvImport" />
    
    <div class="w-full max-w-5xl mx-auto flex flex-col gap-6 px-4 pb-20 select-none">
    
    <!-- Header Block -->
    <div class="relative z-50 flex justify-between items-center bg-white/40 dark:bg-zinc-900/40 backdrop-blur-md p-6 rounded-3xl border border-white/60 dark:border-zinc-800/60 shadow-sm">
      <div class="flex flex-col gap-1.5">
        <h2 class="text-2xl font-black tracking-tight text-zinc-900 dark:text-white">
          My Dictionary <span class="text-xs font-bold text-zinc-400 dark:text-zinc-500 ml-1">({{ vocabularies.length }} words, {{ allGroups.length }} groups)</span>
        </h2>
        <div class="flex items-center gap-4 text-xs font-bold text-zinc-400 dark:text-zinc-500">
          <router-link to="/tags" class="hover:text-indigo-500 transition-colors flex items-center gap-1">🏷️ Tags ({{ allTags.length }})</router-link>
          <router-link to="/groups" class="hover:text-indigo-500 transition-colors flex items-center gap-1">🔗 Groups ({{ allGroups.length }})</router-link>
        </div>
      </div>
      <div class="flex items-center gap-3">
        <!-- Batch Edit Button -->
        <button 
          @click="toggleBatchMode" 
          class="flex items-center justify-center w-[38px] h-[38px] rounded-[14px] transition-all border active:scale-95 shrink-0"
          :class="batchMode 
            ? 'bg-indigo-600 border-indigo-600 text-white shadow-md shadow-indigo-600/20' 
            : 'bg-white/50 border-zinc-200 dark:bg-zinc-800/50 dark:border-zinc-750 text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-800'"
          :title="batchMode ? 'Exit Batch Edit' : 'Batch Edit'"
        >
          <svg v-if="batchMode" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7" />
          </svg>
          <svg v-else class="w-[18px] h-[18px]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
          </svg>
        </button>

        <!-- Combined Add Word & Batch Import Dropdown -->
        <div class="relative group">
          <button class="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-2xl font-bold transition-all shadow-md shadow-indigo-600/10 text-xs active:scale-95 flex items-center gap-1.5 h-[38px]">
            <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M12 4v16m8-8H4" />
            </svg>
            <span>Add Word</span>
            <svg class="w-3.5 h-3.5 opacity-70 ml-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 9l-7 7-7-7" />
            </svg>
          </button>
          
          <!-- Dropdown Menu -->
          <div class="absolute right-0 top-full mt-2 w-40 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-750 rounded-2xl shadow-xl opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-200 z-50 flex flex-col p-1.5 transform origin-top-right scale-95 group-hover:scale-100">
            <button @click="openAddModal" class="px-3 py-2.5 text-left text-xs font-bold text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-700 rounded-xl flex items-center gap-2.5 transition-colors">
              <svg class="w-4 h-4 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 13h6m-3-3v6m5 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              Single Word
            </button>
            <button @click="openBatchImportModal" class="px-3 py-2.5 text-left text-xs font-bold text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-700 rounded-xl flex items-center gap-2.5 transition-colors">
              <svg class="w-4 h-4 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
              </svg>
              Batch Text
            </button>
            <div class="my-1 border-t border-zinc-100 dark:border-zinc-700"></div>
            <button @click="csvFileInput.click()" class="px-3 py-2.5 text-left text-xs font-bold text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-700 rounded-xl flex items-center gap-2.5 transition-colors">
              <svg class="w-4 h-4 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
              </svg>
              Import CSV
            </button>
            <button @click="exportCsv" class="px-3 py-2.5 text-left text-xs font-bold text-zinc-700 dark:text-zinc-300 hover:bg-zinc-100 dark:hover:bg-zinc-700 rounded-xl flex items-center gap-2.5 transition-colors">
              <svg class="w-4 h-4 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              Export CSV
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- Tag Filter capsules -->
    <div v-if="allTags.length > 0" class="flex flex-col gap-2 bg-white/30 dark:bg-zinc-900/20 p-4 rounded-3xl border border-zinc-100 dark:border-zinc-800/50">
      <div class="flex items-center justify-between">
        <span class="text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">Filter by Tag</span>
        <!-- Controls Container -->
        <div class="flex items-center gap-2">
          <!-- View Mode Switcher (Segmented Control) -->
          <div class="flex p-[3px] bg-zinc-100 dark:bg-zinc-800/50 rounded-xl items-center relative select-none">
            <div class="absolute top-[3px] bottom-[3px] bg-white dark:bg-zinc-700 rounded-[9px] shadow-sm transition-all duration-300 ease-out border border-black/5 dark:border-white/5"
                 :class="viewMode === 'card' ? 'left-[3px] w-[66px]' : 'left-[71px] w-[60px]'"></div>
                 
            <button @click="viewMode = 'card'" class="relative z-10 flex items-center justify-center gap-1.5 w-[66px] h-[26px] rounded-[9px] transition-colors text-[11px] font-black tracking-wide" :class="viewMode === 'card' ? 'text-zinc-800 dark:text-zinc-100' : 'text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300'">
              <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z" /></svg>
              Card
            </button>
            <button @click="viewMode = 'list'" class="relative z-10 flex items-center justify-center gap-1.5 w-[60px] h-[26px] rounded-[9px] transition-colors text-[11px] font-black tracking-wide" :class="viewMode === 'list' ? 'text-zinc-800 dark:text-zinc-100' : 'text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300'">
              <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16" /></svg>
              List
            </button>
          </div>
          
          <!-- Self Test Mode Switcher -->
          <button 
            @click="selfTestMode = !selfTestMode; revealedWords.clear()" 
            class="flex items-center justify-center w-[32px] h-[32px] rounded-xl transition-all border active:scale-[0.98]"
            :class="selfTestMode 
              ? 'bg-amber-100/60 border-amber-300 text-amber-600 dark:bg-amber-900/30 dark:border-amber-700 dark:text-amber-400' 
              : 'bg-zinc-100/50 border-zinc-200 dark:bg-zinc-800/30 dark:border-zinc-700 text-zinc-500 hover:text-zinc-700 dark:hover:text-zinc-300'"
            :title="selfTestMode ? 'Exit Self-Test' : 'Self-Test Mode (Hide Meanings)'"
          >
            <svg v-if="selfTestMode" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
            </svg>
            <svg v-else class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
            </svg>
          </button>
        </div>
      </div>
      
      <div class="flex items-center gap-2 overflow-x-auto pb-1 mt-1 custom-scrollbar scrollbar-thin">
        <button 
          @click="selectedTagId = null; currentPage = 1" 
          class="px-4 py-1.5 rounded-full text-xs font-bold border transition-all shrink-0"
          :class="selectedTagId === null 
            ? 'bg-indigo-600 border-indigo-600 text-white shadow-sm' 
            : 'bg-white/50 border-zinc-200 text-zinc-500 dark:bg-zinc-900/30 dark:border-zinc-800 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-900/50'"
        >
          All Words
        </button>
        <button 
          v-for="tag in allTags" :key="tag.id"
          @click="selectedTagId = tag.id; currentPage = 1"
          class="px-4 py-1.5 rounded-full text-xs font-bold border transition-all shrink-0"
          :style="getTagLightStyles(tag.color, selectedTagId === tag.id)"
        >
          #{{ tag.name }}
        </button>
      </div>
    </div>

    <!-- Search and Sort Bar -->
    <div class="flex flex-col sm:flex-row gap-4 justify-between items-center bg-white/40 dark:bg-zinc-900/40 p-4 rounded-3xl border border-white/60 dark:border-zinc-800/60">
      <div class="relative w-full sm:w-80">
        <div class="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-zinc-400">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
            <path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" />
          </svg>
        </div>
        <input 
          v-model="searchQuery" 
          type="text" 
          placeholder="Search word or translation..." 
          class="w-full pl-9 pr-4 py-2 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white/50 dark:bg-zinc-900/50 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 text-xs font-semibold text-zinc-700 dark:text-zinc-300 placeholder-zinc-400"
        />
      </div>
      <div class="flex items-center gap-2 w-full sm:w-auto justify-end">
        <span class="text-xs font-bold text-zinc-400 dark:text-zinc-500">Sort:</span>
        <select v-model="sortBy" class="px-3.5 py-2 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white/50 dark:bg-zinc-900/50 focus:outline-none focus:ring-2 focus:ring-indigo-500/20 text-xs font-bold text-zinc-600 dark:text-zinc-300 cursor-pointer">
          <option value="recent">Recently Added</option>
          <option value="az">Alphabetical A-Z</option>
          <option value="mastery">Mastery Stage</option>
          <option value="forget">Forgot Count (🔥)</option>
        </select>
      </div>
    </div>

    <!-- Spinner Loading -->
    <div v-if="loading" class="flex justify-center p-20">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
    </div>
    
    <!-- Core Word Cards Grid -->
    <div v-else class="flex flex-col gap-6">
      <div v-if="paginatedVocabularies.length === 0" class="text-center p-20 glass-panel bg-white/30 dark:bg-zinc-900/30 rounded-3xl border border-zinc-100 dark:border-zinc-800 flex flex-col items-center gap-3">
        <span class="text-3xl">📭</span>
        <p class="text-sm font-semibold text-zinc-400">No words found match your filter</p>
      </div>
      
      <div v-else :class="viewMode === 'card' ? 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5' : 'flex flex-col gap-3'">
        <div 
          v-for="v in paginatedVocabularies" 
          :key="v.id" 
          @click="batchMode ? toggleSelectWord(v.id) : router.push('/dictionary/' + v.id)"
          class="relative bg-white/60 dark:bg-zinc-900/60 backdrop-blur-md rounded-3xl border border-white/50 dark:border-zinc-800/80 shadow-[0_4px_20px_-4px_rgba(0,0,0,0.02)] hover:-translate-y-1 hover:shadow-lg hover:border-indigo-500/30 dark:hover:border-indigo-500/30 transition-all duration-300 group cursor-pointer"
          :class="[
            viewMode === 'card' ? 'p-6 flex flex-col gap-4' : 'p-3 sm:p-4 flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4',
            batchMode ? 'select-none' : '',
            batchMode && isSelected(v.id) ? 'ring-2 ring-indigo-500 border-indigo-500/0 dark:ring-indigo-500' : ''
          ]"
        >
          <!-- Circular checkbox for selection (visible in Batch Mode) -->
          <div 
            v-if="batchMode" 
            class="z-20 flex items-center justify-center shrink-0"
            :class="viewMode === 'card' ? 'absolute top-4 left-4' : ''"
            @click.stop="toggleSelectWord(v.id)"
          >
            <div 
              class="w-5 h-5 rounded-full border-2 transition-all flex items-center justify-center"
              :class="isSelected(v.id) 
                ? 'bg-indigo-600 border-indigo-600 text-white' 
                : 'border-zinc-300 dark:border-zinc-650 bg-white dark:bg-zinc-900'"
            >
              <svg v-if="isSelected(v.id)" xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
              </svg>
            </div>
          </div>

          <!-- Top Row (Card) / Left Col (List): Word Header & Actions -->
          <div class="flex justify-between items-start shrink-0" :class="[
            batchMode && viewMode === 'card' ? 'pl-6' : '',
            viewMode === 'list' ? 'w-full sm:w-1/4 sm:min-w-[120px]' : ''
          ]">
            <div class="flex flex-col">
              <div class="flex items-center gap-1.5 flex-wrap">
                <span 
                  @click="batchMode ? null : speakWord(v.word)"
                  class="font-black text-zinc-800 dark:text-white tracking-tight cursor-pointer hover:text-indigo-500 transition-colors"
                  :class="viewMode === 'list' ? 'text-lg' : 'text-xl'"
                >
                  {{ v.word }}
                </span>
                
                <!-- Pronounce Icon -->
                <button 
                  v-if="!batchMode"
                  @click.stop="speakWord(v.word)"
                  class="p-1 rounded-full text-zinc-400 hover:text-indigo-500 hover:bg-zinc-100 dark:hover:bg-zinc-800 transition-all"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                  </svg>
                </button>
              </div>
              
              <!-- Phonetics -->
              <span v-if="v.phoneticUk || v.phoneticUs" class="text-xs font-mono text-zinc-400 dark:text-zinc-500 mt-0.5">
                /{{ v.phoneticUk || v.phoneticUs }}/
              </span>
              
              <!-- Add Count Badge -->
              <span v-if="v.addCount > 1" class="text-[10px] font-black text-orange-600 bg-orange-100 dark:text-orange-400 dark:bg-orange-900/30 px-2 py-0.5 rounded-full mt-1.5 w-fit border border-orange-200 dark:border-orange-800 flex items-center gap-1">
                🔥 遗忘 {{ v.addCount }} 次
              </span>
            </div>

            <!-- Hover Quick Action Buttons (Pen & Trash) - Card View Only -->
            <div 
              v-if="!batchMode && viewMode === 'card'"
              class="flex items-center gap-1 bg-zinc-50/80 dark:bg-zinc-800/80 rounded-xl p-0.5 opacity-0 group-hover:opacity-100 transition-opacity duration-300"
            >
              <router-link :to="`/dictionary/${v.id}`" class="p-1.5 text-zinc-400 hover:text-indigo-500 hover:bg-white dark:hover:bg-zinc-700 rounded-lg transition-colors" title="View details">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
                </svg>
              </router-link>
              <button @click.stop="deleteWord(v.id)" class="p-1.5 text-zinc-400 hover:text-rose-500 hover:bg-white dark:hover:bg-zinc-700 rounded-lg transition-colors" title="Delete">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd" />
                </svg>
              </button>
            </div>
          </div>

          <!-- Middle Row (Card) / Hidden (List): Ebbinghaus Mastery Progress (5 dots) -->
          <div v-if="viewMode === 'card'" class="flex items-center justify-between border-t border-zinc-100/50 dark:border-zinc-800/30 pt-3">
            <div class="flex flex-col gap-1">
              <span class="text-[9px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">Mastery</span>
              <div class="flex items-center gap-1.5">
                <span 
                  v-for="i in 5" :key="i"
                  class="w-2 h-2 rounded-full transition-all duration-300"
                  :class="i <= (v.isMastered ? 5 : v.currentStage) ? 'bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.6)]' : 'bg-zinc-200 dark:bg-zinc-800'"
                ></span>
              </div>
            </div>
            
            <div class="flex flex-col gap-1 items-end">
              <span class="text-[9px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">Type</span>
              <span class="text-[10px] font-bold px-2 py-0.5 rounded-full border bg-zinc-50/50 text-zinc-400 dark:bg-zinc-800/20 dark:border-zinc-700/50">
                {{ v.vocabType }}
              </span>
            </div>
          </div>

          <!-- Tags Row (Card) / Right Col (List) -->
          <div class="flex gap-1.5 flex-wrap shrink-0" :class="viewMode === 'list' ? 'hidden lg:flex w-1/5 max-w-[150px]' : ''">
            <span v-for="tag in v.tags?.slice(0, viewMode === 'list' ? 2 : 3)" :key="tag.id" 
                  class="px-2.5 py-0.5 rounded-full text-[10px] font-bold border transition-colors shadow-[0_1px_4px_rgba(0,0,0,0.01)]"
                  :style="getTagLightStyles(tag.color, true)">
              #{{ tag.name }}
            </span>
            <span v-if="v.tags && v.tags.length > (viewMode === 'list' ? 2 : 3)" class="px-2.5 py-0.5 rounded-full text-[10px] font-bold border border-zinc-100 bg-zinc-50/50 text-zinc-400 dark:border-zinc-800 dark:bg-zinc-800/20">
              +{{ v.tags.length - (viewMode === 'list' ? 2 : 3) }}
            </span>
          </div>

          <!-- Bottom: Definition Translation Area (Self-test compatible) -->
          <div class="bg-zinc-50/60 dark:bg-zinc-950/20 p-3 rounded-2xl border border-zinc-100/50 dark:border-zinc-800/50 min-h-12 flex items-center justify-center flex-1"
               :class="viewMode === 'card' ? 'mt-auto' : 'w-full sm:w-auto'">
            
            <!-- Default / Self-Test NOT Active OR Word is Revealed -->
            <div v-if="!selfTestMode || revealedWords.has(v.id)" class="w-full text-left">
              <p class="text-xs font-bold text-zinc-600 dark:text-zinc-300 leading-normal" :class="viewMode === 'list' ? 'line-clamp-2' : ''">
                {{ parseTranslation(v.translation) || 'No translation available' }}
              </p>
            </div>
            
            <!-- Self-Test Active & Word is Hidden -->
            <div 
              v-else 
              @click.stop="toggleReveal(v.id)"
              class="w-full h-full py-1 text-center cursor-pointer hover:bg-amber-100/10 active:scale-[0.99] transition-all rounded-xl border border-dashed border-amber-400/30 flex items-center justify-center gap-1.5 text-amber-500"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
              </svg>
              <span class="text-[10px] font-black uppercase tracking-wider">Show Definition</span>
            </div>
          </div>

          <!-- Actions for List View (Hidden on mobile or card view) -->
          <div 
            v-if="!batchMode && viewMode === 'list'"
            class="hidden sm:flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-300 ml-auto shrink-0"
          >
            <router-link :to="`/dictionary/${v.id}`" class="p-2 text-zinc-400 hover:text-indigo-500 hover:bg-zinc-100 dark:hover:bg-zinc-700 rounded-xl transition-colors" title="View details">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
              </svg>
            </router-link>
            <button @click.stop="deleteWord(v.id)" class="p-2 text-zinc-400 hover:text-rose-500 hover:bg-zinc-100 dark:hover:bg-zinc-700 rounded-xl transition-colors" title="Delete">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <!-- Pagination Footer -->
      <div v-if="totalPages > 1" class="flex justify-between items-center bg-white/40 dark:bg-zinc-900/40 p-4 rounded-3xl border border-white/60 dark:border-zinc-800/60 shadow-sm mt-4">
        <span class="text-xs font-bold text-zinc-400 dark:text-zinc-500">
          Showing {{ (currentPage - 1) * pageSize + 1 }} to {{ Math.min(currentPage * pageSize, filteredAndSortedVocabularies.length) }} of {{ filteredAndSortedVocabularies.length }} words
        </span>
        <div class="flex gap-1.5 items-center">
          <button 
            @click="currentPage > 1 && currentPage--; window.scrollTo({ top: 0, behavior: 'smooth' })" 
            :disabled="currentPage === 1"
            class="p-2 rounded-xl border border-zinc-200 dark:border-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-500 dark:text-zinc-400 transition-colors"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          
          <span class="px-3 py-1.5 text-xs font-black text-zinc-500 dark:text-zinc-400">
            Page {{ currentPage }} / {{ totalPages }}
          </span>
          
          <button 
            @click="currentPage < totalPages && currentPage++; window.scrollTo({ top: 0, behavior: 'smooth' })" 
            :disabled="currentPage === totalPages"
            class="p-2 rounded-xl border border-zinc-200 dark:border-zinc-800 disabled:opacity-40 disabled:cursor-not-allowed hover:bg-zinc-100 dark:hover:bg-zinc-800 text-zinc-500 dark:text-zinc-400 transition-colors"
          >
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </div>
      </div>
    </div>

    <!-- Add Word Modal (Editable Entry) -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-md transition-opacity">
      <div class="w-full max-w-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl rounded-3xl p-8 relative overflow-hidden flex flex-col max-h-[90vh]">
        
        <!-- Decorative subtle gradient blob -->
        <div class="absolute -top-24 -right-24 w-48 h-48 bg-primary/20 rounded-full blur-3xl pointer-events-none"></div>

        <h3 class="text-2xl font-bold mb-2 text-zinc-900 dark:text-white">Add New Word ✨</h3>
        <p v-if="!fetchedData" class="text-sm text-zinc-500 dark:text-zinc-400 mb-6 leading-relaxed">
          Type a word to fetch its meaning from the dictionary.<br>
          <span class="font-medium text-primary">Tip:</span> Append an asterisk (*) for <span class="font-bold">SPELLING</span> mode (e.g., <code class="bg-zinc-200 dark:bg-zinc-800 px-1 rounded text-xs">apple*</code>).
        </p>
        
        <div class="flex-1 overflow-y-auto overscroll-contain pr-2 custom-scrollbar relative z-10">
          <div v-if="!fetchedData" class="flex flex-col gap-5">
            <input 
              ref="searchInputRef"
              v-model="rawWordInput" 
              @keyup.enter="fetchWordInfo"
              type="text"
              class="w-full bg-white dark:bg-black/20 border-2 border-zinc-200 dark:border-white/10 rounded-2xl p-4 outline-none focus:border-primary dark:focus:border-primary transition-colors text-zinc-900 dark:text-white placeholder:text-zinc-400 font-medium text-lg" 
              placeholder="e.g. software"
              :disabled="isFetching"
            />
            
            <!-- Skeleton Loader -->
            <div v-if="isFetching" class="flex flex-col gap-4 mt-2 animate-pulse">
              <div class="h-10 bg-zinc-200 dark:bg-zinc-800 rounded-xl w-1/3"></div>
              <div class="h-12 bg-zinc-200 dark:bg-zinc-800 rounded-xl w-full"></div>
              <div class="h-12 bg-zinc-200 dark:bg-zinc-800 rounded-xl w-full"></div>
              <div class="h-24 bg-zinc-200 dark:bg-zinc-800 rounded-xl w-full"></div>
            </div>
          </div>

          <div v-else class="flex flex-col gap-4">
            <div class="flex items-center gap-3">
              <span class="text-3xl font-bold text-zinc-900 dark:text-white">{{ fetchedData.word }}</span>
              <button @click="speakWord(fetchedData.word)" class="p-1.5 text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded-lg transition-colors cursor-pointer" title="Listen">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5 10v4a2 2 0 002 2h2l4 4V4L9 8H7a2 2 0 00-2 2z" />
                </svg>
              </button>
              <span class="px-2.5 py-1 bg-zinc-200 dark:bg-zinc-700/80 text-zinc-800 dark:text-zinc-200 rounded-md text-xs font-medium tracking-wide">
                {{ fetchedData.vocabType }}
              </span>
            </div>

            <div class="flex flex-col gap-1.5">
              <label class="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Phonetic (UK)</label>
              <input v-model="fetchedData.phoneticUk" type="text" class="w-full bg-white dark:bg-black/20 border border-zinc-200 dark:border-white/10 rounded-xl p-3 outline-none focus:border-primary transition-colors text-zinc-900 dark:text-white font-mono" />
            </div>

            <div class="flex flex-col gap-1.5">
              <label class="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Phonetic (US)</label>
              <input v-model="fetchedData.phoneticUs" type="text" class="w-full bg-white dark:bg-black/20 border border-zinc-200 dark:border-white/10 rounded-xl p-3 outline-none focus:border-primary transition-colors text-zinc-900 dark:text-white font-mono" />
            </div>

            <div class="flex flex-col gap-1.5">
              <label class="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Translation (One per line)</label>
              <textarea v-model="fetchedData.translation" rows="3" class="w-full bg-white dark:bg-black/20 border border-zinc-200 dark:border-white/10 rounded-xl p-3 outline-none focus:border-primary transition-colors text-zinc-900 dark:text-white resize-none"></textarea>
            </div>
            
            <div class="flex flex-col gap-1.5" v-if="fetchedData.tags">
              <label class="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Auto-detected Tags</label>
              <div class="text-sm text-zinc-500">{{ fetchedData.tags }}</div>
            </div>
            
            <!-- Quick Tag Selection -->
            <div class="flex flex-col gap-2 mt-2">
              <label class="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Add Tags</label>
              <div class="flex flex-wrap gap-2">
                <span 
                  v-for="tag in allTags" :key="tag.id"
                  @click="selectedNewTags.includes(tag.id) ? selectedNewTags.splice(selectedNewTags.indexOf(tag.id), 1) : selectedNewTags.push(tag.id)"
                  class="px-3 py-1.5 rounded-full text-xs font-bold cursor-pointer transition-all border shadow-sm select-none"
                  :style="getTagLightStyles(tag.color, selectedNewTags.includes(tag.id))"
                >
                  {{ tag.name }}
                </span>
                <span v-if="allTags.length === 0" class="text-xs text-zinc-400 italic">No tags available.</span>
              </div>
            </div>

            <!-- Quick Group Selection -->
            <div class="flex flex-col gap-2 mt-2">
              <label class="text-sm font-semibold text-zinc-700 dark:text-zinc-300">Add to Groups</label>
              <div class="flex flex-wrap gap-2">
                <span 
                  v-for="group in allGroups" :key="group.id"
                  @click="selectedNewGroups.includes(group.id) ? selectedNewGroups.splice(selectedNewGroups.indexOf(group.id), 1) : selectedNewGroups.push(group.id)"
                  class="px-3 py-1.5 rounded-full text-xs font-bold cursor-pointer transition-all border shadow-sm select-none"
                  :class="selectedNewGroups.includes(group.id) ? 'bg-indigo-100 border-indigo-300 text-indigo-700 dark:bg-indigo-900/40 dark:border-indigo-700 dark:text-indigo-300' : 'bg-white border-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:border-zinc-700 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-700'"
                >
                  {{ group.name || 'Unnamed Group' }}
                </span>
                <span v-if="allGroups.length === 0" class="text-xs text-zinc-400 italic">No groups available.</span>
              </div>
            </div>
          </div>
        </div>

        <div class="flex items-center justify-between mt-6 pt-4 border-t border-zinc-200 dark:border-zinc-800 relative z-10">
          <label class="flex items-center gap-2 cursor-pointer text-xs font-bold text-zinc-600 dark:text-zinc-400 hover:text-indigo-500 transition-colors">
            <input type="checkbox" v-model="keepModalOpen" class="w-4 h-4 rounded border-zinc-300 text-indigo-600 focus:ring-indigo-500 dark:border-zinc-600 dark:bg-zinc-700" />
            Keep open for next word
          </label>
          
          <div class="flex gap-3">
            <button 
            @click="cancelAdd" 
            class="px-6 py-3 bg-zinc-200 dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 rounded-xl hover:bg-zinc-300 dark:hover:bg-zinc-700 font-semibold transition-colors"
            :disabled="isFetching || isSaving"
          >
            Cancel
          </button>
          
          <button 
            v-if="!fetchedData"
            @click="fetchWordInfo" 
            class="px-6 py-3 bg-primary text-white rounded-xl shadow-lg shadow-primary/30 hover:bg-primary/90 font-semibold transition-all flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
            :disabled="isFetching || !rawWordInput.trim()"
          >
            <svg v-if="isFetching" class="animate-spin -ml-1 mr-2 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            {{ isFetching ? 'Fetching...' : 'Fetch Meaning' }}
          </button>

          <button 
            v-else
            @click="saveWord" 
            class="px-8 py-3 bg-indigo-600 text-white rounded-xl shadow-lg shadow-indigo-500/20 hover:bg-indigo-700 font-bold transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 active:scale-95"
            :disabled="isSaving"
          >
            <span v-if="isSaving" class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin"></span>
            {{ isSaving ? 'Saving...' : 'Save Word' }}
          </button>
        </div>
      </div>
      </div>
    </div>

    <!-- Floating Batch Action Toolbar -->
    <transition name="slide-up">
      <div 
        v-if="batchMode && selectedWordIds.length > 0" 
        class="fixed bottom-24 left-1/2 -translate-x-1/2 z-40 w-full max-w-2xl px-4"
      >
        <div class="bg-zinc-900/90 dark:bg-zinc-950/90 text-white backdrop-blur-lg px-6 py-4 rounded-3xl border border-white/10 shadow-2xl flex items-center justify-between gap-4 flex-wrap relative">
          
          <!-- Left: Select All & Count -->
          <div class="flex items-center gap-3">
            <button 
              @click="toggleSelectAllCurrentPage"
              class="flex items-center gap-1.5 text-xs font-bold text-zinc-300 hover:text-white transition-colors"
            >
              <div 
                class="w-4 h-4 rounded border transition-all flex items-center justify-center"
                :class="selectedAllCurrentPage 
                  ? 'bg-indigo-500 border-indigo-500 text-white' 
                  : 'border-zinc-500 bg-transparent'"
              >
                <svg v-if="selectedAllCurrentPage" xmlns="http://www.w3.org/2000/svg" class="h-2.5 w-2.5" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
                </svg>
              </div>
              <span>Select All Page</span>
            </button>
            
            <span class="text-xs font-bold text-zinc-400">|</span>
            <span class="text-xs font-black text-indigo-400">{{ selectedWordIds.length }} selected</span>
          </div>
          
          <!-- Right: Actions -->
          <div class="flex items-center gap-2 flex-wrap">
            <!-- Set Stage Action Button -->
            <div class="relative">
              <button 
                @click="showBatchStageMenu = !showBatchStageMenu; showBatchTagsMenu = false; showBatchGroupMenu = false"
                class="px-3.5 py-2 bg-zinc-800 hover:bg-zinc-700 active:scale-95 text-xs font-bold rounded-2xl border border-zinc-700 transition-all flex items-center gap-1"
              >
                📈 Set Stage
              </button>
              
              <!-- Set Stage Popover menu -->
              <div v-if="showBatchStageMenu" class="absolute bottom-12 right-0 bg-zinc-850 border border-zinc-700/60 rounded-3xl p-3 shadow-2xl flex flex-col gap-2 min-w-[12rem] z-50 text-zinc-200">
                <span class="text-[9px] font-black text-zinc-400 uppercase tracking-widest text-center pb-1 border-b border-zinc-700">Set Memory Stage</span>
                <div class="grid grid-cols-6 gap-1 mt-1">
                  <button 
                    v-for="s in 6" :key="s-1"
                    @click="executeBatchSetStage(s-1)"
                    class="w-7 h-7 rounded-xl hover:bg-indigo-600 hover:text-white flex items-center justify-center font-bold text-xs bg-zinc-800 transition-colors"
                  >
                    {{ s-1 }}
                  </button>
                </div>
              </div>
            </div>
            
            <!-- Tags Action Button -->
            <div class="relative">
              <button 
                @click="showBatchTagsMenu = !showBatchTagsMenu; showBatchStageMenu = false; showBatchGroupMenu = false"
                class="px-3.5 py-2 bg-zinc-800 hover:bg-zinc-700 active:scale-95 text-xs font-bold rounded-2xl border border-zinc-700 transition-all flex items-center gap-1"
              >
                🏷️ Tags
              </button>
              
              <!-- Tags Popover menu -->
              <div v-if="showBatchTagsMenu" class="absolute bottom-12 right-0 bg-zinc-850 border border-zinc-700/60 rounded-3xl p-4 shadow-2xl flex flex-col gap-3 min-w-[16rem] z-50 text-zinc-200 max-h-64 overflow-y-auto custom-scrollbar">
                <div class="flex bg-zinc-800 p-0.5 rounded-xl text-[9px] font-black text-zinc-400 border border-zinc-750">
                  <button 
                    @click="batchActionType = 'addTag'"
                    class="flex-1 py-1 rounded-lg text-center"
                    :class="batchActionType === 'addTag' ? 'bg-zinc-750 text-white shadow-sm font-bold' : ''"
                  >
                    ADD TAG
                  </button>
                  <button 
                    @click="batchActionType = 'removeTag'"
                    class="flex-1 py-1 rounded-lg text-center"
                    :class="batchActionType === 'removeTag' ? 'bg-zinc-750 text-white shadow-sm font-bold' : ''"
                  >
                    REMOVE TAG
                  </button>
                </div>
                
                <div class="flex flex-col gap-1.5">
                  <button 
                    v-for="tag in allTags" :key="tag.id"
                    @click="executeBatchTagAction(tag.id, batchActionType)"
                    class="w-full px-3 py-1.5 rounded-xl hover:bg-indigo-600 hover:text-white flex items-center gap-2 text-xs font-bold bg-zinc-800 transition-colors text-left"
                  >
                    <span class="w-2.5 h-2.5 rounded-full border border-white/10 shrink-0" :style="{ backgroundColor: tag.color }"></span>
                    <span class="truncate">#{{ tag.name }}</span>
                  </button>
                  <span v-if="allTags.length === 0" class="text-[10px] text-zinc-500 italic text-center py-2">No tags exist.</span>
                </div>
              </div>
            </div>
            

            <!-- Delete Button -->
            <button 
              @click="executeBatchDelete"
              class="px-3.5 py-2 bg-rose-600 hover:bg-rose-700 active:scale-95 text-xs font-bold rounded-2xl border border-rose-600 transition-all flex items-center gap-1"
            >
              🗑️ Delete
            </button>
          </div>
        </div>
      </div>
    </transition>

    <!-- Batch Import Modal -->
    <transition name="fade">
      <div v-if="showBatchImportModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-900/40 dark:bg-black/60 backdrop-blur-sm" @click.self="!isImporting && (showBatchImportModal = false)">
        <div class="bg-white dark:bg-zinc-900 rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl border border-zinc-100 dark:border-zinc-800 p-6 flex flex-col gap-4 relative max-h-[85vh]">
          
          <h2 class="text-xl font-black text-zinc-800 dark:text-zinc-100 shrink-0">Batch Import Words</h2>
          
          <!-- Progress Bar State -->
          <div v-if="isImporting" class="flex flex-col items-center justify-center py-12 gap-4 flex-1">
            <div class="w-full bg-zinc-100 dark:bg-zinc-800 h-3 rounded-full overflow-hidden relative">
              <div class="bg-indigo-600 h-full rounded-full transition-all duration-300" :style="{ width: `${(importProgress / importTotal) * 100}%` }"></div>
            </div>
            <div class="text-sm font-bold text-zinc-600 dark:text-zinc-300">
              Importing: {{ importProgress }} / {{ importTotal }} words
            </div>
            <div class="text-xs text-zinc-400">Please do not close this modal or refresh the page.</div>
          </div>
          
          <div v-else class="flex flex-col gap-4 flex-1 overflow-y-auto pr-1 min-h-0 custom-scrollbar">
            <!-- Textarea for paste words -->
            <div class="flex flex-col gap-1.5 shrink-0">
              <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Words to Import (One per line)</label>
              <textarea 
                v-model="importWordsText" 
                rows="6"
                placeholder="e.g.&#10;scrutiny&#10;vague&#10;meticulous" 
                class="w-full px-4 py-3 bg-zinc-50 dark:bg-zinc-805 border border-zinc-200 dark:border-zinc-700 rounded-2xl focus:border-indigo-500 transition-all font-mono text-sm text-zinc-800 dark:text-zinc-200 resize-none"
              ></textarea>
            </div>

            <!-- Tags for import words -->
            <div class="flex flex-col gap-2 mt-1 shrink-0">
              <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Apply Tags to All Words</label>
              <div class="flex flex-wrap gap-2">
                <span 
                  v-for="tag in allTags" :key="tag.id"
                  @click="selectedImportTags.includes(tag.id) ? selectedImportTags.splice(selectedImportTags.indexOf(tag.id), 1) : selectedImportTags.push(tag.id)"
                  class="px-3 py-1.5 rounded-full text-xs font-bold cursor-pointer transition-all border shadow-sm select-none"
                  :style="getTagLightStyles(tag.color, selectedImportTags.includes(tag.id))"
                >
                  {{ tag.name }}
                </span>
                <span v-if="allTags.length === 0" class="text-xs text-zinc-400 italic">No tags available.</span>
              </div>
            </div>

            <!-- Groups for import words -->
            <div class="flex flex-col gap-2 mt-1 shrink-0">
              <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Add All Words to Groups</label>
              <div class="flex flex-wrap gap-2">
                <span 
                  v-for="group in allGroups" :key="group.id"
                  @click="selectedImportGroups.includes(group.id) ? selectedImportGroups.splice(selectedImportGroups.indexOf(group.id), 1) : selectedImportGroups.push(group.id)"
                  class="px-3 py-1.5 rounded-full text-xs font-bold cursor-pointer transition-all border shadow-sm select-none"
                  :class="selectedImportGroups.includes(group.id) ? 'bg-indigo-100 border-indigo-300 text-indigo-700 dark:bg-indigo-900/40 dark:border-indigo-700 dark:text-indigo-300' : 'bg-white border-zinc-200 text-zinc-600 dark:bg-zinc-800 dark:border-zinc-700 dark:text-zinc-400 hover:bg-zinc-50 dark:hover:bg-zinc-700'"
                >
                  {{ group.name || 'Unnamed Group' }}
                </span>
                <span v-if="allGroups.length === 0" class="text-xs text-zinc-400 italic">No groups available.</span>
              </div>
            </div>
          </div>
          
          <div v-if="!isImporting" class="flex gap-3 pt-4 border-t border-zinc-100 dark:border-zinc-800 shrink-0">
            <button @click="showBatchImportModal = false" class="flex-1 py-3 bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 rounded-xl font-bold hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-colors">Cancel</button>
            <button @click="handleBatchImport" class="flex-1 py-3 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 shadow-lg shadow-indigo-600/20 transition-all">Import Now</button>
          </div>
        </div>
      </div>
    </transition>

  </div>
  </div>
</template>

<style scoped>
.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translate(-50%, 2rem) scale(0.9);
  opacity: 0;
}
.bg-zinc-850 {
  background-color: #202024;
}
.border-zinc-750 {
  border-color: #2a2a30;
}
.bg-zinc-750 {
  background-color: #2a2a30;
}
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

