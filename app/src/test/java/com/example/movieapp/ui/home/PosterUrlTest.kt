package com.example.movieapp.ui.home

import org.junit.Assert.*
import org.junit.Test

class PosterUrlTest {
    @Test fun acceptsOnlyAnImageFilenameOnTheIntendedHost() {
        assertEquals("https://image.tmdb.org/t/p/w500/abc-123.jpg", posterUrl("/abc-123.jpg"))
        for (path in listOf(null, "", "//other.example/image.jpg", "/../image.jpg", "/file.jpg?token=x")) {
            assertNull(posterUrl(path))
        }
    }
}
