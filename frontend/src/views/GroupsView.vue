<script setup>
import { ref, onMounted, computed } from 'vue'
import { useToast } from '../composables/useToast'

const { success, error } = useToast()

const groups = ref([])
const loading = ref(true)
const searchQuery = ref('')
const showCreateModal = ref(false)
const newGroupName = ref('')
const newGroupWordIds = ref([])
const totalWordsCount = ref(0)

const showEditModal = ref(false)
const editGroupId = ref(null)
const editGroupName = ref('')

const editModalTab = ref('edit') // 'edit' | 'add'
const wordsPendingAdd = ref([])

const targetGroupId = ref(null)
const allVocabularies = ref([])
const searchWordQuery = ref('')
const selectedWordsToAdd = ref([])

const fetchGroups = async () => {
  loading.value = true
  try {
    const res = await fetch(window.API_BASE_URL + '/api/connection-groups')
    if (res.ok) {
      groups.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch groups', e)
  } finally {
    loading.value = false
  }
}

const fetchTotalWordsCount = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/vocabularies')
    if (res.ok) {
      const data = await res.json()
      totalWordsCount.value = data.length
    }
  } catch (e) {
    console.error(e)
  }
}

onMounted(() => {
  fetchGroups()
  fetchTotalWordsCount()
})

const filteredGroups = computed(() => {
  if (!searchQuery.value.trim()) return groups.value
  const query = searchQuery.value.toLowerCase().trim()
  return groups.value.filter(g => {
    const nameMatch = (g.name || '').toLowerCase().includes(query)
    const wordMatch = g.vocabularies && g.vocabularies.some(v => v.word.toLowerCase().includes(query))
    return nameMatch || wordMatch
  })
})

const openCreateModal = async () => {
  newGroupName.value = ''
  newGroupWordIds.value = []
  searchWordQuery.value = ''
  
  if (allVocabularies.value.length === 0) {
    try {
      const res = await fetch(window.API_BASE_URL + '/api/vocabularies')
      if (res.ok) allVocabularies.value = await res.json()
    } catch (e) {
      console.error('Failed to fetch vocabularies', e)
    }
  }
  showCreateModal.value = true
}

const createGroup = async () => {
  try {
    const res = await fetch(window.API_BASE_URL + '/api/connection-groups', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        name: newGroupName.value.trim() || null,
        vocabularyIds: newGroupWordIds.value
      })
    })
    if (res.ok) {
      showCreateModal.value = false
      newGroupName.value = ''
      newGroupWordIds.value = []
      await fetchGroups()
      success('Group created successfully')
    }
  } catch (e) {
    console.error('Failed to create group', e)
    error('Failed to create group')
  }
}

const availableVocabulariesToCreate = computed(() => {
  if (!searchWordQuery.value.trim()) return allVocabularies.value
  const query = searchWordQuery.value.toLowerCase().trim()
  return allVocabularies.value.filter(v => 
    v.word.toLowerCase().includes(query) || 
    (v.translation && v.translation.toLowerCase().includes(query))
  )
})

const toggleWordToCreateGroup = (id) => {
  const idx = newGroupWordIds.value.indexOf(id)
  if (idx === -1) newGroupWordIds.value.push(id)
  else newGroupWordIds.value.splice(idx, 1)
}

const openEditModal = async (group) => {
  editGroupId.value = group.id
  editGroupName.value = group.name || ''
  
  if (allVocabularies.value.length === 0) {
    try {
      const res = await fetch(window.API_BASE_URL + '/api/vocabularies')
      if (res.ok) allVocabularies.value = await res.json()
    } catch (e) {
      console.error('Failed to fetch vocabularies', e)
    }
  }
  
  selectedWordsToAdd.value = group.vocabularies ? group.vocabularies.map(v => v.id) : []
  searchWordQuery.value = ''
  editModalTab.value = 'edit'
  wordsPendingAdd.value = []
  showEditModal.value = true
}

