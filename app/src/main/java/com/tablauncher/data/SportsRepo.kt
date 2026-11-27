package com.tablauncher.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

enum class MatchState { PRE, LIVE, POST }

data class Team(val name: String, val short: String, val logo: String?, val score: String?, val winner: Boolean)

data class Match(
    val id: String,
    val home: Team,
    val away: Team,
    val state: MatchState,
    val detail: String,
    val start: Long,
    val link: String?,
)

data class League(val id: String, val name: String, val sport: String)

/** Live scores from ESPN's public scoreboard feeds. */
class SportsRepo(private val settings: Settings) {
    private val _matches = MutableStateFlow<Map<String, List<Match>>>(emptyMap())
    val matches: StateFlow<Map<String, List<Match>>> = _matches
    private val fetchedAt = mutableMapOf<String, Long>()

    suspend fun refresh(force: Boolean = false) = coroutineScope {
        settings.current.sportsLeagues.map { id -> async { ensure(id, force) } }.awaitAll()
    }

    /** Loads one league's scores if they are missing or old (every minute while a match is live). */
    suspend fun ensure(id: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val live = _matches.value[id]?.any { it.state == MatchState.LIVE } == true
        val maxAge = if (live) 60_000L else 5 * 60_000L
        if (!force && now - (fetchedAt[id] ?: 0L) < maxAge) return
        val list = load(id) ?: return
        fetchedAt[id] = now
        _matches.value = _matches.value + (id to list)
    }

    private suspend fun load(id: String): List<Match>? {
        return if (id == "cricket") {
            val body = Net.get("https://site.web.api.espn.com/apis/v2/scoreboard/header?sport=cricket&lang=en&region=us") ?: return null
            parseHeader(body)
        } else {
            val body = Net.get("https://site.api.espn.com/apis/site/v2/sports/$id/scoreboard?lang=en&region=us") ?: return null
            parseScoreboard(body)
        }
    }

    /** Format of /apis/site/v2/sports/{sport}/{league}/scoreboard. */
    private fun parseScoreboard(body: String): List<Match> = try {
        val events = JSONObject(body).optJSONArray("events") ?: JSONArray()
        (0 until events.length()).mapNotNull { i ->
            val e = events.getJSONObject(i)
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
            val teams = comp.optJSONArray("competitors") ?: return@mapNotNull null
            var home: Team? = null
            var away: Team? = null
            for (j in 0 until teams.length()) {
                val c = teams.getJSONObject(j)
                val t = c.optJSONObject("team") ?: c
                val team = Team(
                    name = t.optString("shortDisplayName", t.optString("displayName", "?")),
                    short = t.optString("abbreviation", t.optString("shortDisplayName", "?")),
                    logo = t.optString("logo").takeIf { it.startsWith("http") },
                    score = c.optString("score").takeIf { it.isNotBlank() },
                    winner = c.optBoolean("winner", false),
                )
                if (c.optString("homeAway") == "away") away = team else if (home == null) home = team else away = team
            }
            val status = e.optJSONObject("status")?.optJSONObject("type") ?: comp.optJSONObject("status")?.optJSONObject("type")
            Match(
                id = e.optString("id"),
                home = home ?: return@mapNotNull null,
                away = away ?: return@mapNotNull null,
                state = state(status?.optString("state")),
                detail = status?.optString("shortDetail").orEmpty(),
                start = parseDate(e.optString("date")),
                link = e.optJSONArray("links")?.optJSONObject(0)?.optString("href"),
            )
        }.sortedWith(matchOrder)
    } catch (e: Exception) {
        emptyList()
    }

    /** Format of /apis/v2/scoreboard/header (used for cricket, which has many leagues). */
    private fun parseHeader(body: String): List<Match> = try {
        val out = mutableListOf<Match>()
        val sports = JSONObject(body).optJSONArray("sports") ?: JSONArray()
        for (s in 0 until sports.length()) {
            val leagues = sports.getJSONObject(s).optJSONArray("leagues") ?: continue
            for (l in 0 until leagues.length()) {
                val league = leagues.getJSONObject(l)
                val events = league.optJSONArray("events") ?: continue
                for (i in 0 until events.length()) {
                    val e = events.getJSONObject(i)
                    val comps = e.optJSONArray("competitors") ?: continue
                    if (comps.length() < 2) continue
                    fun team(j: Int): Team {
                        val c = comps.getJSONObject(j)
                        return Team(
                            name = c.optString("displayName", c.optString("name", "?")),
                            short = c.optString("abbreviation", c.optString("name", "?")),
                            logo = c.optString("logo").takeIf { it.startsWith("http") },
                            score = c.optString("score").takeIf { it.isNotBlank() },
                            winner = c.optBoolean("winner", false),
                        )
                    }
                    val full = e.optJSONObject("fullStatus")?.optJSONObject("type")
                    val stateStr = e.optString("status").ifBlank { full?.optString("state").orEmpty() }
                    val detail = e.optString("summary").ifBlank { full?.optString("shortDetail").orEmpty() }
                    out += Match(
                        id = e.optString("id"),
                        home = team(0),
                        away = team(1),
                        state = state(stateStr),
                        detail = listOf(detail, league.optString("shortName", league.optString("name")))
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        start = parseDate(e.optString("date")),
                        link = e.optString("link").takeIf { it.startsWith("http") },
                    )
                }
            }
        }
        out.sortedWith(matchOrder).take(20)
    } catch (e: Exception) {
        emptyList()
    }

    private fun state(s: String?) = when (s) {
        "in" -> MatchState.LIVE
        "post" -> MatchState.POST
        else -> MatchState.PRE
    }

    private fun parseDate(s: String): Long = try {
        Instant.parse(if (s.length == 17) s.dropLast(1) + ":00Z" else s).toEpochMilli()
    } catch (e: Exception) {
        0L
    }

    private val matchOrder = compareBy<Match>({ when (it.state) { MatchState.LIVE -> 0; MatchState.PRE -> 1; MatchState.POST -> 2 } }, { it.start })

    companion object {
        val leagues = listOf(
            League("soccer/eng.1", "Premier League", "Football"),
            League("soccer/uefa.champions", "Champions League", "Football"),
            League("soccer/esp.1", "LaLiga", "Football"),
            League("soccer/ita.1", "Serie A", "Football"),
            League("soccer/ger.1", "Bundesliga", "Football"),
            League("soccer/fra.1", "Ligue 1", "Football"),
            League("soccer/usa.1", "MLS", "Football"),
            League("soccer/ksa.1", "Saudi Pro League", "Football"),
            League("soccer/fifa.world", "FIFA World Cup", "Football"),
            League("cricket", "Cricket (all matches)", "Cricket"),
            League("basketball/nba", "NBA", "Basketball"),
            League("basketball/wnba", "WNBA", "Basketball"),
            League("football/nfl", "NFL", "American Football"),
            League("baseball/mlb", "MLB", "Baseball"),
            League("hockey/nhl", "NHL", "Ice Hockey"),
        )

        fun league(id: String) = leagues.firstOrNull { it.id == id }

        /** Sport name to the emoji used on its button. */
        val sports = listOf(
            "Football" to "⚽", "Cricket" to "🏏", "Basketball" to "🏀",
            "American Football" to "🏈", "Baseball" to "⚾", "Ice Hockey" to "🏒",
        )
    }
}
