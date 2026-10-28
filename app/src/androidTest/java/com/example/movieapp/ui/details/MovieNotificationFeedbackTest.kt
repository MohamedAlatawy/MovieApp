package com.example.movieapp.ui.details

import android.app.Notification
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.example.movieapp.push.*
import com.example.movieapp.ui.theme.MovieAppTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MovieNotificationFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun notificationHasSafeDistinctTapTargetsAndValidatedIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = MovieNotifications.intent(context, 9001)
        assertEquals(9001, MovieNotifications.movieId(intent))
        assertEquals("com.example.movieapp.MainActivity", intent.component?.className)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
        assertNull(MovieNotifications.movieId(Intent()))
        assertNull(MovieNotifications.movieId(Intent(intent).putExtra("updated_movie_id", -1)))
        assertTrue(runCatching { MovieNotifications.intent(context, 0) }.isFailure)
        val first = MovieNotifications.build(context, 9001)
        val repeated = MovieNotifications.build(context, 9001)
        val other = MovieNotifications.build(context, 9002)
        assertEquals("Movie updated",first.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(first.contentIntent,repeated.contentIntent)
        assertNotEquals(first.contentIntent,other.contentIntent)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(first.contentIntent.isImmutable)
        assertTrue(first.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertTrue(first.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNotNull(first.smallIcon)
    }

    @Test fun foregroundFeedbackIsTransientAndBackgroundEventsAreNotReplayed() {
        val signal = MovieUpdateSignal()
        lateinit var registry: LifecycleRegistry
        val owner = object : LifecycleOwner {
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { registry = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.STARTED } }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                MovieAppTheme { MovieUpdateSnackbar(signal.events) }
            }
        }
        fun event(id: String) = mapOf("eventType" to "movie_updated", "movieId" to id,
            "updatedAt" to "2026-10-29T00:00:00Z")
        compose.runOnIdle { MovieUpdates.dispatch(event("-1"),signal) {} }
        compose.onNodeWithText("Movie update received").assertDoesNotExist()
        compose.runOnIdle { MovieUpdates.dispatch(event("9001"),signal) {} }
        compose.onNodeWithText("Movie update received").assertIsDisplayed()
        compose.runOnIdle { registry.currentState = Lifecycle.State.CREATED }
        compose.onNodeWithText("Movie update received").assertDoesNotExist()
        compose.runOnIdle { MovieUpdates.dispatch(event("9002"),signal) {} }
        compose.runOnIdle { registry.currentState = Lifecycle.State.STARTED }
        compose.onNodeWithText("Movie update received").assertDoesNotExist()
        compose.runOnIdle { registry.currentState = Lifecycle.State.DESTROYED }
    }
}
