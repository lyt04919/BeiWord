<script setup>
import { ref, onMounted, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useToast } from '../composables/useToast'

const route = useRoute()
const router = useRouter()
const { success, error } = useToast()

const vocabulary = ref(null)
const loading = ref(true)
const isSaving = ref(false)
const isEditing = ref(false)

const allTags = ref([])
const showTagModal = ref(false)
const newTagName = ref('')

// -- Connection Group State --
const showGroupModal = ref(false)
const showJoinGroupModal = ref(false)
const expandedJoinGroupId = ref(null)
const newGroupName = ref('')
const searchGroupWordQuery = ref('')
const searchJoinGroupQuery = ref('')
const selectedWordsForGroup = ref([])
const allGroups = ref([])

const editingTagId = ref(null)
const editingTagName = ref('')
const editingTagColor = ref('')

const showLinkWordModal = ref(false)
const searchLinkWordQuery = ref('')
const allVocabularies = ref([])

const filteredLinkWords = computed(() => {
  const query = searchLinkWordQuery.value.toLowerCase().trim()
  const linkedIds = new Set(vocabulary.value?.linkedWords?.map(v => v.id) || [])
  const available = allVocabularies.value.filter(v => v.id !== vocabulary.value?.id && !linkedIds.has(v.id))
  if (!query) return available
  return available.filter(v => 
    v.word.toLowerCase().includes(query) || 
    (v.translation && v.translation.toLowerCase().includes(query))
  )
})

const filteredGroupWords = computed(() => {
  if (!searchGroupWordQuery.value.trim()) return allVocabularies.value.filter(v => v.id !== vocabulary.value?.id)
  const query = searchGroupWordQuery.value.toLowerCase().trim()
  return allVocabularies.value.filter(v => v.id !== vocabulary.value?.id && (
    v.word.toLowerCase().includes(query) || 
    (v.translation && v.translation.toLowerCase().includes(query))
  ))
})

const filteredJoinGroups = computed(() => {
  if (!searchJoinGroupQuery.value.trim()) return allGroups.value
  const query = searchJoinGroupQuery.value.toLowerCase().trim()
  return allGroups.value.filter(g => {
    const nameMatch = (g.name || '').toLowerCase().includes(query)
    const wordMatch = g.vocabularies && g.vocabularies.some(v => v.word.toLowerCase().includes(query))
    return nameMatch || wordMatch
  })
})

const parseTranslation = (raw, separator = '\n') => {
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
      return inner && inner.translations ? inner.translations.join(separator) : inner
    })
    return cleanTranslations.join(separator)
  }
  return typeof current === 'string' ? current : JSON.stringify(current)
}

const fetchWordDetails = async () => {
  try {
    const res = await fetch(`http://localhost:8080/api/vocabularies`)
    if (res.ok) {
      const allWords = await res.json()
      allVocabularies.value = allWords
      vocabulary.value = allWords.find(v => v.id == route.params.id)
      
      if (vocabulary.value && vocabulary.value.translation) {
        vocabulary.value.translation = parseTranslation(vocabulary.value.translation, '\n')
      }
    }
  } catch (e) {
    console.error('Failed to fetch word details', e)
  }
}

const parsedExamples = computed(() => {
  if (!vocabulary.value || !vocabulary.value.examples) return []
  try {
    let ex = vocabulary.value.examples
    while (typeof ex === 'string') {
      ex = JSON.parse(ex)
    }
    return Array.isArray(ex) ? ex : []
  } catch (e) {
    return []
  }
})

const enrichedConnectionGroups = computed(() => {
  if (!vocabulary.value || !vocabulary.value.connectionGroups) return []
  
  return vocabulary.value.connectionGroups.map(group => {
    // Find all vocabularies that have this group in their connectionGroups
    const wordsInGroup = allVocabularies.value.filter(v => 
      v.connectionGroups && v.connectionGroups.some(g => g.id === group.id)
    )
    return {
      ...group,
      vocabularies: wordsInGroup
    }
  })
})

const fetchTags = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/tags')
    if (res.ok) allTags.value = await res.json()
  } catch (e) {
    console.error('Failed to fetch tags', e)
  }
}

const addTag = async (tag) => {
  if (!vocabulary.value.tags) vocabulary.value.tags = []
  if (!vocabulary.value.tags.find(t => t.id === tag.id)) {
    vocabulary.value.tags.push(tag)
    await saveChanges()
  }
  showTagModal.value = false
}

const createNewTag = async () => {
  if (!newTagName.value.trim()) return
  try {
    const hex = Math.floor(Math.random()*16777215).toString(16)
    const res = await fetch(window.API_BASE_URL + '/api/tags', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newTagName.value.trim(), color: '#' + hex.padStart(6, '0') })
    })
    if (res.ok) {
      const newTag = await res.json()
      allTags.value.push(newTag)
      addTag(newTag)
      newTagName.value = ''
      success('Tag created successfully')
    } else {
      const err = await res.json()
      error(err.message || 'Failed to create tag')
    }
  } catch (e) {
    console.error('Failed to create tag', e)
    error('Network error')
  }
}

const startEditTag = (tag) => {
  editingTagId.value = tag.id
  editingTagName.value = tag.name
  editingTagColor.value = tag.color || '#000000'
}

