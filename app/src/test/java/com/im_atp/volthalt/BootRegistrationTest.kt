package com.im_atp.volthalt

import android.content.Intent
import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootRegistrationTest {
    @Test fun bootBroadcastHasManifestReceiver() {
        val app = RuntimeEnvironment.getApplication()
        val receivers = app.packageManager.queryBroadcastReceivers(
            Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(app.packageName),
            PackageManager.GET_META_DATA
        )
        assertTrue("Boot permission alone does not register a receiver", receivers.isNotEmpty())
    }
}
