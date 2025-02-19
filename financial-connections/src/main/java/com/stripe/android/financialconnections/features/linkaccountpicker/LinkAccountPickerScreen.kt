@file:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)

package com.stripe.android.financialconnections.features.linkaccountpicker

import androidx.activity.compose.BackHandler
import androidx.annotation.RestrictTo
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.airbnb.mvrx.Async
import com.airbnb.mvrx.Fail
import com.airbnb.mvrx.Loading
import com.airbnb.mvrx.Success
import com.airbnb.mvrx.Uninitialized
import com.airbnb.mvrx.compose.collectAsState
import com.airbnb.mvrx.compose.mavericksViewModel
import com.stripe.android.financialconnections.R
import com.stripe.android.financialconnections.features.common.AccessibleDataCallout
import com.stripe.android.financialconnections.features.common.AccountItem
import com.stripe.android.financialconnections.features.common.InstitutionPlaceholder
import com.stripe.android.financialconnections.features.common.LoadingContent
import com.stripe.android.financialconnections.features.common.PaneFooter
import com.stripe.android.financialconnections.features.common.UnclassifiedErrorContent
import com.stripe.android.financialconnections.features.linkaccountpicker.LinkAccountPickerState.Payload
import com.stripe.android.financialconnections.model.FinancialConnectionsSessionManifest.Pane
import com.stripe.android.financialconnections.model.PartnerAccount
import com.stripe.android.financialconnections.presentation.parentViewModel
import com.stripe.android.financialconnections.ui.FinancialConnectionsPreview
import com.stripe.android.financialconnections.ui.LocalImageLoader
import com.stripe.android.financialconnections.ui.TextResource
import com.stripe.android.financialconnections.ui.components.AnnotatedText
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsButton
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsScaffold
import com.stripe.android.financialconnections.ui.components.FinancialConnectionsTopAppBar
import com.stripe.android.financialconnections.ui.components.elevation
import com.stripe.android.financialconnections.ui.theme.FinancialConnectionsTheme
import com.stripe.android.uicore.image.StripeImage

@Composable
internal fun LinkAccountPickerScreen() {
    val viewModel: LinkAccountPickerViewModel = mavericksViewModel()
    val parentViewModel = parentViewModel()
    val state = viewModel.collectAsState()
    BackHandler(enabled = true) {}
    LinkAccountPickerContent(
        state = state.value,
        onCloseClick = { parentViewModel.onCloseWithConfirmationClick(Pane.LINK_ACCOUNT_PICKER) },
        onCloseFromErrorClick = parentViewModel::onCloseFromErrorClick,
        onLearnMoreAboutDataAccessClick = viewModel::onLearnMoreAboutDataAccessClick,
        onNewBankAccountClick = viewModel::onNewBankAccountClick,
        onSelectAccountClick = viewModel::onSelectAccountClick,
        onAccountClick = viewModel::onAccountClick
    )
}

@Composable
private fun LinkAccountPickerContent(
    state: LinkAccountPickerState,
    onCloseClick: () -> Unit,
    onCloseFromErrorClick: (Throwable) -> Unit,
    onLearnMoreAboutDataAccessClick: () -> Unit,
    onNewBankAccountClick: () -> Unit,
    onSelectAccountClick: () -> Unit,
    onAccountClick: (PartnerAccount) -> Unit
) {
    val scrollState = rememberScrollState()
    FinancialConnectionsScaffold(
        topBar = {
            FinancialConnectionsTopAppBar(
                showBack = false,
                elevation = scrollState.elevation,
                onCloseClick = onCloseClick
            )
        }
    ) {
        when (val payload = state.payload) {
            Uninitialized, is Loading -> LinkAccountPickerLoading()
            is Success -> LinkAccountPickerLoaded(
                scrollState = scrollState,
                payload = payload(),
                selectedAccountId = state.selectedAccountId,
                selectNetworkedAccountAsync = state.selectNetworkedAccountAsync,
                onLearnMoreAboutDataAccessClick = onLearnMoreAboutDataAccessClick,
                onSelectAccountClick = onSelectAccountClick,
                onNewBankAccountClick = onNewBankAccountClick,
                onAccountClick = onAccountClick
            )

            is Fail -> UnclassifiedErrorContent(
                error = payload.error,
                onCloseFromErrorClick = onCloseFromErrorClick
            )
        }
    }
}

@Composable
private fun LinkAccountPickerLoading() {
    LoadingContent(
        title = stringResource(R.string.stripe_account_picker_loading_title),
        content = stringResource(R.string.stripe_account_picker_loading_desc)
    )
}

