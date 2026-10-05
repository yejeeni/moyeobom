import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { ToastProvider } from './components/Toasts'
import { Home } from './pages/Home'
import { Review } from './pages/Review'
import { Room } from './pages/Room'
import { RoomProvider } from './room/RoomContext'

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <RoomProvider>
          <Routes>
            <Route path="/" element={<Home />} />
            <Route path="/plan" element={<Navigate to="/" replace />} />
            <Route path="/room" element={<Room />} />
            <Route path="/review" element={<Review />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </RoomProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}
