package com.situstechnologies.OXray.ui.debug

import android.content.Intent
import android.os.Bundle
import com.situstechnologies.OXray.R
import com.situstechnologies.OXray.databinding.ActivityDebugBinding
import com.situstechnologies.OXray.ui.shared.AbstractActivity

class DebugActivity : AbstractActivity<ActivityDebugBinding>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.title_debug)
        binding.scanVPNButton.setOnClickListener {
            startActivity(Intent(this, VPNScanActivity::class.java))
        }
    }
}