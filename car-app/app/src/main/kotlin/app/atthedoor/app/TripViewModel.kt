package app.atthedoor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.plan.Evaluation
import app.atthedoor.domain.plan.VerdictKind
import app.atthedoor.domain.port.NaviResult
import app.atthedoor.domain.port.PhoneEvent
import app.atthedoor.domain.port.ShareLink
import app.atthedoor.domain.port.TripSnapshot
import app.atthedoor.domain.trip.Trip
import app.atthedoor.domain.trip.TripEvent
import app.atthedoor.domain.trip.TripMachine
import app.atthedoor.domain.trip.TripPhase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.UUID

data class TripUiState(
    val venue: Venue? = null,
    val conditions: TravelConditions = TravelConditions(companionRequired = true, otherGuardian = false, stepFreeRequired = true, preferShortWalk = true),
    val evaluation: Evaluation? = null,
    val trip: Trip? = null,
    val naviSearching: Boolean = false,
    val shareLink: ShareLink? = null,
    val shareFailed: Boolean = false,
)

/**
 * 화면 상태와 여행 흐름을 잇는다. 상태 전이는 모두 TripMachine을, 후보 판정은 모두 PlanEvaluator를 거친다.
 * 화면에서 추천 결과를 직접 바꾸지 않는다.
 */
