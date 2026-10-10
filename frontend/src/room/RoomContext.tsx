import { Client, type StompSubscription } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api } from '../api'
import type { Occupant, RoomEnter, RoomEvent, SnapshotPayload } from '../api/types'
import { useToast } from '../components/Toasts'
import { getGuestId } from '../lib/guest'
import { isAlertSoundOn, playChime } from '../lib/preferences'
import { isApiError } from '../api/client'
import { getRoomConfig, type RoomConfig } from '../lib/roomSize'

export type Connection = 'idle' | 'connecting' | 'connected' | 'reconnecting'

interface RoomState {
  roomId: string | null
  /** 입장 코드 */
  code: string | null
  /** 열람실 인원(자리 수) */
  seatCount: number
  /** 실제 사람 자리 수(나 포함). 2 이상이면 코드를 나눠 줄 수 있다 */
  realSeatCount: number
  /** 비어 있는 실제 자리(초대 대기) */
  waiting: Record<number, boolean>
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
  /** 내가 있는 방으로 다시 연결한다. 없으면 브라우저에 기억한 구성으로 새 방을 만든다 */
  enter: () => Promise<RoomEnter>
  /** 새 방을 만들어 옮긴다 */
  create: (config: RoomConfig) => Promise<RoomEnter>
  /** 코드로 다른 방에 들어간다 */
  join: (code: string) => Promise<RoomEnter>
  /** 이 화면의 연결만 정리한다(방에서 나오는 일은 서버가 마무리 확정 때 한다) */
  leave: () => void
}

const EMPTY: RoomState = {
  roomId: null,
  code: null,
  seatCount: 9,
  realSeatCount: 1,
  waiting: {},
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
          const waiting: Record<number, boolean> = {}
          payload.seats.forEach((seat) => {
            seats[seat.seatNo] = seat.occupant
            waiting[seat.seatNo] = seat.waiting
          })
          seatsRef.current = seats
          mySeatRef.current = payload.mySeatNo
          setState((s) => ({
            ...s,
            seats,
            waiting,
            code: payload.code ?? s.code,
            seatCount: payload.seatCount ?? s.seatCount,
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
          setState((s) => ({ ...s, seats: seatsRef.current, waiting: { ...s.waiting, [seatNo]: false } }))
          toast.show(`${occupant.nickname}님이 들어왔어요`)
          return
        }
        case 'SEAT_LEFT': {
          const left = seatsRef.current[seatNo]
          // 실제 사람이 나가면 그 자리는 다시 초대 대기가 된다
          const nowWaiting = Boolean((event.payload as { waiting?: boolean } | null)?.waiting)
          seatsRef.current = { ...seatsRef.current, [seatNo]: null }
          setState((s) => ({ ...s, seats: seatsRef.current, waiting: { ...s.waiting, [seatNo]: nowWaiting } }))
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
          // 서버가 재시작되어 방이 사라졌다(코드도 무효). 기억한 구성으로 새 방을 연다.
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

  /** 들어간 방으로 화면 상태를 맞추고 구독을 다시 건다 */
  const attach = useCallback(
    (entered: RoomEnter) => {
      const changed = roomIdRef.current !== entered.roomId
      roomIdRef.current = entered.roomId
      if (changed) seatsRef.current = {}
      setState((s) => ({
        ...s,
        roomId: entered.roomId,
        code: entered.code,
        seatCount: entered.seatCount,
        realSeatCount: entered.realSeatCount,
        mySeatNo: entered.seatNo,
        nickname: entered.nickname,
        // 새 방이면 스냅샷이 올 때까지 이전 방 자리를 비운다
        seats: changed ? {} : s.seats,
        waiting: changed ? {} : s.waiting,
      }))
      connect()
      return entered
    },
    [connect],
  )

  const create = useCallback(
    async (config: RoomConfig) => attach(await api.createRoom(config.virtualSeats, config.realSeats)),
    [attach],
  )

  const join = useCallback(async (code: string) => attach(await api.joinRoom(code)), [attach])

  const enter = useCallback(async () => {
    try {
      return attach(await api.currentRoom())
    } catch (e) {
      if (!isApiError(e, 'ROOM_NOT_FOUND')) throw e
      const hadRoom = roomIdRef.current !== null
      const created = await create(getRoomConfig())
      if (hadRoom) toast.show('열람실이 닫혀 새 열람실을 열었어요')
      return created
    }
  }, [attach, create, toast])

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

  const value = useMemo(() => ({ ...state, enter, create, join, leave }), [state, enter, create, join, leave])
  return <RoomContext.Provider value={value}>{children}</RoomContext.Provider>
}

export function useRoom(): RoomApi {
  const room = useContext(RoomContext)
  if (!room) throw new Error('RoomProvider 안에서 써야 합니다.')
  return room
}
