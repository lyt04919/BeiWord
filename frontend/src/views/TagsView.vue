<script setup>
import { ref, onMounted } from 'vue'

const tags = ref([])
const loading = ref(true)

const newTagName = ref('')
const newTagColor = ref('#4F46E5') // Default primary color

const editingTagId = ref(null)
const editTagName = ref('')
const editTagColor = ref('')

const fetchTags = async () => {
  loading.value = true
  try {
    const res = await fetch(window.API_BASE_URL + '/api/tags')
    if (res.ok) {
      tags.value = await res.json()
    }
  } catch (e) {
    console.error('Failed to fetch tags', e)
  } finally {
    loading.value = false
  }
}

const addTag = async () => {
  if (!newTagName.value.trim()) return
  try {
    const res = await fetch(window.API_BASE_URL + '/api/tags', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: newTagName.value.trim(),
        color: newTagColor.value
      })
    })
    if (res.ok) {
      newTagName.value = ''
      newTagColor.value = '#' + Math.floor(Math.random()*16777215).toString(16).padStart(6, '0') // Random next color
      fetchTags()
    }
  } catch (e) {
    console.error('Failed to add tag', e)
  }
}

const startEdit = (tag) => {
  editingTagId.value = tag.id
  editTagName.value = tag.name
  editTagColor.value = tag.color || '#4F46E5'
}

const saveEdit = async (id) => {
  try {
    const res = await fetch(`http://localhost:8080/api/tags/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: editTagName.value.trim(),
        color: editTagColor.value
      })
    })
    if (res.ok) {
      editingTagId.value = null
      fetchTags()
    }
  } catch (e) {
    console.error('Failed to update tag', e)
  }
}

const cancelEdit = () => {
  editingTagId.value = null
}

const deleteTag = async (id) => {
  if (!confirm('Are you sure you want to delete this tag? It will be removed from all vocabularies.')) return
  try {
    const res = await fetch(`http://localhost:8080/api/tags/${id}`, {
      method: 'DELETE'
    })
    if (res.ok) {
      fetchTags()
    }
  } catch (e) {
    console.error('Failed to delete tag', e)
  }
}

