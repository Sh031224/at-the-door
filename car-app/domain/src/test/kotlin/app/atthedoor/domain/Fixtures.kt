package app.atthedoor.domain

import app.atthedoor.domain.model.AccessPoint
import app.atthedoor.domain.model.Candidate
import app.atthedoor.domain.model.CandidateType
import app.atthedoor.domain.model.Entrance
import app.atthedoor.domain.model.Evidence
import app.atthedoor.domain.model.Fact
import app.atthedoor.domain.model.GeoPoint
import app.atthedoor.domain.model.OpeningHours
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.model.Venue
import app.atthedoor.domain.model.VerificationStatus
import app.atthedoor.domain.model.WalkRoute
import java.time.LocalTime

/** 기획서 ‘첫 번째 구현 단위’의 공연장 1곳·후보 3개(A 정문 승하차, B 후문 주차, C 지하 주차 미확인). */
object Fixtures {
    private val demo = Evidence("ev-1", "데모 현장 조사", "2026-11-01", VerificationStatus.DEMO)
    private val p = GeoPoint(37.5, 127.0)
    val noon: LocalTime = LocalTime.NOON

    val venue = Venue(
        id = "saebom-hall",
        name = "새봄문화회관 소공연장",
        demo = true,
        contact = "02-000-0000",
        entrances = listOf(
            Entrance("E-front", "정문", p, listOf("e-front-1.jpg"), OpeningHours(LocalTime.of(9, 0), LocalTime.of(22, 0)), Fact(true, demo)),
            Entrance("E-back", "후문", p, listOf("e-back-1.jpg"), OpeningHours(LocalTime.of(9, 0), LocalTime.of(22, 0)), Fact(true, demo)),
        ),
        candidates = listOf(
            Candidate(
                id = "A", type = CandidateType.DROPOFF, name = "정문 승하차 구역",
                accessPoint = AccessPoint(p, "정문 진입로"),
                walk = WalkRoute("E-front", Fact(false, demo), Fact(15, demo), listOf("승하차 구역에서 정문으로 이동")),
                leavesPassengerAlone = true, driverParkingId = "B",
            ),
            Candidate(
                id = "B", type = CandidateType.PARKING, name = "후문 일반 주차 구역",
                accessPoint = AccessPoint(p, "후문 주차장 입구"), lotId = "lot-back",
                walk = WalkRoute("E-back", Fact(false, demo), Fact(80, demo), listOf("파란 안내판을 지나 후문으로 이동")),
            ),
            Candidate(
                id = "B2", type = CandidateType.PARKING, name = "후문 주차 구역 → 정문 경사로",
                accessPoint = AccessPoint(p, "후문 주차장 입구"), lotId = "lot-back",
                walk = WalkRoute("E-front", Fact(false, demo), Fact(140, demo), listOf("주차장 앞 보도를 따라 정문 쪽으로", "경사로로 정문에 들어가기")),
            ),
            Candidate(
                id = "E", type = CandidateType.PARKING, name = "인근 공영주차장",
                accessPoint = AccessPoint(p, "공영주차장 입구"),
                walk = WalkRoute("E-front", Fact(false, demo), Fact(220, demo), listOf("공영주차장 출구에서 보도로", "횡단보도를 건너 정문 경사로로")),
            ),
            Candidate(
                id = "C", type = CandidateType.PARKING, name = "지하 주차장",
                accessPoint = AccessPoint(p, "지하 진입로"),
                walk = WalkRoute("E-front", Fact.unknown(), Fact.unknown(), emptyList()),
            ),
        ),
    )

    /** 할머니 자리: 운전자(아들)만 보호자, 함께 이동, 계단 없는 동선 필수 */
    val soloGuardian = TravelConditions(companionRequired = true, otherGuardian = false, stepFreeRequired = true, preferShortWalk = true)
    val withOtherGuardian = soloGuardian.copy(otherGuardian = true)
}
