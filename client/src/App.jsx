import { useEffect, useMemo, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import interactionPlugin from '@fullcalendar/interaction'
import { api } from './api'
import './App.css'

function toDateKey(date) {
    const y = date.getFullYear()
    const m = String(date.getMonth() + 1).padStart(2, '0')
    const d = String(date.getDate()).padStart(2, '0')
    return `${y}-${m}-${d}`
}

function toMonthRange(date) {
    const y = date.getFullYear()
    const m = date.getMonth()
    const from = new Date(y, m, 1)
    const to = new Date(y, m + 1, 0)
    return { from: toDateKey(from), to: toDateKey(to) }
}

export default function App() {
    const [currentDate, setCurrentDate] = useState(new Date())
    const [selectedDate, setSelectedDate] = useState(toDateKey(new Date()))

    const [monthEntries, setMonthEntries] = useState([])
    const [dayEntries, setDayEntries] = useState([])

    const [inputText, setInputText] = useState('')
    const [draft, setDraft] = useState(null)

    const [filter, setFilter] = useState('ALL') // ALL | EXPENSE | SCHEDULE | NOTE
    const [groupByType, setGroupByType] = useState(true)

    const filteredDayEntries = useMemo(() => {
        if (filter === 'ALL') return dayEntries
        return dayEntries.filter((e) => e.type === filter)
    }, [dayEntries, filter])

    const dayExpenseSum = useMemo(() => {
        return dayEntries
            .filter((e) => e.type === 'EXPENSE')
            .reduce((acc, e) => acc + Number(e.price ?? 0), 0)
    }, [dayEntries])

    const groupedByType = useMemo(() => {
        const groups = { EXPENSE: [], SCHEDULE: [], NOTE: [] }
        for (const e of filteredDayEntries) {
            if (groups[e.type]) groups[e.type].push(e)
            else groups.NOTE.push(e)
        }
        return groups
    }, [filteredDayEntries])

    const [confirm, setConfirm] = useState({
        type: 'EXPENSE',
        entryDate: toDateKey(new Date()),
        content: '',
        price: 0,
        category: '',
        startDateTime: '',
        endDateTime: '',
        location: '',
        syncToGoogle: false,
    })

    const loadMonth = async (dateObj) => {
        const { from, to } = toMonthRange(dateObj)
        const res = await api.get(`/entries`, { params: { from, to } })
        setMonthEntries(res.data ?? [])
    }

    const loadDay = async (dateKey) => {
        const res = await api.get(`/entries/day`, { params: { date: dateKey } })
        setDayEntries(res.data ?? [])
    }

    useEffect(() => {
        loadMonth(currentDate)
    }, [currentDate])

    useEffect(() => {
        loadDay(selectedDate)
    }, [selectedDate])

    const daySummaryMap = useMemo(() => {
        const map = {}
        for (const e of monthEntries) {
            const key = e.entryDate
            if (!map[key]) map[key] = { scheduleCount: 0, expenseSum: 0, noteCount: 0 }
            if (e.type === 'SCHEDULE') map[key].scheduleCount += 1
            else if (e.type === 'EXPENSE') map[key].expenseSum += Number(e.price ?? 0)
            else map[key].noteCount += 1
        }
        return map
    }, [monthEntries])

    const summaryEvents = useMemo(() => {
        return Object.entries(daySummaryMap).map(([date, s]) => ({
            id: `summary-${date}`,
            start: date,      // 'YYYY-MM-DD'
            allDay: true,
            title: '',        // eventContent로 커스텀 렌더
            extendedProps: { summary: s },
        }))
    }, [daySummaryMap])

    const onDateClick = (info) => {
        setSelectedDate(info.dateStr)
        setConfirm((prev) => ({ ...prev, entryDate: info.dateStr }))
    }

    const onDatesSet = (arg) => {
        setCurrentDate(arg.view.currentStart)
    }

    const handleParse = async () => {
        const text = inputText.trim()
        if (!text) return

        const res = await api.post('/entries/parse', {
            text,
            syncToGoogle: false,
        })

        const d = res.data
        setDraft(d)

        setConfirm((prev) => ({
            ...prev,
            type: d.type ?? 'EXPENSE',
            entryDate: d.entryDate ?? selectedDate,
            content: d.content ?? d.rawContent ?? '',
            price: Number(d.price ?? 0),
            category: d.category ?? '',
            syncToGoogle: Boolean(d.syncToGoogle),
            startDateTime: d.startDateTime ?? '',
            endDateTime: d.endDateTime ?? '',
            location: d.location ?? '',
        }))

        setInputText('')
    }

    const handleConfirm = async () => {
        if (!draft?.id) return

        const payload = {
            type: confirm.type,
            entryDate: confirm.entryDate,
            content: confirm.content,
            syncToGoogle: confirm.syncToGoogle,
        }

        if (confirm.type === 'EXPENSE') {
            payload.price = Number(confirm.price ?? 0)
            payload.category = confirm.category
        } else if (confirm.type === 'SCHEDULE') {
            payload.startDateTime = confirm.startDateTime || null
            payload.endDateTime = confirm.endDateTime || null
            payload.location = confirm.location
        }

        await api.post(`/entries/${draft.id}/confirm`, payload)

        setDraft(null)
        await loadMonth(currentDate)
        await loadDay(confirm.entryDate)
        setSelectedDate(confirm.entryDate)
    }

    return (
        <div className="page">
            <header className="topbar">
                <div className="brand">FlowNote</div>
                <div className="inputRow">
                    <input
                        className="textInput"
                        placeholder='예) "오늘 투썸 5500원" / "다음 주 토요일 한남동 18시 약속"'
                        value={inputText}
                        onChange={(e) => setInputText(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === 'Enter') handleParse()
                        }}
                    />
                    <button className="btn" onClick={handleParse}>분석</button>
                </div>
            </header>

            <main className="main">
                <section className="left">
                    <FullCalendar
                        plugins={[dayGridPlugin, interactionPlugin]}
                        initialView="dayGridMonth"
                        height="auto"
                        dateClick={onDateClick}
                        datesSet={onDatesSet}
                        timeZone="local"
                        events={summaryEvents}
                        eventDisplay="block"
                        eventContent={(arg) => {
                            // 우리 summary 이벤트만 렌더
                            const s = arg.event.extendedProps?.summary
                            if (!s) return null

                            const lines = []
                            if (s.expenseSum > 0) lines.push(`₩-${Number(s.expenseSum).toLocaleString()}`)
                            if (s.scheduleCount > 0) lines.push(`● 일정 ${s.scheduleCount}`)
                            if (s.noteCount > 0) lines.push(`📝 ${s.noteCount}`)

                            return (
                                <div className="cellSummaryEvent">
                                    {lines.map((t, i) => (
                                        <div key={i}>{t}</div>
                                    ))}
                                </div>
                            )
                        }}
                    />
                </section>

                <section className="right">
                    <div className="panelTitle">
                        <div>선택 날짜: <b>{selectedDate}</b></div>
                        <div className="summaryRow">
                            <span>지출 합계: <b>₩-{dayExpenseSum.toLocaleString()}</b></span>
                            <span className="dot">·</span>
                            <span>총 {dayEntries.length}건</span>
                        </div>

                        <div className="filterRow">
                            <button className={`chip ${filter==='ALL' ? 'active' : ''}`} onClick={() => setFilter('ALL')}>전체</button>
                            <button className={`chip ${filter==='EXPENSE' ? 'active' : ''}`} onClick={() => setFilter('EXPENSE')}>지출</button>
                            <button className={`chip ${filter==='SCHEDULE' ? 'active' : ''}`} onClick={() => setFilter('SCHEDULE')}>일정</button>
                            <button className={`chip ${filter==='NOTE' ? 'active' : ''}`} onClick={() => setFilter('NOTE')}>메모</button>

                            <div className="spacer" />
                            <label className="toggle">
                                <input
                                    type="checkbox"
                                    checked={groupByType}
                                    onChange={(e) => setGroupByType(e.target.checked)}
                                />
                                타입별 그룹
                            </label>
                        </div>
                    </div>

                    {draft && (
                        <div className="card">
                            <div className="cardTitle">초안(DRAFT) — 확인 후 저장</div>

                            <div className="formRow">
                                <label>타입</label>
                                <select
                                    value={confirm.type}
                                    onChange={(e) => setConfirm((p) => ({ ...p, type: e.target.value }))}
                                >
                                    <option value="EXPENSE">지출</option>
                                    <option value="SCHEDULE">일정</option>
                                    <option value="NOTE">메모</option>
                                </select>
                            </div>

                            <div className="formRow">
                                <label>날짜</label>
                                <input
                                    type="date"
                                    value={confirm.entryDate}
                                    onChange={(e) => setConfirm((p) => ({ ...p, entryDate: e.target.value }))}
                                />
                            </div>

                            <div className="formRow">
                                <label>내용</label>
                                <input
                                    value={confirm.content}
                                    onChange={(e) => setConfirm((p) => ({ ...p, content: e.target.value }))}
                                />
                            </div>

                            {confirm.type === 'EXPENSE' && (
                                <>
                                    <div className="formRow">
                                        <label>금액</label>
                                        <input
                                            type="number"
                                            value={confirm.price}
                                            onChange={(e) => setConfirm((p) => ({ ...p, price: e.target.value }))}
                                        />
                                    </div>
                                    <div className="formRow">
                                        <label>카테고리</label>
                                        <input
                                            value={confirm.category}
                                            onChange={(e) => setConfirm((p) => ({ ...p, category: e.target.value }))}
                                            placeholder="예: 카페"
                                        />
                                    </div>
                                </>
                            )}

                            {confirm.type === 'SCHEDULE' && (
                                <>
                                    <div className="formRow">
                                        <label>시작</label>
                                        <input
                                            type="datetime-local"
                                            value={confirm.startDateTime}
                                            onChange={(e) => setConfirm((p) => ({ ...p, startDateTime: e.target.value }))}
                                        />
                                    </div>
                                    <div className="formRow">
                                        <label>종료</label>
                                        <input
                                            type="datetime-local"
                                            value={confirm.endDateTime}
                                            onChange={(e) => setConfirm((p) => ({ ...p, endDateTime: e.target.value }))}
                                        />
                                    </div>
                                    <div className="formRow">
                                        <label>장소</label>
                                        <input
                                            value={confirm.location}
                                            onChange={(e) => setConfirm((p) => ({ ...p, location: e.target.value }))}
                                            placeholder="예: 한남동"
                                        />
                                    </div>
                                </>
                            )}

                            <div className="formActions">
                                <button className="btn" onClick={handleConfirm}>확정 저장</button>
                                <button
                                    className="btn secondary"
                                    onClick={async () => {
                                        if (draft?.id) {
                                            await api.delete(`/entries/${draft.id}`)
                                        }
                                        setDraft(null)
                                    }}
                                >
                                    취소
                                </button>
                            </div>

                            <div className="hint">
                                confidence: {draft.confidence} / needsUserConfirm: {String(draft.needsUserConfirm)}
                            </div>
                        </div>
                    )}

                    <div className="card">
                        <div className="cardTitle">해당 날짜 기록</div>
                        {filteredDayEntries.length === 0 ? (
                            <div className="muted">기록이 없습니다.</div>
                        ) : groupByType ? (
                            <div className="groupWrap">
                                {(['EXPENSE','SCHEDULE','NOTE']).map((t) => (
                                    groupedByType[t].length > 0 && (
                                        <div key={t} className="groupSection">
                                            <div className="groupHeader">
                                                <b>{t}</b>
                                                <span className="muted"> {groupedByType[t].length}건</span>
                                                {t === 'EXPENSE' && (
                                                    <span className="muted">
                {' '}· 합계 ₩-{groupedByType[t].reduce((acc, e) => acc + Number(e.price ?? 0), 0).toLocaleString()}
              </span>
                                                )}
                                            </div>

                                            <ul className="list">
                                                {groupedByType[t].map((e) => (
                                                    <li key={e.id} className="listItem">
                                                        <div className="badge">{e.type}</div>
                                                        <div className="itemBody">
                                                            <div className="itemTitle">{e.content}</div>

                                                            {e.type === 'EXPENSE' && (
                                                                <div className="sub">
                                                                    ₩-{Number(e.price ?? 0).toLocaleString()} {e.category ? `· ${e.category}` : '· 미분류'}
                                                                </div>
                                                            )}

                                                            {e.type === 'SCHEDULE' && (
                                                                <div className="sub">
                                                                    {e.startDateTime ? `${e.startDateTime}` : ''} {e.location ? `· ${e.location}` : ''}
                                                                </div>
                                                            )}
                                                        </div>
                                                    </li>
                                                ))}
                                            </ul>
                                        </div>
                                    )
                                ))}
                            </div>
                        ) : (
                            <ul className="list">
                                {filteredDayEntries.map((e) => (
                                    <li key={e.id} className="listItem">
                                        <div className="badge">{e.type}</div>
                                        <div className="itemBody">
                                            <div className="itemTitle">{e.content}</div>

                                            {e.type === 'EXPENSE' && (
                                                <div className="sub">
                                                    ₩-{Number(e.price ?? 0).toLocaleString()} {e.category ? `· ${e.category}` : '· 미분류'}
                                                </div>
                                            )}

                                            {e.type === 'SCHEDULE' && (
                                                <div className="sub">
                                                    {e.startDateTime ? `${e.startDateTime}` : ''} {e.location ? `· ${e.location}` : ''}
                                                </div>
                                            )}
                                        </div>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </div>
                </section>
            </main>
        </div>
    )
}
