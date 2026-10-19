package no.vardir.hodd

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/** Tests intentionally replace fixtures; never run them against a phone's real collection. */
class HoddTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        if (Build.HARDWARE !in setOf("ranchu", "goldfish", "cutf_cvm", "cuttlefish")) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "Hodd data tests require a disposable emulator.") })
            return
        }
        super.onCreate(arguments)
    }
}
