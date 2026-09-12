package com.macroresearch.ui.history

import com.macroresearch.data.MacroRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryPagingTest {
    @Test fun historyUsesEightRowsPerPage() {
        assertEquals(8, MacroRepository.HISTORY_PAGE_SIZE)
    }

    @Test fun nextPageLoadsOnlyWhenTheBottomIsVisible() {
        assertFalse(shouldLoadOlder(7, 8, scrolledFromTop = false, hasMore = true, loading = false, failed = false))
        assertFalse(shouldLoadOlder(6, 8, scrolledFromTop = true, hasMore = true, loading = false, failed = false))
        assertTrue(shouldLoadOlder(7, 8, scrolledFromTop = true, hasMore = true, loading = false, failed = false))
        assertFalse(shouldLoadOlder(7, 8, scrolledFromTop = true, hasMore = false, loading = false, failed = false))
        assertFalse(shouldLoadOlder(7, 8, scrolledFromTop = true, hasMore = true, loading = true, failed = false))
        assertFalse(shouldLoadOlder(7, 8, scrolledFromTop = true, hasMore = true, loading = false, failed = true))
        assertFalse(shouldLoadOlder(-1, 0, scrolledFromTop = true, hasMore = true, loading = false, failed = false))
    }
}
