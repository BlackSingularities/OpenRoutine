package com.blacksingularities.openroutine

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.blacksingularities.openroutine.data.Repo
import com.blacksingularities.openroutine.engine.Engine
import com.blacksingularities.openroutine.ui.AppRoot
import com.blacksingularities.openroutine.ui.AppViewModel
import com.blacksingularities.openroutine.ui.theme.OpenRoutineTheme
import com.blacksingularities.openroutine.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Przezroczyste paski systemu bez przyciemnienia – dolny pasek aplikacji sięga do samej krawędzi ekranu.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        UpdateManager.clearUpdatedNotification(this)
        handle(intent)
        setContent { OpenRoutineTheme { AppRoot(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStart() {
        super.onStart()
        App.inForeground = true
    }

    override fun onResume() {
        super.onResume()
        vm.resumed()
    }

    override fun onStop() {
        App.inForeground = false
        super.onStop()
    }

    /** Skrót na ekranie głównym uruchamia rutynę i zamyka się bez pokazywania aplikacji. */
    private fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_RUN) ?: return
        intent.removeExtra(EXTRA_RUN)
        val r = Repo.get(id)
        if (r == null) {
            Toast.makeText(this, "Ta rutyna już nie istnieje", Toast.LENGTH_SHORT).show()
        } else {
            Engine.runManual(r)
            Toast.makeText(this, "Uruchomiono: ${r.name}", Toast.LENGTH_SHORT).show()
        }
        if (intent.getBooleanExtra(EXTRA_FROM_SHORTCUT, false)) finish()
    }

    companion object {
        const val EXTRA_RUN = "run_routine"
        const val EXTRA_FROM_SHORTCUT = "from_shortcut"
    }
}
