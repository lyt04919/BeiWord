<script setup>
import { useToast } from '../../composables/useToast'
import { TransitionGroup } from 'vue'

const { toasts, removeToast } = useToast()

const getIcon = (type) => {
  switch (type) {
    case 'success':
      return `<svg class="w-5 h-5 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7"></path></svg>`
    case 'error':
      return `<svg class="w-5 h-5 text-red-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path></svg>`
    default:
      return `<svg class="w-5 h-5 text-indigo-500" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path></svg>`
  }
}
</script>

<template>
  <div class="fixed top-6 right-6 z-50 flex flex-col gap-3 pointer-events-none w-80">
    <TransitionGroup name="toast">
      <div 
        v-for="toast in toasts" 
        :key="toast.id"
        class="pointer-events-auto bg-white dark:bg-zinc-800 border border-zinc-100 dark:border-zinc-700 shadow-[0_8px_30px_rgb(0,0,0,0.12)] rounded-2xl p-4 flex items-start gap-3 transform transition-all duration-300"
      >
        <div class="shrink-0 mt-0.5" v-html="getIcon(toast.type)"></div>
        <div class="flex-1 flex flex-col pt-0.5">
          <p class="text-sm font-bold text-zinc-800 dark:text-zinc-200 leading-tight">
            {{ toast.message }}
          </p>
        </div>
        <button 
          @click="removeToast(toast.id)" 
          class="shrink-0 text-zinc-400 hover:text-zinc-600 dark:hover:text-zinc-300 transition-colors p-1"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12"></path>
          </svg>
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.toast-enter-from {
  opacity: 0;
  transform: translateX(100%) scale(0.9);
}

.toast-leave-to {
  opacity: 0;
  transform: scale(0.9);
}
</style>