class TripViewModel(private val c: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(TripUiState())
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    val drivingRestricted = c.drivingState.restricted
    val setupBlocked = c.drivingState.setupBlocked
    val isFake = c.isFake

    private var naviJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            val restored = c.tripStore.load()
            val venue = c.venues.get(restored?.venueId ?: DEFAULT_VENUE) ?: c.venues.list().firstOrNull()
            _state.update { it.copy(venue = venue, trip = restored ?: venue?.let { v -> Trip.start(newId(), v.id) }) }
            restored?.conditions?.let { setConditions(it) }
        }
    }

    fun setConditions(conditions: TravelConditions) {
        _state.update { s ->
            s.copy(
                conditions = conditions,
                evaluation = s.venue?.let { c.evaluator.evaluate(it, conditions, LocalTime.now(), s.trip?.unavailableIds.orEmpty()) },
            )
        }
    }

    fun confirmConditions() {
        dispatch(TripEvent.ConditionsConfirmed(_state.value.conditions))
        setConditions(_state.value.conditions)
    }

    fun choosePlan(candidate: Candidate) {
        dispatch(TripEvent.PlanSelected(candidate.id))
        requestNavigation(candidate)
    }

    private fun requestNavigation(candidate: Candidate) {
        naviJob?.cancel()
        naviJob = viewModelScope.launch {
            var guiding = false
            var arrived = false
            c.navi.requestRoute(candidate.accessPoint).collect { r ->
                when (r) {
                    NaviResult.Searching -> _state.update { it.copy(naviSearching = true) }
                    NaviResult.Guiding -> {
                        guiding = true
                        _state.update { it.copy(naviSearching = false) }
                        dispatch(TripEvent.NaviStarted)
                    }
                    is NaviResult.Failed -> failNavi("경로 안내를 시작하지 못했어요 (${r.code})")
                    NaviResult.TimedOut -> if (!guiding) failNavi("내비게이션 응답이 없어요")
                    NaviResult.Arrived -> arrived = true
                    NaviResult.Cancelled, NaviResult.Ended ->
                        if (guiding && !arrived) dispatch(TripEvent.NaviInterrupted("내비 안내가 끝났어요. 다시 안내할까요?"))
                    NaviResult.DestinationChanged -> Unit
                }
            }
        }
    }

    private fun failNavi(reason: String) {
        _state.update { it.copy(naviSearching = false) }
        dispatch(TripEvent.NaviFailed(reason))
    }

    fun confirmParking(note: String?) {
        dispatch(TripEvent.ParkingConfirmed(drivingRestricted = drivingRestricted.value, note = note))
        if (_state.value.trip?.phase == TripPhase.PARKED) {
            c.voice.speak("입구 안내를 휴대폰에서 이어 볼 수 있어요.")
            createShareLink()
        }
    }

    private fun createShareLink() {
        val s = _state.value
        val venue = s.venue ?: return
        val candidate = s.trip?.selectedCandidateId?.let(venue::candidate) ?: return
        val entrance = venue.entrance(candidate.walk.toEntranceId) ?: return
        viewModelScope.launch {
            val link = c.share.createLink(
                TripSnapshot(
                    venueName = venue.name,
                    planName = candidate.name,
                    entranceName = entrance.name,
                    entrancePhotos = entrance.photos,
                    steps = candidate.walk.steps,
                    conditionLabels = conditionLabels(s.conditions),
                    parkingNote = s.trip.parkingNote,
                    evidenceLabel = candidate.walk.stairs.evidence?.let { "${it.source} · ${it.checkedAt}" } ?: "확인 정보 없음",
                ),
            )
            _state.update { it.copy(shareLink = link, shareFailed = link == null) }
            if (link != null) pollPhone(link.token)
        }
    }

    /** 휴대폰에서 누른 도착·이용 불가·복귀를 정차 중에만 10초마다 가져온다. 같은 이벤트 ID는 TripMachine이 한 번만 처리한다. */
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
                            PhoneEvent.EntranceBlocked -> reportUnavailable(e.id)
                            is PhoneEvent.ParkingNote -> dispatch(TripEvent.ParkingNoteSaved(ev.note), e.id)
                        }
                    }
                }
                delay(10_000)
            }
        }
    }

    fun startWalking() = dispatch(TripEvent.WalkStarted)
    fun arrivedAtEntrance() = dispatch(TripEvent.ArrivedAtEntrance)
    fun startReturn() = dispatch(TripEvent.ReturnStarted)

    fun backInCar() {
        dispatch(TripEvent.BackInCar)
        finishShare()
    }

    /** 현재 선택한 후보를 이용할 수 없다고 알림 → 같은 조건으로 재평가 */
    fun reportUnavailable(eventId: String = newId()) {
        val s = _state.value
        val id = s.trip?.selectedCandidateId ?: return
        dispatch(TripEvent.CandidateUnavailable(id), eventId)
        val trip = _state.value.trip?.takeIf { it.phase == TripPhase.REPLANNING } ?: return
        val venue = s.venue ?: return
        val evaluation = c.evaluator.evaluate(venue, trip.conditions ?: s.conditions, LocalTime.now(), trip.unavailableIds)
        _state.update { it.copy(evaluation = evaluation) }
        val alt = evaluation.recommended?.candidate
        if (alt == null) {
            dispatch(TripEvent.NoAlternative)
        } else {
            dispatch(TripEvent.AlternativeChosen(alt.id))
            if (_state.value.trip?.phase == TripPhase.NAVI_REQUESTED) requestNavigation(alt)
        }
    }

    fun cancel() {
        dispatch(TripEvent.Cancel)
        finishShare()
    }

    fun startOver() {
        val venue = _state.value.venue ?: return
        naviJob?.cancel()
        _state.update { TripUiState(venue = venue, conditions = it.conditions, trip = Trip.start(newId(), venue.id)) }
        viewModelScope.launch { c.tripStore.clear() }
    }

    private fun finishShare() {
        pollJob?.cancel()
        val token = _state.value.shareLink?.token ?: return
        viewModelScope.launch { c.share.delete(token) }
        _state.update { it.copy(shareLink = null) }
    }

    private fun dispatch(event: TripEvent, eventId: String = newId()) {
        val current = _state.value.trip ?: return
        val next = TripMachine.reduce(current, event, eventId)
        if (next == current) return
        _state.update { it.copy(trip = next) }
        viewModelScope.launch { c.tripStore.save(next) }
    }

    fun candidatesFor(kind: VerdictKind) = _state.value.evaluation?.of(kind).orEmpty()

    companion object {
        const val DEFAULT_VENUE = "saebom-hall"
        private fun newId() = UUID.randomUUID().toString()

        fun conditionLabels(c: TravelConditions): List<String> = buildList {
            if (c.effectiveStepFreeRequired) add("계단 없는 동선")
            if (c.preferShortWalk) add("보행 거리 짧게")
            if (c.companionRequired) add(if (c.otherGuardian) "다른 보호자와 함께" else "운전자와 함께 이동")
        }
    }
}
