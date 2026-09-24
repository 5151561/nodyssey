package io.github.nodyssey.ui.settings

import io.github.nodyssey.data.TermsRepository
import io.github.nodyssey.model.TermsBlock
import io.github.nodyssey.model.TermsDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `retry replaces an error with content`() =
        runTest(dispatcher) {
            val document = termsDocument()
            var calls = 0
            val viewModel =
                PrivacyViewModel(
                    TermsRepository {
                        calls += 1
                        if (calls == 1) throw IllegalStateException("offline")
                        document
                    },
                )
            advanceUntilIdle()
            assertSame(PrivacyUiState.Error, viewModel.uiState.value)

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(PrivacyUiState.Content(document), viewModel.uiState.value)
            assertEquals(2, calls)
        }

    private fun termsDocument() =
        TermsDocument(
            title = "本网站服务协议",
            effectiveDate = "2022-11-24",
            blocks = listOf(TermsBlock.Paragraph("协议正文。")),
        )
}
