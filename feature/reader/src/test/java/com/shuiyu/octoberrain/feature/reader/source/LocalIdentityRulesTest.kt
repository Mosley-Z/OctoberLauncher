package com.shuiyu.octoberrain.feature.reader.source

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIdentityRulesTest {
    @Test
    fun onlyAbsoluteLiveFileTargetsQualify() {
        assertTrue(LocalIdentityRules.isCandidate("/storage/emulated/0/Books/book.txt"))
        assertFalse(LocalIdentityRules.isCandidate("pipe:[123]"))
        assertFalse(LocalIdentityRules.isCandidate("anon_inode:[eventpoll]"))
        assertFalse(LocalIdentityRules.isCandidate("book.txt"))
        assertFalse(LocalIdentityRules.isCandidate("/storage/book.txt (deleted)"))
        assertFalse(LocalIdentityRules.isCandidate("/storage/book\u0000.txt"))
    }

    @Test
    fun aCopyWithMatchingSizeDoesNotProveIdentity() {
        val opened = LocalFileIdentity(4, 10, 1024)
        assertTrue(LocalIdentityRules.sameFile(opened, LocalFileIdentity(4, 10, 1024)))
        assertFalse(LocalIdentityRules.sameFile(opened, LocalFileIdentity(4, 11, 1024)))
        assertFalse(LocalIdentityRules.sameFile(opened, LocalFileIdentity(5, 10, 1024)))
    }

    @Test
    fun concurrentSizeChangeRejectsTheCandidate() {
        assertFalse(LocalIdentityRules.sameFile(LocalFileIdentity(4, 10, 1024), LocalFileIdentity(4, 10, 2048)))
    }

    @Test
    fun unknownInodeOrSizeCannotProveIdentity() {
        assertFalse(LocalIdentityRules.sameFile(LocalFileIdentity(4, 0, 1), LocalFileIdentity(4, 0, 1)))
        assertFalse(LocalIdentityRules.sameFile(LocalFileIdentity(4, 10, -1), LocalFileIdentity(4, 10, -1)))
    }
}
