package com.ripple.core.types.shamap

import com.ripple.core.coretypes.hash.Hash256
import com.ripple.core.types.shamap.TestHelpers.H256
import com.ripple.core.types.shamap.TestHelpers.Leaf
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ShaMapTest {
    /*

    See the README.md related to shamaps for an overview
    */

    @Test
    fun testAddLeaf() {
        // After adding this first
        val sm = ShaMap()
        sm.addLeaf(Leaf("000"))
        assertTrue(sm.branch(0).isLeaf)
        for (i in 1..15) sm.hasNone(i)
    }

    @Test
    fun testGetLeaf() {
        val sm = ShaMap()
        sm.addLeaf(Leaf("000"))
        val index = "000123"
        val l = Leaf(index)
        sm.addLeaf(l)
        val retrieved = sm.getLeaf(H256(index))
        assertSame(retrieved, l)
    }

    @Test
    fun testWalkHashedTree() {
        val sm = ShaMap()
        sm.addLeaf(Leaf("01"))
        sm.addLeaf(Leaf("03"))
        sm.addLeaf(Leaf("0345"))

        val inners = AtomicInteger()
        val leaves = AtomicInteger()

        sm.walkHashedTree(object : HashedTreeWalker {
            override fun onLeaf(h: Hash256, le: ShaMapLeaf) {
                leaves.incrementAndGet()
            }

            override fun onInner(h: Hash256, inner: ShaMapInner) {
                inners.incrementAndGet()
            }
        })

        assertEquals(leaves.get().toLong(), 3)
        assertEquals(inners.get().toLong(), 3)
    }

    @Test
    fun testInners() {
        // 775 and 776 are in the same inner node because they match on the first 7.
        // If you add 731,

        val sm = ShaMap()
        sm.addLeaf(Leaf("775"))
        sm.addLeaf(Leaf("776"))

        val nodeCount = NodeCount[sm]
        assertEquals(nodeCount.leaves().toLong(), 2)
        // root + 7 + 7
        assertEquals(nodeCount.inners().toLong(), 3)
        assertEquals(sm.branchCount().toLong(), 1)
        assertEquals(sm.branches[7].asInner().branchCount().toLong(), 1)
        assertEquals(sm.branches[7].asInner().branches[7].asInner().branchCount().toLong(), 2)

        sm.addLeaf(Leaf("0345"))
        nodeCount.update()

        assertEquals(nodeCount.inners().toLong(), 3)
        assertEquals(nodeCount.leaves().toLong(), 3)

    }

    @Test
    fun testRemoveLeaf() {
        val sm = ShaMap()
        // Add one leaf
        removeLeafTestHelper(sm)
    }

    private fun removeLeafTestHelper(sm: ShaMap) {
        sm.addLeaf(Leaf("000"))
        val afterOne = sm.hash()

        // Add a second down same path/index
        sm.addLeaf(Leaf("001"))
        for (i in 1..15) sm.hasNone(i)

        // Where before this was a leaf, now it's an inner,
        // cause of common path/prefix in index
        assertTrue(sm.branch(0).isInner)
        // The common prefix `00` leads to an inner branch
        assertTrue(sm.branch(0).asInner().branch(0).isInner)

        // The children of `00`
        val common = sm.branch(0).asInner().branch(0).asInner()

        assertTrue(common.branch(0).isLeaf)
        assertTrue(common.branch(1).isLeaf)
        for (i in 2..15) common.hasNone(i)

        sm.removeLeaf(H256("001"))
        assertTrue(sm.branch(0).isLeaf)
        for (i in 1..15) sm.hasNone(i)
        assertEquals(sm.hash(), afterOne)

        sm.removeLeaf(H256("000"))
        assertEquals(sm.hash(), H256("0"))
    }

    @Test
    fun testAnEmptyInnerHasAZeroHash() {
        val sm = ShaMap()
        assertEquals(sm.hash(), H256("0"))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyOnWrite() {
        val sm = ShaMap()
        assertEquals(sm.hash(), H256("0"))
        val copy1 = sm.copy()
        removeLeafTestHelper(copy1)
        sm.addLeaf(Leaf("01"))
        sm.addLeaf(Leaf("02"))
        sm.addLeaf(Leaf("023"))
        sm.addLeaf(Leaf("024"))
        assertEquals(copy1.hash(), Hash256.ZERO_256)

        val copy2 = sm.copy()
        val copy2Hash = copy2.hash()
        assertEquals(copy2Hash, sm.hash())

        sm.removeLeaf(H256("01"))
        sm.removeLeaf(H256("02"))
        sm.removeLeaf(H256("023"))
        sm.removeLeaf(H256("024"))

        assertEquals(sm.hash(), Hash256.ZERO_256)
        removeLeafTestHelper(sm)

        copy2.invalidate()
        assertEquals(copy2.hash(), copy2Hash)
        assertEquals(copy1.hash(), Hash256.ZERO_256)
    }

    @Test
    @Throws(Exception::class)
    fun testCopyOnWriteSemanticsUsing_getLeafForUpdating() {
        val sm = ShaMap()
        sm.addLeaf(Leaf("01"))
        sm.addLeaf(Leaf("02"))
        sm.addLeaf(Leaf("023"))
        copyOnWriteTestHelper(sm, Leaf("024"))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyOnWriteSemanticsUsing_getLeafForUpdating2() {
        // Just the one leaf, which makes sure we copy leaves probably
        val sm = ShaMap()
        copyOnWriteTestHelper(sm, Leaf("0"))
    }

    private fun copyOnWriteTestHelper(sm: ShaMap, leaf: ShaMapLeaf) {
        sm.addLeaf(leaf)

        // At this point the shamap doesn't do any copy on write
        assertSame(leaf, sm.getLeaf(leaf.index))
        // We can update the leaf in place
        assertSame(leaf, sm.getLeafForUpdating(leaf.index))
        // It's still the same
        assertSame(leaf, sm.getLeaf(leaf.index))

        // We make a copy, which means any changes to either
        // induce a copy on write
        val copy = sm.copy()

        // The leaf is still the same
        assertSame(leaf, sm.getLeaf(leaf.index))
        // because we need to make sure we don't mess with our clones ;)
        assertNotSame(leaf, sm.getLeafForUpdating(leaf.index))
        // now this has been updated via getLeafForUpdating
        // there is no `commit`
        assertNotSame(leaf, sm.getLeaf(leaf.index))

        // And wow, we didn't mess with the original leaf in the copy ;)
        assertSame(leaf, copy.getLeaf(leaf.index))
        // But now it's different after updating
        assertNotSame(leaf, copy.getLeafForUpdating(leaf.index))

        // We haven't actually caused any substantive changes
        assertEquals(sm.hash(), copy.hash())
    }

    private class NodeCount internal constructor(private val sm: ShaMap) {
        private var inners: AtomicInteger? = null
        private var leaves: AtomicInteger? = null

        internal fun inners(): Int {
            return inners!!.get()
        }

        internal fun leaves(): Int {
            return leaves!!.get()
        }

        internal operator fun invoke(): NodeCount {
            inners = AtomicInteger()
            leaves = AtomicInteger()

            sm.walkHashedTree(object : HashedTreeWalker {
                override fun onLeaf(h: Hash256, le: ShaMapLeaf) {
                    leaves!!.incrementAndGet()
                }

                override fun onInner(h: Hash256, inner: ShaMapInner) {
                    inners!!.incrementAndGet()
                }
            })
            return this
        }

        internal fun update(): NodeCount {
            this.invoke()
            return this
        }

        companion object {

            operator fun get(sm: ShaMap): NodeCount {
                return NodeCount(sm).invoke()
            }
        }
    }
}