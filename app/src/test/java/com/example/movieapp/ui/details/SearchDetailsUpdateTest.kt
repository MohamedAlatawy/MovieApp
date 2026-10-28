package com.example.movieapp.ui.details

import com.example.movieapp.domain.model.*
import com.example.movieapp.push.MovieUpdateSignal
import com.example.movieapp.push.MovieUpdates
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SearchDetailsUpdateTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private fun payload(id: Int) = mapOf("eventType" to "movie_updated", "movieId" to "$id", "updatedAt" to "2026-10-29T00:00:00Z")

    @Test fun matchingSignalRefreshesWithoutCacheEvenIfSchedulingFails() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        var calls = 0
        val vm = MovieDetailsViewModel({ error("No Room observation") },
            { id -> calls++; MovieDetails(id,"Title $calls") }, { error("No Home writes") }, signal.events)
        vm.open(9001,MovieOrigin.SEARCH)
        runCurrent()
        assertEquals("Title 1",vm.state.value.movie?.title)
        MovieUpdates.dispatch(payload(3),signal) {}
        runCurrent()
        assertEquals(1,calls)
        assertTrue(runCatching { MovieUpdates.dispatch(payload(9001),signal) { error("Scheduler unavailable") } }.isFailure)
        runCurrent()
        assertEquals(2,calls)
        assertEquals("Title 2",vm.state.value.movie?.title)
        vm.close()
    }

    @Test fun duplicateInflightEventsCoalesceWithoutConcurrentRequests() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var concurrent = 0
        var maxConcurrent = 0
        val vm = MovieDetailsViewModel({ error("No Room") }, { id ->
            calls++; concurrent++; maxConcurrent = maxOf(maxConcurrent,concurrent)
            try { if (calls == 1) gate.await(); MovieDetails(id,"Title $calls") } finally { concurrent-- }
        }, { error("No writes") },signal.events)
        vm.open(1,MovieOrigin.SEARCH)
        runCurrent()
        repeat(5) { MovieUpdates.dispatch(payload(1),signal) {} }
        runCurrent()
        assertEquals(1,calls)
        gate.complete(Unit)
        runCurrent()
        assertEquals(2,calls)
        assertEquals(1,maxConcurrent)
        assertEquals("Title 2",vm.state.value.movie?.title)
        vm.close()
    }

    @Test fun failureRetainsContentAndRetryWorks() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        var fail = false
        val vm = MovieDetailsViewModel({ error("No Room") }, { id ->
            if (fail) throw MovieAccessException.Network()
            MovieDetails(id,"Loaded")
        }, { error("No writes") },signal.events)
        vm.open(1,MovieOrigin.SEARCH); runCurrent()
        fail = true
        MovieUpdates.dispatch(payload(1),signal) {}; runCurrent()
        assertEquals("Loaded",vm.state.value.movie?.title)
        assertTrue(vm.state.value.error); assertFalse(vm.state.value.loading)
        fail = false; vm.retry(); runCurrent()
        assertFalse(vm.state.value.error)
        vm.close()
    }

    @Test fun backgroundClosedAndHomeDetailsIgnoreSearchEvents() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        var calls = 0
        val vm = MovieDetailsViewModel({ flowOf(CachedMovie(1,"Home")) },
            { id -> calls++; MovieDetails(id,"Search") }, {},signal.events)
        vm.open(1,MovieOrigin.HOME); runCurrent()
        MovieUpdates.dispatch(payload(1),signal) {}; runCurrent()
        assertEquals(0,calls)
        vm.open(1,MovieOrigin.SEARCH); runCurrent()
        vm.setActive(false)
        MovieUpdates.dispatch(payload(1),signal) {}; runCurrent()
        assertEquals(1,calls)
        vm.setActive(true); runCurrent()
        assertEquals(2,calls) // Foreground reconciliation, not replay.
        vm.close()
        MovieUpdates.dispatch(payload(1),signal) {}; runCurrent()
        assertEquals(2,calls)
    }

    @Test fun navigationInvalidatesEvenNonCooperativeLateResponse() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        val gate = CompletableDeferred<Unit>()
        val vm = MovieDetailsViewModel({ flowOf(CachedMovie(2,"Home two")) }, { id ->
            if (id == 1) withContext(NonCancellable) { gate.await() }
            MovieDetails(id,"Search $id")
        }, {},signal.events)
        vm.open(1,MovieOrigin.SEARCH); runCurrent()
        MovieUpdates.dispatch(payload(1),signal) {}; runCurrent()
        vm.open(2,MovieOrigin.HOME); runCurrent()
        gate.complete(Unit); runCurrent()
        assertEquals(MovieOrigin.HOME,vm.state.value.origin)
        assertEquals("Home two",vm.state.value.movie?.title)
        vm.close()
    }

    @Test fun invalidMessagesDoNotScheduleOrSignalAndSignalHasNoReplay() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        val scheduled = mutableListOf<Int>()
        val received = mutableListOf<Int>()
        MovieUpdates.dispatch(payload(1),signal) { scheduled += it }
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { signal.events.collect { received += it } }
        MovieUpdates.dispatch(payload(1)+("eventType" to "other"),signal) { scheduled += it }
        MovieUpdates.dispatch(payload(1)+("movieId" to "-1"),signal) { scheduled += it }
        MovieUpdates.dispatch(payload(1),signal) { scheduled += it }
        runCurrent()
        assertEquals(listOf(1,1),scheduled)
        assertEquals(listOf(1),received)
        collector.cancel()
    }

    @Test fun notificationFailureOrDenialDoesNotBlockDataPaths() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        val received = mutableListOf<Int>()
        val scheduled = mutableListOf<Int>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            signal.events.collect { received += it }
        }
        MovieUpdates.dispatch(payload(1),signal, { throw SecurityException("Denied") }) { scheduled += it }
        MovieUpdates.dispatch(payload(2),signal, { /* Permission denied: posting is a no-op. */ }) { scheduled += it }
        runCurrent()
        assertEquals(listOf(1,2),received)
        assertEquals(listOf(1,2),scheduled)
        collector.cancel()
    }

    @Test fun validFeedbackIsIndependentOfSchedulingAndInvalidPayloadHasNone() = runTest(dispatcher) {
        val signal = MovieUpdateSignal()
        val notifications = mutableListOf<Int>()
        assertTrue(runCatching {
            MovieUpdates.dispatch(payload(7),signal, { notifications += it }) { error("Unavailable") }
        }.isFailure)
        MovieUpdates.dispatch(payload(7)+("movieId" to "0"),signal, { notifications += it }) { error("Invalid scheduled") }
        assertEquals(listOf(7),notifications)
    }
}
