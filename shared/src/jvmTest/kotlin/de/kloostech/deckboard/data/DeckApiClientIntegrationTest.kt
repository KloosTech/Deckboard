package de.kloostech.deckboard.data

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end coverage of every [DeckApiClient] endpoint against the *real* Nextcloud Deck
 * backend configured in the desktop app's settings (read via the same [DeckSettingsStore] /
 * java.util.prefs node the desktop app writes to — run the app and fill in Settings… first).
 *
 * All calls operate on a single board/stack/card created at the start of the test, so a real
 * server is required and this is not a hermetic unit test. If no connection is configured the
 * test is skipped (not failed) via [assumeTrue].
 *
 * The board is deleted again at the end (also exercising [DeckApiClient.deleteBoard]) — Deck's
 * API only supports soft-delete (moves to trash, `deletedAt` set), so the board keeps existing
 * server-side until Deck's own trash retention purges it. A failure partway through the test
 * still leaves a timestamped "API Test Board" behind (not yet deleted) to clean up by hand.
 */
class DeckApiClientIntegrationTest {

    @Test
    fun exercisesEveryDeckApiEndpoint() = runBlocking {
        val connection = DeckSettingsStore().connection.value
        assumeTrue(
            "No Deck backend configured — open the desktop app, fill in Settings…, then rerun this test.",
            connection.isConfigured,
        )

        val client = DeckApiClient(connection)
        try {
            // GET /boards
            val boardsBefore = client.getBoards()

            // POST /boards
            val board = client.createBoard(
                title = "API Test Board ${System.currentTimeMillis()}",
                color = "0082C9",
            )

            // GET /boards again — new board must show up
            val boardsAfter = client.getBoards()
            assertTrue(boardsAfter.size > boardsBefore.size, "createBoard did not add a board")
            assertTrue(boardsAfter.any { it.id == board.id }, "created board missing from getBoards()")

            // POST /boards/{boardId}/stacks
            val stack = client.createStack(boardId = board.id, title = "API Test Stack", order = 0)

            // GET /boards/{boardId}/stacks
            val stacks = client.getStacks(board.id)
            assertTrue(stacks.any { it.id == stack.id }, "created stack missing from getStacks()")

            // PUT (OCS) /stacks/{stackId}/done
            client.setDoneStack(boardId = board.id, stackId = stack.id, isDone = true)
            val stacksAfterDoneSet = client.getStacks(board.id)
            assertTrue(
                stacksAfterDoneSet.first { it.id == stack.id }.isDoneColumn,
                "setDoneStack(isDone=true) did not set isDoneColumn",
            )
            client.setDoneStack(boardId = board.id, stackId = stack.id, isDone = false)
            val stacksAfterDoneUnset = client.getStacks(board.id)
            assertTrue(
                !stacksAfterDoneUnset.first { it.id == stack.id }.isDoneColumn,
                "setDoneStack(isDone=false) did not clear isDoneColumn",
            )

            // POST /boards/{boardId}/stacks/{stackId}/cards
            val card = client.createCard(boardId = board.id, stackId = stack.id, title = "API Test Card")

            // PUT /boards/{boardId}/stacks/{stackId}/cards/{cardId}
            val updated = client.updateCard(
                boardId = board.id,
                stackId = stack.id,
                cardId = card.id,
                title = "API Test Card (updated)",
                description = "Written by DeckApiClientIntegrationTest",
                type = "plain",
                order = 0,
                duedate = null,
                archived = false,
                done = null,
            )
            assertEquals("API Test Card (updated)", updated.title)

            // PUT /boards/{boardId}/stacks/{stackId}/cards/{cardId}/reorder
            client.reorderCard(boardId = board.id, targetStackId = stack.id, cardId = card.id, order = 0)

            // Nextcloud Deck seeds every new board with default labels server-side, so the
            // board response should already carry at least one we can (un)assign.
            val label = board.labels.firstOrNull()
            assertTrue(label != null, "created board has no default labels to test assign/removeLabel with")
            val labelId = requireNotNull(label).id!!

            // PUT .../cards/{cardId}/assignLabel
            client.assignLabel(boardId = board.id, stackId = stack.id, cardId = card.id, labelId = labelId)

            // PUT .../cards/{cardId}/removeLabel
            client.removeLabel(boardId = board.id, stackId = stack.id, cardId = card.id, labelId = labelId)

            // POST /boards/{boardId}/labels, then assign the new label to the card
            val created = client.createLabel(boardId = board.id, title = "API Test Label", color = "9B59B6")
            assertEquals("API Test Label", created.title)
            assertTrue(created.id != null, "created label has no id")
            client.assignLabel(boardId = board.id, stackId = stack.id, cardId = card.id, labelId = created.id!!)
            val labelsAfter = client.getBoards().first { it.id == board.id }.labels
            assertTrue(
                labelsAfter.any { it.id == created.id },
                "created label ${created.id} missing from the board's labels: $labelsAfter",
            )

            // GET /boards/{boardId} — members include the owner
            val detail = client.getBoard(board.id)
            assertTrue(detail.users.any { it.uid == connection.username }, "board members missing the owner: ${detail.users}")

            // PUT .../assignUser and .../unassignUser (the owner is always a board member)
            client.assignUser(board.id, stack.id, card.id, connection.username)
            val assigned = client.getStacks(board.id).flatMap { it.cards }.first { it.id == card.id }
            assertTrue(assigned.assignedUsers.any { it.participant?.uid == connection.username }, "assigned user missing: ${assigned.assignedUsers}")
            client.unassignUser(board.id, stack.id, card.id, connection.username)

            // GET sharees (read-only lookup)
            client.searchSharees("a")

            // POST/PUT/DELETE /boards/{boardId}/acl on the throwaway board, with the first group the lookup offers
            val sharee = client.searchSharees("").firstOrNull { it.shareType == 1 }
            if (sharee != null) {
                val acl = client.addAcl(board.id, sharee.shareType, sharee.shareWith, edit = true)
                assertTrue(client.getBoard(board.id).acl.any { it.id == acl.id }, "added acl missing from board")
                client.updateAcl(board.id, acl, edit = false, share = false, manage = false)
                client.deleteAcl(board.id, acl.id)
                assertTrue(client.getBoard(board.id).acl.none { it.id == acl.id }, "acl still present after delete")
            }

            // Comments: create, list, delete
            val comment = client.createComment(card.id, "API test comment")
            assertEquals("API test comment", comment.message)
            assertEquals(connection.username, comment.actorId)
            assertTrue(client.getComments(card.id).any { it.id == comment.id }, "comment missing from list")
            client.deleteComment(card.id, comment.id)
            assertTrue(client.getComments(card.id).none { it.id == comment.id }, "comment still present after delete")

            // Attachments list (empty for a fresh card) and board activity (must not throw)
            assertEquals(0, client.getAttachments(board.id, stack.id, card.id).size)
            client.getActivities(board.id)

            // PUT /boards/{boardId} — change the color dot
            val recolored = client.updateBoard(boardId = board.id, title = board.title, color = "1CBC9B", archived = false)
            assertEquals("1CBC9B", recolored.color?.uppercase())

            // DELETE /boards/{boardId}/stacks/{stackId}/cards/{cardId}
            client.deleteCard(boardId = board.id, stackId = stack.id, cardId = card.id)
            val stacksAfterCardDelete = client.getStacks(board.id)
            val cardStillListed = stacksAfterCardDelete.firstOrNull { it.id == stack.id }?.cards.orEmpty()
                .any { it.id == card.id }
            assertTrue(!cardStillListed, "deleteCard did not remove the card from its stack")

            // DELETE /boards/{boardId} — Deck only soft-deletes, so the board still comes back
            // from getBoards() afterwards, just flagged as trashed.
            client.deleteBoard(board.id)
            val boardsAfterDelete = client.getBoards()
            val trashed = boardsAfterDelete.firstOrNull { it.id == board.id }
            assertTrue(trashed != null && trashed.deletedAt > 0, "deleteBoard did not mark the board as deleted")
        } finally {
            client.close()
        }
    }
}
