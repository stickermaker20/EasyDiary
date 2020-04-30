package me.blog.korn123.easydiary.activities

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.SpannableString
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.formatMinutesToTimeString
import com.simplemobiletools.commons.extensions.moveLastItemToFront
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.helpers.*
import io.github.aafactory.commons.utils.DateUtils
import kotlinx.android.synthetic.main.activity_dev.*
import me.blog.korn123.easydiary.R
import me.blog.korn123.easydiary.adapters.AlarmAdapter
import me.blog.korn123.easydiary.extensions.config
import me.blog.korn123.easydiary.helper.EasyDiaryDbHelper
import me.blog.korn123.easydiary.helper.NOTIFICATION_CHANNEL_DESCRIPTION
import me.blog.korn123.easydiary.helper.NOTIFICATION_CHANNEL_ID
import me.blog.korn123.easydiary.helper.NOTIFICATION_CHANNEL_NAME
import me.blog.korn123.easydiary.models.Alarm
import me.blog.korn123.easydiary.receivers.AlarmReceiver
import java.util.*
import kotlin.math.pow


class DevActivity : EasyDiaryActivity() {

    /***************************************************************************************************
     *   override functions
     *
     ***************************************************************************************************/
    private lateinit var mAlarmAdapter: AlarmAdapter
    private var mAlarmList: ArrayList<Alarm> = arrayListOf()


    /***************************************************************************************************
     *   global properties
     *
     ***************************************************************************************************/
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev)
        setSupportActionBar(toolbar)
        supportActionBar?.run {
            title = "Easy-Diary Dev Mode"
            setDisplayHomeAsUpEnabled(true)
        }

        mAlarmAdapter = AlarmAdapter(
            this@DevActivity,
            mAlarmList,
            AdapterView.OnItemClickListener { _, _, position, _ ->
            }
        )

        alarmRecyclerView.apply {
            layoutManager = androidx.recyclerview.widget.GridLayoutManager(this@DevActivity, 1)
            addItemDecoration(SpacesItemDecoration(resources.getDimensionPixelSize(R.dimen.card_layout_padding)))
            adapter = mAlarmAdapter
        }

        initProperties()
        initDevUI()
        bindEvent()
        updateAlarmList()
    }


    /***************************************************************************************************
     *   etc functions
     *
     ***************************************************************************************************/
    private fun updateAlarmList() {
        mAlarmList.clear()
        mAlarmList.addAll(EasyDiaryDbHelper.readAlarmAll())
        mAlarmAdapter.notifyDataSetChanged()
    }

    private fun initProperties() {
        config.use24HourFormat = false
        val calendar = Calendar.getInstance(Locale.getDefault())
        var minutes = calendar.get(Calendar.HOUR_OF_DAY) * 60
        minutes += calendar.get(Calendar.MINUTE)

        val tempAlarm = Alarm(0)
        if (EasyDiaryDbHelper.countAlarmAll() == 0L) {
            EasyDiaryDbHelper.insertAlarm(tempAlarm)
        }
    }

    private fun initDevUI() {
        val resourceId = resources.getIdentifier("ic_pizza", "drawable", packageName)
        if (resourceId > 0) {
            devConsoleSymbol.setImageDrawable(ContextCompat.getDrawable(this, resourceId))
            devConsoleSymbol.visibility = View.VISIBLE
        }
    }

    private fun bindEvent() {
//        openAlarmManager.setOnClickListener {
//            TransitionHelper.startActivityWithTransition(this, Intent(this, DiaryReminderActivity::class.java).apply {
//                putExtra(ALARM_ID, mAlarm.id)
//            })
//        }

        nextAlarmInfo.setOnClickListener {
            val nextAlarm = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val triggerTimeMillis = (getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime ?: 0
                when (triggerTimeMillis > 0) {
                    true -> DateUtils.getFullPatternDateWithTime(triggerTimeMillis)
                    false -> "Alarm info is not exist."
                }
            } else {
                Settings.System.getString(contentResolver,Settings.System.NEXT_ALARM_FORMATTED)
            }

            toast(nextAlarm, Toast.LENGTH_LONG)
        }

        addAlarm.setOnClickListener {
            EasyDiaryDbHelper.insertAlarm(Alarm())
            updateAlarmList()
        }
    }

    companion object {
        const val ALARM_ID = "alarm_id"
    }
}

