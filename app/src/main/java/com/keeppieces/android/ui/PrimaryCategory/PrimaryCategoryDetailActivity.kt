package com.keeppieces.android.ui.PrimaryCategory

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import com.google.android.material.datepicker.MaterialDatePicker
import com.keeppieces.android.R
import com.keeppieces.android.extension.toCHINADFormatted
import com.keeppieces.android.logic.data.Bill
import com.keeppieces.android.ui.PrimaryCategory.adapter.PrimaryCategoryDetailActivityAdapter
import com.keeppieces.android.ui.PrimaryCategory.viewmodel.PrimaryCategoryDetailActivityViewModel
import com.keeppieces.pie_chart.PieAnimation
import com.keeppieces.pie_chart.PieData
import com.keeppieces.pie_chart.PiePortion
import kotlinx.android.synthetic.main.activity_primary_category_detail.*
import kotlinx.android.synthetic.main.include_detail_datebar.*
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.chrono.IsoChronology
import kotlin.math.abs
import kotlin.properties.Delegates


class PrimaryCategoryDetailActivity : AppCompatActivity() {
    private lateinit var startDate: String
    private lateinit var endDate: String
    private lateinit var primaryCategory: String
    private var primaryOrSecondaryFlag by Delegates.notNull<Boolean>()
    private val viewModel: PrimaryCategoryDetailActivityViewModel by viewModels()

    @RequiresApi(Build.VERSION_CODES.O)
    var startLocalDate: LocalDate = LocalDate.now()

    @RequiresApi(Build.VERSION_CODES.O)
    var endLocalDate: LocalDate = LocalDate.now()
    private var timeSpan: Int = 1
    private var cnt: Int = -1
    private var mode = MonthMode

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_primary_category_detail)
        startDate = intent.getStringExtra("startDate") ?: LocalDate.now().toString()
        endDate = intent.getStringExtra("endDate") ?: LocalDate.now().toString()
        primaryCategory = intent.getStringExtra("primaryCategory") ?: "出了点错误"
        primaryOrSecondaryFlag = intent.getBooleanExtra("primaryOrSecondaryFlag",false)
        initDate()  // 在这里会初始化好timeSpan
        if (savedInstanceState == null) runEnterAnimation()
        setUpView()
        detailLeftArrow.setOnClickListener {
            updateDate(-timeSpan)
            setUpView()
        }
        detailRightArrow.setOnClickListener {
            updateDate(timeSpan)
            setUpView()
        }
        detailDateText.setOnClickListener {
            val builder = MaterialDatePicker.Builder.dateRangePicker()
            val picker = builder.build()
            picker.show(supportFragmentManager, picker.toString())
            picker.addOnPositiveButtonClickListener {
                val format = SimpleDateFormat("yyyy-MM-dd")
                timeSpan = ((it.second!! - it.first!!) / (1000 * 3600 * 24)).toInt() + 1
                timeSpan = if (timeSpan == 0) 1 else timeSpan
                startDate = format.format(it.first)
                startLocalDate = LocalDate.parse(startDate)
                endDate = format.format(it.second)
                endLocalDate = LocalDate.parse(endDate)
                updateMode()
                setUpView()
            }
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initDate() {
        startLocalDate = LocalDate.parse(startDate)
        endLocalDate = LocalDate.parse(endDate)
        timeSpan = endLocalDate.dayOfYear - startLocalDate.dayOfYear + 1
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateDate(span: Int) {
        if (mode == MonthMode) {
            val monthSpan = if (span > 0) 1 else -1
            startLocalDate = startLocalDate.plusMonths(monthSpan.toLong())
            endLocalDate = startLocalDate.plusMonths(1).plusDays(-1)
        } else {
            startLocalDate = startLocalDate.plusDays(span.toLong())
            endLocalDate = endLocalDate.plusDays(span.toLong())
        }

        startDate = startLocalDate.toString()
        endDate = endLocalDate.toString()
//        Log.d("Monthly Date Update", "$startDate ~ $endDate")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateMode() {
        val year = endLocalDate.year
        val month = endLocalDate.monthValue
        val lastDay = endLocalDate.dayOfMonth
        mode =
            if (startLocalDate.dayOfMonth == 1 && startLocalDate.monthValue == month && lastDay >= 28) {
                when (lastDay) {
                    31 -> MonthMode
                    30 -> when (month) {
                        4 -> MonthMode
                        6 -> MonthMode
                        9 -> MonthMode
                        11 -> MonthMode
                        else -> CustomMode
                    }
                    29 -> if (month == 2) MonthMode else CustomMode
                    28 -> if (month == 2 && IsoChronology.INSTANCE.isLeapYear(year.toLong())) MonthMode else CustomMode
                    else -> CustomMode
                }
            } else {
                CustomMode
            }
    }

    private fun runEnterAnimation() {
        income_expenditure_tab_layout.post {
            income_expenditure_tab_layout.translationY -= income_expenditure_tab_layout.height.toFloat()
            income_expenditure_tab_layout.alpha = 0f
            income_expenditure_tab_layout.animate()
                .translationY(0f)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun setUpView() {
        viewModel.getPrimaryCategoryBillInTimeSpan(startDate, endDate, primaryCategory)
            .observe(this) { billList ->
                detailDateText.text = StringBuilder("$startDate ~ $endDate").toString()
                primary_category_title.text = primaryCategory
                setUpPieView(billList)
                viewModel.separateBillList(billList) // 分离收支账单用于展示流水
                primaryIncome.text = StringBuilder("收入:"+viewModel.incomeTotal.toCHINADFormatted())
                primaryExpenditure.text = StringBuilder("支出:"+viewModel.expenditureTotal.toCHINADFormatted())
                setUpViewPager()
            }
        if (cnt <= 0) cnt++
    }

    private fun setUpPieView(bills: List<Bill>) {
        viewModel.getSecondaryClassification(bills)
        viewModel.getSecondaryCategorySummary("blue", "yellow")
        val piePortions = viewModel.secondaryCategorySummary.map {
            PiePortion(
                it.secondaryCategory, abs(it.amount), ContextCompat.getColor(this, it.color)
            )
        }.toList()

        val pieData = PieData(portions = piePortions)
        val pieAnimation = PieAnimation(primary_category_pie_chart).apply {
            duration = 600
        }
        primary_category_pie_chart.setPieData(pieData = pieData, animation = pieAnimation)
    }

    private fun setUpViewPager() {
        second_category_view_pager.adapter = PrimaryCategoryDetailActivityAdapter(
            supportFragmentManager,
            2,  // 收入和支出
            viewModel.incomeBillList,
            viewModel.expenditureBillList
        )
        second_category_view_pager.offscreenPageLimit = 0
        income_expenditure_tab_layout.setupWithViewPager(second_category_view_pager, true)
        second_category_view_pager.setCurrentItem(0, true)
    }

    companion object {
        fun start(context: Context, startDate: String, endDate: String, primaryCategory: String, primaryOrSecondaryFlag:Boolean) {
            val intent = Intent(context, PrimaryCategoryDetailActivity::class.java)
            intent.putExtra("startDate", startDate)
            intent.putExtra("endDate", endDate)
            intent.putExtra("primaryCategory", primaryCategory)
            intent.putExtra("primaryOrSecondaryFlag",primaryOrSecondaryFlag)
            startActivity(context, intent, null)
        }
    }
}