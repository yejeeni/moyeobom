import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ToastProvider } from './components/Toasts'
import { Landing } from './pages/Landing'
import { Plan } from './pages/Plan'
import { Review } from './pages/Review'
import { Room } from './pages/Room'
import { RoomProvider } from './room/RoomContext'

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <RoomProvider>
          <Routes>
            <Route path="/" element={<Landing />} />
            <Route path="/plan" element={<Plan />} />
            <Route path="/room" element={<Room />} />
            <Route path="/review" element={<Review />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </RoomProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
