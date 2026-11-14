package app.atthedoor.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.atthedoor.app.ui.components.*
import app.atthedoor.app.ui.screens.ArrivalPlanBody
import app.atthedoor.app.ui.theme.Atd
import app.atthedoor.app.ui.theme.AtTheDoorTheme
import app.atthedoor.data.VenueJson
import app.atthedoor.domain.model.TravelConditions
import app.atthedoor.domain.plan.PlanEvaluator
import app.atthedoor.domain.trip.*
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

/** Pleos 앱 창 크기로 실제 Compose 컴포넌트를 렌더링한다. SDK 동작 시연과는 구별한다. */
class ArrivalPlanScreenshotTest {
    @get:Rule val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(screenWidth = 1682, screenHeight = 1210, xdpi = 160, ydpi = 160,
            density = Density.MEDIUM, orientation = ScreenOrientation.LANDSCAPE, locale = "ko", softButtons = false),
        theme = "android:Theme.Material.Light.NoActionBar",
        useDeviceResolution = true,
    )

    @Test fun confirmedPlan() {
        val venue = paparazzi.context.assets.open("saebom-hall.json").bufferedReader().use { VenueJson.parse(it.readText()) }
        val conditions = TravelConditions.DEFAULT
        val trip = TripMachine.reduce(Trip.start("screenshot", venue.id), TripEvent.ConditionsConfirmed(conditions), "confirmed")
        val state = TripUiState(loading = false, venues = listOf(venue), venue = venue, draft = conditions,
            trip = trip, evaluation = PlanEvaluator().evaluate(venue, conditions, LocalTime.NOON))
        paparazzi.snapshot {
            AtTheDoorTheme(darkTheme = false) {
                Column(Modifier.fillMaxSize().background(Atd.colors.bg)) {
                    TopBar(venue.name, Stage.PREPARE, venue.demo, {}, {})
                    Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 36.dp)) {
                        ArrivalPlanBody(state, {}, {})
                    }
                    ActionBar(Segment("후문 주차장으로 안내", {}, SegmentKind.PRIMARY, Glyph.Navigate), Segment("다른 계획 보기", {}))
                }
            }
        }
    }
}
