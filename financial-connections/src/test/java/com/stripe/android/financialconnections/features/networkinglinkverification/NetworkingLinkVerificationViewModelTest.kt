package com.stripe.android.financialconnections.features.networkinglinkverification

import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.test.MavericksTestRule
import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.Logger
import com.stripe.android.financialconnections.ApiKeyFixtures.consumerSession
import com.stripe.android.financialconnections.ApiKeyFixtures.partnerAccount
import com.stripe.android.financialconnections.ApiKeyFixtures.partnerAccountList
import com.stripe.android.financialconnections.ApiKeyFixtures.sessionManifest
import com.stripe.android.financialconnections.TestFinancialConnectionsAnalyticsTracker
import com.stripe.android.financialconnections.domain.ConfirmVerification
import com.stripe.android.financialconnections.domain.GetManifest
import com.stripe.android.financialconnections.domain.GoNext
import com.stripe.android.financialconnections.domain.LookupConsumerAndStartVerification
import com.stripe.android.financialconnections.domain.MarkLinkVerified
import com.stripe.android.financialconnections.domain.PollNetworkedAccounts
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest.Pane.INSTITUTION_PICKER
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest.Pane.LINK_ACCOUNT_PICKER
import com.stripe.android.model.ConsumerSession
import com.stripe.android.model.VerificationType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NetworkingLinkVerificationViewModelTest {

    @get:Rule
    val mavericksTestRule = MavericksTestRule()

    private val getManifest = mock<GetManifest>()
    private val goNext = mock<GoNext>()
    private val confirmVerification = mock<ConfirmVerification>()
    private val pollNetworkedAccounts = mock<PollNetworkedAccounts>()
    private val lookupConsumerAndStartVerification = mock<LookupConsumerAndStartVerification>()
    private val markLinkVerified = mock<MarkLinkVerified>()
    private val analyticsTracker = TestFinancialConnectionsAnalyticsTracker()

    private fun buildViewModel(
        state: NetworkingLinkVerificationState = NetworkingLinkVerificationState()
    ) = NetworkingLinkVerificationViewModel(
        goNext = goNext,
        getManifest = getManifest,
        lookupConsumerAndStartVerification = lookupConsumerAndStartVerification,
        confirmVerification = confirmVerification,
        markLinkVerified = markLinkVerified,
        pollNetworkedAccounts = pollNetworkedAccounts,
        analyticsTracker = analyticsTracker,
        logger = Logger.noop(),
        initialState = state
    )

    @Test
    fun `init - starts SMS verification with consumer session secret`() = runTest {
        val email = "test@test.com"
        val consumerSession = consumerSession()
        whenever(getManifest()).thenReturn(
            sessionManifest().copy(accountholderCustomerEmailAddress = email)
        )

        val onStartVerificationCaptor = argumentCaptor<suspend () -> Unit>()
        val onVerificationStartedCaptor = argumentCaptor<suspend (ConsumerSession) -> Unit>()

        val viewModel = buildViewModel()

        assertThat(viewModel.awaitState().payload).isInstanceOf(Loading::class.java)

        verify(lookupConsumerAndStartVerification).invoke(
            email = eq(email),
            businessName = anyOrNull(),
            verificationType = eq(VerificationType.SMS),
            onConsumerNotFound = any(),
            onLookupError = any(),
            onStartVerification = onStartVerificationCaptor.capture(),
            onVerificationStarted = onVerificationStartedCaptor.capture(),
            onStartVerificationError = any()
        )

        onStartVerificationCaptor.firstValue()
        onVerificationStartedCaptor.firstValue(consumerSession)

        val state = viewModel.awaitState()
        assertThat(state.payload()!!.consumerSessionClientSecret)
            .isEqualTo(consumerSession.clientSecret)
    }

    @Test
    fun `init - ConsumerNotFound sends analytics and navigates to institution picker`() = runTest {
        val email = "test@test.com"
        val onConsumerNotFoundCaptor = argumentCaptor<suspend () -> Unit>()

        whenever(getManifest()).thenReturn(
            sessionManifest().copy(accountholderCustomerEmailAddress = email)
        )

        val viewModel = buildViewModel()

        assertThat(viewModel.awaitState().payload).isInstanceOf(Loading::class.java)

        verify(lookupConsumerAndStartVerification).invoke(
            email = eq(email),
            businessName = anyOrNull(),
            verificationType = eq(VerificationType.SMS),
            onConsumerNotFound = onConsumerNotFoundCaptor.capture(),
            onLookupError = any(),
            onStartVerification = any(),
            onVerificationStarted = any(),
            onStartVerificationError = any()
        )

        onConsumerNotFoundCaptor.firstValue()

        assertThat(viewModel.awaitState().payload).isInstanceOf(Loading::class.java)
        verify(goNext).invoke(INSTITUTION_PICKER)
        analyticsTracker.assertContainsEvent(
            "linked_accounts.networking.verification.error",
            mapOf(
                "pane" to "networking_link_verification",
                "error" to "ConsumerNotFoundError"
            )
        )
    }

    @Test
    fun `otpEntered - on valid OTP and no accounts confirms and navigates to manifest next pane`() =
        runTest {
            val email = "test@test.com"
            val consumerSession = consumerSession()
            val onStartVerificationCaptor = argumentCaptor<suspend () -> Unit>()
            val onVerificationStartedCaptor = argumentCaptor<suspend (ConsumerSession) -> Unit>()
            val linkVerifiedManifest = sessionManifest().copy(nextPane = INSTITUTION_PICKER)

            whenever(getManifest()).thenReturn(
                sessionManifest().copy(accountholderCustomerEmailAddress = email)
            )
            // verify succeeds
            whenever(markLinkVerified()).thenReturn(linkVerifiedManifest)
            // polling returns no networked accounts
            whenever(pollNetworkedAccounts(any()))
                .thenReturn(partnerAccountList().copy(data = emptyList()))

            val viewModel = buildViewModel()

            verify(lookupConsumerAndStartVerification).invoke(
                email = eq(email),
                businessName = anyOrNull(),
                verificationType = eq(VerificationType.SMS),
                onConsumerNotFound = any(),
                onLookupError = any(),
                onStartVerification = onStartVerificationCaptor.capture(),
                onVerificationStarted = onVerificationStartedCaptor.capture(),
                onStartVerificationError = any()
            )

            onStartVerificationCaptor.firstValue()
            onVerificationStartedCaptor.firstValue(consumerSession)

            val otpController = viewModel.awaitState().payload()!!.otpElement.controller

            // enters valid OTP
            for (i in 0 until otpController.otpLength) {
                otpController.onValueChanged(i, "1")
            }

            verify(confirmVerification).sms(any(), eq("111111"))
            verify(goNext).invoke(linkVerifiedManifest.nextPane)
        }

    @Test
    fun `otpEntered - valid OTP and accounts confirms and navigates to LINK_ACCOUNT_PICKER`() =
        runTest {
            val email = "test@test.com"
            val consumerSession = consumerSession()
            val onStartVerificationCaptor = argumentCaptor<suspend () -> Unit>()
            val onVerificationStartedCaptor = argumentCaptor<suspend (ConsumerSession) -> Unit>()
            val linkVerifiedManifest = sessionManifest().copy(nextPane = INSTITUTION_PICKER)
            whenever(getManifest()).thenReturn(
                sessionManifest().copy(accountholderCustomerEmailAddress = email)
            )

            // polling returns some networked accounts
            val partnerAccountsList = partnerAccountList().copy(data = (listOf(partnerAccount())))
            whenever(pollNetworkedAccounts(any())).thenReturn(partnerAccountsList)
            whenever(markLinkVerified()).thenReturn((linkVerifiedManifest))

            val viewModel = buildViewModel()

            verify(lookupConsumerAndStartVerification).invoke(
                email = eq(email),
                businessName = anyOrNull(),
                verificationType = eq(VerificationType.SMS),
                onConsumerNotFound = any(),
                onLookupError = any(),
                onStartVerification = onStartVerificationCaptor.capture(),
                onVerificationStarted = onVerificationStartedCaptor.capture(),
                onStartVerificationError = any()
            )

            onStartVerificationCaptor.firstValue()
            onVerificationStartedCaptor.firstValue(consumerSession)

            val otpController = viewModel.awaitState().payload()!!.otpElement.controller

            // enters valid OTP
            for (i in 0 until otpController.otpLength) {
                otpController.onValueChanged(i, "1")
            }

            verify(confirmVerification).sms(any(), eq("111111"))
            verify(goNext).invoke(LINK_ACCOUNT_PICKER)
        }
}