onMounted(() => {
  fetchTags()
  // Init a random default color
  newTagColor.value = '#' + Math.floor(Math.random()*16777215).toString(16).padStart(6, '0')
})
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
          <h2 class="text-2xl font-black tracking-tight text-zinc-900 dark:text-white">Manage Tags</h2>
          <p class="text-xs font-semibold text-zinc-400 dark:text-zinc-500 mt-1">Create, edit, and organize the tags you use for vocabulary words.</p>
        </div>
      </div>
    </div>

    <!-- Add Tag Form -->
    <div class="bg-white/40 dark:bg-zinc-900/40 backdrop-blur-md p-6 rounded-3xl border border-white/60 dark:border-zinc-800/60 shadow-sm flex flex-col sm:flex-row gap-4 items-end">
      <div class="flex-1 w-full">
        <label class="block text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-2">New Tag Name</label>
        <input 
          v-model="newTagName" 
          @keyup.enter="addTag"
          type="text" 
          placeholder="e.g. IELTS, Hard, Geography" 
          class="w-full px-4 py-2.5 rounded-2xl border border-zinc-200 dark:border-zinc-800 bg-white/50 dark:bg-zinc-900/50 focus:outline-none focus:ring-2 focus:ring-indigo-500/25 focus:border-indigo-500 text-xs font-semibold text-zinc-700 dark:text-zinc-300 placeholder-zinc-450"
        />
      </div>
      <div class="w-full sm:w-20">
        <label class="block text-[10px] font-black text-zinc-400 dark:text-zinc-500 uppercase tracking-widest mb-2 text-center">Color</label>
        <div class="relative w-full h-[40px] rounded-2xl overflow-hidden border border-zinc-200 dark:border-zinc-800 cursor-pointer bg-white/50 dark:bg-zinc-900/50">
          <input 
            v-model="newTagColor" 
            type="color" 
            class="absolute -inset-2 w-[calc(100%+16px)] h-[calc(100%+16px)] cursor-pointer"
          />
        </div>
      </div>
      <button 
        @click="addTag" 
        :disabled="!newTagName.trim()"
        class="w-full sm:w-auto px-6 py-2.5 rounded-2xl bg-indigo-600 text-white font-bold text-xs disabled:opacity-50 disabled:cursor-not-allowed hover:bg-indigo-700 transition-all h-[40px] shadow-md shadow-indigo-600/10 active:scale-95"
      >
        Add Tag
      </button>
    </div>

    <!-- Spinner Loading -->
    <div v-if="loading" class="flex justify-center p-20">
      <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600"></div>
    </div>

    <div v-else-if="tags.length === 0" class="text-center p-16 bg-white/30 dark:bg-zinc-900/30 border border-zinc-150 dark:border-zinc-800/80 rounded-3xl text-sm font-semibold text-zinc-400">
      No tags found. Create your first tag above!
    </div>

    <!-- Tags Cards Grid -->
    <div v-else class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
      <div 
        v-for="tag in tags" 
        :key="tag.id" 
        class="relative bg-white/60 dark:bg-zinc-900/60 backdrop-blur-md p-4 rounded-3xl border border-white/50 dark:border-zinc-800/80 shadow-[0_4px_20px_-4px_rgba(0,0,0,0.02)] hover:border-indigo-500/20 dark:hover:border-indigo-500/20 transition-all duration-300 flex items-center justify-between group h-16"
      >
        
        <!-- Read Mode -->
        <template v-if="editingTagId !== tag.id">
          <div class="flex items-center gap-3">
            <div class="w-3.5 h-3.5 rounded-full border border-black/10 dark:border-white/10 shadow-sm" :style="{ backgroundColor: tag.color || '#ccc' }"></div>
            <span class="font-bold text-sm text-zinc-800 dark:text-zinc-200">#{{ tag.name }}</span>
          </div>
          
          <div class="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
            <button @click="startEdit(tag)" class="p-1.5 text-zinc-400 hover:text-indigo-500 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-lg transition-colors" title="Edit tag">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path d="M13.586 3.586a2 2 0 112.828 2.828l-.793.793-2.828-2.828.793-.793zM11.379 5.793L3 14.172V17h2.828l8.38-8.379-2.83-2.828z" />
              </svg>
            </button>
            <button @click="deleteTag(tag.id)" class="p-1.5 text-zinc-400 hover:text-rose-500 hover:bg-zinc-100 dark:hover:bg-zinc-800 rounded-lg transition-colors" title="Delete tag">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M9 2a1 1 0 00-.894.553L7.382 4H4a1 1 0 000 2v10a2 2 0 002 2h8a2 2 0 002-2V6a1 1 0 100-2h-3.382l-.724-1.447A1 1 0 0011 2H9zM7 8a1 1 0 012 0v6a1 1 0 11-2 0V8zm5-1a1 1 0 00-1 1v6a1 1 0 102 0V8a1 1 0 00-1-1z" clip-rule="evenodd" />
              </svg>
            </button>
          </div>
        </template>

        <!-- Edit Mode -->
        <template v-else>
          <div class="flex items-center gap-2 flex-1 mr-3">
            <div class="relative w-6 h-6 rounded-full overflow-hidden border border-zinc-200 dark:border-zinc-800 shrink-0">
              <input v-model="editTagColor" type="color" class="absolute -inset-1.5 w-[calc(100%+12px)] h-[calc(100%+12px)] cursor-pointer" />
            </div>
            <input 
              v-model="editTagName" 
              @keyup.enter="saveEdit(tag.id)"
              @keyup.esc="cancelEdit"
              type="text" 
              class="w-full px-2.5 py-1 rounded-xl border border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900 text-xs font-bold text-zinc-800 dark:text-zinc-200 focus:outline-none focus:ring-1 focus:ring-indigo-500"
              autofocus
            />
          </div>
          <div class="flex items-center gap-1 shrink-0">
            <button @click="saveEdit(tag.id)" class="px-2 py-1 bg-green-500 hover:bg-green-600 text-white rounded-lg text-[10px] font-bold transition-all shadow-sm">
              Save
            </button>
            <button @click="cancelEdit" class="px-2 py-1 bg-zinc-150 dark:bg-zinc-800 text-zinc-500 dark:text-zinc-400 rounded-lg text-[10px] font-bold hover:bg-zinc-200 dark:hover:bg-zinc-700 transition-all">
              Cancel
            </button>
          </div>
        </template>

      </div>
    </div>
  </div>
</template>
