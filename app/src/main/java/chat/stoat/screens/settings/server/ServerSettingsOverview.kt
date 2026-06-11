package chat.stoat.screens.settings.server

import android.app.Application
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.activities.StoatTweenFloat
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.microservices.autumn.uploadToAutumn
import chat.stoat.api.routes.server.patchServer
import chat.stoat.composables.generic.InlineMediaPicker
import chat.stoat.composables.generic.ListHeader
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.Server
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import java.io.File

class ServerSettingsOverviewViewModel(val context: Application) : ViewModel() {
    var initialServer by mutableStateOf<Server?>(null)

    var serverName by mutableStateOf("")
    var serverDescription by mutableStateOf("")

    var iconModel by mutableStateOf<Any?>(null)
    var iconIsUploading by mutableStateOf(false)
    var iconUploadProgress by mutableFloatStateOf(0f)

    var bannerModel by mutableStateOf<Any?>(null)
    var bannerIsUploading by mutableStateOf(false)
    var bannerUploadProgress by mutableFloatStateOf(0f)

    var uploadError by mutableStateOf<String?>(null)
    var updateError by mutableStateOf<String?>(null)

    fun populateWithServer(serverId: String) {
        val server = StoatAPI.serverCache[serverId]
        initialServer = server
        server?.let {
            serverName = it.name ?: ""
            serverDescription = it.description ?: ""
            iconModel = it.icon?.let { icon -> "$STOAT_FILES/icons/${icon.id}" }
            bannerModel = it.banner?.let { banner -> "$STOAT_FILES/banners/${banner.id}/${banner.filename}" }
        }
    }

    private fun unsetServerImage(field: String) {
        uploadError = null

        initialServer?.id?.let { serverId ->
            viewModelScope.launch {
                try {
                    patchServer(serverId, remove = listOf(field))
                    if (field == "Icon") {
                        iconModel = null
                    } else if (field == "Banner") {
                        bannerModel = null
                    }
                } catch (e: Exception) {
                    updateError = e.message
                } finally {
                    if (field == "Icon") {
                        iconIsUploading = false
                    } else if (field == "Banner") {
                        bannerIsUploading = false
                    }
                }
            }
        } ?: run {
            if (field == "Icon") {
                iconIsUploading = false
            } else if (field == "Banner") {
                bannerIsUploading = false
            }
        }
    }

    fun pickIcon(newModel: Any?) {
        iconModel = newModel
        iconUploadProgress = 0f
        pickServerImage(
            newModel = newModel,
            category = "icons",
            removeField = "Icon",
            setUploading = { iconIsUploading = it },
            setProgress = { iconUploadProgress = it },
            onUploaded = { id -> patchServer(initialServer?.id ?: "", icon = id) }
        )
    }

    fun pickBanner(newModel: Any?) {
        bannerModel = newModel
        bannerUploadProgress = 0f
        pickServerImage(
            newModel = newModel,
            category = "banners",
            removeField = "Banner",
            setUploading = { bannerIsUploading = it },
            setProgress = { bannerUploadProgress = it },
            onUploaded = { id -> patchServer(initialServer?.id ?: "", banner = id) }
        )
    }

    private fun pickServerImage(
        newModel: Any?,
        category: String,
        removeField: String,
        setUploading: (Boolean) -> Unit,
        setProgress: (Float) -> Unit,
        onUploaded: suspend (String) -> Unit
    ) {
        uploadError = null
        setProgress(0f)

        val uri = when (newModel) {
            is Uri -> newModel
            is String -> Uri.parse(newModel)
            else -> null
        } ?: run {
            setUploading(true)
            unsetServerImage(removeField)
            return
        }

        val mediaFile = File(context.cacheDir, uri.lastPathSegment ?: category)

        mediaFile.outputStream().use { output ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.copyTo(output)
            }
        }

        val mime = context.contentResolver.getType(uri)
        if (mime?.endsWith("webp") == true) {
            uploadError = "WebP is not supported"
            return
        }

