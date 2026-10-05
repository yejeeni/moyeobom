import { Client, type StompSubscription } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api } from '../api'
import type { Occupant, RoomEnter, RoomEvent, SnapshotPayload } from '../api/types'
import { useToast } from '../components/Toasts'
import { getGuestId } from '../lib/guest'
import { isAlertSoundOn, playChime } from '../lib/preferences'

export type Connection = 'idle' | 'connecting' | 'connected' | 'reconnecting'

interface RoomState {
  roomId: string | null
  mySeatNo: number | null
  nickname: string | null
  seats: Record<number, Occupant | null>
  /** 서버 시각 - 내 PC 시각. 경과 시간을 서버 기준으로 맞춘다. */
  offsetMs: number
  connection: Connection
  /** 스냅샷을 받을 때마다 늘어난다. 재연결 뒤 내 상태를 다시 불러올 때 쓴다. */
  snapshotVersion: number
}

interface RoomApi extends RoomState {
  enter: () => Promise<RoomEnter>
  leave: () => void
}

const EMPTY: RoomState = {
  roomId: null,
  mySeatNo: null,
  nickname: null,
  seats: {},
  offsetMs: 0,
  connection: 'idle',
  snapshotVersion: 0,
}

const RoomContext = createContext<RoomApi | null>(null)

/**
 * 열람실 WebSocket 연결. 화면을 옮겨도(열람실 ↔ 회고) 연결을 유지하도록 앱 최상단에 둔다.
 * 서버 → 클라이언트 단방향이며, 동작은 모두 REST로 보낸다.
 */
export function RoomProvider({ children }: { children: ReactNode }) {
  const toast = useToast()
  const [state, setState] = useState<RoomState>(EMPTY)
  const clientRef = useRef<Client | null>(null)
  const roomIdRef = useRef<string | null>(null)
  const subscriptionsRef = useRef<StompSubscription[]>([])
  const seatsRef = useRef<Record<number, Occupant | null>>({})
  const mySeatRef = useRef<number | null>(null)
  const enterRef = useRef<() => Promise<RoomEnter>>(() => Promise.reject())

  const handleEvent = useCallback(
    (event: RoomEvent) => {
      const seatNo = event.seatNo ?? 0
      switch (event.type) {
        case 'ROOM_SNAPSHOT': {
          const payload = event.payload as SnapshotPayload
          const seats: Record<number, Occupant | null> = {}
          payload.seats.forEach((seat) => (seats[seat.seatNo] = seat.occupant))
          seatsRef.current = seats
          mySeatRef.current = payload.mySeatNo
          setState((s) => ({
            ...s,
            seats,
            mySeatNo: payload.mySeatNo,
            nickname: seats[payload.mySeatNo]?.nickname ?? s.nickname,
            offsetMs: Date.parse(payload.serverTime) - Date.now(),
            snapshotVersion: s.snapshotVersion + 1,
          }))
          return
        }
        case 'SEAT_JOINED': {
          const occupant = event.payload as Occupant
          seatsRef.current = { ...seatsRef.current, [seatNo]: occupant }
          setState((s) => ({ ...s, seats: seatsRef.current }))
          toast.show(`${occupant.nickname}님이 들어왔어요`)
          return
        }
        case 'SEAT_LEFT': {
          const left = seatsRef.current[seatNo]
          seatsRef.current = { ...seatsRef.current, [seatNo]: null }
          setState((s) => ({ ...s, seats: seatsRef.current }))
          if (left) toast.show(`${left.nickname}님이 자리를 떠났어요`)
          return
        }
        case 'STATE_CHANGED':
        case 'COUNTS_CHANGED': {
          const current = seatsRef.current[seatNo]
          if (!current) return
          seatsRef.current = { ...seatsRef.current, [seatNo]: { ...current, ...(event.payload as Partial<Occupant>) } }
          setState((s) => ({ ...s, seats: seatsRef.current }))
          return
        }
        case 'BREAK_ALERT': {
          const { message } = event.payload as { message: string }
          toast.show(`충분히 집중했어요. ${message}`, 'refresh')
          if (isAlertSoundOn()) playChime()
          return
        }
        case 'ROOM_NOT_FOUND':
          // 서버가 재시작되어 방이 사라졌다. 다시 입장하면 새 방과 새 닉네임을 받는다.
          enterRef.current().catch(() => {})
          return
      }
    },
    [toast],
  )

  const subscribeAll = useCallback(
    (client: Client) => {
      subscriptionsRef.current.forEach((sub) => sub.unsubscribe())
      const parse = (body: string) => handleEvent(JSON.parse(body) as RoomEvent)
      const subs: StompSubscription[] = []
      // 토픽을 먼저 구독하고 스냅샷을 받아야 그 사이 이벤트를 놓치지 않는다
      if (roomIdRef.current) subs.push(client.subscribe(`/topic/rooms/${roomIdRef.current}`, (m) => parse(m.body)))
      subs.push(client.subscribe('/user/queue/notices', (m) => parse(m.body)))
      subs.push(client.subscribe('/user/queue/room-snapshot', (m) => parse(m.body)))
      subscriptionsRef.current = subs
    },
    [handleEvent],
  )

  const connect = useCallback(() => {
    if (clientRef.current) {
      if (clientRef.current.connected) subscribeAll(clientRef.current)
      return
    }
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      connectHeaders: { 'X-Guest-Id': getGuestId() ?? '' },
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 3000,
    })
    client.onConnect = () => {
      setState((s) => ({ ...s, connection: 'connected' }))
      subscribeAll(client)
    }
    client.onWebSocketClose = () => {
      subscriptionsRef.current = []
      setState((s) => (s.connection === 'idle' ? s : { ...s, connection: 'reconnecting' }))
    }
    clientRef.current = client
    setState((s) => ({ ...s, connection: 'connecting' }))
    client.activate()
  }, [subscribeAll])

  const enter = useCallback(async () => {
    const entered = await api.enterRoom()
    roomIdRef.current = entered.roomId
    setState((s) => ({ ...s, roomId: entered.roomId, mySeatNo: entered.seatNo, nickname: entered.nickname }))
    connect()
    return entered
  }, [connect])

  useEffect(() => {
    enterRef.current = enter
  }, [enter])

  const leave = useCallback(() => {
    const client = clientRef.current
    clientRef.current = null
    roomIdRef.current = null
    subscriptionsRef.current = []
    seatsRef.current = {}
    setState(EMPTY)
    client?.deactivate()
  }, [])

  useEffect(() => () => void clientRef.current?.deactivate(), [])

  const value = useMemo(() => ({ ...state, enter, leave }), [state, enter, leave])
  return <RoomContext.Provider value={value}>{children}</RoomContext.Provider>
}

export function useRoom(): RoomApi {
  const room = useContext(RoomContext)
  if (!room) throw new Error('RoomProvider 안에서 써야 합니다.')
  return room
}
