import { BrowserRouter, Navigate, Route, Routes, useParams } from 'react-router-dom'
import { CharacterLibraryProvider } from './character/CharacterLibrary'
import { ToastProvider } from './components/Toasts'
import { Home } from './pages/Home'
import { Review } from './pages/Review'
import { Room } from './pages/Room'
import { RoomProvider } from './room/RoomContext'

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <CharacterLibraryProvider>
          <RoomProvider>
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/plan" element={<Navigate to="/" replace />} />
              <Route path="/room" element={<Room />} />
              <Route path="/review" element={<Review />} />
              <Route path="/r/:code" element={<InviteLink />} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </RoomProvider>
        </CharacterLibraryProvider>
      </ToastProvider>
    </BrowserRouter>
  )
}

/** 초대 링크(/r/코드)는 첫 화면에 코드를 채워 보낸다 */
function InviteLink() {
  const { code = '' } = useParams()
  return <Navigate to={`/?code=${encodeURIComponent(code)}`} replace />
}
