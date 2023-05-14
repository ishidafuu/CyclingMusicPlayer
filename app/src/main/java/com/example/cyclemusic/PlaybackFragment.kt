import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyclemusic.R
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.util.Random

data class Song(val name: String, val path: String, var tempo: Int)

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
    private val viewModel: SharedViewModel by activityViewModels()
    private var folderPath: String? = null
    private var currentSongName: String? = null
    private val defaultTemp: Int = 100
    private val deltaTemp: Int = 2
    private val minTemp: Int = 10
    private val maxTemp: Int = 200
    private var playbackSpeed: Int = defaultTemp

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.playback_fragment, container, false)

        setupViews(view)
        setupObservables()
        setupButtons()
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

        populateSongList()

        val songAdapter = songRecyclerView.adapter as SongAdapter
        songAdapter.songs.clear()
        songAdapter.songs.addAll(songList as ArrayList<Song>)
        songAdapter.notifyDataSetChanged()
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
        currentSongName = null
        songAdapter.selectedPosition = -1
        songAdapter.notifyItemChanged(previousSelectedPosition)
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
        val mediaItem = MediaItem.fromUri(Uri.parse(path))
        currentSongName = mediaItem.mediaMetadata.title?.toString() ?: File(path).name
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        playbackSpeed = getTempoForSong(currentSongName)
        updateTempo()
        exoPlayer.playWhenReady = true
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

    private fun onSongSelected(position: Int) {
        try {
            val selectedFile = songList[position]
            playMedia(selectedFile.path)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
    }

    private fun saveTempoForSong(songName: String?, tempo: Int) {
        if (songName == null) {
            return
        }

        sharedPreferences.edit().putInt(songName, tempo).apply()
    }

    private fun getTempoForSong(songName: String?): Int {
        if (songName == null) {
            return defaultTemp
        }

        val tempo: Any = sharedPreferences.getAll()[songName] ?: defaultTemp
        
        if (tempo is Int == false) {
            saveTempoForSong(songName, defaultTemp)
            return defaultTemp
        }
        
        return tempo
        
//        return sharedPreferences.getInt(songName, defaultTemp)
    }

    private fun updateSongTempo(songName: String?, tempo: Int) {
        if (songName == null) {
            return
        }

        val song = songList.find { it.name == songName }
        song?.tempo = tempo
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

    private inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val songTitle: TextView = view.findViewById(R.id.songTitle)
        val songTempo: TextView = view.findViewById(R.id.songTempo)
        val songLayout: LinearLayout = view.findViewById(R.id.songLayout)

        fun bind(song: Song) {
            songTitle.text = song.name
            songTempo.text = "Tempo: ${song.tempo}"
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