const saveTagEdit = async () => {
  if (!editingTagName.value.trim() || !editingTagId.value) return
  try {
    const res = await fetch(`http://localhost:8080/api/tags/${editingTagId.value}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: editingTagName.value.trim(), color: editingTagColor.value })
    })
    if (res.ok) {
      const updatedTag = await res.json()
      const index = allTags.value.findIndex(t => t.id === updatedTag.id)
      if (index !== -1) allTags.value[index] = updatedTag
      
      if (vocabulary.value.tags) {
        const vIndex = vocabulary.value.tags.findIndex(t => t.id === updatedTag.id)
        if (vIndex !== -1) vocabulary.value.tags[vIndex] = updatedTag
      }
      
      
      editingTagId.value = null
      success('Tag updated successfully')
    } else {
      const err = await res.json()
      error(err.message || 'Failed to update tag')
    }
  } catch (e) {
    console.error('Failed to update tag', e)
    error('Network error')
  }
}

const removeTag = async (tagId) => {
  vocabulary.value.tags = vocabulary.value.tags.filter(t => t.id !== tagId)
  await saveChanges()
}

const openGroupModal = async () => {
  if (allVocabularies.value.length === 0) {
    try {
      const res = await fetch(window.API_BASE_URL + '/api/vocabularies')
      if (res.ok) allVocabularies.value = await res.json()
    } catch (e) {
      console.error('Failed to fetch vocabularies', e)
    }
  }
  selectedWordsForGroup.value = [vocabulary.value.id]
  showGroupModal.value = true
}

const openJoinGroupModal = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/connection-groups')
    if (res.ok) {
      const groups = await res.json()
      const currentGroupIds = (vocabulary.value.connectionGroups || []).map(g => g.id)
      allGroups.value = groups.filter(g => !currentGroupIds.includes(g.id))
      showJoinGroupModal.value = true
    }
  } catch (e) {
    console.error('Failed to fetch groups', e)
  }
}

const toggleWordForGroup = (id) => {
  if (id === vocabulary.value.id) return
  const idx = selectedWordsForGroup.value.indexOf(id)
  if (idx === -1) {
    selectedWordsForGroup.value.push(id)
  } else {
    selectedWordsForGroup.value.splice(idx, 1)
  }
}

const createGroupWithSelectedWords = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/connection-groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: newGroupName.value.trim() || null,
        vocabularyIds: selectedWordsForGroup.value
      })
    })
    if (res.ok) {
      showGroupModal.value = false
      newGroupName.value = ''
      selectedWordsForGroup.value = []
      await fetchWordDetails()
      success('Group created successfully')
    } else {
      const err = await res.json()
      error(err.message || 'Failed to create group')
    }
  } catch (e) {
    console.error('Failed to create group', e)
    error('Network error')
  }
}

const joinExistingGroup = async (groupId) => {
  try {
    const res = await fetch(`http://localhost:8080/api/connection-groups/${groupId}/vocabularies/${vocabulary.value.id}`, {
      method: 'POST'
    })
    if (res.ok) {
      showJoinGroupModal.value = false
      await fetchWordDetails()
      success('Joined group successfully')
    }
  } catch (e) {
    console.error('Failed to join group', e)
    error('Failed to join group')
  }
}

const removeFromGroup = async (groupId) => {
  try {
    const res = await fetch(`http://localhost:8080/api/connection-groups/${groupId}/vocabularies/${vocabulary.value.id}`, {
      method: 'DELETE'
    })
    if (res.ok) {
      await fetchWordDetails()
      success('Removed from group')
    }
  } catch (e) {
    console.error('Failed to remove from group', e)
    error('Failed to remove from group')
  }
}

const fetchAllVocabularies = async () => {
  if (allVocabularies.value.length > 0) return
  try {
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies')
    if (res.ok) allVocabularies.value = await res.json()
  } catch (e) { console.error('Failed to fetch vocabularies', e) }
}

const openLinkWordModal = () => {
  showLinkWordModal.value = true
  fetchAllVocabularies()
}

const linkWord = async (linkedId) => {
  try {
    const res = await fetch(`http://localhost:8080/api/vocabularies/${vocabulary.value.id}/links/${linkedId}`, { method: 'POST' })
    if (res.ok) {
      await fetchWordDetails()
      searchLinkWordQuery.value = ''
    }
  } catch (e) { console.error('Failed to link word', e) }
}

const unlinkWord = async (linkedId) => {
  if (!confirm('Remove this link?')) return
  try {
    const res = await fetch(`http://localhost:8080/api/vocabularies/${vocabulary.value.id}/links/${linkedId}`, { method: 'DELETE' })
    if (res.ok) {
      await fetchWordDetails()
    }
  } catch (e) { console.error('Failed to unlink word', e) }
}

