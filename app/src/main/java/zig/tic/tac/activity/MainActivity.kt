package zig.tic.tac.activity

import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import io.objectbox.Box
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import zig.tic.App
import zig.tic.tac.R
import zig.tic.tac.adapter.TaskAdapter
import zig.tic.tac.entity.Task
import zig.tic.tac.util.secondsToTime


class MainActivity : AppCompatActivity(), Runnable {

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
    }

    private lateinit var timeMenuItem: MenuItem
    private lateinit var playPauseMenuItem: MenuItem
    private lateinit var handler: Handler
    private var seconds = 0
    private var currentTask: Task? = null
    private var isPaused = true
    private var tasks = mutableListOf<Task>()
    private lateinit var box: Box<Task>
    private lateinit var delMenuItem: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener {
            AlertDialog.Builder(this)
                    .setView(R.layout.dialog_task_name)
                    .setPositiveButton(android.R.string.ok) { d, i ->
                        val text = (d as AlertDialog).findViewById<EditText>(R.id.edtTaskName)?.text
                        start(text.toString(), null)
                    }
                    .create().show()
        }
        rvTasks.setHasFixedSize(true)
        handler = Handler()
        box = (application as App).getBox()

        Log.i(TAG, "is savedInstanceState null=>${(savedInstanceState == null)}")
    }

    fun start(txt: String, taskToContinue: Task?) {
        title = txt
        timeMenuItem.isVisible = true
        playPauseMenuItem.isVisible = true
        delMenuItem.isVisible = true
        if (currentTask != null) {
            currentTask?.setElapsedTime(seconds)
            tasks.add(0, currentTask!!)
            box.put(currentTask)
            if (rvTasks.adapter == null) {
                rvTasks.adapter = TaskAdapter(tasks)
            } else {
                rvTasks.adapter.notifyItemInserted(0)
            }
        }
        if (taskToContinue == null) {
            currentTask = Task(txt, System.currentTimeMillis())
            seconds = 0
        } else {
            currentTask = taskToContinue
            val i = tasks.indexOf(taskToContinue)
            tasks.removeAt(i)
            box.remove(taskToContinue)
            rvTasks.adapter.notifyItemRemoved(i)
            seconds = taskToContinue.getElapsedTime()
        }
        handler.removeCallbacks(this)
        isPaused = false
        playPauseMenuItem.icon = getDrawable()
        handler.postDelayed(this, 1000)
    }

    override fun run() {
        Log.i(TAG, "run")
        if (!isPaused) {
            timeMenuItem.title = secondsToTime(++seconds)
            handler.postDelayed(this, 1000)
        }
    }

    fun pause(item: MenuItem) {
        isPaused = if (isPaused) {
            handler.postDelayed(this, 1000)
            false
        } else true

        item.icon = getDrawable()
    }

    private fun getDrawable(): Drawable {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getDrawable(if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        } else {
            resources.getDrawable(if (isPaused) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        timeMenuItem = menu.findItem(R.id.action_time)
        playPauseMenuItem = menu.findItem(R.id.action_play_pause)
        delMenuItem = menu.findItem(R.id.action_delete)
        val list = box.all
        if (list.isNotEmpty()) {
            tasks.addAll(list)
            currentTask = tasks.find { it.isRunning() }
            if (currentTask != null) {
                currentTask?.setIsRunning(false)
                title = currentTask?.title
                timeMenuItem.title = secondsToTime(currentTask?.getElapsedTime()!! + getTimeClosed())
                timeMenuItem.isVisible = true
                playPauseMenuItem.isVisible = true
                delMenuItem.isVisible = true
                tasks.remove(currentTask!!)
                seconds = currentTask!!.getElapsedTime() + getTimeClosed()
                if (currentTask?.getTimeClosed()!! > 0) {
                    isPaused = false
                    handler.postDelayed(this, 1000)
                    currentTask?.setTimeClosed(0)
                }
                playPauseMenuItem.icon = getDrawable()
            }
            rvTasks.adapter = TaskAdapter(tasks)
        }
        return true
    }

    fun delete(item: MenuItem) {
        Log.i(TAG, "delete")
        title = getString(R.string.app_name)
        playPauseMenuItem.isVisible = false
        timeMenuItem.isVisible = false
        item.isVisible = false
        if (currentTask?.getId() != null) {
            box.remove(currentTask)
        }
        isPaused = true
        handler.removeCallbacks(this)
        currentTask = null
        timeMenuItem.title=getString(R.string.time_elapsed)
    }

    private fun getTimeClosed(): Int = if (currentTask?.getTimeClosed() == 0L) 0 else (System.currentTimeMillis() - currentTask?.getTimeClosed()!!).div(1000).toInt()


    override fun onPause() {
        super.onPause()
        if (currentTask != null && !tasks.contains(currentTask!!)) {
            currentTask?.setIsRunning(true)
            currentTask?.setElapsedTime(seconds)
            if (!isPaused) currentTask?.setTimeClosed(System.currentTimeMillis())
            box.put(currentTask)
            handler.removeCallbacks(this)
        }
        Log.i(TAG, "on pause")
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume")
        if (!isPaused && currentTask != null) {
            seconds += getTimeClosed()
            timeMenuItem.title = secondsToTime(seconds)
            handler.postDelayed(this, 1000)
        }
    }
}