class SpacesItemDecoration(private val space: Int) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: androidx.recyclerview.widget.RecyclerView, state: androidx.recyclerview.widget.RecyclerView.State) {
        val position = parent.getChildAdapterPosition(view)
        when (position == 0) {
            true -> outRect.top = 0
            false -> outRect.top = space
        }
    }
}


/***************************************************************************************************
 *   extensions
 *
 ***************************************************************************************************/
//data class Alarm(var id: Int, var timeInMinutes: Int, var days: Int, var isEnabled: Boolean, var vibrate: Boolean, var soundTitle: String, var soundUri: String, var label: String)

fun formatTime(showSeconds: Boolean, use24HourFormat: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val hoursFormat = if (use24HourFormat) "%02d" else "%01d"
    var format = "$hoursFormat:%02d"

    return if (showSeconds) {
        format += ":%02d"
        String.format(format, hours, minutes, seconds)
    } else {
        String.format(format, hours, minutes)
    }
}

fun Context.isScreenOn() = (getSystemService(Context.POWER_SERVICE) as PowerManager).isScreenOn

fun Context.getOpenAlarmTabIntent(): PendingIntent {
    val diaryMainIntent = Intent(this, DiaryMainActivity::class.java)
    return PendingIntent.getActivity(this, 1000, diaryMainIntent, PendingIntent.FLAG_UPDATE_CURRENT)
}

fun Context.getAlarmIntent(alarm: Alarm): PendingIntent {
    val intent = Intent(this, AlarmReceiver::class.java)
    intent.putExtra(DevActivity.ALARM_ID, alarm.id)
    return PendingIntent.getBroadcast(this, alarm.id, intent, PendingIntent.FLAG_UPDATE_CURRENT)
}

fun Context.cancelAlarmClock(alarm: Alarm) {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    alarmManager.cancel(getAlarmIntent(alarm))
}

// step01
fun Context.scheduleNextAlarm(alarm: Alarm, showToast: Boolean) {
    val calendar = Calendar.getInstance()
    calendar.firstDayOfWeek = Calendar.MONDAY
    for (i in 0..7) {
        val currentDay = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val isCorrectDay = alarm.days and 2.0.pow(currentDay).toInt() != 0
        val currentTimeInMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        if (isCorrectDay && (alarm.timeInMinutes > currentTimeInMinutes || i > 0)) {
            val triggerInMinutes = alarm.timeInMinutes - currentTimeInMinutes + (i * DAY_MINUTES)
            setupAlarmClock(alarm, triggerInMinutes * 60 - calendar.get(Calendar.SECOND))

            if (showToast) {
                showRemainingTimeMessage(triggerInMinutes)
            }
            break
        } else {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
    }
}

// step02
fun Context.setupAlarmClock(alarm: Alarm, triggerInSeconds: Int) {
    val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val targetMS = System.currentTimeMillis() + triggerInSeconds * 1000
    AlarmManagerCompat.setAlarmClock(alarmManager, targetMS, getOpenAlarmTabIntent(), getAlarmIntent(alarm))
}

fun Context.showRemainingTimeMessage(totalMinutes: Int) {
    val fullString = String.format(getString(R.string.alarm_goes_off_in), formatMinutesToTimeString(totalMinutes))
    toast(fullString, Toast.LENGTH_LONG)
}

fun Context.showAlarmNotification(alarm: Alarm) {
    val pendingIntent = getOpenAlarmTabIntent()
    val notification = getAlarmNotification(pendingIntent, alarm)
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(alarm.id, notification)
    val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
    if (isScreenOn()) {
        scheduleNextAlarm(alarm, true)
    } else {
        scheduleNextAlarm(alarm, false)
        powerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP, "myApp:notificationLock").apply {
            acquire(3000)
        }
    }
}

