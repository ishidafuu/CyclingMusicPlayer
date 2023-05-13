import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cyclemusic.R
import java.io.File
import java.util.Random


data class Song(val name: String, val path: String)
class PlaybackFragment : Fragment() {

    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var songRecyclerView: RecyclerView
    private lateinit var stopButton: Button
    private lateinit var playButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var songList: MutableList<Song>
    private val viewModel: SharedViewModel by activityViewModels()
    private var folderPath: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.playback_fragment, container, false)

        viewModel.selectedFolderPath.observe(viewLifecycleOwner, { folderPath ->
            setFolderPath(folderPath)
        })

        songRecyclerView = view.findViewById(R.id.songRecyclerView)
        stopButton = view.findViewById(R.id.stopButton)
        playButton = view.findViewById(R.id.playButton)

        mediaPlayer = MediaPlayer()
        songList = ArrayList()

        populateSongList()

        val adapter = SongAdapter(songList)

        songRecyclerView.adapter = adapter
        songRecyclerView.layoutManager = LinearLayoutManager(context)

        stopButton.setOnClickListener {
            stopMedia()
        }

        playButton.setOnClickListener {
            playRandomMedia()
        }

        sharedPreferences = requireContext().getSharedPreferences("CycleMusicPrefs", Context.MODE_PRIVATE)
        val lastFolderPath = sharedPreferences.getString("LastFolderPath", null)
        if (lastFolderPath != null) {
            setFolderPath(lastFolderPath)
        }

        return view
    }

    private fun populateSongList() {
        songList = if (folderPath != null) {
            ArrayList(fetchAllMp3Files(File(folderPath)))
        } else {
            ArrayList(fetchAllMp3Files(Environment.getExternalStorageDirectory()))
        }
    }

    private fun fetchAllMp3Files(root: File): List<Song> {
        val songFiles = ArrayList<Song>()
        val files = root.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory) {
                    songFiles.addAll(fetchAllMp3Files(file))
                } else if (file.name.endsWith(".mp3", ignoreCase = true)) {
                    songFiles.add(Song(file.name, file.absolutePath))
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

    fun stopMedia() {

        mediaPlayer.stop()
        mediaPlayer.reset() // MediaPlayerの状態をリセットします
        val songAdapter = (songRecyclerView.adapter as SongAdapter)
        val previousSelectedPosition = songAdapter.selectedPosition
        songAdapter.selectedPosition = -1
        songAdapter.notifyItemChanged(previousSelectedPosition)
    }

    fun playMedia(path: String) {
//        val randomIndex = Random().nextInt(songList.size)
//        playMedia(songList[randomIndex])
        mediaPlayer.stop()
        mediaPlayer.reset() // MediaPlayerの状態をリセットします
        mediaPlayer.setDataSource(path)
        mediaPlayer.prepare()
        mediaPlayer.start()
    }


    fun playRandomMedia() {
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
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
//            stopButton.text = getString(R.string.play_text)
        }
    }

    override fun onResume() {
        super.onResume()
        updateFileList()
//        if (!mediaPlayer.isPlaying) {
//            mediaPlayer.start()
//            playPauseButton.text = getString(R.string.pause_text)
//        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (mediaPlayer.isPlaying) {
            mediaPlayer.stop()
        }
        mediaPlayer.release()
    }

    private inner class SongViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val songNameTextView: TextView = view.findViewById(R.id.songNameTextView)
        val songLayout: LinearLayout = view.findViewById(R.id.songLayout)
    }

    private inner class SongAdapter(val songs: MutableList<Song>) : RecyclerView.Adapter<SongViewHolder>() {

        var selectedPosition = -1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
            val view = layoutInflater.inflate(R.layout.song_item, parent, false)
            return SongViewHolder(view)
        }

        override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
            val song = songs[position]
            holder.songNameTextView.text = song.name
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
