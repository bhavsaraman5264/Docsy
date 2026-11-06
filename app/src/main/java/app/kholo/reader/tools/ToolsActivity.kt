package app.kholo.reader.tools

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kholo.reader.KholoApp
import app.kholo.reader.R
import app.kholo.reader.ViewerActivity
import app.kholo.reader.settings.LanguageStore
import app.kholo.reader.ui.theme.AppTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val EXTRA_TOOL = "tool"
private const val PDF_MIME = "application/pdf"
private const val MAX_PHOTOS = 50

/** Har tool isi Activity me chalta hai. Camera ki permission nahi lagti, photo system camera app leta hai. */
class ToolsActivity : ComponentActivity() {
    private val authority: String get() = "$packageName.files"

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val kind = ToolKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_TOOL) } ?: ToolKind.Merge
        val container = (application as KholoApp).container
        setContent {
            AppTheme(container.settings) {
                val viewModel: ToolsViewModel = viewModel(
                    factory = viewModelFactory { initializer { ToolsViewModel(kind, container) } },
                )
                // Camera app aksar phone ghuma deti hai. Activity dobara bane to bhi photo ka pata rahe.
                var scanUri by rememberSaveable { mutableStateOf<Uri?>(null) }
                val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    uri?.let { viewModel.addInputs(listOf(it)) }
                }
                val pickMany = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments(),
                ) { viewModel.addInputs(it) }
                val pickPhotos = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS),
                ) { viewModel.addInputs(it) }
                val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
                    val uri = scanUri
                    if (taken && uri != null) viewModel.addInputs(listOf(uri))
                }
                val saver = rememberLauncherForActivityResult(CreateDocumentAny()) { uri ->
                    // ViewModel se, kyunki file picker ke beech phone ghume to Activity nayi hoti hai.
                    val result = viewModel.state.value.result
                    if (uri != null && result != null) save(result.file, uri)
                }
                val pick: () -> Unit = {
                    when (kind) {
                        ToolKind.Photos -> pickPhotos.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                        ToolKind.Scan -> {
                            val uri = FileProvider.getUriForFile(this, authority, viewModel.newScanFile())
                            scanUri = uri
                            try {
                                camera.launch(uri)
                            } catch (_: ActivityNotFoundException) {
                                toast(R.string.error_no_app)
                            }
                        }
                        ToolKind.Merge -> pickMany.launch(arrayOf(PDF_MIME))
                        else -> pickOne.launch(arrayOf(PDF_MIME))
                    }
                }
                LaunchedEffect(Unit) {
                    if (savedInstanceState == null) {
                        val given = intent.data
                        if (given != null) viewModel.addInputs(listOf(given)) else pick()
                    }
                }
                ToolScreen(
                    viewModel = viewModel,
                    actions = ToolActions(
                        onBack = ::finish,
                        onPick = pick,
                        onSave = { saver.launch(it.name to it.mime) },
                        onShare = ::share,
                        onOpen = ::open,
                    ),
                )
            }
        }
    }

    /** Badi file main thread par likhne se app atak jati hai. */
    private fun save(file: File, target: Uri) {
        lifecycleScope.launch { toast(withContext(Dispatchers.IO) { write(file, target) }) }
    }

    private fun write(file: File, target: Uri): Int = try {
        val stream = contentResolver.openOutputStream(target)
        if (stream == null) {
            R.string.error_file_gone
        } else {
            stream.use { out -> file.inputStream().use { it.copyTo(out) } }
            R.string.tool_saved
        }
    } catch (_: IOException) {
        R.string.error_no_space
    } catch (_: SecurityException) {
        R.string.error_file_gone
    } catch (_: IllegalArgumentException) {
        R.string.error_file_gone
    }

    private fun share(result: ToolResult) {
        val uri = FileProvider.getUriForFile(this, authority, result.file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(result.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        launch(Intent.createChooser(send, null))
    }

    private fun open(result: ToolResult) {
        val uri = FileProvider.getUriForFile(this, authority, result.file)
        launch(Intent(Intent.ACTION_VIEW, uri, this, ViewerActivity::class.java))
    }

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            toast(R.string.error_no_app)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun intent(context: Context, kind: ToolKind): Intent =
            Intent(context, ToolsActivity::class.java).putExtra(EXTRA_TOOL, kind.name)
    }
}

/** Save ke waqt naam aur type dono dena hota hai. */
private class CreateDocumentAny : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.second)
            .putExtra(Intent.EXTRA_TITLE, input.first)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        intent?.data.takeIf { resultCode == RESULT_OK_CODE }

    private companion object {
        const val RESULT_OK_CODE = -1
    }
}
