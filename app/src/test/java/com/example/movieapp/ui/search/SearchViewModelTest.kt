package com.example.movieapp.ui.search

import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingData
import com.example.movieapp.domain.model.MovieSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test fun trimsDebouncesAndCancelsOldQueriesImmediately() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        val requested = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
        val vm = SearchViewModel { query -> flow {
            requested += query
            try {
                emit(PagingData.empty<MovieSummary>())
                awaitCancellation()
            } finally { cancelled += query }
        } }
        store.put("search", vm)
        try {
            vm.updateQuery("bat")
            backgroundScope.launch { vm.state.value.movies.collect() }
            runCurrent()
            advanceTimeBy(399)
            assertTrue(requested.isEmpty())
            vm.updateQuery("  batman  ")
            backgroundScope.launch { vm.state.value.movies.collect() }
            runCurrent()
            advanceTimeBy(400)
            runCurrent()
            assertEquals(listOf("batman"), requested)
            val previous = vm.state.value.movies
            vm.updateQuery("toy")
            runCurrent()
            assertEquals(listOf("batman"), cancelled)
            assertNotSame(previous, vm.state.value.movies)
            backgroundScope.launch { vm.state.value.movies.collect() }
            runCurrent()
            advanceTimeBy(400)
            runCurrent()
            assertEquals(listOf("batman", "toy"), requested)
            val sameTerm = vm.state.value.movies
            vm.updateQuery(" toy ")
            assertSame(sameTerm, vm.state.value.movies)
            vm.updateQuery("   ")
            runCurrent()
            assertEquals(listOf("batman", "toy"), cancelled)
            backgroundScope.launch { vm.state.value.movies.collect() }
            advanceTimeBy(500)
            runCurrent()
            assertEquals(2, requested.size)
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
