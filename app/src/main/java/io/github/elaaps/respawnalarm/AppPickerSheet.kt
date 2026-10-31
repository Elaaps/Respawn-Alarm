package io.github.elaaps.respawnalarm

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(selectedPackage: String?, defaultPackage: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps by produceState<List<LaunchableApp>?>(null) {
        value = withContext(Dispatchers.Default) { loadLaunchableApps(context) }
    }
    val query = rememberTextFieldState()
    val search = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val inputField = @Composable {
        SearchBarDefaults.InputField(
            textFieldState = query,
            searchBarState = search,
            onSearch = {},
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        )
    }
    SettingSheet(stringResource(R.string.target_app), onDismiss) { close ->
        val pick = { packageName: String ->
            onPick(packageName)
            scope.launch {
                search.animateToCollapsed()
                close()
            }
            Unit
        }
        SearchBar(state = search, inputField = inputField, modifier = Modifier.fillMaxWidth())
        AppList(apps, selectedPackage, defaultPackage, pick, Modifier.weight(1f))
        ExpandedFullScreenSearchBar(state = search, inputField = inputField) {
            val matches = apps?.filter {
                it.label.contains(query.text, ignoreCase = true) || it.packageName.contains(query.text, ignoreCase = true)
            }
            AppList(matches, selectedPackage, defaultPackage, pick, Modifier.padding(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppList(
    apps: List<LaunchableApp>?,
    selectedPackage: String?,
    defaultPackage: String?,
    onPick: (String) -> Unit,
    modifier: Modifier,
) {
    if (apps == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
            SegmentedListItem(
                selected = app.packageName == selectedPackage,
                onClick = { onPick(app.packageName) },
                shapes = ListItemDefaults.segmentedShapes(index, apps.size),
                leadingContent = { Image(app.icon, contentDescription = null, modifier = Modifier.size(APP_ICON_SIZE)) },
                supportingContent = { Text(app.packageName) },
                trailingContent = if (app.packageName == selectedPackage) {
                    { Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
            ) { Text(markDefault(app.label, isDefault = app.packageName == defaultPackage)) }
        }
    }
}

private val APP_ICON_SIZE = 40.dp
