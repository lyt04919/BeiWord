#!/bin/bash

# A quick start script to run both backend and frontend servers simultaneously

# Handle Ctrl+C (SIGINT) to kill both background processes
trap "echo -e '\nStopping servers...'; kill $BACKEND_PID $FRONTEND_PID; exit" SIGINT SIGTERM

echo "🚀 Starting BeiWord Project..."

# Start the Spring Boot Backend in the background
echo "☕️ Starting Java Backend (Port 8080)..."
cd backend
mvn spring-boot:run > /dev/null 2>&1 &
BACKEND_PID=$!
cd ..

# Start the Vue Frontend in the background
echo "⚡️ Starting Vue Frontend (Port 5173)..."
cd frontend
npm run dev > /dev/null 2>&1 &
FRONTEND_PID=$!
cd ..

echo "🖥️  Starting Desktop App Mode (via Chrome)..."
# Wait for Vite server to start, then launch Chrome in standalone app mode
(sleep 5 && open -na "Google Chrome" --args --app="http://localhost:5173") &

echo ""
echo "✅ Project is running!"
echo "➡️  Frontend available at: http://localhost:5173"
echo "➡️  Backend available at: http://localhost:8080"
echo ""
echo "🛑 Press Ctrl+C to stop both servers."

# Wait indefinitely until interrupted
wait