@Composable
private fun LinkAccountPickerLoaded(
    selectedAccountId: String?,
    selectNetworkedAccountAsync: Async<Unit>,
    payload: Payload,
    onLearnMoreAboutDataAccessClick: () -> Unit,
    onSelectAccountClick: () -> Unit,
    onNewBankAccountClick: () -> Unit,
    onAccountClick: (PartnerAccount) -> Unit,
    scrollState: ScrollState
) {
    Column(
        Modifier
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .weight(1f)
        ) {
            Spacer(modifier = Modifier.size(16.dp))
            Title(merchantName = payload.businessName)
            Spacer(modifier = Modifier.size(24.dp))
            payload.accounts.forEach { account ->
                NetworkedAccountItem(
                    selected = account.id == selectedAccountId,
                    account = account,
                    onAccountClicked = { selected ->
                        if (selectNetworkedAccountAsync !is Loading) onAccountClick(selected)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            SelectNewAccount(onClick = onNewBankAccountClick)
            Spacer(modifier = Modifier.size(16.dp))
        }
        PaneFooter(elevation = scrollState.elevation) {
            AccessibleDataCallout(
                payload.accessibleData,
                onLearnMoreAboutDataAccessClick
            )
            Spacer(modifier = Modifier.size(12.dp))
            FinancialConnectionsButton(
                enabled = selectedAccountId != null,
                loading = selectNetworkedAccountAsync is Loading,
                onClick = onSelectAccountClick,
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.stripe_link_account_picker_cta))
            }
        }
    }
}

@Composable
private fun NetworkedAccountItem(
    account: PartnerAccount,
    onAccountClicked: (PartnerAccount) -> Unit,
    selected: Boolean
) {
    AccountItem(
        selected = selected,
        onAccountClicked = onAccountClicked,
        // Override the default disabled to show that the account is disconnected
        account = account.copy(
            allowSelectionMessage = stringResource(
                id = R.string.stripe_link_account_picker_disconnected
            )
        )
    ) {
        val modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(3.dp))
        val institutionIcon = account.institution?.icon?.default
        when {
            institutionIcon.isNullOrEmpty() -> InstitutionPlaceholder(modifier)
            else -> StripeImage(
                url = institutionIcon,
                imageLoader = LocalImageLoader.current,
                contentDescription = null,
                modifier = modifier,
                contentScale = ContentScale.Crop,
                errorContent = { InstitutionPlaceholder(modifier) }
            )
        }
    }
}

@Composable
private fun SelectNewAccount(
    onClick: () -> Unit
) {
    val shape = remember { RoundedCornerShape(8.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                color = FinancialConnectionsTheme.colors.borderDefault,
                shape = shape
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            val brandColor = FinancialConnectionsTheme.colors.textBrand
            Image(
                modifier = Modifier
                    .size(24.dp)
                    .background(color = brandColor.copy(alpha = 0.1f), CircleShape)
                    .padding(3.dp)
                    .clip(CircleShape),
                imageVector = Icons.Filled.Add,
                colorFilter = ColorFilter.tint(brandColor),
                contentDescription = stringResource(id = R.string.stripe_link_account_picker_new_account)
            )
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = stringResource(id = R.string.stripe_link_account_picker_new_account),
                style = FinancialConnectionsTheme.typography.body,
                color = FinancialConnectionsTheme.colors.textBrand
            )
        }
    }
}

@Composable
private fun Title(
    merchantName: String?
) {
    AnnotatedText(
        text = TextResource.Text(
            when {
                merchantName != null -> stringResource(
                    R.string.stripe_link_account_picker_title,
                    merchantName
                )

                else -> stringResource(
                    R.string.stripe_link_account_picker_title_nobusiness
                )
            }
        ),
        defaultStyle = FinancialConnectionsTheme.typography.subtitle,
        annotationStyles = emptyMap(),
        onClickableTextClick = {},
    )
}

@Composable
@Preview(group = "LinkAccountPicker Pane")
internal fun LinkAccountPickerScreenPreview(
    @PreviewParameter(LinkAccountPickerPreviewParameterProvider::class)
    state: LinkAccountPickerState
) {
    FinancialConnectionsPreview {
        LinkAccountPickerContent(
            state = state,
            onCloseClick = {},
            onCloseFromErrorClick = {},
            onLearnMoreAboutDataAccessClick = {},
            onNewBankAccountClick = {},
            onSelectAccountClick = {},
            onAccountClick = {}
        )
    }
}
