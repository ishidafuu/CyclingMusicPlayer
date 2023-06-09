import android.content.Context
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.fragment.app.Fragment
import com.example.cyclemusic.R
import java.io.File

class FolderSelectionFragment : Fragment(), OnBackPressed {

    private lateinit var backButton: Button
    private lateinit var currentDir: File
    private lateinit var folderListView: ListView
    private lateinit var folderList: MutableList<String>
    private lateinit var folders: MutableList<File>

    private var listener: OnFolderSelectedListener? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.folder_selection_fragment, container, false)

        setupViews(view)
        populateFolderList()
        setupListView()
        setupButtons()

        return view
    }

    private fun setupViews(view: View) {
        backButton = view.findViewById(R.id.backButton)
        folderListView = view.findViewById(R.id.folderListView)
        folderList = ArrayList()
        folders = ArrayList()

        currentDir = Environment.getExternalStorageDirectory()
    }

    private fun setupListView() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            folderList
        )

        folderListView.adapter = adapter

        folderListView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val selectedFolder = folders[position]
                handleFolderSelection(selectedFolder, adapter)
            }
    }

    private fun handleFolderSelection(selectedFolder: File, adapter: ArrayAdapter<String>) {
        if (selectedFolder.isDirectory) {
            currentDir = selectedFolder
            populateFolderList()
            adapter.notifyDataSetChanged()
            folderSelected(currentDir.absolutePath)
        } else if (selectedFolder.name.endsWith(".mp3", ignoreCase = true)) {
            // Handle .mp3 file selection
        }
    }

    private fun setupButtons() {
        backButton.setOnClickListener {
            backPressed()
        }
    }

    private fun backPressed() {
        if (currentDir.path != Environment.getExternalStorageDirectory().path) {
            currentDir = currentDir.parentFile as File
            populateFolderList()
            (folderListView.adapter as ArrayAdapter<*>).notifyDataSetChanged()
            folderSelected(currentDir.absolutePath)
        }
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnFolderSelectedListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement OnFolderSelectedListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    private fun populateFolderList() {
        folderList.clear()
        folders.clear()
        val files = currentDir.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory
                    || file.name.endsWith(".mp3", ignoreCase = true)
                ) {
                    folderList.add(file.name)
                    folders.add(file)
                }
            }
        }
    }

    private fun folderSelected(folderPath: String) {
        listener?.onFolderSelected(folderPath)
    }

    override fun onBackPressed(): Boolean {
        backPressed()
        return true
    }
}
