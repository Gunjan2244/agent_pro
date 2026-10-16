package com.sledje.agentpro.feature.website.data

import com.sledje.agentpro.core.catalog.EnquirySource
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.sledje.agentpro.core.data.RoomSyncTable
import com.sledje.agentpro.core.data.SyncTable
import com.sledje.agentpro.core.format.Dates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---- site_daily_views cache (read-only for the client; written by record_site_view) -------------

const val SITE_DAILY_VIEWS_TABLE = "site_daily_views"

/** A `site_daily_views` row as PostgREST returns it (docs/BACKEND.md, Tables). */
@Serializable
data class SiteDailyViewDto(
    val id: String,
    @SerialName("site_id") val siteId: String,
    /** Asia/Kolkata calendar day, `YYYY-MM-DD`. */
    val day: String,
    val views: Int = 0,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Entity(tableName = "site_daily_views")
data class SiteDailyViewEntity(
    @PrimaryKey val id: String,
    val siteId: String,
    val day: String,
    val views: Int,
    val updatedAt: String,
    val deletedAt: String?,
)

@Dao
interface SiteDailyViewDao {
    /** Live rows only (DECISIONS #27), oldest day first. */
    @Query("SELECT * FROM site_daily_views WHERE deletedAt IS NULL ORDER BY day")
    fun observeAll(): Flow<List<SiteDailyViewEntity>>

    @Upsert
    suspend fun upsert(rows: List<SiteDailyViewEntity>)

    @Query("DELETE FROM site_daily_views WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM site_daily_views")
    suspend fun deleteAll()
}

fun SiteDailyViewDto.toEntity() = SiteDailyViewEntity(id, siteId, day, views, updatedAt, deletedAt)

fun siteDailyViewsSyncTable(dao: SiteDailyViewDao): SyncTable = RoomSyncTable(
    name = SITE_DAILY_VIEWS_TABLE,
    serializer = SiteDailyViewDto.serializer(),
    toEntity = { it.toEntity() },
    upsertAll = dao::upsert,
    deleteByIds = dao::deleteByIds,
    deleteAll = dao::deleteAll,
)

// ---- Stats ---------------------------------------------------------------------------------------

/** The Stats screen periods: the last N days including today (Asia/Kolkata). */
enum class StatsPeriod(val days: Int) { Week(7), Month(30) }

/** One day of views. */
data class DayViews(val day: LocalDate, val views: Int)

/** Stats for one period (docs/UX.md, Stats screen: Views, Enquiries, Visit → enquiry %, one views chart). */
data class SiteStats(
    val period: StatsPeriod,
    /** Every day of the period, oldest first, zero-filled. */
    val days: List<DayViews>,
    val views: Int,
    val enquiries: Int,
) {
    /** Website enquiries per 100 views, or null without views (the screen shows "—"). */
    val conversionPercent: Double? get() = if (views == 0) null else enquiries * 100.0 / views
}

/** Pure aggregation for the Stats screen and the hub row; unit-tested. */
object SiteStatsAggregator {
    fun aggregate(
        rows: List<DayViews>,
        enquiryDays: List<LocalDate>,
        period: StatsPeriod,
        today: LocalDate,
    ): SiteStats {
        val first = today.minus(DatePeriod(days = period.days - 1))
        val byDay = rows.filter { it.day in first..today }.groupBy { it.day }.mapValues { (_, v) -> v.sumOf { it.views } }
        val days = (0 until period.days).map { i -> first.plus(DatePeriod(days = i)) }.map { DayViews(it, byDay[it] ?: 0) }
        return SiteStats(
            period = period,
            days = days,
            views = days.sumOf { it.views },
            enquiries = enquiryDays.count { it in first..today },
        )
    }

    fun parseRows(rows: List<SiteDailyViewEntity>): List<DayViews> =
        rows.mapNotNull { row -> runCatching { LocalDate.parse(row.day) }.getOrNull()?.let { DayViews(it, row.views) } }
}

/**
 * The days (Asia/Kolkata) on which website enquiries arrived (`enquiries.source = 'website'`). The
 * Enquiries feature (Phase 6) owns that cache; until it is wired in, [None] counts no enquiries.
 */
fun interface WebsiteEnquirySource {
    fun observeWebsiteEnquiryDays(): Flow<List<LocalDate>>

    companion object {
        val None = WebsiteEnquirySource { flowOf(emptyList()) }

        /** The Enquiries cache: the Asia/Kolkata day of every `source = website` enquiry (AppContainer's wiring). */
        fun from(enquiries: EnquiryRepository) = WebsiteEnquirySource {
            enquiries.observeAll().map { all ->
                all.filter { it.source == EnquirySource.Website }.map { Dates.dateOf(it.createdAt) }
            }
        }
    }
}

/** Site views (from the synced cache) and website enquiries, aggregated per period. */
class SiteStatsRepository(
    private val views: SiteDailyViewDao,
    private val enquiries: WebsiteEnquirySource = WebsiteEnquirySource.None,
    private val today: () -> LocalDate = { Dates.today() },
) {
    fun observe(period: StatsPeriod): Flow<SiteStats> =
        combine(views.observeAll(), enquiries.observeWebsiteEnquiryDays()) { rows, enquiryDays ->
            SiteStatsAggregator.aggregate(SiteStatsAggregator.parseRows(rows), enquiryDays, period, today())
        }

    /** Views in the last 7 days (the hub's Stats row). */
    fun observeWeekViews(): Flow<Int> = observe(StatsPeriod.Week).map { it.views }
}