        viewModelScope.launch {
            setUploading(true)
            try {
                val id = uploadToAutumn(
                    mediaFile,
                    uri.lastPathSegment ?: category,
                    category,
                    ContentType.parse(mime ?: "image/*"),
                    onProgress = { soFar, outOf ->
                        setProgress(soFar.toFloat() / outOf.toFloat())
                    }
                )

                onUploaded(id)
            } catch (e: Exception) {
                uploadError = e.message
                setProgress(0f)
            } finally {
                setUploading(false)
            }
        }
    }

    fun updateServer() {
        updateError = null
        viewModelScope.launch {
            try {
                patchServer(
                    initialServer?.id ?: "",
                    name = if (serverName != initialServer?.name) serverName else null,
                    description = if (serverDescription != initialServer?.description) serverDescription else null
                )
                initialServer = initialServer?.copy(
                    name = serverName,
                    description = serverDescription
                )
            } catch (e: Exception) {
                updateError = e.message
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsOverview(
    navController: NavController,
    serverId: String,
    viewModel: ServerSettingsOverviewViewModel = koinViewModel()
) {
    val currentServer = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    LaunchedEffect(serverId) {
        viewModel.populateWithServer(serverId)
    }

    val serverInfoUpdated by remember(
        currentServer,
        viewModel.serverName,
        viewModel.serverDescription
    ) {
        derivedStateOf {
            currentServer?.let { server ->
                (server.name ?: "") != viewModel.serverName ||
                        (server.description ?: "") != viewModel.serverDescription
            } ?: false
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.server_settings_overview),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = serverInfoUpdated,
                enter = scaleIn(animationSpec = StoatTweenFloat),
                exit = scaleOut(animationSpec = StoatTweenFloat)
            ) {
                FloatingActionButton(onClick = { viewModel.updateServer() }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24dp),
                        contentDescription = stringResource(R.string.server_settings_overview_save)
                    )
                }
            }
        }
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            currentServer?.let {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    ListHeader {
                        Text(stringResource(R.string.server_settings_overview_info))
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InlineMediaPicker(
                            currentModel = viewModel.iconModel,
                            onPick = { viewModel.pickIcon(it) },
                            circular = true,
                            mimeType = "image/*",
                            canRemove = true,
                            enabled = !viewModel.iconIsUploading,
                            onRemove = { viewModel.pickIcon(null) },
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    AnimatedVisibility(visible = viewModel.iconIsUploading) {
                        LinearProgressIndicator(
                            progress = { viewModel.iconUploadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    ListHeader {
                        Text(stringResource(R.string.server_settings_overview_banner))
                    }

                    InlineMediaPicker(
                        currentModel = viewModel.bannerModel,
                        onPick = { viewModel.pickBanner(it) },
                        mimeType = "image/*",
                        canRemove = true,
                        enabled = !viewModel.bannerIsUploading,
                        onRemove = { viewModel.pickBanner(null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    )

                    AnimatedVisibility(visible = viewModel.bannerIsUploading) {
                        LinearProgressIndicator(
                            progress = { viewModel.bannerUploadProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    AnimatedVisibility(visible = viewModel.uploadError != null) {
                        Text(
                            viewModel.uploadError
                                ?: stringResource(R.string.server_settings_overview_update_info_error_fallback),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    AnimatedVisibility(visible = viewModel.updateError != null) {
                        Text(
                            viewModel.updateError
                                ?: stringResource(R.string.server_settings_overview_update_info_error_fallback),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 16.dp)
                        )
                    }

                    TextField(
                        label = {
                            Text(stringResource(R.string.server_settings_overview_name))
                        },
                        value = viewModel.serverName,
                        onValueChange = { viewModel.serverName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        singleLine = true
                    )

                    TextField(
                        label = {
                            Text(stringResource(R.string.server_settings_overview_description))
                        },
                        placeholder = {
                            Text(stringResource(R.string.server_settings_overview_description_hint))
                        },
                        value = viewModel.serverDescription,
                        onValueChange = { viewModel.serverDescription = it },
                        modifier = Modifier
                            .animateContentSize()
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 16.dp),
                        singleLine = false,
                        minLines = 3
                    )
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
