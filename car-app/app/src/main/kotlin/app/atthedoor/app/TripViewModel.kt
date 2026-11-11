package app.atthedoor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.Entrance
import app.atthedoor.domain.model.Josa
import app.atthedoor.domain.model.PhotoKind
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.plan.ConditionHint
import app.atthedoor.domain.plan.ConditionParser
import app.atthedoor.domain.plan.Evaluation
import app.atthedoor.domain.port.NaviResult
import app.atthedoor.domain.port.PhoneEvent
import app.atthedoor.domain.port.ShareLink
import app.atthedoor.domain.port.TripSnapshot
import app.atthedoor.domain.trip.FeedbackKind
import app.atthedoor.domain.trip.HandoffWatcher
import app.atthedoor.domain.trip.HandoffCue
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripEvent
import app.atthedoor.domain.trip.TripMachine
import app.atthedoor.domain.trip.TripPhase
import app.atthedoor.domain.trip.VehicleSignals
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

/** 출발 전 계획에서 고르는 도착 예정 시각. 운영·출입 시간 판정에 쓴다. */
enum class Arrival(val label: String, val minutes: Long) {
    NOW("지금", 0), IN_30("30분 후", 30), IN_60("1시간 후", 60), IN_120("2시간 후", 120);

    fun time(now: LocalTime = LocalTime.now()): LocalTime = now.plusMinutes(minutes)
}

enum class NaviStatus { IDLE, SENDING, SEARCHING, GUIDING }

sealed interface ShareState {
    data object None : ShareState
    data object Creating : ShareState
    data class Ready(val link: ShareLink) : ShareState
    /** 서버에 연결하지 못함. 차량 화면 안내로 대체한다. */
    data object Failed : ShareState
    /** 사용자가 휴대폰 연결을 건너뜀 */
    data object Skipped : ShareState
}

data class VoiceState(
    val listening: Boolean = false,
    val partial: String? = null,
    val heard: String? = null,
    val recognized: List<String> = emptyList(),
    val error: String? = null,
)

data class TripUiState(
    val loading: Boolean = true,
    val venues: List<Venue> = emptyList(),
    val venue: Venue? = null,
    /** 계획 작성 중 화면에서 고치는 조건. 확인을 눌러야 여행에 들어간다. */
    val draft: TravelConditions = TravelConditions.DEFAULT,
    val arrival: Arrival = Arrival.NOW,
    val evaluation: Evaluation? = null,
    val trip: Trip? = null,
    val navi: NaviStatus = NaviStatus.IDLE,
    val share: ShareState = ShareState.None,
    val voice: VoiceState = VoiceState(),
    /** 주행 중 재계획으로 계획이 바뀌었을 때 잠시 보여 줄 문구. 음성과 함께 화면에서도 바뀐 것을 알 수 있게 한다. */
    val replanned: String? = null,
) {
    val selected: Candidate? get() = trip?.selectedCandidateId?.let { venue?.candidate(it) }
    val selectedEntrance: Entrance? get() = selected?.let { venue?.entrance(it.walk.toEntranceId) }
}

/**
 * 화면 상태와 여행 흐름을 잇는다. 상태 전이는 모두 TripMachine을, 후보 판정은 모두 PlanEvaluator·Replanner를 거친다.
 * 화면에서 추천 결과를 직접 바꾸지 않는다.
 */
class TripViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    val drivingRestricted = c.drivingState.restricted
    val setupBlocked = c.drivingState.setupBlocked
    val isFake = c.isFake
    val demo = c.demo

    private val handoffWatcher = HandoffWatcher()

    /** 차량 신호로 고른 ‘주차 확인’ 권유. 신호만으로 주차를 확정하지 않는다. */
    val handoffCue: StateFlow<HandoffCue?> = combine(
        _state.map { it.trip?.phase }.distinctUntilChanged(),
        drivingRestricted,
        combine(c.vehicle.gear, c.vehicle.frontPassengerBuckled, c.vehicle.frontPassengerDoorOpen) { g, b, d -> VehicleSignals(g, b, d) },
    ) { phase, restricted, signals -> phase?.let { handoffWatcher.observe(it, restricted, signals) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var naviJob: Job? = null
    private var pollJob: Job? = null
    private var voiceJob: Job? = null
    private var spokenCueTripId: String? = null

    init {
        c.start()
        viewModelScope.launch {
            val venues = c.venues.list()
            val restored = c.tripStore.load()?.let { TripMachine.reduce(it, TripEvent.Resumed, newId()) }
            val venue = restored?.venueId?.let { id -> venues.firstOrNull { it.id == id } } ?: venues.firstOrNull { it.id == DEFAULT_VENUE } ?: venues.firstOrNull()
            val trip = restored?.takeIf { venue != null && it.venueId == venue.id && !it.phase.finished } ?: venue?.let { Trip.start(newId(), it.id) }
            _state.update { it.copy(loading = false, venues = venues, venue = venue, trip = trip, draft = trip?.conditions ?: TravelConditions.DEFAULT) }
            trip?.let { c.tripStore.save(it) }
            reevaluate()
            if (trip != null && trip.phase in HANDOFF_PHASES) createShareLink()
        }
        viewModelScope.launch {
            handoffCue.collect { cue ->
                val tripId = _state.value.trip?.id
                if (cue != null && tripId != null && spokenCueTripId != tripId) {
                    spokenCueTripId = tripId
                    c.voice.speak("주차했으면 ‘주차했어요’를 눌러 주세요. 입구 안내를 휴대폰으로 이어 볼 수 있어요.")
                }
            }
        }
    }

    // ── S01·S02 목적지와 이동 조건 ─────────────────────────────

    fun selectVenue(venueId: String) {
        val venue = _state.value.venues.firstOrNull { it.id == venueId } ?: return
        dispatch(TripEvent.VenueSelected(venueId))
        _state.update { it.copy(venue = venue) }
        reevaluate()
    }

    fun updateDraft(conditions: TravelConditions) {
        _state.update { it.copy(draft = conditions) }
        reevaluate()
    }

    fun setArrival(arrival: Arrival) {
        _state.update { it.copy(arrival = arrival) }
        reevaluate()
    }

    fun confirmConditions() {
        dispatch(TripEvent.ConditionsConfirmed(_state.value.draft))
        reevaluate()
    }

    fun editConditions() {
        dispatch(TripEvent.EditConditions)
        _state.update { it.copy(replanned = null) }
    }

    /** 후보 비교에서 ‘이렇게 바꾸면 후보가 돼요’를 눌렀을 때. 조건 화면을 거치지 않고 바꾼 조건으로 다시 평가한다. */
    fun applyHint(hint: ConditionHint) {
        val base = _state.value.trip?.conditions ?: _state.value.draft
        val next = when (hint) {
            ConditionHint.ADD_OTHER_GUARDIAN -> base.copy(companionRequired = true, otherGuardian = true)
            ConditionHint.CONFIRM_PERMIT -> base.copy(hasDisabledParkingPermit = true)
        }
        editConditions()
        updateDraft(next)
        confirmConditions()
        showReplanned(
            when (hint) {
                ConditionHint.ADD_OTHER_GUARDIAN -> "‘다른 보호자와 함께’로 바꿔 다시 평가했어요"
                ConditionHint.CONFIRM_PERMIT -> "‘장애인 주차 자격 확인함’으로 바꿔 다시 평가했어요"
            },
        )
    }

    /** SpeechToText로 받은 발화를 키워드 규칙으로 조건 초안에 반영한다. 실패하면 기존 조건을 건드리지 않는다. */
    fun startListening() {
        if (_state.value.voice.listening) return stopListening()
        voiceJob?.cancel()
        _state.update { it.copy(voice = VoiceState(listening = true)) }
        voiceJob = viewModelScope.launch {
            var finalText: String? = null
            runCatching {
                c.speech.listen().collect { r ->
                    _state.update { it.copy(voice = it.voice.copy(partial = r.text)) }
                    if (r.completed) finalText = r.text
                }
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(voice = VoiceState(error = "음성을 알아듣지 못했어요. 화면에서 골라 주세요.")) }
                return@launch
            }
            val text = finalText ?: _state.value.voice.partial
            if (text.isNullOrBlank()) {
                _state.update { it.copy(voice = VoiceState(error = "들린 말이 없어요. 다시 말하거나 화면에서 골라 주세요.")) }
                return@launch
            }
            applyUtterance(text)
        }
    }

    fun micDenied() {
        _state.update { it.copy(voice = VoiceState(error = "마이크 권한이 없어 음성으로 정할 수 없어요. 화면에서 골라 주세요.")) }
    }

    fun stopListening() {
        voiceJob?.cancel()
        c.speech.stop()
        _state.update { it.copy(voice = it.voice.copy(listening = false)) }
    }

    private fun applyUtterance(text: String) {
        val s = _state.value
        val parsed = ConditionParser.parse(text, s.draft, s.venues)
        if (!parsed.understoodAnything) {
            _state.update { it.copy(voice = VoiceState(heard = text, error = "조건을 알아듣지 못했어요. 화면에서 골라 주세요.")) }
            return
        }
        parsed.venueId?.let { if (it != s.venue?.id) selectVenue(it) }
        _state.update { it.copy(draft = parsed.conditions, voice = VoiceState(heard = text, recognized = parsed.recognized)) }
        reevaluate()
        if (parsed.conditions.companionRequired && !parsed.recognized.any { "함께" in it || "동행" in it }) {
            c.voice.speak("차에서 내려 입구까지 함께 이동해야 하나요?")
        }
    }

    // ── S03·S04 계획 선택과 내비 연결 ─────────────────────────

    fun choosePlan(candidate: Candidate) {
        dispatch(TripEvent.PlanSelected(candidate.id))
        requestNavigation(candidate)
    }

    /** 내비 실패·취소 뒤 같은 계획으로 다시 안내 */
    fun retryNavigation() {
        _state.value.selected?.let(::choosePlan)
    }

    /** 내비 요청 중 사용자가 비교 화면으로 돌아감. 실패와 같게 처리해 선택은 유지한다. */
    fun cancelNavigation() {
        naviJob?.cancel()
        c.navi.cancel()
        failNavi("내비 요청을 멈췄어요. 계획은 그대로 두었어요.")
    }

    /** 차량 접근 중 같은 계획으로 내비를 다시 요청한다. */
    fun retryNavigationFromApproach() {
        val plan = _state.value.selected ?: return
        dispatch(TripEvent.NaviInterrupted("같은 계획으로 다시 안내할게요."))
        choosePlan(plan)
    }

    private fun requestNavigation(candidate: Candidate) {
        naviJob?.cancel()
        _state.update { it.copy(navi = NaviStatus.SENDING) }
        naviJob = viewModelScope.launch {
            var guiding = false
            var arrived = false
            c.navi.requestRoute(candidate.accessPoint).collect { r ->
                when (r) {
                    NaviResult.Searching -> _state.update { it.copy(navi = NaviStatus.SEARCHING) }
                    NaviResult.Guiding -> {
                        guiding = true
                        _state.update { it.copy(navi = NaviStatus.GUIDING) }
                        dispatch(TripEvent.NaviStarted)
                    }
                    is NaviResult.Failed -> if (!guiding) failNavi("경로 안내를 시작하지 못했어요. 다시 안내를 눌러 주세요. (${r.code})")
                    NaviResult.TimedOut -> if (!guiding) failNavi("내비게이션 응답이 없어요. 계획은 그대로 두었어요.")
                    NaviResult.Arrived -> arrived = true
                    NaviResult.Cancelled, NaviResult.Ended -> if (guiding && !arrived) {
                        _state.update { it.copy(navi = NaviStatus.IDLE) }
                        dispatch(TripEvent.NaviInterrupted("내비 안내가 중간에 끝났어요. 같은 계획으로 다시 안내할까요?"))
                        naviJob?.cancel()
                    }
                    NaviResult.DestinationChanged -> Unit
                }
            }
        }
    }

    private fun failNavi(reason: String) {
        _state.update { it.copy(navi = NaviStatus.IDLE) }
        dispatch(TripEvent.NaviFailed(reason))
        naviJob?.cancel()
    }

    /** 차량 접근 중 ‘주차장이 꽉 찼어요’. 주행 중에도 한 번 누르면 되고, 결과는 음성으로 알린다. */
    fun reportParkingFull() {
        val id = _state.value.trip?.selectedCandidateId ?: return
        dispatch(TripEvent.CandidateUnavailable(id))
        afterReplanStarted()
    }

    /** 걷는 중 ‘이 입구를 이용할 수 없어요’. 차량 화면이나 휴대폰에서 알린다. */
    fun reportEntranceBlocked(eventId: String = newId()) {
        val entranceId = _state.value.selected?.walk?.toEntranceId ?: return
        dispatch(TripEvent.EntranceBlocked(entranceId), eventId)
        afterReplanStarted()
    }

    /** 여행 중 동행 조건 변경. 계획 전체를 다시 평가한다. */
    fun changeConditions(conditions: TravelConditions) {
        _state.update { it.copy(draft = conditions) }
        dispatch(TripEvent.ConditionsChanged(conditions))
        afterReplanStarted()
    }

    private fun afterReplanStarted() {
        val s = _state.value
        val beforeEntrance = s.selectedEntrance?.name
        val trip = s.trip?.takeIf { it.phase == TripPhase.REPLANNING } ?: return
        val venue = s.venue ?: return
        val outcome = c.replanner.replan(venue, trip, evaluationTime(trip.resumePhase))
        _state.update { it.copy(evaluation = outcome.evaluation) }
        dispatch(outcome.event)
        val after = _state.value
        when (after.trip?.phase) {
            TripPhase.NAVI_REQUESTED -> after.selected?.let {
                val walk = it.walk.distanceM.takeIf { d -> d.known }?.let { d -> " 주차 후 입구까지 약 ${d.value}미터예요." }.orEmpty()
                c.voice.speak("${Josa.euroRo(it.name)} 바꿨어요.$walk 내비게이션을 다시 안내할게요.")
                showReplanned("${Josa.euroRo(it.name)} 바꿨어요")
                requestNavigation(it)
            }
            TripPhase.WALKING, TripPhase.RETURNING -> {
                val next = after.selectedEntrance?.name ?: "다른 입구"
                val why = beforeEntrance?.takeIf { it != next }?.let { "${Josa.eulReul(it)} 이용할 수 없어 " }.orEmpty()
                c.voice.speak("${why}같은 주차 구역에서 ${Josa.euroRo(next)} 가는 길로 바꿨어요.")
                showReplanned("${why}${Josa.euroRo(next)} 가는 길로 바꿨어요")
                pushShareUpdate()
            }
            TripPhase.NEEDS_CHECK -> {
                // 직전 재계획의 성공 문구가 남아 ‘대안 없음’과 함께 보이지 않게 지운다.
                replannedJob?.cancel()
                _state.update { it.copy(replanned = null) }
                c.voice.speak("확인된 대안이 없어요. 시설에 문의해 주세요.")
            }
            TripPhase.CHOOSING -> {
                naviJob?.cancel()
                _state.update { it.copy(navi = NaviStatus.IDLE) }
            }
            else -> Unit
        }
    }

    private var replannedJob: Job? = null

    private fun showReplanned(text: String) {
        replannedJob?.cancel()
        _state.update { it.copy(replanned = text) }
        replannedJob = viewModelScope.launch {
            delay(15_000)
            _state.update { it.copy(replanned = null) }
        }
    }

    // ── S05~S08 주차 확인, 휴대폰 안내, 방문, 복귀 ──────────

    fun confirmParking(note: String?) {
        if (drivingRestricted.value) return
        viewModelScope.launch {
            val location = c.location.current()
            dispatch(TripEvent.ParkingConfirmed(drivingRestricted = drivingRestricted.value, note = note, location = location))
            if (_state.value.trip?.phase == TripPhase.PARKED) {
                c.voice.speak("입구 안내를 휴대폰에서 이어 볼 수 있어요.")
                createShareLink()
            }
        }
    }

    fun saveParkingNote(note: String) {
        dispatch(TripEvent.ParkingNoteSaved(note))
        pushShareUpdate()
    }

    fun skipPhone() {
        _state.update { it.copy(share = ShareState.Skipped) }
    }

    fun retryShare() = createShareLink()

    fun startWalking() = dispatch(TripEvent.WalkStarted)
    fun arrivedAtEntrance() = dispatch(TripEvent.ArrivedAtEntrance)
    fun undoArrival() = dispatch(TripEvent.ArrivalUndone)
    fun startReturn() = dispatch(TripEvent.ReturnStarted)

    fun backInCar() {
        dispatch(TripEvent.BackInCar)
        finishShare()
    }

    fun giveFeedback(kinds: Set<FeedbackKind>) = dispatch(TripEvent.FeedbackGiven(kinds))

    fun cancel() {
        dispatch(TripEvent.Cancel)
        naviJob?.cancel()
        finishShare()
    }

    fun startOver() {
        val s = _state.value
        val venue = s.venue ?: return
        naviJob?.cancel()
        finishShare()
        val trip = Trip.start(newId(), venue.id)
        _state.update { it.copy(trip = trip, navi = NaviStatus.IDLE, share = ShareState.None, voice = VoiceState(), arrival = Arrival.NOW) }
        viewModelScope.launch { c.tripStore.save(trip) }
        reevaluate()
    }

    private fun createShareLink() {
        val snapshot = snapshot() ?: return
        _state.update { it.copy(share = ShareState.Creating) }
        viewModelScope.launch {
            val link = c.share.createLink(snapshot)
            _state.update { it.copy(share = if (link != null) ShareState.Ready(link) else ShareState.Failed) }
            if (link != null) pollPhone(link.token)
        }
    }

    private fun pushShareUpdate() {
        val link = (_state.value.share as? ShareState.Ready)?.link ?: return
        val snapshot = snapshot() ?: return
        viewModelScope.launch { c.share.update(link.token, snapshot) }
    }

    private fun snapshot(): TripSnapshot? {
        val s = _state.value
        val venue = s.venue ?: return null
        val candidate = s.selected ?: return null
        val entrance = s.selectedEntrance ?: return null
        return TripSnapshot(
            venueName = venue.name,
            planName = candidate.name,
            entranceName = entrance.name,
            entrancePhotos = entrance.photos,
            photosAreIllustrations = entrance.photoKind == PhotoKind.ILLUSTRATION,
            steps = candidate.walk.steps,
            returnSteps = candidate.walk.returnSteps,
            parkingNote = s.trip?.parkingNote,
            evidenceLabel = (candidate.walk.stairs.evidence ?: candidate.walk.distanceM.evidence)?.label ?: "확인 정보 없음",
            demo = venue.demo,
            contact = venue.contact,
        )
    }

    /** 휴대폰에서 누른 도착·입구 폐쇄·복귀·주차 메모를 정차 중에 5초마다 가져온다. 같은 이벤트 ID는 TripMachine이 한 번만 처리한다. */
    private fun pollPhone(token: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (!drivingRestricted.value) {
                    c.share.pollEvents(token).forEach { e ->
                        when (val ev = e.event) {
                            PhoneEvent.Arrived -> {
                                dispatch(TripEvent.WalkStarted, "${e.id}-walk")
                                dispatch(TripEvent.ArrivedAtEntrance, e.id)
                            }
                            PhoneEvent.Returning -> dispatch(TripEvent.ReturnStarted, e.id)
                            PhoneEvent.ArrivalUndone -> dispatch(TripEvent.ArrivalUndone, e.id)
                            PhoneEvent.EntranceBlocked -> if (e.id !in _state.value.trip?.processedEventIds.orEmpty()) reportEntranceBlocked(e.id)
                            is PhoneEvent.ParkingNote -> dispatch(TripEvent.ParkingNoteSaved(ev.note), e.id)
                        }
                    }
                }
                delay(5_000)
            }
        }
    }

    private fun finishShare() {
        pollJob?.cancel()
        val token = (_state.value.share as? ShareState.Ready)?.link?.token
        _state.update { it.copy(share = ShareState.None) }
        if (token != null) viewModelScope.launch { c.share.delete(token) }
    }

    // ── 공통 ──────────────────────────────────────────────

    /** 출발 전(계획·선택 단계)에는 도착 예정 시각으로, 이미 출발한 뒤에는 지금 시각으로 판정한다. */
    private fun evaluationTime(phase: TripPhase?): LocalTime =
        if (phase == null || phase == TripPhase.PLANNING || phase == TripPhase.CHOOSING) _state.value.arrival.time() else LocalTime.now()

    private fun reevaluate() {
        _state.update { s ->
            val conditions = if (s.trip?.phase == TripPhase.PLANNING || s.trip == null) s.draft else s.trip.conditions ?: s.draft
            s.copy(evaluation = s.venue?.let { c.evaluator.evaluate(it, conditions, evaluationTime(s.trip?.phase), s.trip?.unavailable ?: app.atthedoor.domain.plan.Unavailable()) })
        }
    }

    private fun dispatch(event: TripEvent, eventId: String = newId()) {
        val current = _state.value.trip ?: return
        val next = TripMachine.reduce(current, event, eventId)
        if (next == current) return
        _state.update { it.copy(trip = next) }
        viewModelScope.launch { c.tripStore.save(next) }
    }

    override fun onCleared() {
        c.stop()
    }

    companion object {
        const val DEFAULT_VENUE = "saebom-hall"
        private val HANDOFF_PHASES = setOf(TripPhase.PARKED, TripPhase.WALKING, TripPhase.VISITING, TripPhase.RETURNING)
        private fun newId() = UUID.randomUUID().toString()
    }
}
