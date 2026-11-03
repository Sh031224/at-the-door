package app.atthedoor.data

import android.content.Context
import app.atthedoor.domain.model.Venue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** assets(= 저장소 루트 data/venues)에 들어 있는 시설 데이터를 읽는다. 첫 구현은 네트워크 없이 동작한다. */
class VenueRepository(private val context: Context) {

    suspend fun list(): List<Venue> = withContext(Dispatchers.IO) {
        context.assets.list("")!!
            .filter { it.endsWith(".json") }
            .sorted()
            .map { load(it) }
    }

    suspend fun get(venueId: String): Venue? = withContext(Dispatchers.IO) {
        runCatching { load("$venueId.json") }.getOrNull()
    }

    private fun load(file: String): Venue =
        context.assets.open(file).bufferedReader().use { VenueJson.parse(it.readText()) }
}