fun Context.rescheduleEnabledAlarms() {
//    toast("Reschedule enabled alarms that Easy Diary.", Toast.LENGTH_LONG)
    EasyDiaryDbHelper.readAlarmAll().forEach {
        if (it.isEnabled) scheduleNextAlarm(it, false)
    }
}

@SuppressLint("NewApi")
fun Context.getAlarmNotification(pendingIntent: PendingIntent, alarm: Alarm): Notification {
    if (isOreoPlus()) {
        // Create the NotificationChannel
        val importance = NotificationManager.IMPORTANCE_HIGH
        val mChannel = NotificationChannel("${NOTIFICATION_CHANNEL_ID}_dev", "${NOTIFICATION_CHANNEL_NAME}_dev", importance)
        mChannel.description = NOTIFICATION_CHANNEL_DESCRIPTION
        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(AppCompatActivity.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(mChannel)
    }

    val builder = NotificationCompat.Builder(applicationContext, "${NOTIFICATION_CHANNEL_ID}_dev")
            .setDefaults(Notification.DEFAULT_ALL)
            .setWhen(System.currentTimeMillis())
            .setSmallIcon(R.drawable.ic_launcher_round)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher_round))
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentTitle("Easy Diary alarm: ${alarm.id}")
            .setContentText(alarm.label)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

    val notification = builder.build()
//    notification.flags = notification.flags or Notification.FLAG_INSISTENT
    return notification
}

fun Context.getFormattedTime(passedSeconds: Int, showSeconds: Boolean, makeAmPmSmaller: Boolean): SpannableString {
    val use24HourFormat = config.use24HourFormat
    val hours = (passedSeconds / 3600) % 24
    val minutes = (passedSeconds / 60) % 60
    val seconds = passedSeconds % 60

    return if (!use24HourFormat) {
        val formattedTime = formatTo12HourFormat(showSeconds, hours, minutes, seconds)
        val spannableTime = SpannableString(formattedTime)
        val amPmMultiplier = if (makeAmPmSmaller) 0.4f else 1f
        spannableTime.setSpan(RelativeSizeSpan(amPmMultiplier), spannableTime.length - 5, spannableTime.length, 0)
        spannableTime
    } else {
        val formattedTime = formatTime(showSeconds, use24HourFormat, hours, minutes, seconds)
        SpannableString(formattedTime)
    }
}

fun Context.formatTo12HourFormat(showSeconds: Boolean, hours: Int, minutes: Int, seconds: Int): String {
    val appendable = getString(if (hours >= 12) R.string.p_m else R.string.a_m)
    val newHours = if (hours == 0 || hours == 12) 12 else hours % 12
    return "${formatTime(showSeconds, false, newHours, minutes, seconds)} $appendable"
}

// format day bits to strings like "Mon, Tue, Wed"
fun Context.getSelectedDaysString(bitMask: Int): String {
    val dayBits = arrayListOf(MONDAY_BIT, TUESDAY_BIT, WEDNESDAY_BIT, THURSDAY_BIT, FRIDAY_BIT, SATURDAY_BIT, SUNDAY_BIT)
    val weekDays = arrayListOf("월", "화", "수", "목", "금", "토", "일")

    if (baseConfig.isSundayFirst) {
        dayBits.moveLastItemToFront()
        weekDays.moveLastItemToFront()
    }

    var days = ""
    dayBits.forEachIndexed { index, bit ->
        if (bitMask and bit != 0) {
            days += "${weekDays[index]}, "
        }
    }
    return days.trim().trimEnd(',')
}

fun Activity.showOverLockScreen() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
}