const saveChanges = async () => {
  if (!vocabulary.value) return
  isSaving.value = true
  
  try {
    const payload = { ...vocabulary.value }
    payload.translation = JSON.stringify({ translations: payload.translation.split('\n').filter(t => t.trim() !== '') })
    
    const res = await fetch(`http://localhost:8080/api/vocabularies/${payload.id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    
    if (res.ok) {
      isEditing.value = false
      await fetchWordDetails() // Refresh details to get formatted translations
      success('Changes saved successfully')
    } else {
      const err = await res.json()
      error(err.message || 'Failed to save changes')
    }
  } catch (e) {
    console.error('Failed to save changes', e)
    error('Network error')
  } finally {
    isSaving.value = false
  }
}

const speakWord = (word, type = 1) => {
  const url = `https://dict.youdao.com/dictvoice?audio=${encodeURIComponent(word)}&type=${type}`
  new Audio(url).play().catch(e => console.error("Audio play failed:", e))
}

const setStageManually = (stageNum) => {
  if (!isEditing.value) return
  if (vocabulary.value.currentStage === stageNum) {
    // If click the same stage, toggle it back to 0 (Unlearned)
    vocabulary.value.currentStage = 0
  } else {
    vocabulary.value.currentStage = stageNum
  }
  // Sync isMastered based on stage
  vocabulary.value.isMastered = (vocabulary.value.currentStage >= 5)
}

onMounted(async () => {
  await fetchWordDetails()
  await fetchTags()
  loading.value = false
})

watch(() => route.params.id, async (newId) => {
  if (newId) {
    loading.value = true
    await fetchWordDetails()
    await fetchTags()
    loading.value = false
  }
})
</script>

<template>
  <div>
    <div class="w-full max-w-4xl mx-auto flex flex-col gap-6 px-4 pb-20 select-none">
    
    <!-- Navigation Back Header -->
    <div class="flex items-center gap-4 mb-2">
      <button @click="router.back()" class="p-2.5 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-2xl transition-all text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200 border border-zinc-200/50 dark:border-zinc-800/80 bg-white/40 dark:bg-zinc-900/40 backdrop-blur-md shadow-sm" title="Go Back">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M10 19l-7-7m0 0l7-7m-7 7h18" />
        </svg>
      </button>
      <h2 class="text-2xl font-black tracking-tight text-zinc-900 dark:text-white">Word Details</h2>
    </div>

    <!-- Spinner Loading -->
    <div v-if="loading" class="flex justify-center p-20">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
    </div>

    <!-- Main Content Panel -->
    <div v-else-if="vocabulary" class="bg-white/60 dark:bg-zinc-900/60 backdrop-blur-md p-8 rounded-3xl border border-white/50 dark:border-zinc-800/80 shadow-[0_4px_20px_-4px_rgba(0,0,0,0.02)] relative overflow-hidden">
      <!-- Decorative background glow -->
      <div class="absolute -top-32 -right-32 w-64 h-64 bg-indigo-500/5 rounded-full blur-3xl pointer-events-none"></div>

      <div class="flex flex-col gap-8 relative z-10">
        
        <!-- Word and Phonetics Header Area -->
        <div class="flex justify-between items-start flex-wrap gap-4 border-b border-zinc-100/50 dark:border-zinc-800/30 pb-6">
          <div class="flex flex-col gap-2">
            <h1 class="text-5xl font-black text-zinc-900 dark:text-white tracking-tight">{{ vocabulary.word }}</h1>
            <div class="flex gap-2 mt-1.5 flex-wrap">
              <button 
                v-if="vocabulary.phoneticUk" 
                @click="speakWord(vocabulary.word, 1)"
                class="flex items-center gap-1.5 px-3 py-1 bg-indigo-50/50 hover:bg-indigo-100/50 dark:bg-indigo-950/20 dark:hover:bg-indigo-950/40 border border-indigo-100/50 dark:border-indigo-900/30 rounded-full text-xs font-semibold text-indigo-600 dark:text-indigo-400 transition-all select-none"
              >
                <span>UK /{{ vocabulary.phoneticUk }}/</span>
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                </svg>
              </button>
              <button 
                v-if="vocabulary.phoneticUs" 
                @click="speakWord(vocabulary.word, 2)"
                class="flex items-center gap-1.5 px-3 py-1 bg-indigo-50/50 hover:bg-indigo-100/50 dark:bg-indigo-950/20 dark:hover:bg-indigo-950/40 border border-indigo-100/50 dark:border-indigo-900/30 rounded-full text-xs font-semibold text-indigo-600 dark:text-indigo-400 transition-all select-none"
              >
                <span>US /{{ vocabulary.phoneticUs }}/</span>
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15.536 8.464a5 5 0 010 7.072m2.828-9.9a9 9 0 010 12.728M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" />
                </svg>
              </button>
            </div>
          </div>
          
          <!-- Mode badge or segmented toggle controls -->
          <div class="flex items-center gap-3">
            <div v-if="isEditing" class="flex bg-zinc-100 dark:bg-zinc-800 p-0.5 rounded-xl text-[10px] font-black text-zinc-400 border border-zinc-200/50 dark:border-zinc-850">
              <button 
                @click="vocabulary.vocabType = 'RECOGNITION'"
                class="px-3 py-1.5 rounded-lg transition-all duration-200"
                :class="vocabulary.vocabType === 'RECOGNITION' ? 'bg-white dark:bg-zinc-700 text-zinc-900 dark:text-white shadow-sm' : ''"
              >
                RECOGNITION
              </button>
              <button 
                @click="vocabulary.vocabType = 'SPELLING'"
                class="px-3 py-1.5 rounded-lg transition-all duration-200"
                :class="vocabulary.vocabType === 'SPELLING' ? 'bg-white dark:bg-zinc-700 text-zinc-900 dark:text-white shadow-sm' : ''"
              >
                SPELLING
              </button>
            </div>
            
            <span v-else class="text-[10px] font-black text-zinc-400 tracking-wider">
              {{ vocabulary.vocabType }}
            </span>
          </div>
        </div>

        <!-- Details Grid Content -->
        <div class="grid grid-cols-1 lg:grid-cols-5 gap-8">
          
          <!-- Column 1: Definitions and Examples (Spans 3 cols) -->
          <div class="flex flex-col gap-6 lg:col-span-3">
            
            <!-- Translation Section -->
            <div class="flex flex-col gap-2">
              <label class="text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">Translation</label>
              
              <textarea v-if="isEditing"
                v-model="vocabulary.translation" 
                rows="4" 
                class="w-full bg-white dark:bg-zinc-950/20 border border-zinc-200 dark:border-zinc-850 rounded-2xl p-4 outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-colors text-sm font-semibold text-zinc-850 dark:text-white resize-none leading-relaxed"
                placeholder="Enter translations, one per line..."
              ></textarea>
              
              <div v-else class="w-full text-base font-medium text-zinc-800 dark:text-zinc-200 leading-relaxed whitespace-pre-wrap py-2">
                {{ vocabulary.translation || 'No translation provided.' }}
              </div>
            </div>
            
            <!-- Example Sentences Section -->
            <div class="flex flex-col gap-2">
              <label class="text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest flex items-center gap-1.5">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
                </svg>
                Example Sentences
              </label>
              
              <div class="flex flex-col gap-4 w-full transition-all" :class="isEditing ? 'bg-zinc-50/50 dark:bg-zinc-950/25 border border-zinc-100 dark:border-zinc-850/50 rounded-2xl p-6 min-h-[7rem]' : 'py-2'">
                <div v-if="parsedExamples.length > 0" class="flex flex-col gap-5">
                  <div 
                    v-for="(sentence, index) in parsedExamples" 
                    :key="index" 
                    :class="[isEditing ? 'border-b border-zinc-100 dark:border-zinc-850/50 pb-4 last:border-0 last:pb-0' : 'mb-3']"
                  >
                    <!-- v-html compiles <b> highlights in standard dictionary sentences -->
                    <div class="text-zinc-800 dark:text-zinc-200 font-bold leading-normal text-sm" v-html="sentence.en"></div>
                    <div class="text-zinc-400 dark:text-zinc-500 mt-2 font-semibold">{{ sentence.zh }}</div>
                  </div>
                </div>
                <div v-else class="text-zinc-400 italic text-xs font-semibold py-4 text-center">
                  No example sentences found for this word.
                </div>
              </div>
            </div>
          </div>

          <!-- Column 2: Metadata, Tags, Connections & Mastery (Spans 2 cols) -->
          <div class="flex flex-col gap-6 lg:col-span-2">
            
            <!-- Mastery Stage Indicator Section -->
            <div class="flex flex-col gap-2 transition-all" :class="isEditing ? 'bg-zinc-50/50 dark:bg-zinc-950/25 border border-zinc-100 dark:border-zinc-850/50 rounded-2xl p-4' : 'py-2'">
              <div class="flex justify-between items-center">
                <label class="text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">Ebbinghaus Mastery Stage</label>
                <!-- Mastered Pill Indicator -->
                <span 
                  v-if="vocabulary.isMastered" 
                  class="px-2 py-0.5 bg-green-500/10 text-green-600 border border-green-500/20 rounded-full text-[9px] font-black tracking-wider"
                >
                  MASTERED
                </span>
              </div>
              
              <div class="flex flex-col gap-2.5 mt-2">
                <div class="flex items-center gap-2">
                  <!-- Mastery Dots, interactive in Edit Mode -->
                  <span 
                    v-for="i in 5" 
                    :key="i"
                    @click="setStageManually(i)"
                    class="w-3.5 h-3.5 rounded-full transition-all duration-300"
                    :class="[
                      i <= (vocabulary.isMastered ? 5 : vocabulary.currentStage) 
                        ? 'bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.6)]' 
                        : 'bg-zinc-200 dark:bg-zinc-800',
                      isEditing ? 'cursor-pointer hover:scale-110' : ''
                    ]"
                    :title="isEditing ? `Set stage to ${i}` : ''"
                  ></span>
                  
                  <span class="text-xs font-black ml-2 transition-colors" :class="isEditing ? 'text-zinc-400 dark:text-zinc-500' : 'text-zinc-700 dark:text-zinc-300'">
                    Stage {{ vocabulary.isMastered ? 5 : vocabulary.currentStage }} / 5
                  </span>
                </div>
                
                <p v-if="isEditing" class="text-[10px] text-zinc-400 leading-normal font-bold">
                  💡 Edit Mode Active: Click any dot to set the mastery stage manually. Set to active stage again to reset to 0 (Unlearned).
                </p>
                <p v-if="!isEditing" class="text-[10px] text-zinc-400 leading-normal font-bold">
                  Progress increases automatically as you review this word successfully.
                </p>
              </div>
            </div>

            <!-- Custom Phrases & Notes -->
            <div class="flex flex-col gap-2">
              <label class="text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest">Custom Phrases & Notes</label>
              
              <textarea v-if="isEditing"
                v-model="vocabulary.phrases" 
                rows="4" 
                class="w-full bg-white dark:bg-zinc-950/20 border border-zinc-200 dark:border-zinc-850 rounded-2xl p-4 outline-none focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500 transition-colors text-xs font-semibold text-zinc-850 dark:text-white resize-none leading-relaxed"
                placeholder="e.g. justify oneself - 自圆其说"
              ></textarea>
              
              <div v-else class="w-full text-sm font-medium text-zinc-800 dark:text-zinc-200 leading-relaxed whitespace-pre-wrap py-2">
                {{ vocabulary.phrases || 'No custom phrases added.' }}
              </div>
            </div>

            <!-- Tags Section -->
            <div class="flex flex-col gap-2">
              <label class="text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest flex items-center justify-between">
                <span>Tags</span>
                <button v-if="isEditing" @click="showTagModal = true" class="text-indigo-600 hover:text-indigo-700 bg-indigo-50/50 dark:bg-indigo-950/20 px-2 py-0.5 rounded-lg border border-indigo-100/50 dark:border-indigo-900/30 text-[10px] font-black transition-all">
                  Manage Tags
                </button>
              </label>
              
              <div class="flex flex-wrap gap-2 transition-all" :class="isEditing ? 'p-4 bg-zinc-50/50 dark:bg-zinc-950/25 border border-zinc-100 dark:border-zinc-850/50 rounded-2xl min-h-[3rem]' : 'py-2'">
                <span 
                  v-for="tag in vocabulary.tags" 
                  :key="tag.id" 
                  class="px-2.5 py-0.5 rounded-full text-[10px] font-bold text-white shadow-sm flex items-center gap-1 transition-colors"
                  :class="{ 'group': isEditing }"
                  :style="{ backgroundColor: tag.color }"
                >
                  #{{ tag.name }}
                  <button v-if="isEditing" @click="removeTag(tag.id)" class="opacity-0 group-hover:opacity-100 transition-opacity ml-1 hover:text-black/50">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
                    </svg>
                  </button>
                </span>
                <span v-if="!vocabulary.tags || vocabulary.tags.length === 0" class="text-xs text-zinc-400 italic font-semibold">No tags added yet.</span>
              </div>
            </div>

            <!-- Connections (Groups) -->
            <div class="flex flex-col gap-2 transition-all" :class="isEditing ? 'p-5 bg-white/50 dark:bg-black/20 border border-zinc-100 dark:border-zinc-800/50 rounded-3xl' : 'py-2'">
              <div class="flex items-center justify-between mb-1">
                <span class="text-xs font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest flex items-center gap-1.5"><span class="text-indigo-400">🔗</span> Connection Groups</span>
                <div v-if="isEditing" class="flex gap-2">
                  <button @click="openJoinGroupModal" class="px-3 py-1.5 rounded-xl bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-400 font-bold text-[10px] uppercase hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-colors flex items-center gap-1">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 5a1 1 0 011 1v3h3a1 1 0 110 2h-3v3a1 1 0 11-2 0v-3H6a1 1 0 110-2h3V6a1 1 0 011-1z" clip-rule="evenodd" /></svg>
                    Join
                  </button>
                  <button @click="openGroupModal" class="px-3 py-1.5 rounded-xl bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 font-bold text-[10px] uppercase hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors flex items-center gap-1">
                    <svg xmlns="http://www.w3.org/2000/svg" class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd" /></svg>
                    New Group
                  </button>
                </div>
              </div>
              
              <div v-if="enrichedConnectionGroups && enrichedConnectionGroups.length > 0" class="flex flex-col gap-3">
                <div v-for="group in enrichedConnectionGroups" :key="group.id" class="flex flex-col gap-2 relative transition-all" :class="isEditing ? 'p-3 bg-white dark:bg-zinc-800 border border-zinc-100 dark:border-zinc-700/50 rounded-2xl' : 'py-1'">
                  <div class="flex items-center justify-between pb-1" :class="isEditing ? 'border-b border-zinc-100/50 dark:border-zinc-700/50' : ''">
                    <span class="text-[10px] font-black uppercase" :class="group.name ? 'text-zinc-500' : 'text-zinc-400 italic'">
                      {{ group.name || 'Unnamed Group' }}
                    </span>
                    <button v-if="isEditing" @click="removeFromGroup(group.id)" class="text-zinc-400 hover:text-rose-500 transition-colors" title="Remove current word from this group">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M4.293 4.293a1 1 0 011.414 0L10 8.586l4.293-4.293a1 1 0 111.414 1.414L11.414 10l4.293 4.293a1 1 0 01-1.414 1.414L10 11.414l-4.293 4.293a1 1 0 01-1.414-1.414L8.586 10 4.293 5.707a1 1 0 010-1.414z" clip-rule="evenodd" /></svg>
                    </button>
                  </div>
                  <div class="flex flex-wrap gap-1.5">
                    <div v-for="v in group.vocabularies" :key="v.id">
                      <span v-if="v.id === vocabulary.id" class="px-2.5 py-1 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 text-[10px] font-bold rounded-lg border border-indigo-100 dark:border-indigo-800/50">
                        {{ v.word }} (Current)
                      </span>
                      <router-link v-else :to="`/dictionary/${v.id}`" class="px-2.5 py-1 bg-zinc-100 dark:bg-zinc-800/80 text-zinc-600 dark:text-zinc-300 text-[10px] font-bold rounded-lg hover:bg-indigo-100 dark:hover:bg-indigo-900/50 transition-colors border border-zinc-200 dark:border-zinc-700/50">
                        {{ v.word }}
                      </router-link>
                    </div>
                  </div>
                </div>
              </div>
              <span v-else class="text-xs text-zinc-400 font-semibold italic py-1">No groups created yet.</span>
            </div>
            
          </div>
        </div>

        <!-- Removed Footer Control Buttons inline block -->

      </div>
    </div>
    
    <div v-else class="flex flex-col items-center justify-center p-12 text-zinc-500 glass-panel bg-white/40 dark:bg-zinc-900/40 rounded-3xl border border-zinc-200">
      <h3 class="text-xl font-bold mb-2">Word not found</h3>
      <p class="text-sm text-zinc-400">The vocabulary item you're looking for doesn't exist.</p>
    </div>

    <!-- Tag Management Modal Sheet -->
    <div v-if="showTagModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-md transition-opacity">
      <div class="w-full max-w-sm bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl rounded-3xl p-6 relative">
        <h3 class="text-lg font-black text-zinc-800 dark:text-white mb-4">Manage Tag Selections</h3>
        
        <div class="flex flex-col gap-4">
          <!-- All Tags List -->
          <div class="flex flex-col gap-2 max-h-60 overflow-y-auto overscroll-contain pr-1 custom-scrollbar">
            <div 
              v-for="tag in allTags" 
              :key="tag.id" 
              class="flex items-center gap-2 bg-white dark:bg-black/20 p-2.5 rounded-2xl border border-zinc-100 dark:border-zinc-800/80 shadow-sm"
            >
              <div v-if="editingTagId === tag.id" class="flex-1 flex items-center gap-2">
                <input type="color" v-model="editingTagColor" class="w-5 h-5 rounded cursor-pointer border-0 p-0" />
                <input type="text" v-model="editingTagName" class="flex-1 bg-transparent border-b border-indigo-500 outline-none text-xs px-1 py-0.5" @keyup.enter="saveTagEdit" />
                <button @click="saveTagEdit" class="text-[10px] font-black text-green-500 hover:text-green-600 px-1 uppercase">Save</button>
                <button @click="editingTagId = null" class="text-[10px] font-black text-zinc-400 hover:text-zinc-600 px-1 uppercase">Cancel</button>
              </div>
              
              <div v-else class="flex-1 flex items-center justify-between">
                <div class="flex items-center gap-2">
                  <span class="w-2.5 h-2.5 rounded-full border border-black/10" :style="{ backgroundColor: tag.color }"></span>
                  <span class="text-xs font-bold text-zinc-700 dark:text-zinc-300">#{{ tag.name }}</span>
                </div>
                <div class="flex items-center gap-1">
                  <button 
                    v-if="!vocabulary.tags?.find(vt => vt.id === tag.id)" 
                    @click="addTag(tag)" 
                    class="text-[10px] px-2 py-1 bg-indigo-50 hover:bg-indigo-100 text-indigo-600 dark:bg-indigo-950/20 dark:hover:bg-indigo-950/40 rounded-lg font-black uppercase transition-colors"
                  >
                    Add
                  </button>
                  <button 
                    v-else 
                    @click="removeTag(tag.id)" 
                    class="text-[10px] px-2 py-1 bg-rose-500/10 text-rose-500 hover:bg-rose-500/20 rounded-lg font-black uppercase transition-colors"
                  >
                    Remove
                  </button>
                  <button 
                    @click="startEditTag(tag)" 
                    class="text-[10px] px-2 py-1 bg-zinc-100 dark:bg-zinc-800 text-zinc-500 dark:text-zinc-400 hover:bg-zinc-200 rounded-lg font-black uppercase transition-colors"
                  >
                    Edit
                  </button>
                </div>
              </div>
            </div>
            
            <div v-if="allTags.length === 0" class="text-xs font-semibold text-zinc-400 italic text-center py-4">No tags created yet.</div>
          </div>
          
          <!-- Create New -->
          <div class="flex gap-2 mt-2 pt-3 border-t border-zinc-200/50 dark:border-zinc-850">
            <input v-model="newTagName" @keyup.enter="createNewTag" type="text" placeholder="Create new tag..." class="flex-1 bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl px-3 py-2 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
            <button @click="createNewTag" class="px-4 py-2 bg-indigo-600 text-white rounded-xl shadow-md hover:bg-indigo-700 font-bold transition-all text-xs active:scale-95">Create</button>
          </div>
        </div>

        <div class="flex justify-end mt-6">
          <button @click="showTagModal = false" class="px-5 py-2 bg-zinc-200 dark:bg-zinc-800 text-xs font-bold rounded-xl hover:bg-zinc-300 dark:hover:bg-zinc-700 transition-colors">Done</button>
        </div>
      </div>
    </div>

    <!-- Group Modal Sheet -->
    <div v-if="showGroupModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-md transition-opacity">
      <div class="w-full max-w-lg bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl rounded-3xl p-6 relative flex flex-col max-h-[85vh]">
        <h3 class="text-lg font-black text-zinc-800 dark:text-white mb-4 shrink-0">Create Connection Group</h3>
        
        <div class="flex flex-col gap-4 flex-1 min-h-0">
          <!-- Group Name -->
          <div class="flex flex-col gap-1.5 shrink-0">
            <label class="text-[10px] font-black text-zinc-500 uppercase tracking-wider">Group Name (Optional)</label>
            <input v-model="newGroupName" type="text" placeholder="e.g. Synonyms of Happy" class="w-full bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl px-4 py-2.5 outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
          </div>

          <!-- Select Words -->
          <div class="flex flex-col gap-1.5 flex-1 min-h-0">
            <label class="text-[10px] font-black text-zinc-500 uppercase tracking-wider shrink-0">Select Words to Add</label>
            <div class="relative w-full shrink-0">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-zinc-400">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" /></svg>
              </div>
              <input v-model="searchGroupWordQuery" type="text" placeholder="Search dictionary..." class="w-full pl-9 pr-3 py-2 bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
            </div>

            <div class="flex flex-col gap-2 overflow-y-auto overscroll-contain custom-scrollbar mt-2 border border-zinc-100 dark:border-zinc-800/50 rounded-xl p-2 bg-white/50 dark:bg-black/10 flex-1">
              <div 
                v-for="w in filteredGroupWords.slice(0, 100)" :key="w.id"
                @click="toggleWordForGroup(w.id)"
                class="flex items-center justify-between p-2.5 rounded-xl cursor-pointer transition-colors border"
                :class="selectedWordsForGroup.includes(w.id) ? 'bg-indigo-50 dark:bg-indigo-900/30 border-indigo-200 dark:border-indigo-700/50 text-indigo-700 dark:text-indigo-300' : 'bg-white dark:bg-zinc-800 border-transparent hover:border-zinc-200 dark:hover:border-zinc-700'"
              >
                <div class="flex flex-col">
                  <span class="font-bold text-sm">{{ w.word }}</span>
                  <span class="text-[10px] opacity-70">{{ parseTranslation(w.translation, ', ') }}</span>
                </div>
                <div 
                  class="w-5 h-5 rounded-md border flex items-center justify-center transition-all"
                  :class="selectedWordsForGroup.includes(w.id) ? 'bg-indigo-500 border-indigo-500 text-white' : 'border-zinc-300 dark:border-zinc-600'"
                >
                  <svg v-if="selectedWordsForGroup.includes(w.id)" xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" /></svg>
                </div>
              </div>
            </div>
            
            <div class="flex items-center gap-2 mt-2 shrink-0">
              <span class="text-[10px] font-black uppercase text-indigo-500">{{ selectedWordsForGroup.length }} words selected</span>
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-3 mt-6 pt-4 border-t border-zinc-200/50 dark:border-zinc-850 shrink-0">
          <button @click="showGroupModal = false" class="px-5 py-2.5 bg-zinc-200 dark:bg-zinc-800 text-xs font-bold rounded-xl hover:bg-zinc-300 dark:hover:bg-zinc-700 transition-colors">Cancel</button>
          <button @click="createGroupWithSelectedWords" class="px-6 py-2.5 bg-indigo-600 text-white rounded-xl shadow-md shadow-indigo-500/20 hover:bg-indigo-700 font-bold transition-all text-xs active:scale-95">Create Group</button>
        </div>
      </div>
    </div>
    
    <!-- Join Existing Group Modal -->
    <div v-if="showJoinGroupModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-md transition-opacity">
      <div class="w-full max-w-md bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl rounded-3xl p-6 relative flex flex-col max-h-[80vh]">
        <h3 class="text-lg font-black text-zinc-800 dark:text-white mb-4 shrink-0">Join Existing Group</h3>
        
        <div class="relative w-full mb-4 shrink-0">
          <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-zinc-400">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" /></svg>
          </div>
          <input v-model="searchJoinGroupQuery" type="text" placeholder="Search groups..." class="w-full pl-9 pr-3 py-2 bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
        </div>
        
        <div class="flex flex-col gap-2 overflow-y-auto overscroll-contain custom-scrollbar pr-1 flex-1 min-h-0">
          <div v-if="filteredJoinGroups.length > 0">
            <div 
              v-for="group in filteredJoinGroups" :key="group.id"
              class="flex flex-col bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-2xl mb-2 shadow-sm overflow-hidden transition-all"
            >
              <div 
                class="flex items-center justify-between p-3 cursor-pointer hover:bg-zinc-50 dark:hover:bg-zinc-800/50 transition-colors"
                @click="expandedJoinGroupId = expandedJoinGroupId === group.id ? null : group.id"
              >
                <div class="flex flex-col">
                  <div class="flex items-center gap-2">
                    <span class="font-bold text-sm text-zinc-800 dark:text-zinc-200 truncate max-w-[220px]" :class="!group.name ? 'text-zinc-600 dark:text-zinc-400' : ''">{{ group.name || (group.vocabularies?.length ? group.vocabularies.map(v => v.word).join(', ') : 'Empty Group') }}</span>
                    <svg 
                      class="w-4 h-4 text-zinc-400 transition-transform duration-200"
                      :class="expandedJoinGroupId === group.id ? 'rotate-180 text-indigo-500' : ''"
                      fill="none" viewBox="0 0 24 24" stroke="currentColor"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                    </svg>
                  </div>
                  <span class="text-[10px] text-zinc-400 font-semibold mt-0.5">{{ group.vocabularies?.length || 0 }} words</span>
                </div>
                <button 
                  @click.stop="joinExistingGroup(group.id)" 
                  class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-600 dark:bg-indigo-950/20 dark:hover:bg-indigo-950/40 font-black text-[10px] uppercase rounded-lg transition-colors"
                >
                  Join
                </button>
              </div>
              
              <!-- Expandable content -->
              <div 
                v-show="expandedJoinGroupId === group.id"
                class="px-3 pb-3 border-t border-zinc-100 dark:border-zinc-800 pt-3 bg-zinc-50 dark:bg-zinc-900/50"
              >
                <div class="flex flex-wrap gap-1.5" v-if="group.vocabularies && group.vocabularies.length > 0">
                  <span 
                    v-for="v in group.vocabularies" :key="v.id"
                    class="px-2 py-1 bg-white dark:bg-zinc-800 border border-zinc-200 dark:border-zinc-700 rounded-md text-[10px] font-bold text-zinc-600 dark:text-zinc-300"
                  >
                    {{ v.word }}
                  </span>
                </div>
                <div v-else class="text-[10px] text-zinc-400 font-medium italic">This group is currently empty.</div>
              </div>
            </div>
          </div>
          <div v-else class="text-xs text-zinc-400 font-semibold italic text-center py-8">No matching groups found.</div>
        </div>

        <div class="flex justify-end mt-4 shrink-0 pt-4 border-t border-zinc-200 dark:border-zinc-800">
          <button @click="showJoinGroupModal = false" class="px-5 py-2 bg-zinc-200 dark:bg-zinc-800 text-xs font-bold rounded-xl hover:bg-zinc-300 dark:hover:bg-zinc-700 transition-colors">Close</button>
        </div>
      </div>
    </div>
    
    <!-- Link Word Modal -->
    <div v-if="showLinkWordModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-md transition-opacity">
      <div class="w-full max-w-md bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl rounded-3xl p-6 relative flex flex-col max-h-[80vh]">
        <h3 class="text-lg font-black text-zinc-800 dark:text-white mb-4">Link Related Word</h3>
        
        <div class="relative w-full mb-4 shrink-0">
          <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-zinc-400">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" /></svg>
          </div>
          <input v-model="searchLinkWordQuery" type="text" placeholder="Search dictionary for a word..." class="w-full pl-9 pr-3 py-2 bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
        </div>
        
        <div class="flex-1 overflow-y-auto overscroll-contain pr-2 custom-scrollbar relative z-10">
          <div v-if="filteredLinkWords.length > 0">
            <div 
              v-for="w in filteredLinkWords.slice(0, 50)" :key="w.id"
              class="flex items-center justify-between p-3 bg-white dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-2xl mb-2 shadow-sm"
            >
              <span class="font-bold text-sm text-zinc-800 dark:text-zinc-200">{{ w.word }}</span>
              <button @click="linkWord(w.id)" class="px-3 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-600 dark:bg-indigo-950/20 dark:hover:bg-indigo-950/40 font-black text-[10px] uppercase rounded-lg transition-colors">Link</button>
            </div>
            <div v-if="filteredLinkWords.length > 50" class="text-center text-[10px] text-zinc-400 pt-2 pb-4">Showing first 50 results...</div>
          </div>
          <div v-else class="text-xs text-zinc-400 font-semibold italic text-center py-8">No unlinked words match your search.</div>
        </div>

        <div class="flex justify-end mt-4 shrink-0 pt-4 border-t border-zinc-200 dark:border-zinc-800">
          <button @click="showLinkWordModal = false" class="px-5 py-2 bg-zinc-200 dark:bg-zinc-800 text-xs font-bold rounded-xl hover:bg-zinc-300 dark:hover:bg-zinc-700 transition-colors">Close</button>
        </div>
      </div>
    </div>
  </div>
  <!-- Floating Action Button for Edit Mode -->
  <div class="fixed bottom-8 right-8 z-40 flex flex-col items-end gap-3">
    <button v-if="!isEditing"
      @click="isEditing = true"
      class="w-14 h-14 bg-indigo-600 text-white rounded-full flex items-center justify-center shadow-[0_8px_30px_rgb(0,0,0,0.12)] hover:bg-indigo-700 hover:shadow-[0_8px_30px_rgb(99,102,241,0.4)] hover:-translate-y-1 transition-all active:scale-95"
      title="Edit Word"
    >
      <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
      </svg>
    </button>

    <div v-if="isEditing" class="flex gap-3">
      <button
        @click="isEditing = false; fetchWordDetails()"
        class="px-5 py-3 bg-white dark:bg-zinc-800 text-zinc-700 dark:text-zinc-300 rounded-full font-bold hover:bg-zinc-100 dark:hover:bg-zinc-700 shadow-[0_8px_30px_rgb(0,0,0,0.12)] transition-all text-sm active:scale-95 border border-zinc-200 dark:border-zinc-700"
      >
        Cancel
      </button>
      <button
        @click="saveChanges" 
        class="px-6 py-3 bg-indigo-600 text-white rounded-full font-bold hover:bg-indigo-700 hover:shadow-[0_8px_30px_rgb(99,102,241,0.4)] shadow-[0_8px_30px_rgb(0,0,0,0.12)] transition-all flex items-center gap-2 disabled:opacity-50 text-sm active:scale-95"
        :disabled="isSaving"
      >
        <svg v-if="isSaving" class="animate-spin -ml-1 h-4 w-4 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
          <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
        <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" />
        </svg>
        {{ isSaving ? 'Saving...' : 'Save' }}
      </button>
    </div>
    </div>
  </div>
</template>
