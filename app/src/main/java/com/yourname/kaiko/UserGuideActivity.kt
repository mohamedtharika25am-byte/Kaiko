package com.yourname.kaiko

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.yourname.kaiko.databinding.ActivityUserGuideBinding

/**
 * Dedicated Kaiko User Guide screen.
 * Simple, easy-to-read sections explaining triggers, 3x press setup, location, guardians,
 * and active SOS controls.
 */
class UserGuideActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserGuideBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserGuideBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
