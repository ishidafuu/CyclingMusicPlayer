import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    val selectedFolderPath: MutableLiveData<String> = MutableLiveData()
}
