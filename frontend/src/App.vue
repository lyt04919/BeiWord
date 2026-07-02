<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import ToastProvider from './components/ui/ToastProvider.vue'

const route = useRoute()
</script>

<template>
  <main class="w-full min-h-screen relative overflow-x-hidden bg-gradient-to-br from-indigo-50 via-white to-purple-50 dark:from-zinc-950 dark:via-zinc-900 dark:to-zinc-950 text-zinc-900 dark:text-white">
    <!-- Abstract background shapes -->
    <div class="fixed top-[-10%] left-[-10%] w-[40%] h-[40%] rounded-full bg-blue-300/10 blur-3xl pointer-events-none"></div>
    <div class="fixed bottom-[-10%] right-[-10%] w-[40%] h-[40%] rounded-full bg-purple-300/10 blur-3xl pointer-events-none"></div>
    
    <!-- Main Content with bottom padding to avoid dock overlap -->
    <div class="relative z-10 w-full min-h-screen flex flex-col pb-28">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>

    <!-- Global Toast Provider -->
    <ToastProvider />

    <!-- Floating Bottom Navigation Dock -->
    <div class="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 flex items-center justify-around w-72 h-14 bg-white/70 dark:bg-zinc-900/70 backdrop-blur-2xl rounded-full border border-white/40 dark:border-zinc-800/80 shadow-[0_12px_40px_-8px_rgba(0,0,0,0.12)] px-4">
      <!-- 1. Dictionary Tab (Left) -->
      <router-link 
        to="/dictionary" 
        class="flex flex-col items-center justify-center w-12 h-12 rounded-full transition-colors text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-200"
        active-class="!text-indigo-600 dark:!text-indigo-400"
        title="Dictionary"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
        </svg>
      </router-link>

      <!-- 2. Home Tab (Center) -->
      <router-link 
        to="/" 
        class="flex flex-col items-center justify-center w-12 h-12 rounded-full transition-colors text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-200"
        active-class="!text-indigo-600 dark:!text-indigo-400"
        title="Home"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
        </svg>
      </router-link>

      <!-- 3. Statistics Tab (Right) -->
      <router-link 
        to="/statistics" 
        class="flex flex-col items-center justify-center w-12 h-12 rounded-full transition-colors text-zinc-400 hover:text-zinc-800 dark:hover:text-zinc-200"
        active-class="!text-indigo-600 dark:!text-indigo-400"
        title="Statistics"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
        </svg>
      </router-link>
    </div>
  </main>
</template>

<style>
/* Global Glassmorphism panels */
.glass-panel {
  background-color: rgba(255, 255, 255, 0.45);
  backdrop-filter: blur(40px);
}
.dark .glass-panel {
  background-color: rgba(24, 24, 27, 0.45);
}

/* Page Transition Animations */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
