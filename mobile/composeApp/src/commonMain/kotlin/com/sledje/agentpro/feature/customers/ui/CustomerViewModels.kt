package com.sledje.agentpro.feature.customers.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sledje.agentpro.core.catalog.CatalogEntry
import com.sledje.agentpro.core.catalog.DueBucket
import com.sledje.agentpro.core.catalog.Interest
import com.sledje.agentpro.core.catalog.PolicyStatus
import com.sledje.agentpro.core.data.AppError
import com.sledje.agentpro.core.data.KeyValueStore
import com.sledje.agentpro.core.data.Result
import com.sledje.agentpro.core.format.Phone
import com.sledje.agentpro.core.forms.FieldSpec
import com.sledje.agentpro.core.forms.FieldType
import com.sledje.agentpro.core.forms.FormState
import com.sledje.agentpro.core.ui.UiState
import com.sledje.agentpro.domain.DueBuckets
import com.sledje.agentpro.feature.customers.data.Customer
import com.sledje.agentpro.feature.customers.data.CustomerInput
import com.sledje.agentpro.feature.customers.data.CustomerRepository
import com.sledje.agentpro.feature.enquiries.data.Enquiry
import com.sledje.agentpro.feature.enquiries.data.EnquiryRepository
import com.sledje.agentpro.feature.enquiries.ui.SaveState
import com.sledje.agentpro.feature.policies.data.Policy
import com.sledje.agentpro.feature.policies.data.PolicyRepository
import com.sledje.agentpro.feature.policies.data.PremiumPayment
import com.sledje.agentpro.resources.Res
import com.sledje.agentpro.resources.auth_email
import com.sledje.agentpro.resources.cat_field_city
import com.sledje.agentpro.resources.cat_field_name
import com.sledje.agentpro.resources.crm_notes
import com.sledje.agentpro.resources.customer_address
import com.sledje.agentpro.resources.customer_dob
import com.sledje.agentpro.resources.customer_nominee
import com.sledje.agentpro.resources.customers_filter_due_month
import com.sledje.agentpro.resources.customers_filter_no_policy
import com.sledje.agentpro.resources.profile_mobile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Instant

/**
 * The Customers list filters (docs/UX.md: All · Overdue · Due this month · No policy). They are UI
 * filters over catalog `dueBuckets`, not stored values, so they live here; "Overdue" uses the catalog
 * bucket's own label. "Due this month" means a premium due in the next 30 days (the `this_week` and
 * `this_month` buckets together).
 */
enum class CustomerFilter(override val key: String, override val label: StringResource, override val order: Int) : CatalogEntry {
    Overdue("overdue", DueBucket.Overdue.label, 1),
    DueThisMonth("due_month", Res.string.customers_filter_due_month, 2),
    NoPolicy("no_policy", Res.string.customers_filter_no_policy, 3),
    ;

    override val deprecated: Boolean get() = false
}

/** One customer row: "2 policies · ₹45,000/yr" and the earliest due date of their active policies. */
data class CustomerRow(
    val customer: Customer,
    val policyCount: Int,
    val annualPremium: Double,
    val nextDue: LocalDate?,
)

data class CustomerListData(val rows: List<CustomerRow>, val counts: Map<CustomerFilter, Int>, val total: Int)

