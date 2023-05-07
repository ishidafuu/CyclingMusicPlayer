package com.example.cyclemusic

import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.example.cyclemusic.databinding.ActivityMainBinding

private const val TAG = "MainActivity"

// MainActivityクラスはAppCompatActivityを継承します
@UnstableApi 
class MainActivity : AppCompatActivity() {

    // viewBindingを遅延初期化
    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    // プレーヤーのリスナーを初期化
    private val playbackStateListener: Player.Listener = playbackStateListener()
    private var player: ExoPlayer? = null

    // プレーヤーの状態を保持する変数
    private var playWhenReady = true
    private var currentItem = 0
    private var playbackPosition = 0L
    private val requestCodePermission = 1
    private lateinit var mediaUrlList: List<String>

    // アクティビティが作成されたときに呼び出されるコールバック
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)

        // SeekBarのリスナーを設定
        viewBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val speed = progress / 100f
                    setPlaybackSpeedWithoutChangingPitch(speed)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // ストレージへのアクセス許可をリクエスト
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                requestCodePermission
            )
        } else {
            loadMp3FilesAndInitializePlayer()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermission) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadMp3FilesAndInitializePlayer()
            } else {
                // Permission denied
            }
        }
    }

    private fun loadMp3FilesAndInitializePlayer() {
        val mp3FileList = getMp3FilesFromStorage()
        if (mp3FileList.isNotEmpty()) {
            mediaUrlList = mp3FileList.map { it.contentUri.toString() }
            initializePlayer()
        } else {
            // No MP3 files found
        }
    }

    private fun getMp3FilesFromStorage(): List<AudioFile> {
        val contentResolver = contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cursor = contentResolver.query(uri, null, null, null, null)

        val mp3Files = mutableListOf<AudioFile>()

        cursor?.use {
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(mimeTypeColumn)
                if (mimeType == "audio/mpeg") {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn)
                    val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                    mp3Files.add(
                        AudioFile(
                            id,
                            title,
                            contentUri
                        )
                    )
                }
            }
        }

        return mp3Files
    }


    // アクティビティが開始されたときに呼び出されるコールバック
    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
        }
    }

    // アクティビティが再開されたときに呼び出されるコールバック
    public override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (Util.SDK_INT <= 23 || player == null) {
            initializePlayer()
        }
    }

    // アクティビティが一時停止されたときに呼び出されるコールバック
    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    // アクティビティが停止されたときに呼び出されるコールバック
    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            releasePlayer()
        }
    }

    // プレーヤーを初期化する関数
    private fun initializePlayer() {
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exoPlayer ->
                viewBinding.videoView.player = exoPlayer

                val mediaItem = MediaItem.Builder()
                    .setUri(getString(R.string.media_url_dash))
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
                exoPlayer.setMediaItem(mediaItem)
                exoPlayer.playWhenReady = playWhenReady
                exoPlayer.seekTo(currentItem, playbackPosition)
                exoPlayer.addListener(playbackStateListener)
                exoPlayer.prepare()
            }
    }

    // プレーヤーを解放する関数
    private fun releasePlayer() {
        player?.let { exoPlayer ->
            playbackPosition = exoPlayer.currentPosition
            currentItem = exoPlayer.currentMediaItemIndex
            playWhenReady = exoPlayer.playWhenReady
            exoPlayer.removeListener(playbackStateListener)
            exoPlayer.release()
        }
        player = null
    }

    // プレーヤーの再生速度を設定するメソッド (ピッチを変更しない)
    fun setPlaybackSpeedWithoutChangingPitch(speed: Float) {
        player?.let { exoPlayer ->
            val currentPitch = exoPlayer.playbackParameters.pitch
            val playbackParameters = PlaybackParameters(speed, currentPitch)
            exoPlayer.setPlaybackParameters(playbackParameters)
        }
    }
    
    // システムUIを非表示にする関数
    @SuppressLint("InlinedApi")
    private fun hideSystemUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, viewBinding.videoView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

// プレーヤーの再生状態が変更されたときにログに出力するリスナー関数
private fun playbackStateListener() = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        val stateString: String = when (playbackState) {
            ExoPlayer.STATE_IDLE -> "ExoPlayer.STATE_IDLE -"
            ExoPlayer.STATE_BUFFERING -> "ExoPlayer.STATE_BUFFERING -"
            ExoPlayer.STATE_READY -> "ExoPlayer.STATE_READY -"
            ExoPlayer.STATE_ENDED -> "ExoPlayer.STATE_ENDED -"
            else -> "UNKNOWN_STATE -"
        }
        Log.d(TAG, "changed state to $stateString")
    }
}

data class AudioFile(
    val id: Long,
    val title: String,
    val contentUri: Uri
)
