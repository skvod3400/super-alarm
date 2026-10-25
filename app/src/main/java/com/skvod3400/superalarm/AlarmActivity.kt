package com.skvod3400.superalarm

import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.skvod3400.superalarm.databinding.ActivityAlarmBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Ringing screen: the alarm can only be dismissed or snoozed after solving a problem. */
class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding
    private lateinit var problem: MathProblem
    private var alarmId = -1

    /** Set when the alarm clock opened this screen; the sound is started in [onResume]. */
    private var pendingFire: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!binding.doneSection.isVisible) toast(R.string.solve_first)
            }
        })

        binding.check.setOnClickListener { checkAnswer() }
        binding.answer.setOnEditorActionListener { _, _, _ ->
            checkAnswer()
            true
        }
        binding.dismiss.setOnClickListener {
            AlarmService.stop(this)
            finish()
        }
        binding.snooze.setOnClickListener {
            AlarmScheduler.snooze(this, alarmId)
            AlarmService.stop(this)
            toast(R.string.snoozed)
            finish()
        }

        handleIntent(intent)
        newProblem()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        isOnScreen = true
        pendingFire?.let {
            pendingFire = null
            AlarmTrigger.fire(this, it)
        }
        if (!AlarmService.isRinging) {
            finish()
            return
        }
        binding.time.text = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    override fun onPause() {
        isOnScreen = false
        super.onPause()
    }

    // Volume buttons must not silence the alarm.
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> true
        else -> super.onKeyDown(keyCode, event)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == ACTION_FIRE) pendingFire = intent
        alarmId = intent.getIntExtra(EXTRA_ALARM_ID, -1)
        val alarm = AlarmRepository.get(this, alarmId)
        val label = if (alarmId == TEST_ALARM_ID) getString(R.string.test_alarm) else alarm?.label
        binding.label.text = label
        binding.label.isVisible = !label.isNullOrBlank()
        binding.snooze.isVisible = alarm != null
    }

    private fun newProblem() {
        problem = MathProblem.random()
        binding.problem.text = problem.text
        binding.answer.text = null
        binding.answer.requestFocus()
    }

    private fun checkAnswer() {
        val answer = binding.answer.text?.toString()?.trim()?.toIntOrNull() ?: return
        if (answer == problem.answer) {
            binding.problemSection.isVisible = false
            binding.doneSection.isVisible = true
            WindowCompat.getInsetsController(window, binding.root).hide(WindowInsetsCompat.Type.ime())
        } else {
            ObjectAnimator.ofFloat(
                binding.problemSection, View.TRANSLATION_X, 0f, 30f, -30f, 20f, -20f, 10f, -10f, 0f,
            ).setDuration(400).start()
            toast(R.string.wrong_answer)
            newProblem()
        }
    }

    private fun toast(textRes: Int) = Toast.makeText(this, textRes, Toast.LENGTH_SHORT).show()

    companion object {
        @Volatile
        var isOnScreen = false
            private set

        fun intent(context: Context, alarmId: Int): Intent =
            Intent(context, AlarmActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