/** Customers list (docs/UX.md, Customers tab): search, filter chips with counts, rows by name. */
class CustomerListViewModel(
    customers: CustomerRepository,
    policies: PolicyRepository,
    private val store: KeyValueStore,
    private val today: () -> LocalDate,
) : ViewModel() {
    private val _filter = MutableStateFlow<CustomerFilter?>(null)
    val filter: StateFlow<CustomerFilter?> = _filter.asStateFlow()
    private val query = MutableStateFlow("")

    val state: StateFlow<UiState<CustomerListData>> = combine(
        customers.observeAll(),
        policies.observeAll(),
        _filter,
        query,
    ) { all, allPolicies, filter, q ->
        if (all.isEmpty()) {
            UiState.Empty
        } else {
            val rows = rows(all, allPolicies)
            val day = today()
            val counts = CustomerFilter.entries.associateWith { f -> rows.count { matches(it, f, day) } }
            UiState.Content(
                CustomerListData(rows.filter { (filter == null || matches(it, filter, day)) && search(it, q) }, counts, rows.size),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState.Loading)

    init {
        viewModelScope.launch { store.get(FILTER_KEY)?.let { k -> _filter.value = CustomerFilter.entries.firstOrNull { it.key == k } } }
    }

    fun setFilter(f: CustomerFilter?) {
        _filter.value = f
        viewModelScope.launch { store.put(FILTER_KEY, f?.key) }
    }

    fun setQuery(q: String) { query.value = q }

    companion object {
        const val FILTER_KEY = "customers.filter"

        fun rows(customers: List<Customer>, policies: List<Policy>): List<CustomerRow> {
            val byCustomer = policies.groupBy { it.customerId }
            return customers.map { c ->
                val own = byCustomer[c.id].orEmpty()
                val active = own.filter { it.status == PolicyStatus.Active }
                CustomerRow(c, own.size, active.sumOf { it.annualPremium }, active.mapNotNull { it.nextDueDate }.minOrNull())
            }
        }

        fun matches(row: CustomerRow, filter: CustomerFilter, today: LocalDate): Boolean {
            val bucket = row.nextDue?.let { DueBuckets.bucketOf(it, today) }
            return when (filter) {
                CustomerFilter.Overdue -> bucket == DueBucket.Overdue
                CustomerFilter.DueThisMonth -> bucket == DueBucket.ThisWeek || bucket == DueBucket.ThisMonth
                CustomerFilter.NoPolicy -> row.policyCount == 0
            }
        }

        fun search(row: CustomerRow, q: String): Boolean {
            val t = q.trim()
            if (t.isEmpty()) return true
            val c = row.customer
            val digits = t.filter { it.isDigit() }
            return c.name.contains(t, ignoreCase = true) || c.city?.contains(t, ignoreCase = true) == true ||
                (digits.length >= 3 && c.phone?.filter { it.isDigit() }?.contains(digits) == true)
        }
    }
}

/** One line of the Activity tab (docs/UX.md: "a timeline of the source enquiry and payments"). */
sealed interface ActivityItem {
    val date: LocalDate

    data class FromEnquiry(override val date: LocalDate, val interest: Interest?) : ActivityItem
    data class Payment(override val date: LocalDate, val amount: Double, val planName: String?) : ActivityItem
    data class Added(override val date: LocalDate) : ActivityItem
}

data class CustomerDetail(
    val customer: Customer,
    val policies: List<Policy>,
    val activity: List<ActivityItem>,
)

sealed interface CustomerDetailState {
    data object Loading : CustomerDetailState
    data object Gone : CustomerDetailState
    data class Shown(val detail: CustomerDetail) : CustomerDetailState
}

/** Customer detail (docs/UX.md): header, Policies · Details · Activity, and Delete with a confirm dialog. */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerDetailViewModel(
    val id: String,
    private val customers: CustomerRepository,
    policies: PolicyRepository,
    enquiries: EnquiryRepository,
    private val dateOf: (Instant) -> LocalDate,
) : ViewModel() {
    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()
    private val _error = MutableStateFlow<AppError?>(null)
    val error: StateFlow<AppError?> = _error.asStateFlow()
    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val customer = customers.observe(id)
    private val source = customer.flatMapLatest { c -> c?.sourceEnquiryId?.let { enquiries.observe(it) } ?: flowOf(null) }

    val state: StateFlow<CustomerDetailState> = combine(
        customer,
        policies.observeForCustomer(id),
        policies.observeAllPayments(),
        source,
    ) { c, own, payments, enquiry ->
        if (c == null) CustomerDetailState.Gone else CustomerDetailState.Shown(CustomerDetail(c, own, activity(c, own, payments, enquiry)))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CustomerDetailState.Loading)

    fun delete() {
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            when (val r = customers.delete(id)) {
                is Result.Ok -> _deleted.value = true
                is Result.Err -> _error.value = r.error
            }
            _deleting.value = false
        }
    }

    fun errorShown() { _error.value = null }

    private fun activity(c: Customer, own: List<Policy>, payments: List<PremiumPayment>, enquiry: Enquiry?): List<ActivityItem> {
        val plans = own.associate { it.id to it.planName }
        val items = mutableListOf<ActivityItem>()
        payments.filter { it.policyId in plans }.forEach { items += ActivityItem.Payment(it.paidOn, it.amount, plans[it.policyId]) }
        enquiry?.let { items += ActivityItem.FromEnquiry(dateOf(it.createdAt), it.interest) }
        items += ActivityItem.Added(dateOf(c.createdAt))
        // Newest first; on the same day the customer being added sits below the payments, the enquiry below that.
        return items.sortedWith(compareByDescending<ActivityItem> { it.date }.thenBy { rank(it) })
    }

    private fun rank(i: ActivityItem) = when (i) {
        is ActivityItem.Payment -> 0
        is ActivityItem.Added -> 1
        is ActivityItem.FromEnquiry -> 2
    }
}

/** How the customer form was opened. */
sealed interface CustomerFormMode {
    /** "Add manually" or "Pick from contacts" (prefilled [name] / [phone]). */
    data class Add(val name: String? = null, val phone: String? = null) : CustomerFormMode
    data class Edit(val id: String) : CustomerFormMode

    /** Enquiry detail → Convert to customer. */
    data class Convert(val enquiryId: String) : CustomerFormMode
}

/**
 * The customer form (docs/UX.md, Customer form: Name* and mobile* always shown; email, DOB, address,
 * city, nominee and notes under More details). Convert prefills from the enquiry and links it.
 */
class CustomerFormViewModel(
    val mode: CustomerFormMode,
    private val customers: CustomerRepository,
    private val enquiries: EnquiryRepository,
) : ViewModel() {
    private val _form = MutableStateFlow<FormState?>(null)
    val form: StateFlow<FormState?> = _form.asStateFlow()

    private val _state = MutableStateFlow(SaveState())
    val state: StateFlow<SaveState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val initial: Map<String, Any?> = when (mode) {
                is CustomerFormMode.Add -> mapOf(NAME to mode.name, PHONE to mode.phone?.let { Phone.normalize(it) ?: it })
                is CustomerFormMode.Edit -> customers.observe(mode.id).first()?.let { c ->
                    mapOf(
                        NAME to c.name, PHONE to c.phone, EMAIL to c.email, DOB to c.dob, ADDRESS to c.address,
                        CITY to c.city, NOMINEE to c.nominee, NOTES to c.notes,
                    )
                }.orEmpty()
                is CustomerFormMode.Convert -> enquiries.observe(mode.enquiryId).first()?.let { e ->
                    mapOf(NAME to e.name, PHONE to e.phone, EMAIL to e.email)
                }.orEmpty()
            }
            _form.value = FormState(FIELDS, initial.filterValues { it != null })
        }
    }

    fun save() {
        val form = _form.value ?: return
        if (_state.value.saving || !form.validateAll()) return
        val v = form.values()
        val input = CustomerInput(
            name = v[NAME] as String,
            phone = v[PHONE] as String?,
            email = v[EMAIL] as String?,
            dob = v[DOB] as LocalDate?,
            address = v[ADDRESS] as String?,
            city = v[CITY] as String?,
            nominee = v[NOMINEE] as String?,
            notes = v[NOTES] as String?,
        )
        _state.value = SaveState(saving = true)
        viewModelScope.launch {
            val result = when (mode) {
                is CustomerFormMode.Add -> customers.create(input)
                is CustomerFormMode.Edit -> customers.update(mode.id, input)
                is CustomerFormMode.Convert -> customers.convertEnquiry(mode.enquiryId, input)
            }
            _state.value = when (result) {
                is Result.Ok -> SaveState(savedId = result.value.id)
                is Result.Err -> SaveState(error = result.error)
            }
        }
    }

    companion object {
        const val NAME = "name"
        const val PHONE = "phone"
        const val EMAIL = "email"
        const val DOB = "dob"
        const val ADDRESS = "address"
        const val CITY = "city"
        const val NOMINEE = "nominee"
        const val NOTES = "notes"

        val FIELDS: List<FieldSpec> = listOf(
            FieldSpec(NAME, FieldType.Text, Res.string.cat_field_name, required = true, maxLength = 80),
            FieldSpec(PHONE, FieldType.Phone, Res.string.profile_mobile, required = true),
            FieldSpec(EMAIL, FieldType.Email, Res.string.auth_email, maxLength = 120),
            FieldSpec(DOB, FieldType.Date, Res.string.customer_dob),
            FieldSpec(ADDRESS, FieldType.Multiline, Res.string.customer_address, maxLength = 200),
            FieldSpec(CITY, FieldType.Text, Res.string.cat_field_city, maxLength = 60),
            FieldSpec(NOMINEE, FieldType.Text, Res.string.customer_nominee, maxLength = 80),
            FieldSpec(NOTES, FieldType.Multiline, Res.string.crm_notes, maxLength = 500),
        )

        /** Name and mobile are always shown; the rest go under More details. */
        val VISIBLE = setOf(NAME, PHONE)
    }
}
