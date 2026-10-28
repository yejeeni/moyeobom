import { Client, TickerStrategy, type StompSubscription } from '@stomp/stompjs'
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { api } from '../api'
import type { MateSettings, Occupant, RoomEnter, RoomEvent, RoomSettings, SnapshotPayload } from '../api/types'
import { useToast } from '../components/Toasts'
import { getGuestId } from '../lib/guest'
import { getMateSettings, isAlertSoundOn, playChime, setMateSettings } from '../lib/preferences'
import { isApiError } from '../api/client'
import { clearLastRoom, getLastRoom, setLastRoom } from '../lib/lastRoom'
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
  /** 메이트 휴식 없음: 가상 메이트가 쉬지 않고 계속 집중한다(방 전체 설정) */
  matesAlwaysFocus: boolean
  /** 가상 메이트가 자리를 뜨지 않는다(방 전체 설정) */
  matesStay: boolean
  /** 방 설정을 바꿀 수 있는 사람(방 주인)의 자리. 모르면 0 */
  ownerSeatNo: number
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
  /** 메이트 설정을 바꾼다. 방 주인만 바꿀 수 있고, 고른 값은 다음 새 방에도 쓴다 */
  changeMateSettings: (settings: MateSettings) => Promise<void>
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
  matesAlwaysFocus: false,
  matesStay: false,
  ownerSeatNo: 0,
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
  // 설정 알림을 띄울지 비교하려고 마지막 방 설정을 따로 둔다
  const settingsRef = useRef<RoomSettings>({ matesAlwaysFocus: false, matesStay: false, ownerSeatNo: 0 })
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
          settingsRef.current = {
            matesAlwaysFocus: payload.matesAlwaysFocus,
            matesStay: payload.matesStay,
            ownerSeatNo: payload.ownerSeatNo,
          }
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
            ...settingsRef.current,
          }))
          return
        }
        case 'ROOM_SETTINGS_CHANGED': {
          const settings = event.payload as RoomSettings
          const me = mySeatRef.current
          const before = settingsRef.current
          settingsRef.current = settings
          // 방 주인이 바꾼 설정은 다른 사람에게 알리고, 방 주인을 이어받으면 나에게 알린다
          const changedMates =
            settings.matesAlwaysFocus !== before.matesAlwaysFocus || settings.matesStay !== before.matesStay
          if (settings.ownerSeatNo !== me && changedMates) {
            toast.show(`방을 만든 사람이 메이트 설정을 바꿨어요 (${describeMates(settings)})`)
          }
          if (settings.ownerSeatNo === me && before.ownerSeatNo !== me && before.ownerSeatNo !== 0) {
            toast.show('방을 만든 사람이 나가서 이제 내가 방 설정을 바꿀 수 있어요')
          }
          setState((s) => ({ ...s, ...settings }))
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
          // 오늘 마무리처럼 내가 나간 알림은 나에게 띄우지 않는다
          if (left && seatNo !== mySeatRef.current) toast.show(`${left.nickname}님이 자리를 떠났어요`)
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
      // 탭이 오래 안 보이면 브라우저가 화면의 타이머를 1분에 한 번 정도로 늦춘다. 그러면 연결 확인 신호가 30초 넘게 끊겨
      // 서버가 연결이 끊겼다고 보고 집중을 닫는다. Web Worker의 타이머는 덜 늦춰져서 다른 화면을 보는 동안에도 신호가 간다
      heartbeatStrategy: TickerStrategy.Worker,
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
      // 방 주인의 자리는 스냅샷으로 받는다. 내가 주인이면 바로 안다
      const ownerSeatNo = entered.owner ? entered.seatNo : changed ? 0 : settingsRef.current.ownerSeatNo
      settingsRef.current = { matesAlwaysFocus: entered.matesAlwaysFocus, matesStay: entered.matesStay, ownerSeatNo }
      setState((s) => ({
        ...s,
        roomId: entered.roomId,
        code: entered.code,
        seatCount: entered.seatCount,
        realSeatCount: entered.realSeatCount,
        mySeatNo: entered.seatNo,
        nickname: entered.nickname,
        ...settingsRef.current,
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
    async (config: RoomConfig) =>
      attach(await api.createRoom(config.virtualSeats, config.realSeats, getMateSettings())),
    [attach],
  )

  const join = useCallback(async (code: string) => attach(await api.joinRoom(code)), [attach])

  // 들어가는 중에 또 부르면(개발 모드의 화면 진입 두 번 실행, 재연결과 겹침 등) 같은 요청을 기다린다.
  // 따로 부르면 방이 없을 때 방을 두 개 만들고, 화면이 내가 없는 방을 구독하게 된다
  const enteringRef = useRef<Promise<RoomEnter> | null>(null)
  const enter = useCallback(() => {
    if (enteringRef.current) return enteringRef.current
    const entering = (async () => {
      try {
        return attach(await api.currentRoom())
      } catch (e) {
        if (!isApiError(e, 'ROOM_NOT_FOUND')) throw e
        const hadRoom = roomIdRef.current !== null
        // 서버가 다시 켜져 방이 사라졌으면 같은 코드로 되살린다. 같이 쓰던 사람들도 같은 방에 다시 모인다
        const last = getLastRoom()
        if (last) {
          try {
            const restored = attach(await api.restoreRoom(last))
            if (restored.code === last.code) {
              toast.show(
                last.realSeats > 1
                  ? `서버가 업데이트되어 같은 코드(${last.code})의 열람실로 다시 모였어요`
                  : '서버가 업데이트되어 열람실을 다시 열었어요',
              )
            } else if (hadRoom) {
              toast.show('열람실이 닫혀 새 열람실을 열었어요')
            }
            return restored
          } catch (restoreError) {
            // 되살릴 수 있는 시간이 지났으면 새 방을 연다
            if (!isApiError(restoreError, 'ROOM_RESTORE_EXPIRED')) throw restoreError
          }
        }
        const created = await create(getRoomConfig())
        if (hadRoom) toast.show('열람실이 닫혀 새 열람실을 열었어요')
        return created
      }
    })().finally(() => {
      enteringRef.current = null
    })
    enteringRef.current = entering
    return entering
  }, [attach, create, toast])

  useEffect(() => {
    enterRef.current = enter
  }, [enter])

  const leave = useCallback(() => {
    clearLastRoom()
    const client = clientRef.current
    clientRef.current = null
    roomIdRef.current = null
    subscriptionsRef.current = []
    seatsRef.current = {}
    settingsRef.current = { matesAlwaysFocus: false, matesStay: false, ownerSeatNo: 0 }
    setState(EMPTY)
    client?.deactivate()
  }, [])

  const changeMateSettings = useCallback(async (mates: MateSettings) => {
    const settings = await api.changeRoomSettings(mates)
    setMateSettings(mates)
    settingsRef.current = settings
    setState((s) => ({ ...s, ...settings }))
  }, [])

  // 방이 사라졌을 때 되살릴 수 있게 지금 방의 코드·구성·설정을 기억한다
  useEffect(() => {
    if (!state.roomId || !state.code || state.mySeatNo === null) return
    setLastRoom({
      code: state.code,
      virtualSeats: state.seatCount - state.realSeatCount,
      realSeats: state.realSeatCount,
      matesAlwaysFocus: state.matesAlwaysFocus,
      matesStay: state.matesStay,
      wasOwner: state.ownerSeatNo === state.mySeatNo,
    })
  }, [state.roomId, state.code, state.seatCount, state.realSeatCount, state.matesAlwaysFocus, state.matesStay, state.ownerSeatNo, state.mySeatNo])

  useEffect(() => () => void clientRef.current?.deactivate(), [])

  const value = useMemo(
    () => ({ ...state, enter, create, join, leave, changeMateSettings }),
    [state, enter, create, join, leave, changeMateSettings],
  )
  return <RoomContext.Provider value={value}>{children}</RoomContext.Provider>
}

/** 알림에 쓸 메이트 설정 요약. 예: "휴식 없음 · 이탈 없음" */
function describeMates({ matesAlwaysFocus, matesStay }: MateSettings): string {
  return `${matesAlwaysFocus ? '휴식 없음' : '휴식 있음'} · ${matesStay ? '이탈 없음' : '가끔 이탈'}`
}

export function useRoom(): RoomApi {
  const room = useContext(RoomContext)
  if (!room) throw new Error('RoomProvider 안에서 써야 합니다.')
  return room
}