const updateGroup = async () => {
  try {
    const nameRes = await fetch(`${window.API_BASE_URL}/api/connection-groups/${editGroupId.value}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: editGroupName.value.trim() || null })
    })
    
    const wordsRes = await fetch(`${window.API_BASE_URL}/api/connection-groups/${editGroupId.value}/vocabularies`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(selectedWordsToAdd.value)
    })
    
    if (nameRes.ok && wordsRes.ok) {
      showEditModal.value = false
      await fetchGroups()
      success('Group updated successfully')
    } else {
      error('Failed to update group')
    }
  } catch (e) {
    console.error('Failed to update group', e)
    error('Failed to update group')
  }
}

const deleteGroup = async (id) => {
  if (!confirm('Are you sure you want to delete this group? The words will remain in your dictionary.')) return
  try {
    const res = await fetch(`${window.API_BASE_URL}/api/connection-groups/${id}`, { method: 'DELETE' })
    if (res.ok) {
      await fetchGroups()
      success('Group deleted')
    }
  } catch (e) {
    console.error('Failed to delete group', e)
    error('Failed to delete group')
  }
}

const groupCurrentVocabularies = computed(() => {
  return allVocabularies.value.filter(v => selectedWordsToAdd.value.includes(v.id))
})

const availableVocabulariesToAdd = computed(() => {
  const available = allVocabularies.value.filter(v => !selectedWordsToAdd.value.includes(v.id))
  if (!searchWordQuery.value.trim()) return available
  const query = searchWordQuery.value.toLowerCase().trim()
  return available.filter(v => 
    v.word.toLowerCase().includes(query) || 
    (v.translation && v.translation.toLowerCase().includes(query))
  )
})

const toggleWordPendingAdd = (id) => {
  const idx = wordsPendingAdd.value.indexOf(id)
  if (idx === -1) wordsPendingAdd.value.push(id)
  else wordsPendingAdd.value.splice(idx, 1)
}

const confirmAddWords = () => {
  selectedWordsToAdd.value.push(...wordsPendingAdd.value)
  wordsPendingAdd.value = []
  editModalTab.value = 'edit'
}

const removeWordFromEditGroup = (id) => {
  const idx = selectedWordsToAdd.value.indexOf(id)
  if (idx !== -1) {
    selectedWordsToAdd.value.splice(idx, 1)
  }
}

const parseTranslation = (raw) => {
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
</script>

<template>
  <div class="w-full max-w-4xl mx-auto flex flex-col gap-6 px-4 pb-20 select-none mt-4">
    <!-- Header Block with Back Button -->
    <div class="flex justify-between items-center bg-white/40 dark:bg-zinc-900/40 backdrop-blur-md p-6 rounded-3xl border border-white/60 dark:border-zinc-800/60 shadow-sm">
      <div class="flex items-center gap-4">
        <router-link to="/dictionary" class="p-2.5 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-2xl transition-all text-zinc-500 hover:text-zinc-800 dark:hover:text-zinc-200" title="Back to Dictionary">
          <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M15 19l-7-7 7-7" />
          </svg>
        </router-link>
        <div>
          <h1 class="text-3xl font-black tracking-tight text-zinc-800 dark:text-zinc-100">
            Groups <span class="text-indigo-500">.</span>
          </h1>
          <p class="text-xs font-bold text-zinc-400 mt-1">Manage connection groups ({{ groups.length }} groups, {{ totalWordsCount }} words)</p>
        </div>
      </div>
      <button 
        @click="openCreateModal" 
        class="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-2xl font-bold transition-all shadow-md shadow-indigo-600/10 text-xs active:scale-95 flex items-center gap-1.5"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
          <path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd" />
        </svg>
        <span>New Group</span>
      </button>
    </div>

    <!-- Search Bar -->
    <div class="relative w-full">
      <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" /></svg>
      <input 
        v-model="searchQuery" 
        type="text" 
        placeholder="Search groups by name..." 
        class="w-full pl-11 pr-4 py-3.5 bg-white dark:bg-zinc-900 border-2 border-zinc-100 dark:border-zinc-800 rounded-2xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 transition-all font-bold text-zinc-700 dark:text-zinc-200 shadow-sm"
      />
    </div>

    <div v-if="loading" class="flex flex-col items-center justify-center py-20 gap-4">
      <svg class="animate-spin h-8 w-8 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path></svg>
      <p class="text-zinc-400 font-bold tracking-wide">Loading Groups...</p>
    </div>

    <div v-else-if="filteredGroups.length === 0" class="flex flex-col items-center justify-center py-20 text-center bg-white dark:bg-zinc-900 rounded-3xl border border-zinc-100 dark:border-zinc-800">
      <div class="w-16 h-16 bg-zinc-100 dark:bg-zinc-800 rounded-2xl flex items-center justify-center mb-4">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-8 w-8 text-zinc-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" /></svg>
      </div>
      <h3 class="text-xl font-bold text-zinc-700 dark:text-zinc-300 mb-2">No Groups Found</h3>
      <p class="text-zinc-500 font-medium">Create a new group to link related vocabulary together.</p>
    </div>

    <div v-else class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <div v-for="group in filteredGroups" :key="group.id" class="group bg-white dark:bg-zinc-900 p-5 rounded-3xl border border-zinc-100 dark:border-zinc-800 shadow-sm hover:shadow-md hover:border-indigo-500/30 transition-all flex flex-col gap-4">
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-2xl bg-indigo-50 dark:bg-indigo-900/30 text-indigo-500 flex items-center justify-center">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" /></svg>
            </div>
            <div>
              <h3 class="text-lg font-black text-zinc-800 dark:text-zinc-200 truncate max-w-[200px]" :class="!group.name ? 'text-zinc-500 dark:text-zinc-400' : ''">
                {{ group.name || (group.vocabularies?.length ? group.vocabularies.map(v => v.word).join(', ') : 'Empty Group') }}
              </h3>
              <p class="text-xs font-bold text-zinc-400 mt-0.5">{{ group.vocabularies?.length || 0 }} words</p>
            </div>
          </div>
          <div class="flex items-center gap-1">
            <button @click="openEditModal(group)" class="opacity-0 group-hover:opacity-100 p-2 text-zinc-400 hover:text-indigo-500 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded-xl transition-all" title="Edit Group">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" /></svg>
            </button>
            <button @click="deleteGroup(group.id)" class="opacity-0 group-hover:opacity-100 p-2 text-zinc-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-900/30 rounded-xl transition-all" title="Delete Group">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd" /></svg>
            </button>
          </div>
        </div>
        
        <div class="flex flex-wrap gap-1.5 mt-auto">
          <router-link 
            v-for="v in group.vocabularies?.slice(0, 10)" :key="v.id"
            :to="`/dictionary/${v.id}`"
            class="text-[10px] font-bold px-2.5 py-1 bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 rounded-lg hover:bg-indigo-100 dark:hover:bg-indigo-900/50 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer"
          >
            {{ v.word }}
          </router-link>
          <span v-if="group.vocabularies?.length > 10" class="text-[10px] font-bold px-2.5 py-1 bg-zinc-50 dark:bg-zinc-800/50 text-zinc-400 rounded-lg">
            +{{ group.vocabularies.length - 10 }}
          </span>
          <span v-if="!group.vocabularies || group.vocabularies.length === 0" class="text-[10px] text-zinc-400 font-semibold italic py-1">Empty group</span>
        </div>
      </div>
    </div>

    <!-- Edit Group Modal -->
    <transition name="fade">
      <div v-if="showEditModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-900/40 dark:bg-black/60 backdrop-blur-sm" @click.self="showEditModal = false">
        <div class="bg-white dark:bg-zinc-900 rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl border border-zinc-100 dark:border-zinc-800 p-6 flex flex-col gap-4 relative max-h-[85vh]">
          
          <!-- TAB 1: EDIT GROUP DETAILS & WORDS -->
          <template v-if="editModalTab === 'edit'">
            <h2 class="text-xl font-black text-zinc-800 dark:text-zinc-100 shrink-0">Edit Group</h2>
            
            <div class="flex flex-col gap-1.5 shrink-0">
              <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Group Name</label>
              <input 
                v-model="editGroupName" 
                type="text" 
                placeholder="e.g. Word Roots, Synonyms" 
                class="w-full px-4 py-3 bg-zinc-50 dark:bg-zinc-800 border-2 border-zinc-100 dark:border-zinc-700 rounded-xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 transition-all font-bold text-zinc-700 dark:text-zinc-200"
                @keyup.enter="updateGroup"
              />
            </div>

            <div class="flex flex-col gap-1.5 flex-1 min-h-0">
              <div class="flex justify-between items-center shrink-0">
                <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Words in Group ({{ selectedWordsToAdd.length }})</label>
                <button @click="editModalTab = 'add'; wordsPendingAdd = []" class="flex items-center gap-1 text-xs font-black text-indigo-600 hover:text-indigo-700 dark:text-indigo-400 dark:hover:text-indigo-300 transition-colors">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 3a1 1 0 011 1v5h5a1 1 0 110 2h-5v5a1 1 0 11-2 0v-5H4a1 1 0 110-2h5V4a1 1 0 011-1z" clip-rule="evenodd" /></svg>
                  Add Words
                </button>
              </div>

              <div class="flex-1 overflow-y-auto overscroll-contain pr-1 custom-scrollbar border border-zinc-100 dark:border-zinc-800/50 rounded-xl p-2 bg-zinc-50/50 dark:bg-black/10 min-h-0 mt-1">
                <div v-if="groupCurrentVocabularies.length === 0" class="text-xs text-zinc-400 font-semibold italic text-center py-12">
                  No words in this group yet. Click "Add Words" above.
                </div>
                <div v-else class="flex flex-col gap-1.5">
                  <div 
                    v-for="w in groupCurrentVocabularies" :key="w.id"
                    class="flex items-center justify-between p-2.5 rounded-xl border border-transparent bg-white dark:bg-zinc-800 shadow-sm"
                  >
                    <div class="flex flex-col">
                      <span class="font-bold text-sm text-zinc-700 dark:text-zinc-200">{{ w.word }}</span>
                      <span class="text-[10px] text-zinc-400 dark:text-zinc-500 mt-0.5">{{ parseTranslation(w.translation) }}</span>
                    </div>
                    <button @click="removeWordFromEditGroup(w.id)" class="p-1.5 text-zinc-400 hover:text-rose-500 hover:bg-rose-50 dark:hover:bg-rose-950/20 rounded-lg transition-colors" title="Remove from Group">
                      <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                    </button>
                  </div>
                </div>
              </div>
            </div>
            
            <div class="flex gap-3 pt-4 border-t border-zinc-100 dark:border-zinc-800 shrink-0">
              <button @click="showEditModal = false" class="flex-1 py-3 bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 rounded-xl font-bold hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-colors">Cancel</button>
              <button @click="updateGroup" class="flex-1 py-3 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 shadow-lg shadow-indigo-600/20 transition-all">Save Changes ({{ selectedWordsToAdd.length }})</button>
            </div>
          </template>

          <!-- TAB 2: SELECT WORDS TO ADD -->
          <template v-else>
            <h2 class="text-xl font-black text-zinc-800 dark:text-zinc-100 shrink-0">Add Words to Group</h2>
            
            <div class="flex flex-col gap-1.5 flex-1 min-h-0">
              <div class="relative w-full shrink-0">
                <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-zinc-400">
                  <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" /></svg>
                </div>
                <input v-model="searchWordQuery" type="text" placeholder="Search dictionary..." class="w-full pl-9 pr-3 py-2.5 bg-zinc-50 dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
              </div>

              <div class="flex flex-col gap-2 overflow-y-auto custom-scrollbar mt-2 border border-zinc-100 dark:border-zinc-800/50 rounded-xl p-2 bg-zinc-50/50 dark:bg-black/10 flex-1 min-h-0">
                <div v-if="availableVocabulariesToAdd.length > 0">
                  <div 
                    v-for="w in availableVocabulariesToAdd.slice(0, 100)" :key="w.id"
                    @click="toggleWordPendingAdd(w.id)"
                    class="flex items-center justify-between p-2.5 rounded-xl cursor-pointer transition-colors border mb-1.5"
                    :class="wordsPendingAdd.includes(w.id) ? 'bg-indigo-50 dark:bg-indigo-900/30 border-indigo-200 dark:border-indigo-700/50 text-indigo-700 dark:text-indigo-300' : 'bg-white dark:bg-zinc-800 border-transparent hover:border-zinc-200 dark:hover:border-zinc-700'"
                  >
                    <div class="flex flex-col">
                      <span class="font-bold text-sm">{{ w.word }}</span>
                      <span class="text-[10px] opacity-70">{{ parseTranslation(w.translation) }}</span>
                    </div>
                    <div 
                      class="w-5 h-5 rounded-md border flex items-center justify-center transition-all"
                      :class="wordsPendingAdd.includes(w.id) ? 'bg-indigo-500 border-indigo-500 text-white' : 'border-zinc-300 dark:border-zinc-600'"
                    >
                      <svg v-if="wordsPendingAdd.includes(w.id)" xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" /></svg>
                    </div>
                  </div>
                </div>
                <div v-else class="text-xs text-zinc-400 font-semibold italic text-center py-8">No unlinked words found.</div>
              </div>
              
              <div class="flex items-center gap-2 mt-1 shrink-0">
                <span class="text-[10px] font-black uppercase text-indigo-500">{{ wordsPendingAdd.length }} words selected</span>
              </div>
            </div>
            
            <div class="flex gap-3 pt-4 border-t border-zinc-100 dark:border-zinc-800 shrink-0">
              <button @click="editModalTab = 'edit'; wordsPendingAdd = []" class="flex-1 py-3 bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 rounded-xl font-bold hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-colors">Back</button>
              <button @click="confirmAddWords" class="flex-1 py-3 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 shadow-lg shadow-indigo-600/20 transition-all" :disabled="wordsPendingAdd.length === 0" :class="wordsPendingAdd.length === 0 ? 'opacity-50 cursor-not-allowed' : ''">Add Selected</button>
            </div>
          </template>
          
        </div>
      </div>
    </transition>

    <!-- Create Group Modal -->
    <transition name="fade">
      <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-zinc-900/40 dark:bg-black/60 backdrop-blur-sm" @click.self="showCreateModal = false">
        <div class="bg-white dark:bg-zinc-900 rounded-3xl w-full max-w-lg overflow-hidden shadow-2xl border border-zinc-100 dark:border-zinc-800 p-6 flex flex-col gap-4 relative max-h-[85vh]">
          <h2 class="text-xl font-black text-zinc-800 dark:text-zinc-100 shrink-0">Create Group</h2>
          
          <div class="flex flex-col gap-1.5 shrink-0">
            <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Group Name (Optional)</label>
            <input 
              v-model="newGroupName" 
              type="text" 
              placeholder="e.g. Word Roots, Synonyms" 
              class="w-full px-4 py-3 bg-zinc-50 dark:bg-zinc-800 border-2 border-zinc-100 dark:border-zinc-700 rounded-xl focus:border-indigo-500 focus:ring-4 focus:ring-indigo-500/10 transition-all font-bold text-zinc-700 dark:text-zinc-200"
              @keyup.enter="createGroup"
            />
          </div>

          <div class="flex flex-col gap-1.5 flex-1 min-h-0">
            <label class="text-xs font-bold text-zinc-500 uppercase tracking-wider">Select Words to Add ({{ newGroupWordIds.length }})</label>
            <div class="relative w-full shrink-0">
              <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none text-zinc-400">
                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8 4a4 4 0 100 8 4 4 0 000-8zM2 8a6 6 0 1110.89 3.476l4.817 4.817a1 1 0 01-1.414 1.414l-4.816-4.816A6 6 0 012 8z" clip-rule="evenodd" /></svg>
              </div>
              <input v-model="searchWordQuery" type="text" placeholder="Search dictionary..." class="w-full pl-9 pr-3 py-2 bg-zinc-50 dark:bg-black/20 border border-zinc-200 dark:border-zinc-800 rounded-xl outline-none focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 transition-all text-xs font-semibold" />
            </div>

            <div class="flex flex-col gap-2 overflow-y-auto custom-scrollbar mt-2 border border-zinc-100 dark:border-zinc-800/50 rounded-xl p-2 bg-zinc-50/50 dark:bg-black/10 flex-1 min-h-0">
              <div v-if="availableVocabulariesToCreate.length > 0">
                <div 
                  v-for="w in availableVocabulariesToCreate.slice(0, 100)" :key="w.id"
                  @click="toggleWordToCreateGroup(w.id)"
                  class="flex items-center justify-between p-2.5 rounded-xl cursor-pointer transition-colors border mb-1.5"
                  :class="newGroupWordIds.includes(w.id) ? 'bg-indigo-50 dark:bg-indigo-900/30 border-indigo-200 dark:border-indigo-700/50 text-indigo-700 dark:text-indigo-300' : 'bg-white dark:bg-zinc-800 border-transparent hover:border-zinc-200 dark:hover:border-zinc-700'"
                >
                  <div class="flex flex-col">
                    <span class="font-bold text-sm text-zinc-700 dark:text-zinc-200">{{ w.word }}</span>
                    <span class="text-[10px] text-zinc-400 dark:text-zinc-500 mt-0.5">{{ parseTranslation(w.translation) }}</span>
                  </div>
                  <div 
                    class="w-5 h-5 rounded-md border flex items-center justify-center transition-all"
                    :class="newGroupWordIds.includes(w.id) ? 'bg-indigo-500 border-indigo-500 text-white' : 'border-zinc-300 dark:border-zinc-600'"
                  >
                    <svg v-if="newGroupWordIds.includes(w.id)" xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd" /></svg>
                  </div>
                </div>
              </div>
              <div v-else class="text-xs text-zinc-400 font-semibold italic text-center py-8">No words found in dictionary.</div>
            </div>
            
            <div class="flex items-center gap-2 mt-1 shrink-0">
              <span class="text-[10px] font-black uppercase text-indigo-500">{{ newGroupWordIds.length }} words selected</span>
            </div>
          </div>
          
          <div class="flex gap-3 pt-4 border-t border-zinc-100 dark:border-zinc-800 shrink-0">
            <button @click="showCreateModal = false" class="flex-1 py-3 bg-zinc-100 dark:bg-zinc-800 text-zinc-600 dark:text-zinc-300 rounded-xl font-bold hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-colors">Cancel</button>
            <button @click="createGroup" class="flex-1 py-3 bg-indigo-600 text-white rounded-xl font-bold hover:bg-indigo-700 shadow-lg shadow-indigo-600/20 transition-all">Create Group ({{ newGroupWordIds.length }})</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
