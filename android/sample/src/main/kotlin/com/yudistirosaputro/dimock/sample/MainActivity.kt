package com.yudistirosaputro.dimock.sample

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yudistirosaputro.dimock.okhttp.Dimock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The states dimock exists to exercise. Force any of them from the inspector's "Mock this" or from your agent. */
sealed interface UiState {
    data object Loading : UiState
    data class Posts(val posts: List<Post>) : UiState
    data class Detail(val post: Post, val comments: List<Comment>) : UiState
    data object Empty : UiState
    data class Error(val message: String) : UiState
}

class PostsViewModel : ViewModel() {
    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state

    init { loadPosts() }

    fun loadPosts() = run("GET /posts") {
        val posts = ApiFactory.api.posts()
        if (posts.isEmpty()) UiState.Empty else UiState.Posts(posts)
    }

    fun open(id: Int) = run("GET /posts/$id") {
        UiState.Detail(ApiFactory.api.post(id), ApiFactory.api.comments(id))
    }

    fun create() = run("POST /posts") {
        val created = ApiFactory.api.create(NewPost(userId = 1, title = "Hello from dimock", body = "Created ${System.currentTimeMillis()}"))
        UiState.Detail(created, emptyList())
    }

    fun delete(id: Int) = run("DELETE /posts/$id") {
        ApiFactory.api.delete(id)
        val posts = ApiFactory.api.posts()
        if (posts.isEmpty()) UiState.Empty else UiState.Posts(posts)
    }

    private fun run(label: String, block: suspend () -> UiState) {
        _state.value = UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: Exception) {
                UiState.Error("$label failed · ${e.javaClass.simpleName}: ${e.message ?: ""}")
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val notifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* dimock re-posts on its own */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 13+: the inspector is opened from dimock's notification, so ask once. Copy this line into your app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { MaterialTheme { SampleScreen() } }
    }
}

@Composable
fun SampleScreen(viewModel: PostsViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Posts", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (Dimock.isServerRunning) {
                    "Every call below is captured by dimock. Pull down the notification to open the inspector, " +
                        "or from your machine: adb forward tcp:${Dimock.port} tcp:${Dimock.port} && npx dimock health"
                } else {
                    "dimock is not running in this build (release uses the no-op artifact)."
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::loadPosts) { Text("Reload") }
                OutlinedButton(onClick = viewModel::create) { Text("Create post") }
                TextButton(onClick = { Dimock.launch(context) }, enabled = Dimock.isInitialized) { Text("Inspector") }
            }
            HorizontalDivider()
            when (val s = state) {
                UiState.Loading -> CircularProgressIndicator()
                UiState.Empty -> Text("No posts. (Empty state — try Mock this → Empty on GET /posts)")
                is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is UiState.Posts -> PostList(s.posts, onOpen = viewModel::open, onDelete = viewModel::delete)
                is UiState.Detail -> PostDetail(s, onBack = viewModel::loadPosts)
            }
        }
    }
}

@Composable
private fun PostList(posts: List<Post>, onOpen: (Int) -> Unit, onDelete: (Int) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(posts, key = { it.id }) { post ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(post.id) }.padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${post.id}  ${post.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onDelete(post.id) }) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun PostDetail(detail: UiState.Detail, onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("← All posts") }
        Text(detail.post.title, style = MaterialTheme.typography.titleMedium)
        Text(detail.post.body, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Text("${detail.comments.size} comments", style = MaterialTheme.typography.labelLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(detail.comments, key = { it.id }) { c -> Text("${c.email}: ${c.body}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
