package com.fileviewer.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocIdsTest {
    @Test fun parentInSubfolder() = assertEquals("primary:Download", DocIds.parentOf("primary:Download/a.bin"))
    @Test fun parentDeep() = assertEquals("primary:A/B", DocIds.parentOf("primary:A/B/c.bin"))
    @Test fun parentAtRoot() = assertEquals("primary:", DocIds.parentOf("primary:a.bin"))
    @Test fun noParentForRoot() = assertNull(DocIds.parentOf("primary:"))
    @Test fun noParentForOtherProviders() = assertNull(DocIds.parentOf("12345"))

    @Test fun insideTree() = assertTrue(DocIds.isInside("primary:Download/a.bin", "primary:Download"))
    @Test fun insideRootTree() = assertTrue(DocIds.isInside("primary:Download/a.bin", "primary:"))
    @Test fun similarPrefixIsNotInside() = assertFalse(DocIds.isInside("primary:Downloads/a.bin", "primary:Download"))
    @Test fun treeItselfIsNotInside() = assertFalse(DocIds.isInside("primary:Download", "primary:Download"))
}
