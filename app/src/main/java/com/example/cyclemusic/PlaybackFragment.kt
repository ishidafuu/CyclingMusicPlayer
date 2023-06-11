import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyclemusic.MainActivity
import com.example.cyclemusic.R
import java.io.File
import java.util.Random
import java.util.concurrent.TimeUnit

data class Song(val name: String, val path: String, var tempo: Int, var duration: Long = 0L)

class PlaybackFragment : Fragment() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var songRecyclerView: RecyclerView
    private lateinit var stopButton: Button
    private lateinit var playButton: Button
    private lateinit var tempoUpButton: Button
    private lateinit var tempoDownButton: Button
    private lateinit var defaultTempoButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var songList: MutableList<Song>
    private lateinit var mediaSession: MediaSessionCompat
    private val viewModel: SharedViewModel by activityViewModels()
    private var folderPath: String? = null
    private var currentSongName: String? = null
    private val defaultTemp: Int = 100
    private val deltaTemp: Int = 2
    private val minTemp: Int = 10
    private val maxTemp: Int = 200
    private var playbackSpeed: Int = defaultTemp
    private var songStartTime: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.playback_fragment, container, false)

        setupViews(view)
        setupObservables()
        setupButtons()
        setupMediaSession()
        loadLastFolderPath()
        populateSongList()

        return view
    }


    private fun setupViews(view: View) {
        songRecyclerView = view.findViewById(R.id.songRecyclerView)
        stopButton = view.findViewById(R.id.stopButton)
        playButton = view.findViewById(R.id.playButton)
        tempoUpButton = view.findViewById(R.id.tempoUpButton)
        tempoDownButton = view.findViewById(R.id.tempoDownButton)
        defaultTempoButton = view.findViewById(R.id.defaultTempoButton)

        exoPlayer = ExoPlayer.Builder(requireContext()).build().apply {
            setMediaItem(MediaItem.fromUri(""))
            prepare()
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)
                if (state == Player.STATE_ENDED) {
                    val duration = getDurationForSong(currentSongName)
                    val updatedDuration = duration + exoPlayer.duration
                    saveDurationForSong(currentSongName, updatedDuration)
                    updateSongDuration(currentSongName, updatedDuration)
                    updateSongInRecyclerView(currentSongName)
                    playNextSong()
                }
            }
        })


        songList = ArrayList()

        val adapter = SongAdapter(songList)

        songRecyclerView.adapter = adapter
        songRecyclerView.layoutManager = LinearLayoutManager(context)
    }

    private fun setupObservables() {
        viewModel.selectedFolderPath.observe(viewLifecycleOwner) { folderPath ->
            setFolderPath(folderPath)
        }
    }

    private fun setupButtons() {
        stopButton.setOnClickListener {
            stopMedia()
        }

        playButton.setOnClickListener {
            playRandomMedia()
        }

        tempoUpButton.setOnClickListener {
            increaseTempo()
            updateSongTempo(currentSongName, playbackSpeed)
            updateSongInRecyclerView(currentSongName)
        }

        tempoDownButton.setOnClickListener {
            decreaseTempo()
            updateSongTempo(currentSongName, playbackSpeed)
            updateSongInRecyclerView(currentSongName)
        }

        defaultTempoButton.setOnClickListener {
            resetTempo()
            updateSongTempo(currentSongName, playbackSpeed)
            updateSongInRecyclerView(currentSongName)
        }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(requireContext(), "PlaybackFragment")
        mediaSession.setCallback(object : MediaSessionCompat.Callback() {
            // 既存のコード

            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {

                val keyEvent = mediaButtonIntent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent?.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (currentSongName == null) {
                                playRandomMedia()
                            } else {
                                exoPlayer.playWhenReady = true
                            }
                        }

                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            exoPlayer.playWhenReady = false
                        }

                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            exoPlayer.playWhenReady = !exoPlayer.isPlaying
                        }

                        KeyEvent.KEYCODE_MEDIA_STOP -> {
                            stopMedia()
                        }
                        // 他のキーイベントもここで処理します
                    }
                }

                return super.onMediaButtonEvent(mediaButtonIntent)
            }
        })

        // 重要：メディアセッションをアクティブにする
        mediaSession.isActive = true
    }

    private fun loadLastFolderPath() {
        sharedPreferences = requireContext().getSharedPreferences("CycleMusicPrefs", Context.MODE_PRIVATE)
        val lastFolderPath = sharedPreferences.getString("LastFolderPath", null)
        if (lastFolderPath != null) {
            setFolderPath(lastFolderPath)
        }
    }

    private fun populateSongList() {
        val newSongList = if (folderPath != null) {
            ArrayList(fetchAllMp3Files(File(folderPath)))
        } else {
            ArrayList(fetchAllMp3Files(Environment.getExternalStorageDirectory()))
        }
        songList.clear()
        songList.addAll(newSongList)

        val songAdapter = songRecyclerView.adapter as SongAdapter
        songAdapter.notifyDataSetChanged()
    }

    private fun fetchAllMp3Files(root: File): List<Song> {
        val songFiles = ArrayList<Song>()
        val files = root.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    songFiles.addAll(fetchAllMp3Files(file))
                } else if (file.name.endsWith(".mp3", ignoreCase = true)) {
                    val mediaItem = MediaItem.fromUri(Uri.parse(file.absolutePath))
                    val songName = mediaItem.mediaMetadata.title?.toString() ?: File(file.absolutePath).name
                    val tempo = getTempoForSong(songName)
                    songFiles.add(Song(file.name, file.absolutePath, tempo))
                }
            }
        }
        return songFiles
    }

    private fun setFolderPath(folderPath: String) {
        this.folderPath = folderPath
        sharedPreferences.edit().putString("LastFolderPath", folderPath).apply()

        val folderName = File(folderPath).name
        (activity as MainActivity).supportActionBar?.title = folderName

        populateSongList()
    }

    fun updateFileList() {
        viewModel.selectedFolderPath.value?.let {
            setFolderPath(it)
        }
    }

    private fun stopMedia() {
        exoPlayer.stop()
        val songAdapter = (songRecyclerView.adapter as SongAdapter)
        val previousSelectedPosition = songAdapter.selectedPosition

        stopSongDurationTracking(currentSongName)
        currentSongName = null
        songAdapter.selectedPosition = -1
        songAdapter.notifyItemChanged(previousSelectedPosition)
    }

    private fun stopSongDurationTracking(songName: String?) {
        if (songName == null) {
            return
        }

        val elapsedTime = System.currentTimeMillis() - songStartTime
        val previousDuration = getDurationForSong(songName)
        saveDurationForSong(songName, previousDuration + elapsedTime)
    }


    private fun increaseTempo() {
        playbackSpeed += deltaTemp
        if (playbackSpeed > maxTemp) playbackSpeed = maxTemp
        updateTempo()
        saveTempoForSong(currentSongName, playbackSpeed)
    }

    private fun decreaseTempo() {
        playbackSpeed -= deltaTemp
        if (playbackSpeed < minTemp) playbackSpeed = minTemp
        updateTempo()
        saveTempoForSong(currentSongName, playbackSpeed)
    }

    private fun resetTempo() {
        playbackSpeed = defaultTemp
        updateTempo()
        saveTempoForSong(currentSongName, playbackSpeed)
    }

    private fun updateTempo() {
        exoPlayer.setPlaybackParameters(PlaybackParameters(playbackSpeed / 100f))
    }

    fun playMedia(path: String) {

        if (requestAudioFocus()) {
            // Audio focus was granted, start the playback
            val mediaItem = MediaItem.fromUri(Uri.parse(path))
            currentSongName = mediaItem.mediaMetadata.title?.toString() ?: File(path).name
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            playbackSpeed = getTempoForSong(currentSongName)
            updateTempo()
            exoPlayer.playWhenReady = true
            songStartTime = System.currentTimeMillis()
            startSongDurationTracking(currentSongName)
        } else {
            // Audio focus was not granted, do not start the playback
            Log.d("AudioFocus", "Audio focus was not granted.")
        }
    }

    private fun startSongDurationTracking(songName: String?) {
        songStartTime = System.currentTimeMillis()
    }


    private fun playRandomMedia() {
        if (songList.isEmpty()) {
            return
        }

        val songAdapter = songRecyclerView.adapter as SongAdapter
        if (songAdapter.itemCount == 0) {
            return
        }

        val randomIndex = Random().nextInt(songAdapter.itemCount)
        playMedia(songList[randomIndex].path)

        val previousSelectedPosition = songAdapter.selectedPosition
        songAdapter.selectedPosition = randomIndex
        songAdapter.notifyItemChanged(previousSelectedPosition)
        songAdapter.notifyItemChanged(randomIndex)

        songRecyclerView.layoutManager?.scrollToPosition(randomIndex)
    }
    
    override fun onPause() {
        super.onPause()
        if (exoPlayer.isPlaying) {
            exoPlayer.playWhenReady = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateFileList()
        if (!exoPlayer.isPlaying) {
            exoPlayer.playWhenReady = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
        mediaSession.release()
    }

    private fun saveTempoForSong(songName: String?, tempo: Int) {
        if (songName == null) {
            return
        }

        sharedPreferences.edit().putInt(songName + "_tempo", tempo).apply()
    }

    private fun getTempoForSong(songName: String?): Int {
        if (songName == null) {
            return defaultTemp
        }

        return sharedPreferences.getInt(songName + "_tempo", defaultTemp)
    }

    private fun saveDurationForSong(songName: String?, duration: Long) {
        if (songName == null) {
            return
        }

        sharedPreferences.edit().putLong(songName + "_duration", duration).apply()
    }

    private fun getDurationForSong(songName: String?): Long {
        if (songName == null) {
            return 0L
        }

        return sharedPreferences.getLong(songName + "_duration", 0L)
    }

    private fun updateSongTempo(songName: String?, tempo: Int) {
        if (songName == null) {
            return
        }

        val song = songList.find { it.name == songName }
        song?.tempo = tempo
    }

    private fun updateSongDuration(songName: String?, duration: Long) {
        if (songName == null) {
            return
        }

        val song = songList.find { it.name == songName }
        song?.duration = duration
    }

    private fun updateSongInRecyclerView(songName: String?) {
        val songAdapter = songRecyclerView.adapter as SongAdapter
        songName?.let {
            val songIndex = songList.indexOfFirst { it.name == songName }
            if (songIndex != -1) {
                songAdapter.notifyItemChanged(songIndex)
            }
        }
    }

    private fun playNextSong() {
        val currentIndex = songList.indexOfFirst { it.name == currentSongName }
        val nextIndex = if (currentIndex + 1 < songList.size) currentIndex + 1 else 0
        val nextSong = songList[nextIndex]
        playMedia(nextSong.path)
    }

    private fun requestAudioFocus(): Boolean {
        val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> Log.d("AudioFocus", "AUDIOFOCUS_GAIN")
                    AudioManager.AUDIOFOCUS_LOSS -> Log.d("AudioFocus", "AUDIOFOCUS_LOSS")
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Log.d("AudioFocus", "AUDIOFOCUS_LOSS_TRANSIENT")
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Log.d("AudioFocus", "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK")
                }
            }.build()

        val result = audioManager.requestAudioFocus(focusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun convertMillisToMinutesAndSeconds(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)

        return String.format("%d:%02d", minutes, seconds)
    }

    private inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val songTitle: TextView = view.findViewById(R.id.songTitle)
        val songTempoAndDuration: TextView = view.findViewById(R.id.songTempoAndDuration)
        val songLayout: LinearLayout = view.findViewById(R.id.songLayout)

        fun bind(song: Song) {
            songTitle.text = song.name
            val songDuration = getDurationForSong(song.name)
            val duration = convertMillisToMinutesAndSeconds(songDuration)

            songTempoAndDuration.text = "${song.tempo}% $duration"
        }
    }

    private inner class SongAdapter(val songs: MutableList<Song>) : RecyclerView.Adapter<SongViewHolder>() {

        var selectedPosition = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.song_item, parent, false)
            return SongViewHolder(view)
        }

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            val song = songs[position]
            holder.bind(song)

            holder.songLayout.setOnClickListener {
                playMedia(song.path)
                notifyItemChanged(selectedPosition)
                selectedPosition = position
                notifyItemChanged(selectedPosition)
            }
            holder.songLayout.setBackgroundColor(if (position == selectedPosition) Color.DKGRAY else Color.TRANSPARENT)
        }


        override fun getItemCount() = songs.size
    }
}

